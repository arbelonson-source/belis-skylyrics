package dev.beli.skylyrics.render;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingDeque;

import javax.imageio.ImageIO;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Song and playlist covers for the song library. A few threads download them, they're kept on disk
 * so they show up straight away next time, and the most recently drawn ones stay on the GPU.
 * Everything except the download threads runs on the render thread.
 */
public final class CoverCache {
	private static final Logger LOG = LoggerFactory.getLogger("SkyLyrics");
	public static final int SIZE = 128;
	/** Matches the rounding of the library cards. */
	private static final float CORNER = SIZE * 5f / 56;
	private static final int KEEP = 96;
	private static final int THREADS = 4;
	private static final int UPLOADS_PER_FRAME = 4;
	/** When scrolling fast, covers that scrolled past are dropped instead of downloaded. */
	private static final int MAX_WAITING = 48;
	private static final int DISK_FILES = 600;
	private static final long FADE_NS = 250_000_000L;

	/** One cover. Only handed out once it's on the GPU. */
	public static final class Cover {
		private final String url;
		/** Its texture name, given when it goes on the GPU (see {@link CoverCache#upload}); render thread only. */
		private @Nullable Identifier id;
		private volatile @Nullable NativeImage image;
		private volatile boolean dropped;
		private volatile int color = AlbumArt.FALLBACK_COLOR;
		/** When the download last failed, or 0 while it hasn't. */
		private volatile long failedAt;
		/** How many loads have failed; after {@link #MAX_FAILURES} it isn't tried again this session. */
		private volatile int failures;
		private boolean uploaded;
		private long shownAt;

		private Cover(String url) {
			this.url = url;
		}

		/** Its texture name. Only for a cover {@link CoverCache#get(String)} handed out, which is on the GPU. */
		public Identifier id() {
			return id;
		}

		/** A bright colour from the cover, for its glow. */
		public int color() {
			return color;
		}

		/** 0 to 1 over the first moment after it arrived, for fading it in. */
		public float shown(long now) {
			return Math.clamp((now - shownAt) / (float) FADE_NS, 0f, 1f);
		}
	}

	/** The most a cover's body may take to arrive. */
	private static final long BODY_TIMEOUT_S = 20;

	/** A cover whose download failed is tried again after this long. */
	static final long RETRY_NS = 120_000_000_000L;

	/** A cover that failed this many times shows the no-cover tile for the rest of the session. */
	static final int MAX_FAILURES = 3;

	/** Whether a download that failed at {@code failedAt} (nanoTime, 0 for never) is due another go. */
	public static boolean retryDue(long failedAt, long now) {
		return failedAt != 0 && now - failedAt >= RETRY_NS;
	}

	/** Fetches and decodes one cover; tests swap it. */
	interface Reader {
		@Nullable BufferedImage read(String url) throws Exception;
	}

	private final Path dir;
	private final Reader reader;
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
	/** Most recently drawn last. */
	private final LinkedHashMap<String, Cover> covers = new LinkedHashMap<>(16, 0.75f, true);
	/** Newest request first, so what's on screen now loads before what scrolled past. */
	private final LinkedBlockingDeque<Cover> waiting = new LinkedBlockingDeque<>();
	private final ConcurrentLinkedQueue<Cover> ready = new ConcurrentLinkedQueue<>();
	private final List<Thread> threads = new ArrayList<>();
	/** How many texture names were made; with {@link #freeIds} it stays about {@link #KEEP}. */
	private int made;
	/**
	 * Texture names let go of by covers that left the cache, for the next covers put on the GPU. Minecraft 1.21.4 and 1.21.5
	 * keep a menu render type for every texture name ever drawn until the game closes, so a new name for every cover would
	 * add one for each cover seen in a long session of browsing. A name let go of while the library draws its covers is
	 * only given out again by a later frame's {@link #upload}, which runs before the covers are drawn, so no cover is ever
	 * drawn under the name another one had that frame. Render thread only.
	 */
	private final java.util.ArrayDeque<Identifier> freeIds = new java.util.ArrayDeque<>();

	public CoverCache(Path dir) {
		this.dir = dir;
		this.reader = this::read;
	}

	CoverCache(Path dir, Reader reader) {
		this.dir = dir;
		this.reader = reader;
	}

	/** The cover at this address if it's ready to draw; otherwise starts loading it and returns null. */
	public @Nullable Cover get(String url) {
		return get(url, System.nanoTime());
	}

