package dev.beli.skylyrics.render;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
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
		private final Identifier id;
		private volatile @Nullable NativeImage image;
		private volatile boolean dropped;
		private volatile int color = AlbumArt.FALLBACK_COLOR;
		/** When the download last failed, or 0 while it hasn't. */
		private volatile long failedAt;
		private boolean uploaded;
		private long shownAt;

		private Cover(String url, Identifier id) {
			this.url = url;
			this.id = id;
		}

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

	/** A cover whose download failed is tried again after this long. */
	static final long RETRY_NS = 120_000_000_000L;

	/** Whether a download that failed at {@code failedAt} (nanoTime, 0 for never) is due another go. */
	public static boolean retryDue(long failedAt, long now) {
		return failedAt != 0 && now - failedAt >= RETRY_NS;
	}

	private final Path dir;
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
						.followRedirects(HttpClient.Redirect.NORMAL)
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
	private int made;

	public CoverCache(Path dir) {
		this.dir = dir;
	}

	/** The cover at this address if it's ready to draw; otherwise starts loading it and returns null. */
	public @Nullable Cover get(String url) {
		if (!url.startsWith("https://") && !url.startsWith("http://")) {
			return null;
		}
		Cover cover = covers.get(url);
		if (cover == null) {
			cover = new Cover(url, Identifier.fromNamespaceAndPath("skylyrics", "dynamic/cover/" + made++));
			covers.put(url, cover);
			start();
			waiting.offerFirst(cover);
			while (waiting.size() > MAX_WAITING) {
				Cover old = waiting.pollLast();
				if (old != null) {
					old.dropped = true;
					covers.remove(old.url);
				}
			}
			trim();
		}
		if (!cover.uploaded && cover.image == null && retryDue(cover.failedAt, System.nanoTime())) {
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
			DynamicTexture texture = new DynamicTexture(() -> "SkyLyrics cover", image) {
				{
					sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
				}
			};
			Minecraft.getInstance().getTextureManager().register(cover.id, texture);
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
			Minecraft.getInstance().getTextureManager().release(cover.id);
		}
	}

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
			BufferedImage image = read(cover.url);
			if (cover.dropped) {
				return;
			}
			if (image == null) {
				cover.failedAt = Math.max(1, System.nanoTime());
				return;
			}
			cover.color = AlbumArt.pickColor(image);
			cover.image = AlbumArt.shrink(image, SIZE, CORNER);
			ready.add(cover);
		} catch (Exception | OutOfMemoryError e) {
			cover.failedAt = Math.max(1, System.nanoTime());
			LOG.debug("Couldn't load cover {}: {}", cover.url, e.toString());
		}
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

	/** Checks the picture size from its header, before anything is decoded. */
	static boolean dimensionsOk(byte[] bytes) {
		try (javax.imageio.stream.ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
			Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReaders(in);
			if (!readers.hasNext()) {
				return false;
			}
			javax.imageio.ImageReader reader = readers.next();
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

	private @Nullable BufferedImage read(String url) throws Exception {
		Path file = dir.resolve(fileName(url));
		BufferedImage cached = readCached(file);
		if (cached != null) {
			return cached;
		}
		HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
		HttpResponse<java.io.InputStream> response = http().send(request, HttpResponse.BodyHandlers.ofInputStream());
		byte[] bytes;
		try (java.io.InputStream body = response.body()) {
			if (response.statusCode() / 100 != 2 || !acceptable(response.headers().firstValueAsLong("Content-Length").orElse(-1))) {
				return null;
			}
			bytes = readCapped(body, MAX_BYTES);
		}
		if (bytes == null || !dimensionsOk(bytes)) {
			return null;
		}
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
		if (image != null) {
			store(dir, file, bytes);
		}
		return image;
	}

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
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
			if (image != null) {
				file.toFile().setLastModified(System.currentTimeMillis());
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
		try {
			Files.createDirectories(dir);
			Path temp = Files.createTempFile(dir, "cover", ".part");
			Files.write(temp, bytes);
			dev.beli.skylyrics.util.SafeFile.replace(temp, file);
		} catch (java.io.IOException | RuntimeException e) {
			LOG.debug("Could not cache cover {}", file, e);
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
