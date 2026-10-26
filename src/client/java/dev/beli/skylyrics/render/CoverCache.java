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
import java.nio.file.StandardCopyOption;
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

	private final Path dir;
	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
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
			if (image == null || cover.dropped) {
				return;
			}
			cover.color = AlbumArt.pickColor(image);
			cover.image = AlbumArt.shrink(image, SIZE, CORNER);
			ready.add(cover);
		} catch (Exception e) {
			LOG.debug("Couldn't load cover {}: {}", cover.url, e.toString());
		}
	}

	private @Nullable BufferedImage read(String url) throws Exception {
		Path file = dir.resolve(fileName(url));
		if (Files.isRegularFile(file)) {
			BufferedImage image = ImageIO.read(file.toFile());
			if (image != null) {
				file.toFile().setLastModified(System.currentTimeMillis());
				return image;
			}
		}
		HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
		HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
		if (response.statusCode() / 100 != 2) {
			return null;
		}
		byte[] bytes = response.body();
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
		if (image != null) {
			Files.createDirectories(dir);
			Path temp = Files.createTempFile(dir, "cover", ".part");
			Files.write(temp, bytes);
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		return image;
	}

	/** Keeps the folder from growing forever: removes the covers not seen for the longest. */
	private void tidyDisk() {
		File[] files = dir.toFile().listFiles();
		if (files == null || files.length <= DISK_FILES) {
			return;
		}
		Arrays.sort(files, Comparator.comparingLong(File::lastModified));
		for (int i = 0; i < files.length - DISK_FILES; i++) {
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