	@Nullable Cover get(String url, long now) {
		if (!url.startsWith("https://") && !url.startsWith("http://")) {
			return null;
		}
		Cover cover = covers.get(url);
		if (cover == null) {
			cover = new Cover(url);
			covers.put(url, cover);
			start();
			waiting.offerFirst(cover);
			while (waiting.size() > MAX_WAITING) {
				Cover old = waiting.pollLast();
				if (old != null) {
					old.dropped = true;
					covers.remove(old.url, old);
				}
			}
			trim();
		}
		if (!cover.uploaded && cover.image == null && cover.failures < MAX_FAILURES && retryDue(cover.failedAt, now)) {
			cover.failedAt = 0;
			waiting.offerFirst(cover);
		}
		return cover.uploaded ? cover : null;
	}

	/** Moves a few freshly loaded covers to the GPU. Call once a frame while covers are shown. */
	public void upload() {
		for (int i = 0; i < UPLOADS_PER_FRAME; i++) {
			Cover cover = ready.poll();
			if (cover == null) {
				return;
			}
			NativeImage image = cover.image;
			cover.image = null;
			if (image == null) {
				continue;
			}
			if (cover.dropped) {
				image.close();
				continue;
			}
			Identifier id = freeIds.pollFirst();
			if (id == null) {
				id = Identifier.fromNamespaceAndPath("skylyrics", "dynamic/cover/" + made++);
			}
			textures.register(id, image);
			cover.id = id;
			cover.uploaded = true;
			cover.shownAt = System.nanoTime();
		}
	}

	private void trim() {
		Iterator<Map.Entry<String, Cover>> it = covers.entrySet().iterator();
		while (covers.size() > KEEP && it.hasNext()) {
			Cover cover = it.next().getValue();
			it.remove();
			forget(cover);
		}
	}

	private void forget(Cover cover) {
		cover.dropped = true;
		if (cover.uploaded) {
			cover.uploaded = false;
			textures.release(cover.id);
			freeIds.add(cover.id);
		}
	}

	/** Puts covers on the GPU under a texture name and takes them off again; tests, which have no game, swap it. */
	interface Textures {
		void register(Identifier id, NativeImage image);

		void release(Identifier id);
	}

	/** The game's texture manager. */
	private static final class GameTextures implements Textures {
		@Override
		public void register(Identifier id, NativeImage image) {
			DynamicTexture texture = new DynamicTexture(() -> "SkyLyrics cover", image) {
				{
					sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
				}
			};
			Minecraft.getInstance().getTextureManager().register(id, texture);
		}

		@Override
		public void release(Identifier id) {
			Minecraft.getInstance().getTextureManager().release(id);
		}
	}

	/** Render thread only. */
	Textures textures = new GameTextures();
	/** Turns a downloaded cover into the rounded square the GPU gets; tests swap in one that holds no native memory. */
	java.util.function.Function<BufferedImage, NativeImage> toImage = image -> AlbumArt.shrink(image, SIZE, CORNER);

	private void start() {
		if (!threads.isEmpty()) {
			return;
		}
		for (int i = 0; i < THREADS; i++) {
			boolean first = i == 0;
			Thread t = new Thread(() -> {
				if (first) {
					tidyDisk();
				}
				work();
			}, "SkyLyrics covers " + (i + 1));
			t.setDaemon(true);
			t.start();
			threads.add(t);
		}
	}

	private void work() {
		try {
			while (true) {
				Cover cover = waiting.takeFirst();
				if (!cover.dropped) {
					load(cover);
				}
			}
		} catch (InterruptedException e) {
			// shutting down
		}
	}

	private void load(Cover cover) {
		try {
			BufferedImage image = reader.read(cover.url);
			if (cover.dropped) {
				return;
			}
			if (image == null) {
				fail(cover);
				return;
			}
			cover.color = AlbumArt.pickColor(image);
			cover.image = toImage.apply(image);
			ready.add(cover);
		} catch (InterruptedException e) {
			// shutting down: keep the flag so work() ends
			Thread.currentThread().interrupt();
		} catch (java.io.IOException e) {
			if (e instanceof javax.imageio.IIOException) {
				// The bytes arrived but won't decode (CMYK JPEG, truncated file): that's the cover's fault, so it counts
				fail(cover);
				LOG.debug("Couldn't decode cover {}: {}", cover.url, e.toString());
				return;
			}
			// Network trouble (offline, timeout): retried later, and not counted against the cover
			cover.failedAt = Math.max(1, System.nanoTime());
			LOG.debug("Couldn't load cover {}: {}", cover.url, e.toString());
		} catch (Throwable e) {
			// Not just Exception: a decoder's LinkageError or a StackOverflowError must not kill the worker thread
			fail(cover);
			LOG.debug("Couldn't load cover {}: {}", cover.url, e.toString());
		}
	}

