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
	void aCoverThatCannotBeCachedIsOnlyAMissedSave(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws IOException {
		java.nio.file.Path notAFolder = tmp.resolve("blocked");
		java.nio.file.Files.writeString(notAFolder, "x");
		// read() has already decoded the image and returns it whatever store does, so store must swallow the failure
		assertDoesNotThrow(() -> CoverCache.store(notAFolder, notAFolder.resolve("a.img"), png(8, 8)));
		// and the save that failed left nothing behind: the blocker is as it was
		assertEquals("x", java.nio.file.Files.readString(notAFolder));
		assertFalse(java.nio.file.Files.exists(notAFolder.resolve("a.img")));
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
		// Damage the header (not just cut the tail: a decoder may hand back half an image from a cut file)
		byte[] damaged = whole.clone();
		for (int i = 0; i < 16; i++) {
			damaged[i] = (byte) (0x55 + i);
		}
		java.nio.file.Files.write(file, damaged);
		assertNull(CoverCache.readCached(file));
		assertFalse(java.nio.file.Files.exists(file));
		assertNull(CoverCache.readCached(file));
	}

	@Test
	void aCoverWhoseDecoderThrowsIsRemovedToo(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws IOException {
		byte[] whole = png(16, 16);
		// The signature and the header stay good, the image data is garbage: the decoder starts and then throws
		byte[] damaged = whole.clone();
		for (int i = 40; i < damaged.length - 12; i++) {
			damaged[i] = (byte) 0xFF;
		}
		java.nio.file.Path file = tmp.resolve("d.img");
		java.nio.file.Files.write(file, damaged);
		assertNull(CoverCache.readCached(file));
		assertFalse(java.nio.file.Files.exists(file));
	}

	@Test
	void aCoverThatCannotBeReadJustNowIsKept(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws IOException {
		java.nio.file.Path file = tmp.resolve("c.img");
		java.nio.file.Files.write(file, png(16, 16));
		// Unreadable for now (another program holds it): it's still a good cover, so it must stay
		org.junit.jupiter.api.Assumptions.assumeTrue(file.toFile().setReadable(false) && !java.nio.file.Files.isReadable(file),
				"can't make a file unreadable here");
		try {
			assertNull(CoverCache.readCached(file));
			assertTrue(java.nio.file.Files.exists(file));
		} finally {
			file.toFile().setReadable(true);
		}
		assertEquals(16, CoverCache.readCached(file).getWidth());
	}

	@Test
	void tidyingKeepsTheNewestFiles(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws IOException {
		for (int i = 0; i < 5; i++) {
			java.nio.file.Path p = tmp.resolve("f" + i);
			java.nio.file.Files.writeString(p, "x");
			p.toFile().setLastModified(1_000_000L * (5 - i));
		}
		CoverCache.tidy(tmp.toFile().listFiles(), 2);
		assertEquals(java.util.Set.of("f0", "f1"), java.util.Arrays.stream(tmp.toFile().list()).collect(java.util.stream.Collectors.toSet()));
		CoverCache.tidy(null, 2);
	}
}
