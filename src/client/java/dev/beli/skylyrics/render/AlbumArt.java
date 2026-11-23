package dev.beli.skylyrics.render;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.beli.skylyrics.media.Track;

/**
 * Loads the album cover of the current song: a small texture for the now-playing card,
 * and a bright colour picked from it for the lyrics.
 */
public final class AlbumArt {
	private static final Logger LOG = LoggerFactory.getLogger("SkyLyrics");
	public static final int SIZE = 64;
	/** The most a cover's body may take to arrive. */
	private static final long BODY_TIMEOUT_S = 20;
	public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("skylyrics", "dynamic/album_art");
	/** Used when there's no cover, or it's all grey. */
	public static final int FALLBACK_COLOR = 0xB57BFF;

	/** Built on first use: the SSL context and java.net.http classes cost tens of milliseconds at mod init. */
	private volatile @org.jspecify.annotations.Nullable HttpClient httpClient;

	private HttpClient http() {
		HttpClient c = httpClient;
		if (c == null) {
			synchronized (this) {
				c = httpClient;
				if (c == null) {
					httpClient = c = HttpClient.newBuilder()
						.connectTimeout(Duration.ofSeconds(5))
						.followRedirects(HttpClient.Redirect.NEVER)
						.build();
				}
			}
		}
		return c;
	}
	private final ExecutorService executor;
	/** The load running or waiting for the current cover; the next song's load() cancels it. */
	private @Nullable Future<?> job;

	public AlbumArt() {
		this(() -> true);
	}