	private static void fail(Cover cover) {
		cover.failures++;
		cover.failedAt = Math.max(1, System.nanoTime());
	}

	/** The biggest cover body taken, and the biggest picture decoded; real covers are 300-640 px and well under 1 MB. */
	static final int MAX_BYTES = 8 * 1024 * 1024;
	private static final int MAX_SIDE = 4096;

	/** False when the announced length is over the limit; -1 (not announced) passes and is capped while reading. */
	static boolean acceptable(long contentLength) {
		return contentLength <= MAX_BYTES;
	}

	/** Reads at most max bytes; null when the stream has more than that. */
	static byte @Nullable [] readCapped(java.io.InputStream in, int max) throws java.io.IOException {
		byte[] bytes = in.readNBytes(max + 1);
		return bytes.length > max ? null : bytes;
	}

	static boolean sizeOk(int width, int height) {
		return width > 0 && height > 0 && width <= MAX_SIDE && height <= MAX_SIDE;
	}

	/**
	 * The picture formats covers come in. Nothing else is decoded: a cover is whatever the music app hands over (a song
	 * file's own picture, a podcast's), and Java's TIFF decoder makes room for whatever a strip's length field says, so a
	 * 1 KB file that claims 16 x 16 pixels had it ask for up to 2 GB at once.
	 */
	private static final java.util.Set<String> FORMATS = java.util.Set.of("png", "jpeg", "jpg", "gif", "bmp");

	/** The decoder for these bytes when they are in one of the {@link #FORMATS}, else null (no decoder takes them, or a format covers don't come in). */
	private static javax.imageio.@Nullable ImageReader coverReader(javax.imageio.stream.ImageInputStream in) {
		Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReaders(in);
		if (!readers.hasNext()) {
			return null;
		}
		javax.imageio.ImageReader reader = readers.next();
		try {
			if (FORMATS.contains(reader.getFormatName().toLowerCase(java.util.Locale.ROOT))) {
				return reader;
			}
		} catch (java.io.IOException | RuntimeException e) {
			// a decoder that can't say what it reads isn't trusted with one either
		}
		reader.dispose();
		return null;
	}

	/**
	 * The most scans of a progressive JPEG that are decoded. Encoders write about ten, each one a finer pass over the whole
	 * picture; one that adds nothing takes a few dozen bytes, and the decoder still goes over the whole picture for it, so
	 * a cover made of thousands of them kept a cover thread busy for minutes (one of the 8 MB allowed, for hours).
	 */
	static final int MAX_PASSES = 32;

	/** How every cover is read: at most {@link #MAX_PASSES} passes. */
	private static javax.imageio.ImageReadParam limited(javax.imageio.ImageReader reader) {
		javax.imageio.ImageReadParam param = reader.getDefaultReadParam();
		param.setSourceProgressivePasses(0, MAX_PASSES);
		return param;
	}

	/**
	 * Decodes a cover no bigger than needed: the reader skips pixels while reading (source subsampling), so a huge
	 * cover never sits whole on the heap. The result's longer side lands in [2 * target, 4 * target] (and the
	 * shorter one stays at least {@code target} where the picture allows); pictures up to 4 * target decode as they are.
	 * When subsampling fails it is read whole, by the same decoder. Null when no decoder takes the bytes or they are in
	 * a format covers don't come in (see {@link #FORMATS}); covers saved on disk come through here too.
	 */
	static @Nullable BufferedImage decodeSmall(byte[] bytes, int target) throws java.io.IOException {
		try (javax.imageio.stream.ImageInputStream in = new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
			javax.imageio.ImageReader reader = coverReader(in);
			if (reader == null) {
				return null;
			}
			try {
				reader.setInput(in, true, true);
				int step = subsampling(reader.getWidth(0), reader.getHeight(0), target);
				javax.imageio.ImageReadParam param = limited(reader);
				param.setSourceSubsampling(step, step, 0, 0);
				BufferedImage image = reader.read(0, param);
				if (image != null) {
					return image;
				}
			} catch (java.io.IOException | RuntimeException e) {
				LOG.debug("Subsampled cover decode failed, reading whole", e);
			} finally {
				reader.dispose();
			}
		}
		try (javax.imageio.stream.ImageInputStream in = new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
			javax.imageio.ImageReader reader = coverReader(in);
			if (reader == null) {
				return null;
			}
			try {
				reader.setInput(in, true, true);
				return reader.read(0, limited(reader));
			} finally {
				reader.dispose();
			}
		}
	}

