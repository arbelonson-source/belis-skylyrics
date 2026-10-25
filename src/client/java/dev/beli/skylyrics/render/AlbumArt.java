package dev.beli.skylyrics.render;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

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

import dev.beli.skylyrics.media.Track;

/**
 * Loads the album cover of the current song: a small texture for the now-playing card,
 * and a bright colour picked from it for the lyrics.
 */
public final class AlbumArt {
	private static final Logger LOG = LoggerFactory.getLogger("SkyLyrics");
	public static final int SIZE = 64;
	public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("skylyrics", "dynamic/album_art");
	/** Used when there's no cover, or it's all grey. */
	public static final int FALLBACK_COLOR = 0xB57BFF;

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "SkyLyrics album art");
		t.setDaemon(true);
		return t;
	});
	private final AtomicLong generation = new AtomicLong();

	private volatile int color = FALLBACK_COLOR;
	private volatile @Nullable NativeImage pending;
	private volatile boolean hasTexture;
	private @Nullable DynamicTexture texture;
	private String loadedUrl = "";

	/** Bright colour from the cover. Changes smoothly are handled by the caller. */
	public int color() {
		return color;
	}

	/** True if {@link #TEXTURE} currently shows this song's cover. Call {@link #upload()} first. */
	public boolean hasTexture() {
		return hasTexture;
	}

	public void load(Track track) {
		String url = track.artUrl();
		if (url.equals(loadedUrl)) {
			return;
		}
		loadedUrl = url;
		long gen = generation.incrementAndGet();
		hasTexture = false;
		if (url.isEmpty()) {
			color = FALLBACK_COLOR;
			return;
		}
		executor.execute(() -> {
			try {
				BufferedImage image = read(url);
				if (image == null || generation.get() != gen) {
					return;
				}
				int picked = pickColor(image);
				NativeImage scaled = toNative(image);
				if (generation.get() != gen) {
					scaled.close();
					return;
				}
				color = picked;
				NativeImage old = pending;
				pending = scaled;
				if (old != null) {
					old.close();
				}
			} catch (Exception e) {
				LOG.debug("Couldn't load album art {}: {}", url, e.toString());
				if (generation.get() == gen) {
					color = FALLBACK_COLOR;
				}
			}
		});
	}

	/** Moves a freshly loaded cover to the GPU. Render thread only. */
	public void upload() {
		NativeImage image = pending;
		if (image == null) {
			return;
		}
		pending = null;
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
		hasTexture = true;
	}

	private @Nullable BufferedImage read(String url) throws Exception {
		if (url.startsWith("file://")) {
			Path path = Path.of(URI.create(url));
			try (InputStream in = Files.newInputStream(path)) {
				return ImageIO.read(in);
			}
		}
		if (url.startsWith("http://") || url.startsWith("https://")) {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
			HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream in = response.body()) {
				return response.statusCode() / 100 == 2 ? ImageIO.read(in) : null;
			}
		}
		return null; // data: URIs and other odd schemes aren't worth it
	}

	/** Center-crops to a square and scales to {@link #SIZE}. */
	private static NativeImage toNative(BufferedImage src) {
		int side = Math.min(src.getWidth(), src.getHeight());
		int x0 = (src.getWidth() - side) / 2;
		int y0 = (src.getHeight() - side) / 2;
		NativeImage out = new NativeImage(SIZE, SIZE, false);
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				// Box filter: average the source pixels that fall in this output pixel
				int sx0 = x0 + x * side / SIZE, sx1 = Math.max(sx0 + 1, x0 + (x + 1) * side / SIZE);
				int sy0 = y0 + y * side / SIZE, sy1 = Math.max(sy0 + 1, y0 + (y + 1) * side / SIZE);
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
				// Round cover: fade out past the circle's edge over about one pixel
				float dx = x + 0.5f - SIZE / 2f, dy = y + 0.5f - SIZE / 2f;
				float edge = SIZE / 2f - (float) Math.sqrt(dx * dx + dy * dy);
				int a = Math.round(255 * Math.clamp(edge + 0.5f, 0f, 1f));
				out.setPixel(x, y, a << 24 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n));
			}
		}
		return out;
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
		NativeImage image = pending;
		if (image != null) {
			image.close();
		}
	}
}
