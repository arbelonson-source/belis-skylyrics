package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class CoverCacheTest {
	@Test
	void eachAddressGetsItsOwnSteadyFileName() {
		String a = CoverCache.fileName("https://i.scdn.co/image/abc");
		assertEquals(a, CoverCache.fileName("https://i.scdn.co/image/abc"));
		assertNotEquals(a, CoverCache.fileName("https://i.scdn.co/image/abd"));
		assertTrue(a.matches("[0-9a-f]{40}\\.img"), a);
	}

	@Test
	void roundedCornersAreClearAndTheMiddleIsSolid() {
		assertEquals(0f, AlbumArt.cornerAlpha(0, 0, 128, 11), 1e-4);
		assertEquals(1f, AlbumArt.cornerAlpha(64, 64, 128, 11), 1e-4);
		assertEquals(1f, AlbumArt.cornerAlpha(0, 64, 128, 11), 1e-4);
		assertEquals(1f, AlbumArt.cornerAlpha(64, 127, 128, 11), 1e-4);
	}

	@Test
	void halfTheSizeAsRadiusMakesACircle() {
		assertEquals(0f, AlbumArt.cornerAlpha(0, 0, 64, 32), 1e-4);
		assertEquals(1f, AlbumArt.cornerAlpha(32, 32, 64, 32), 1e-4);
		assertEquals(1f, AlbumArt.cornerAlpha(1, 32, 64, 32), 1e-4);
		assertTrue(AlbumArt.cornerAlpha(8, 8, 64, 32) < 0.01f);
	}

	@Test
	void bodiesOverEightMegabytesAreRefused() {
		assertTrue(CoverCache.acceptable(-1));
		assertTrue(CoverCache.acceptable(50_000));
		assertTrue(CoverCache.acceptable(8L * 1024 * 1024));
		assertFalse(CoverCache.acceptable(8L * 1024 * 1024 + 1));
	}

	@Test
	void cappedReadStopsWhenNoLengthWasGiven() throws IOException {
		assertEquals(10, CoverCache.readCapped(new ByteArrayInputStream(new byte[10]), 10).length);
		assertNull(CoverCache.readCapped(new ByteArrayInputStream(new byte[11]), 10));
	}

	@Test
	void hugePicturesAreRefusedBeforeDecoding() {
		assertTrue(CoverCache.sizeOk(640, 640));
		assertTrue(CoverCache.sizeOk(4096, 4096));
		assertFalse(CoverCache.sizeOk(4097, 100));
		assertFalse(CoverCache.sizeOk(100, 4097));
		assertFalse(CoverCache.sizeOk(0, 100));
	}

	@Test
	void sizeIsReadFromTheHeaderOnly() throws IOException {
		assertTrue(CoverCache.dimensionsOk(png(8, 8)));
		byte[] big = png(8, 8);
		// IHDR: width at offset 16, height at 20; claim 5000 x 5000 without any pixel data for it
		big[18] = 0x13;
		big[19] = (byte) 0x88;
		big[22] = 0x13;
		big[23] = (byte) 0x88;
		assertFalse(CoverCache.dimensionsOk(big));
		assertFalse(CoverCache.dimensionsOk(new byte[] {1, 2, 3}));
	}

	private static byte[] png(int w, int h) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", out);
		return out.toByteArray();
	}

	@Test
	void albumArtAppliesTheSameByteAndSizeLimits() throws IOException {
		assertNull(AlbumArt.decode(null));
		assertNull(AlbumArt.decode(new byte[] {1, 2, 3}));
		ByteArrayOutputStream big = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(5000, 8, BufferedImage.TYPE_INT_RGB), "png", big);
		assertNull(AlbumArt.decode(big.toByteArray()));
		ByteArrayOutputStream ok = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB), "png", ok);
		assertEquals(40, AlbumArt.decode(ok.toByteArray()).getWidth());
	}

	@Test
	void aCoverThatCannotBeCachedStillCountsAsDownloaded(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws IOException {
		java.nio.file.Path notAFolder = tmp.resolve("blocked");
		java.nio.file.Files.writeString(notAFolder, "x");
		assertDoesNotThrow(() -> CoverCache.store(notAFolder, notAFolder.resolve("a.img"), png(8, 8)));
		java.nio.file.Path ok = tmp.resolve("covers");
		CoverCache.store(ok, ok.resolve("b.img"), png(8, 8));
		assertTrue(java.nio.file.Files.isRegularFile(ok.resolve("b.img")));
	}

	@Test
	void aDamagedCachedCoverIsRemovedSoItDownloadsAgain(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws IOException {
		byte[] whole = png(16, 16);
		java.nio.file.Path file = tmp.resolve("c.img");
		java.nio.file.Files.write(file, whole);
		assertEquals(16, CoverCache.readCached(file).getWidth());
		java.nio.file.Files.write(file, java.util.Arrays.copyOf(whole, whole.length / 2));
		assertNull(CoverCache.readCached(file));
		assertFalse(java.nio.file.Files.exists(file));
		assertNull(CoverCache.readCached(file));
	}
}