	/** Every how many pixels to read so the longer side lands in [2 * target, 4 * target] (the shrink's box filter needs a few samples per output pixel); 1 up to 4 * target. */
	static int subsampling(int width, int height, int target) {
		int longer = Math.max(width, height), shorter = Math.min(width, height);
		if (longer <= 4 * target) {
			return 1; // already small: decode as is
		}
		int step = (longer + 4 * target - 1) / (4 * target);
		return Math.max(1, Math.min(step, shorter / target));
	}

	/** Checks the picture's format and size from its header, before anything is decoded. */
	static boolean dimensionsOk(byte[] bytes) {
		try (javax.imageio.stream.ImageInputStream in = new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
			javax.imageio.ImageReader reader = coverReader(in);
			if (reader == null) {
				return false;
			}
			try {
				reader.setInput(in, true, true);
				return sizeOk(reader.getWidth(0), reader.getHeight(0));
			} finally {
				reader.dispose();
			}
		} catch (java.io.IOException | RuntimeException e) {
			return false;
		}
	}

	/** Whether a non-2xx answer counts against the cover; one that may pass (5xx, 429, 408, 407) is retried like network trouble instead. */
	static boolean countsAsFailure(int status) {
		return !CoverRetry.temporaryStatus(status);
	}

	private @Nullable BufferedImage read(String url) throws Exception {
		Path file = dir.resolve(fileName(url));
		BufferedImage cached = readCached(file);
		if (cached != null) {
			return cached;
		}
		HttpResponse<java.io.InputStream> response = dev.beli.skylyrics.util.Net.getFollowing(http(), URI.create(url), Duration.ofSeconds(10));
		byte[] bytes;
		try (java.io.InputStream body = response.body()) {
			// A server that stalls mid-body would hold this worker for ever, so the stream is closed after a while
			dev.beli.skylyrics.util.Net.closeAfter(body, Duration.ofSeconds(BODY_TIMEOUT_S));
			int status = response.statusCode();
			if (status / 100 != 2 && !countsAsFailure(status)) {
				throw new java.io.IOException("HTTP " + status);
			}
			if (status / 100 != 2 || !acceptable(response.headers().firstValueAsLong("Content-Length").orElse(-1))) {
				return null;
			}
			bytes = readCapped(body, MAX_BYTES);
		}
		if (bytes == null || !dimensionsOk(bytes)) {
			return null;
		}
		BufferedImage image;
		try {
			image = decodeSmall(bytes, SIZE);
		} catch (javax.imageio.IIOException e) {
			throw e;
		} catch (java.io.IOException e) {
			throw new javax.imageio.IIOException("Cover won't decode", e); // not network trouble: see load()
		}
		if (image != null) {
			cache(dir, file, image);
		}
		return image;
	}

	/**
	 * Saves what the mod actually uses (the cover as a {@link #SIZE}-px square) instead of the downloaded bytes, which can
	 * be several MB each. Files saved by older versions hold the raw download and still read the same way.
	 */
	static void cache(Path dir, Path file, BufferedImage image) {
		byte[] small;
		try {
			small = encodeSmall(image);
		} catch (java.io.IOException | RuntimeException e) {
			LOG.debug("Could not encode cover {} for the cache", file, e);
			return;
		}
		store(dir, file, small);
	}

