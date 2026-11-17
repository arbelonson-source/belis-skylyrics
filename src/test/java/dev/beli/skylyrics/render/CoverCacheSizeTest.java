package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The disk cache holds the small image the mod uses, not the multi-MB download. */
class CoverCacheSizeTest {
	private static BufferedImage noisy(int w, int h, int type) {
		Random rnd = new Random(7);
		BufferedImage img = new BufferedImage(w, h, type);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				img.setRGB(x, y, 0xFF000000 | rnd.nextInt(0x1000000));
			}
		}
		return img;
	}

	private static BufferedImage gradient(int w, int h, int type) {
		BufferedImage img = new BufferedImage(w, h, type);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				img.setRGB(x, y, 0xFF000000 | (255 * x / w) << 16 | (255 * y / h) << 8 | 0x40);
			}
		}
		return img;
	}

	private static byte[] encode(BufferedImage img, String format) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(img, format, out);
		return out.toByteArray();
	}

	@Test
	void aStoredCoverIsSmallAndReadsBackAsTheSameSquare(@TempDir Path dir) throws IOException {
		BufferedImage big = noisy(1000, 1000, BufferedImage.TYPE_INT_RGB);
		Path file = dir.resolve("a.img");
		CoverCache.cache(dir, file, big);
		assertTrue(Files.size(file) < 100_000, "stored " + Files.size(file) + " bytes");
		BufferedImage back = CoverCache.readCached(file);
		assertNotNull(back);
		assertEquals(CoverCache.SIZE, back.getWidth());
		assertEquals(CoverCache.SIZE, back.getHeight());
	}

	@Test
	void theStoredPixelsMatchTheCover(@TempDir Path dir) throws IOException {
		// Alpha sources (ARGB) and a wide picture: centre square, colours kept
		BufferedImage wide = gradient(600, 400, BufferedImage.TYPE_INT_ARGB);
		Path file = dir.resolve("w.img");
		CoverCache.cache(dir, file, wide);
		BufferedImage back = CoverCache.readCached(file);
		assertEquals(CoverCache.SIZE, back.getWidth());
		// Centre of the square is the centre of the source
		int c = back.getRGB(64, 64) & 0xFFFFFF, s = wide.getRGB(300, 200) & 0xFFFFFF;
		for (int shift = 0; shift <= 16; shift += 8) {
			assertTrue(Math.abs(((c >> shift) & 0xFF) - ((s >> shift) & 0xFF)) <= 6, "channel " + shift);
		}
		// Left edge of the square is x = 100 of the source: red ~ 255 * 100 / 600
		int red = (back.getRGB(0, 64) >> 16) & 0xFF;
		assertTrue(Math.abs(red - 255 * 100 / 600) <= 8, "red " + red);
	}

	@Test
	void shrinkingAveragesTheBlockNotJustOnePixel(@TempDir Path dir) throws IOException {
		// A 256 px chequerboard of black and white pixels comes down 2:1 to grey, not to the colour of every other pixel
		BufferedImage chequer = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < 256; y++) {
			for (int x = 0; x < 256; x++) {
				chequer.setRGB(x, y, (x + y) % 2 == 0 ? 0xFFFFFF : 0x000000);
			}
		}
		Path file = dir.resolve("c.img");
		CoverCache.cache(dir, file, chequer);
		BufferedImage back = CoverCache.readCached(file);
		assertEquals(CoverCache.SIZE, back.getWidth());
		for (int[] at : new int[][] {{0, 0}, {1, 0}, {50, 77}, {127, 127}}) {
			int grey = back.getRGB(at[0], at[1]) & 0xFF;
			assertTrue(Math.abs(grey - 128) <= 3, "pixel " + at[0] + "," + at[1] + " is " + grey);
		}
	}

	@Test
	void aSmallCoverIsNotEnlarged(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("s.img");
		CoverCache.cache(dir, file, gradient(40, 40, BufferedImage.TYPE_INT_RGB));
		assertEquals(40, CoverCache.readCached(file).getWidth());
	}

	@Test
	void aFileSavedBeforeStillReads(@TempDir Path dir) throws IOException {
		Path png = dir.resolve("old.img");
		Files.write(png, encode(gradient(900, 900, BufferedImage.TYPE_INT_RGB), "png"));
		BufferedImage a = CoverCache.readCached(png);
		assertNotNull(a);
		assertTrue(a.getWidth() >= 2 * CoverCache.SIZE);
		Path jpg = dir.resolve("old2.img");
		Files.write(jpg, encode(gradient(500, 500, BufferedImage.TYPE_INT_RGB), "jpg"));
		assertNotNull(CoverCache.readCached(jpg));
	}

	@Test
	void aFailedWriteStillLeavesNoPartFile(@TempDir Path dir) throws IOException {
		CoverCache.store(dir, dir.resolve("a.img"), CoverCache.encodeSmall(noisy(300, 300, BufferedImage.TYPE_INT_RGB)), (to, bytes) -> {
			throw new IOException("disk full");
		});
		try (var files = Files.list(dir)) {
			assertEquals(0, files.count());
		}
	}
}