	/** @param wanted false while the player has Lyrics turned off: then no cover is downloaded (see {@link #wake}) */
	public AlbumArt(java.util.function.BooleanSupplier wanted) {
		this(Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "SkyLyrics album art");
			t.setDaemon(true);
			return t;
		}), null, wanted);
	}

	AlbumArt(ExecutorService executor) {
		this(executor, null);
	}

	AlbumArt(ExecutorService executor, @Nullable Scheduler scheduler) {
		this(executor, scheduler, () -> true);
	}

	AlbumArt(ExecutorService executor, @Nullable Scheduler scheduler, java.util.function.BooleanSupplier wanted) {
		this.executor = executor;
		this.scheduler = scheduler != null ? scheduler : this::scheduleLater;
		this.wanted = wanted;
	}

	/** Whether covers may be downloaded now: false while the player has Lyrics turned off. */
	private final java.util.function.BooleanSupplier wanted;

	/** Runs something after a delay; replaced in tests. */
	interface Scheduler {
		Future<?> schedule(Runnable task, long delayMs);
	}

	/** Fetches and decodes one cover; replaced in tests. */
	interface Fetcher {
		@Nullable BufferedImage read(String url) throws Exception;
	}

	private final Scheduler scheduler;
	Fetcher fetcher = this::read;
	/** The one pending retry of the current cover, if any. */
	private @Nullable Future<?> retry;
	private java.util.concurrent.@Nullable ScheduledExecutorService timer;

	private synchronized Future<?> scheduleLater(Runnable task, long delayMs) {
		if (timer == null) {
			java.util.concurrent.ScheduledThreadPoolExecutor t = new java.util.concurrent.ScheduledThreadPoolExecutor(1, r -> {
				Thread thread = new Thread(r, "SkyLyrics album art retry");
				thread.setDaemon(true);
				return thread;
			});
			t.setRemoveOnCancelPolicy(true);
			timer = t;
		}
		return timer.schedule(task, delayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
	}
	private final AtomicLong generation = new AtomicLong();
	/** Held while a song change is registered and while a finished cover is accepted, so the colour and the image go together. */
	private final Object accept = new Object();

	volatile int color = FALLBACK_COLOR;
	/**
	 * The newest decoded cover, waiting for the render thread. Both sides only ever swap it with getAndSet, so every image is
	 * owned by exactly one side at a time: the worker closes the one it replaced, the render thread owns the one it took.
	 */
	private final AtomicReference<@Nullable Pending> pending = new AtomicReference<>();
	/** A decoded cover and the song change it belongs to. */
	record Pending(NativeImage image, long gen) {
	}
	/** The generation of the cover on the GPU now, -1 for none yet. */
	private volatile long uploadedGen = -1;
	/** The generation at which the current song (or its cover being dropped) began; covers uploaded before it are another song's. */
	private volatile long songStartGen = 0;
	private @Nullable DynamicTexture texture;
	private String loadedUrl = "";
	private String loadedSong = "";
	/** Closes a cover that arrived for a song we already left; replaced in tests to see it happen. */
	java.util.function.Consumer<NativeImage> closeStale = NativeImage::close;
	/** Turns a decoded cover into the round image the GPU gets; tests swap in one that holds no native memory. */
	java.util.function.Function<BufferedImage, NativeImage> toImage = image -> shrink(image, SIZE, SIZE / 2f);

	/** Bright colour from the cover. Changes smoothly are handled by the caller. */
	public int color() {
		return color;
	}

	/** True if {@link #TEXTURE} currently shows this song's cover. Call {@link #upload()} first. */
	public boolean hasTexture() {
		// A cover of the same song stays on show while a corrected one loads: it counts from the song's first load on
		return uploadedGen >= songStartGen;
	}

	/** Whether a cover from song change {@code coverGen} belongs to the song of change {@code currentGen}. */
	static boolean isCurrent(long coverGen, long currentGen) {
		return coverGen == currentGen;
	}

	/** Records that the cover of song change {@code gen} is on the GPU. */
	void markUploaded(long gen) {
		uploadedGen = gen;
	}

	/*
	 * Locks: accept guards the song change (show, the parked song) and the hand-over of a finished cover. It may be held
	 * while this is taken (booking a retry on the timer, the HTTP client made on first use), never the other way round:
	 * nothing holding this may wait for accept, or a song change and a failing download lock each other up.
	 */

	/** The song whose cover waits for Lyrics to be turned on, or null. Guarded by {@link #accept}. */
	private @Nullable Track parked;
	/** Whether {@link #parked} holds a song, read without the lock by {@link #wake} on every tick. */
	private volatile boolean anyParked;

	/** Lyrics were turned back on: loads the cover of the song that started while they were off. Cheap enough for every tick. */
	public void wake() {
		if (!anyParked || !wanted.getAsBoolean()) {
			return;
		}
		synchronized (accept) {
			Track t = parked;
			parked = null;
			anyParked = false;
			if (t != null) {
				show(t);
			}
		}
	}

	/** The song changed (or its cover did). While Lyrics are off nothing shows a cover, so none is downloaded until {@link #wake}. */
	public void load(Track track) {
		synchronized (accept) {
			if (!wanted.getAsBoolean()) {
				parked = track;
				anyParked = true;
				dropForParked(track);
				return;
			}
			parked = null;
			anyParked = false;
			show(track);
		}
	}

	/** A cover task found Lyrics off: the song waits for {@link #wake}, which starts its cover again. */
	private void parkAgain(Track track, long gen) {
		synchronized (accept) {
			if (generation.get() == gen && parked == null) {
				parked = track;
				anyParked = true;
				loadedUrl = ""; // so the same address is loaded again, not taken as already there
			}
		}
	}

	/**
	 * A song came while Lyrics are off: its cover waits for {@link #wake}, and the last song's goes now, with its colour, as
	 * {@link #show} would drop it, so the Music tab's Now playing card doesn't show it beside this song's title. An album-mate
	 * with the same cover, or the same song's corrected one, keeps what is on show. Called with {@link #accept} held.
	 */
	private void dropForParked(Track track) {
		String url = track.artUrl();
		String song = track.title() + "\n" + track.artist();
		if (url.equals(loadedUrl)) {
			loadedSong = song; // the cover on show is this song's too
			return;
		}
		if (song.equals(loadedSong) && !url.isEmpty()) {
			return;
		}
		loadedUrl = ""; // so wake() loads this song's cover, even the one dropped here when that song comes back
		songStartGen = generation.incrementAndGet();
		color = FALLBACK_COLOR;
		stopLoading();
	}

	/** Stops the last cover's download or retry and drops a decoded one not yet on the GPU. Called with {@link #accept} held. */
	private void stopLoading() {
		Future<?> previous = job;
		job = null;
		if (previous != null) {
			previous.cancel(true); // a stalled download of the last cover must not hold up this one
		}
		Future<?> oldRetry = retry;
		retry = null;
		if (oldRetry != null) {
			oldRetry.cancel(false); // the last song's cover is no longer wanted
		}
		// A cover that finished decoding for the song we just left must not be shown on this one
		Pending stale = pending.getAndSet(null);
		if (stale != null) {
			stale.image().close();
		}
	}

	/** Called with {@link #accept} held (and never with this held, see the lock note above). */
	private void show(Track track) {
		String url = track.artUrl();
		String song = track.title() + "\n" + track.artist();
		if (url.equals(loadedUrl)) {
			loadedSong = song; // an album-mate with the same cover: remember the song, so a later one with another cover is new
			return;
		}
		loadedUrl = url;
		boolean newSong = !song.equals(loadedSong);
		loadedSong = song;
		long gen;
		boolean keepOnFail = !newSong && !url.isEmpty(); // a corrected cover that fails leaves the one we have
		synchronized (accept) {
			gen = generation.incrementAndGet();
			if (newSong || url.isEmpty()) {
				songStartGen = gen; // the old cover goes at once; for the same song it stays until the corrected one is up
			}
			// The last song's colour must not glow on this one while its cover loads (or if it never does). A cover that
			// only changed for the same song (a late album, another image size) keeps the colour until the new one is in.
			if (newSong || url.isEmpty()) {
				color = FALLBACK_COLOR;
			}
			stopLoading();
			if (!url.isEmpty()) {
				job = executor.submit(task(track, url, gen, 0, keepOnFail)); // submit doesn't block, so job is set before any later load() looks
			}
		}
	}

	private Runnable task(Track track, String url, long gen, int failures, boolean keepOnFail) {
		return () -> {
			if (!wanted.getAsBoolean()) {
				parkAgain(track, gen); // Lyrics were turned off while this waited (a retry, say): nothing is asked
				return;
			}
			try {
				BufferedImage image = fetcher.read(url);
				if (generation.get() != gen) {
					return;
				}
				if (image == null) {
					if (keepOnFail) {
						LOG.debug("Corrected album art {} not usable, keeping the current cover", url);
					}
					// A cover we can't read (WebP, a failed answer, a data: URI) must not leave the last song's colour behind
					synchronized (accept) {
						if (generation.get() == gen && !keepOnFail) {
							color = colorFor(null);
						}
					}
					return;
				}
				int picked = colorFor(image);
				NativeImage scaled = toImage.apply(image);
				Pending old;
				synchronized (accept) {
					if (generation.get() != gen) {
						scaled.close();
						return;
					}
					// The colour is taken together with the image, so a song we already left never tints the new one
					color = picked;
					old = pending.getAndSet(new Pending(scaled, gen));
				}
				if (old != null) {
					old.image().close();
				}
				// load() may have moved on between the check above and the hand-over: take our own image back
				if (generation.get() != gen) {
					Pending mine = pending.getAndSet(null);
					if (mine != null) {
						mine.image().close();
					}
				}
			} catch (Exception | OutOfMemoryError e) {
				LOG.debug("Couldn't load album art {}: {}", url, e.toString());
				synchronized (accept) {
					if (generation.get() == gen) {
						if (!keepOnFail) {
							color = FALLBACK_COLOR;
						}
						// No network at song start must not mean no cover for the whole song
						if (e instanceof Exception && failures < CoverRetry.MAX_RETRIES && CoverRetry.temporary(e)) {
							retryLater(track, url, gen, failures + 1, keepOnFail);
						}
					}
				}
			}
		};
	}

	/** Called with {@link #accept} held. Keeps at most one retry pending, and only for the song it was scheduled for. */
	private void retryLater(Track track, String url, long gen, int failures, boolean keepOnFail) {
		Future<?> old = retry;
		if (old != null) {
			old.cancel(false);
		}
		retry = scheduler.schedule(() -> {
			synchronized (accept) {
				if (generation.get() != gen) {
					return;
				}
				retry = null;
				job = executor.submit(task(track, url, gen, failures, keepOnFail));
			}
		}, CoverRetry.delayMs(failures));
	}

	/** Takes the waiting cover; one from a song we already left is closed and dropped. */
	@Nullable Pending takeIfCurrent() {
		Pending taken = pending.getAndSet(null);
		if (taken != null && !isCurrent(taken.gen(), generation.get())) {
			closeStale.accept(taken.image());
			return null;
		}
		return taken;
	}

	/** Test seam: hands over a decoded cover the way the worker does. */
	void offer(NativeImage image, long gen) {
		Pending old = pending.getAndSet(new Pending(image, gen));
		if (old != null) {
			old.image().close();
		}
	}

	/** Moves a freshly loaded cover to the GPU. Render thread only. */
	public void upload() {
		Pending taken = takeIfCurrent();
		if (taken == null) {
			return;
		}
		NativeImage image = taken.image();
		Minecraft mc = Minecraft.getInstance();
		if (texture == null) {
			texture = new DynamicTexture(() -> "SkyLyrics album art", image) {
				{
					// Smooth scaling, so the round cover doesn't get jagged edges when drawn small
					sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
				}
			};
			mc.getTextureManager().register(TEXTURE, texture);
		} else {
			texture.setPixels(image);
			texture.upload();
		}
		// Stamped with the image's own change, not the current one: a load() during the upload leaves this cover marked as the old song's
		markUploaded(taken.gen());
	}

	private @Nullable BufferedImage read(String url) throws Exception {
		Path path = fileUrlPath(url);
		if (path != null) {
			// Only a plain file: a pipe, a device or a folder is never read
			if (!Files.isRegularFile(path) || !CoverCache.acceptable(Files.size(path))) {
				return null;
			}
			try (InputStream in = Files.newInputStream(path)) {
				return decode(CoverCache.readCapped(in, CoverCache.MAX_BYTES));
			}
		}
		if (url.startsWith("http://") || url.startsWith("https://")) {
			HttpResponse<InputStream> response = dev.beli.skylyrics.util.Net.getFollowing(http(), URI.create(url), Duration.ofSeconds(10));
			try (InputStream in = response.body()) {
				if (CoverRetry.temporaryStatus(response.statusCode())) {
					throw new CoverRetry.TemporaryFailure("Cover server answered " + response.statusCode(), null);
				}
				// The request's timeout only covers the wait for the answer: a server that stalls mid-body would hold
				// the single worker (and every later cover) forever, so the stream is closed after a while
				dev.beli.skylyrics.util.Net.closeAfter(in, Duration.ofSeconds(BODY_TIMEOUT_S));
				if (response.statusCode() / 100 != 2
						|| !CoverCache.acceptable(response.headers().firstValueAsLong("Content-Length").orElse(-1))) {
					return null;
				}
				byte[] bytes;
				try {
					bytes = CoverCache.readCapped(in, CoverCache.MAX_BYTES);
				} catch (java.io.IOException e) {
					throw new CoverRetry.TemporaryFailure("Cover download broke off", e); // dropped or stalled body
				}
				return decode(bytes);
			}
		}
		return null; // data: URIs and other odd schemes aren't worth it
	}

	/**
	 * The local path a file cover address points to, or null when it isn't one. Players write these loosely: "file:///a b/c.jpg"
	 * with raw spaces, "file://localhost/a", "file:/a", percent-encoded or raw unicode. Another host's file is not local.
	 */
	private static final java.util.regex.Pattern DRIVE = java.util.regex.Pattern.compile("^[A-Za-z]:$");

	static @Nullable Path fileUrlPath(@Nullable String url) {
		if (url == null) {
			return null;
		}
		try {
			// The strict way first: right for a Windows drive and for any well-formed address on every system
			URI uri = URI.create(url);
			if ("file".equalsIgnoreCase(uri.getScheme())) {
				String authority = uri.getRawAuthority();
				if (authority != null && DRIVE.matcher(authority).matches()) {
					// "file://C:/x.jpg": some players write the drive where the host goes; it is a path, not a computer
					String text = fileUrlString(url);
					try {
						return text == null ? null : safe(Path.of(text));
					} catch (java.nio.file.InvalidPathException e) {
						return null;
					}
				}
				if (authority != null && !authority.isEmpty() && !authority.equalsIgnoreCase("localhost")) {
					return null; // another computer: on Windows that is a network share, which would send login details to it
				}
				if (authority == null || authority.isEmpty()) {
					String raw = uri.getPath();
					if (raw == null || uncLike(raw)) {
						return null;
					}
					return safe(Path.of(uri));
				}
			}
		} catch (RuntimeException e) {
			// raw spaces, "localhost" and other loose forms: decoded by hand below
		}
		String decoded = fileUrlString(url);
		if (decoded == null) {
			return null;
		}
		try {
			return safe(Path.of(decoded));
		} catch (java.nio.file.InvalidPathException e) {
			return null;
		}
	}

	/** Two slashes or backslashes in a row at the start: a network share or device path on Windows. */
	private static boolean uncLike(String path) {
		return path.length() >= 2 && (path.charAt(0) == '/' || path.charAt(0) == '\\') && (path.charAt(1) == '/' || path.charAt(1) == '\\');
	}

	private static @Nullable Path safe(Path path) {
		String text = path.toString();
		return path.isAbsolute() && !text.startsWith("\\\\") && !text.startsWith("//") ? path : null;
	}

	/** The decoded path text of a loose file address ("/home/a b/c.jpg", "C:/Users/c.jpg"), or null when it isn't a local file. */
	static @Nullable String fileUrlString(@Nullable String url) {
		if (url == null || url.length() < 6 || !url.regionMatches(true, 0, "file:", 0, 5)) {
			return null;
		}
		String rest = url.substring(5);
		if (rest.startsWith("//")) {
			int slash = rest.indexOf('/', 2);
			if (slash < 0) {
				return null;
			}
			String host = rest.substring(2, slash);
			if (DRIVE.matcher(host).matches()) {
				rest = "/" + host + rest.substring(slash); // "file://C:/x.jpg" is the path "C:/x.jpg"
			} else if (host.isEmpty() || host.equalsIgnoreCase("localhost")) {
				rest = rest.substring(slash);
			} else {
				return null;
			}
		}
		if (!rest.startsWith("/")) {
			return null;
		}
		java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		for (int i = 0; i < rest.length(); ) {
			int cp = rest.codePointAt(i);
			if (cp == '%' && i + 2 < rest.length() && Character.digit(rest.charAt(i + 1), 16) >= 0 && Character.digit(rest.charAt(i + 2), 16) >= 0) {
				bytes.write(Character.digit(rest.charAt(i + 1), 16) * 16 + Character.digit(rest.charAt(i + 2), 16));
				i += 3;
			} else {
				byte[] b = new String(Character.toChars(cp)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
				bytes.write(b, 0, b.length);
				i += Character.charCount(cp);
			}
		}
		String decoded = bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
		if (decoded.indexOf('\0') >= 0) {
			return null;
		}
		if (uncLike(decoded)) {
			return null;
		}
		// "/C:/Users/x": a Windows drive, without the slash in front
		if (decoded.length() >= 3 && decoded.charAt(0) == '/' && Character.isLetter(decoded.charAt(1)) && decoded.charAt(1) < 128 && decoded.charAt(2) == ':') {
			decoded = decoded.substring(1);
		}
		return decoded;
	}

	/** Decodes the bytes unless there are none (over the byte cap) or the picture header announces too many pixels. */
	static @Nullable BufferedImage decode(byte @Nullable [] bytes) throws java.io.IOException {
		if (bytes == null || !CoverCache.dimensionsOk(bytes)) {
			return null;
		}
		return CoverCache.decodeSmall(bytes, SIZE);
	}

	/** Center-crops to a square, scales to {@code size} and rounds the corners ({@code size / 2} makes a circle). */
	static NativeImage shrink(BufferedImage src, int size, float radius) {
		int side = Math.min(src.getWidth(), src.getHeight());
		int x0 = (src.getWidth() - side) / 2;
		int y0 = (src.getHeight() - side) / 2;
		NativeImage out = new NativeImage(size, size, false);
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				// Box filter: average the source pixels that fall in this output pixel
				int sx0 = x0 + x * side / size, sx1 = Math.max(sx0 + 1, x0 + (x + 1) * side / size);
				int sy0 = y0 + y * side / size, sy1 = Math.max(sy0 + 1, y0 + (y + 1) * side / size);
				long r = 0, g = 0, b = 0, n = 0;
				for (int sy = sy0; sy < sy1; sy += Math.max(1, (sy1 - sy0) / 4)) {
					for (int sx = sx0; sx < sx1; sx += Math.max(1, (sx1 - sx0) / 4)) {
						int p = src.getRGB(sx, sy);
						r += (p >> 16) & 0xFF;
						g += (p >> 8) & 0xFF;
						b += p & 0xFF;
						n++;
					}
				}
				int a = Math.round(255 * cornerAlpha(x, y, size, radius));
				out.setPixel(x, y, a << 24 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n));
			}
		}
		return out;
	}

	/** How much of pixel (x, y) is inside a rounded square: fades out past the edge over about one pixel. */
	static float cornerAlpha(int x, int y, int size, float radius) {
		float cx = Math.clamp(x + 0.5f, radius, size - radius);
		float cy = Math.clamp(y + 0.5f, radius, size - radius);
		float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
		return Math.clamp(radius - (float) Math.sqrt(dx * dx + dy * dy) + 0.5f, 0f, 1f);
	}

	/** The colour for a cover: picked from it, or the fallback when there is none. */
	static int colorFor(@Nullable BufferedImage image) {
		return image == null ? FALLBACK_COLOR : pickColor(image);
	}

	/**
	 * Picks a colour that looks good glowing in the sky: the most common hue among
	 * the colourful pixels, then made bright and saturated enough to read.
	 */
	static int pickColor(BufferedImage image) {
		int buckets = 24;
		double[] weight = new double[buckets];
		double[] satSum = new double[buckets];
		int stepX = Math.max(1, image.getWidth() / 48);
		int stepY = Math.max(1, image.getHeight() / 48);
		float[] hsb = new float[3];
		for (int y = 0; y < image.getHeight(); y += stepY) {
			for (int x = 0; x < image.getWidth(); x += stepX) {
				int p = image.getRGB(x, y);
				if ((p >>> 24) < 32) {
					continue; // (nearly) clear: its colour isn't shown
				}
				java.awt.Color.RGBtoHSB((p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF, hsb);
				float sat = hsb[1], bri = hsb[2];
				if (sat < 0.25f || bri < 0.2f) {
					continue; // greys, blacks and whites don't make a nice glow
				}
				int bucket = Math.min(buckets - 1, (int) (hsb[0] * buckets));
				double w = sat * bri;
				weight[bucket] += w;
				satSum[bucket] += sat * w;
			}
		}
		int best = -1;
		double bestWeight = 0;
		for (int i = 0; i < buckets; i++) {
			// Neighbouring buckets count a bit so a hue split across two still wins
			double w = weight[i] + 0.5 * (weight[(i + 1) % buckets] + weight[(i + buckets - 1) % buckets]);
			if (w > bestWeight) {
				bestWeight = w;
				best = i;
			}
		}
		if (best < 0 || weight[best] < 1.5) {
			return FALLBACK_COLOR;
		}
		float hue = (best + 0.5f) / buckets;
		float sat = (float) Math.max(0.55, Math.min(0.85, satSum[best] / weight[best]));
		return java.awt.Color.HSBtoRGB(hue, sat, 1f) & 0xFFFFFF;
	}

	public void close() {
		executor.shutdownNow();
		Pending image = pending.getAndSet(null);
		if (image != null) {
			image.image().close();
		}
	}
}