	/**
	 * The centred square of the picture, box-filtered down to {@link #SIZE} px (never enlarged), as PNG bytes. The mod
	 * crops to a square and ignores transparency everywhere, so this is opaque RGB; any source type is read through getRGB.
	 */
	static byte[] encodeSmall(BufferedImage src) throws java.io.IOException {
		int side = Math.min(src.getWidth(), src.getHeight());
		int x0 = (src.getWidth() - side) / 2, y0 = (src.getHeight() - side) / 2;
		int size = Math.min(side, SIZE);
		BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < size; y++) {
			int sy0 = y0 + y * side / size, sy1 = Math.max(sy0 + 1, y0 + (y + 1) * side / size);
			for (int x = 0; x < size; x++) {
				int sx0 = x0 + x * side / size, sx1 = Math.max(sx0 + 1, x0 + (x + 1) * side / size);
				long r = 0, g = 0, b = 0, n = 0;
				for (int sy = sy0; sy < sy1; sy++) {
					for (int sx = sx0; sx < sx1; sx++) {
						int p = src.getRGB(sx, sy);
						r += (p >> 16) & 0xFF;
						g += (p >> 8) & 0xFF;
						b += p & 0xFF;
						n++;
					}
				}
				out.setRGB(x, y, (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n));
			}
		}
		java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		// In memory, not through a temp file: a locked temp folder (antivirus, a locked-down Windows PC) would stop every save
		try (javax.imageio.stream.ImageOutputStream stream = new javax.imageio.stream.MemoryCacheImageOutputStream(bytes)) {
			if (!ImageIO.write(out, "png", stream)) {
				throw new java.io.IOException("no PNG writer");
			}
		}
		return bytes.toByteArray();
	}

	/** Saved covers bigger than this are older raw downloads (a small one is 20-40 KB) and are saved again small. */
	static final int SHRINK_OVER_BYTES = 200 * 1024;

	/** The cover saved earlier, or null when there is none, it can't be read now, or the file is damaged (which is then removed so it downloads again). */
	static @Nullable BufferedImage readCached(Path file) {
		if (!Files.isRegularFile(file)) {
			return null;
		}
		byte[] bytes;
		try {
			bytes = Files.readAllBytes(file);
		} catch (java.io.IOException e) {
			// Locked or unreadable just now (another program, Windows): keep it, the download stands in this time
			LOG.debug("Cached cover {} can't be read now", file, e);
			return null;
		}
		try {
			BufferedImage image = decodeSmall(bytes, SIZE);
			if (image != null) {
				file.toFile().setLastModified(System.currentTimeMillis());
				if (bytes.length > SHRINK_OVER_BYTES && file.getParent() != null) {
					// Saved by an older version as the raw download: keep the small form instead (a failure keeps the old file)
					cache(file.getParent(), file, image);
				}
				return image;
			}
		} catch (java.io.IOException | RuntimeException e) {
			LOG.debug("Cached cover {} is damaged", file, e);
		}
		file.toFile().delete();
		return null;
	}

	/** Keeps a downloaded cover on disk for next time; a full or locked disk only costs the next download. */
	static void store(Path dir, Path file, byte[] bytes) {
		store(dir, file, bytes, Files::write, DISK_FILES);
	}

	private static final java.util.concurrent.atomic.AtomicInteger SAVES = new java.util.concurrent.atomic.AtomicInteger();

	/** The write itself, so tests can make it fail. */
	interface Writer {
		void write(Path to, byte[] bytes) throws java.io.IOException;
	}

	static void store(Path dir, Path file, byte[] bytes, Writer writer) {
		store(dir, file, bytes, writer, DISK_FILES);
	}

	/** Every tenth of {@code keep} saves also trims the folder, so a long session can't run far past the cap. */
	static void store(Path dir, Path file, byte[] bytes, Writer writer, int keep) {
		Path temp = null;
		try {
			Files.createDirectories(dir);
			temp = Files.createTempFile(dir, "cover", ".part");
			writer.write(temp, bytes);
			dev.beli.skylyrics.util.SafeFile.replace(temp, file);
			temp = null; // moved
		} catch (java.io.IOException | RuntimeException e) {
			LOG.debug("Could not cache cover {}", file, e);
		} finally {
			if (temp != null) {
				try {
					Files.deleteIfExists(temp);
				} catch (java.io.IOException | RuntimeException ignored) {
					// the next tidy-up of the folder takes it
				}
			}
		}
		if (SAVES.incrementAndGet() % Math.max(1, keep / 10) == 0) {
			tidy(dir.toFile().listFiles(), keep);
		}
	}

	/** Keeps the folder from growing forever: removes the covers not seen for the longest. */
	private void tidyDisk() {
		tidy(dir.toFile().listFiles(), DISK_FILES);
	}

	/** Deletes the oldest files beyond {@code keep}. Times are read once up front: other threads touch them while we sort. */
	static void tidy(File @Nullable [] files, int keep) {
		if (files == null || files.length <= keep) {
			return;
		}
		Map<File, Long> times = new java.util.HashMap<>();
		for (File f : files) {
			times.put(f, f.lastModified());
		}
		Arrays.sort(files, Comparator.comparingLong(times::get));
		for (int i = 0; i < files.length - keep; i++) {
			files[i].delete();
		}
	}

	/** Same address, same file name, and nothing odd in it. */
	static String fileName(String url) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-1").digest(url.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash) + ".img";
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	public void close() {
		threads.forEach(Thread::interrupt);
		for (Cover cover; (cover = ready.poll()) != null; ) {
			NativeImage image = cover.image;
			if (image != null) {
				image.close();
			}
		}
	}
}
