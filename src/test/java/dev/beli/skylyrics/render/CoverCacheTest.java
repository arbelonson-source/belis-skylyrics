package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

	private static BufferedImage bigPicture(int w, int h) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int r = 40 + 200 * x / w, g = 30 + 150 * y / h, b = ((x / 64 + y / 64) & 1) == 0 ? 220 : 90;
				img.setRGB(x, y, r << 16 | g << 8 | b);
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
	void aHugeCoverDecodesToTwoToFourTimesTheTargetKeepingItsAspect() throws IOException {
		BufferedImage src = bigPicture(4000, 3000);
		for (String format : new String[] {"png", "jpg"}) {
			for (int target : new int[] {64, 128}) {
				BufferedImage out = CoverCache.decodeSmall(encode(src, format), target);
				int longer = Math.max(out.getWidth(), out.getHeight());
				assertTrue(longer <= 4 * target && longer >= 2 * target, format + " " + target + " -> " + out.getWidth() + "x" + out.getHeight());
				assertEquals(4000 / 3000.0, out.getWidth() / (double) out.getHeight(), 1.5 / out.getHeight(), format);
			}
		}
	}

	@Test
	void aSmallCoverDecodesAsItIs() throws IOException {
		BufferedImage out = CoverCache.decodeSmall(encode(bigPicture(300, 300), "png"), 128);
		assertEquals(300, out.getWidth());
		assertEquals(300, out.getHeight());
		BufferedImage tiny = CoverCache.decodeSmall(encode(bigPicture(40, 40), "png"), 128);
		assertEquals(40, tiny.getWidth());
		// 300 is over 4 x 64, so at the AlbumArt target it is halved
		assertEquals(150, CoverCache.decodeSmall(encode(bigPicture(300, 300), "png"), 64).getWidth());
	}

	@Test
	void aCorruptCoverDecodesToNullOrThrowsLikeBefore() throws IOException {
		byte[] bad = {1, 2, 3, 4, 5};
		assertNull(CoverCache.decodeSmall(bad, 128));
		byte[] cut = encode(bigPicture(2000, 2000), "png");
		byte[] half = java.util.Arrays.copyOf(cut, cut.length / 2);
		// the subsampled read fails on the cut file and so does the whole read it falls back to: the caller sees the IOException
		assertThrows(IOException.class, () -> CoverCache.decodeSmall(half, 128));
	}

	@Test
	void theSubsampledColourStaysClose() throws IOException {
		// A smooth cover: a green-to-blue sweep with a darker corner, like a real scan
		BufferedImage src = new BufferedImage(2000, 2000, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < 2000; y++) {
			for (int x = 0; x < 2000; x++) {
				float t = (x + y) / 4000f;
				src.setRGB(x, y, java.awt.Color.HSBtoRGB(0.25f + 0.35f * t, 0.8f, 0.9f - 0.4f * (y / 2000f) * (x / 2000f)));
			}
		}
		int whole = AlbumArt.pickColor(src);
		int small = AlbumArt.pickColor(CoverCache.decodeSmall(encode(src, "png"), AlbumArt.SIZE));
		float[] a = java.awt.Color.RGBtoHSB(whole >> 16 & 255, whole >> 8 & 255, whole & 255, null);
		float[] b = java.awt.Color.RGBtoHSB(small >> 16 & 255, small >> 8 & 255, small & 255, null);
		float dh = Math.abs(a[0] - b[0]);
		// the hue is the middle of a 1/24 bucket, so under half a bucket apart is the very same bucket
		assertTrue(Math.min(dh, 1 - dh) < 1f / 48, "hue moved from " + a[0] + " to " + b[0]);
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

	@Test
	void aFailedWriteLeavesNoPartFileBehind(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws IOException {
		CoverCache.store(dir, dir.resolve("a.img"), png(8, 8), (to, bytes) -> {
			throw new IOException("disk full");
		});
		try (var files = java.nio.file.Files.list(dir)) {
			assertEquals(0, files.count());
		}
	}

	@Test
	void anErrorFromTheDecoderDoesNotKillTheWorkers(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
		java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
		java.util.concurrent.CountDownLatch next = new java.util.concurrent.CountDownLatch(1);
		// More failing covers than there are worker threads, then one more
		CoverCache cache = new CoverCache(dir, url -> {
			if (url.endsWith("/last")) {
				next.countDown();
				return null;
			}
			reads.incrementAndGet();
			throw new NoClassDefFoundError("decoder");
		});
		try {
			for (int i = 0; i < 6; i++) {
				cache.get("https://example.test/bad" + i);
			}
			long end = System.nanoTime() + 20_000_000_000L;
			while (reads.get() < 6 && System.nanoTime() < end) {
				Thread.sleep(10);
			}
			assertEquals(6, reads.get());
			cache.get("https://example.test/last");
			assertTrue(next.await(20, java.util.concurrent.TimeUnit.SECONDS), "the workers are still running");
		} finally {
			cache.close();
		}
	}

	private static java.lang.reflect.Field field(Object o, String name) throws Exception {
		java.lang.reflect.Field f = o.getClass().getDeclaredField(name);
		f.setAccessible(true);
		return f;
	}

	@Test
	void aCoverThatNeverLoadsIsGivenUpAfterThreeTries(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
		java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
		CoverCache cache = new CoverCache(dir, url -> {
			reads.incrementAndGet();
			return null;
		});
		try {
			String url = "https://example.test/gone";
			long later = System.nanoTime();
			long end = System.nanoTime() + 10_000_000_000L;
			// The clock jumps past the retry gap on every call; the cover is requested again only until it has failed 3 times
			while (reads.get() < 3 && System.nanoTime() < end) {
				later += CoverCache.RETRY_NS * 2;
				cache.get(url, later);
				Thread.sleep(5);
			}
			assertEquals(3, reads.get());
			// reads counts when the reader is called, a moment before the failure is recorded: wait for the record
			// (the cache has no accessor, so look at the cover's own counter) or a get could start a 4th read
			Object cover = ((java.util.Map<?, ?>) field(cache, "covers").get(cache)).get(url);
			java.lang.reflect.Field failures = field(cover, "failures");
			long settle = System.nanoTime() + 20_000_000_000L;
			while (failures.getInt(cover) < 3 && System.nanoTime() < settle) {
				Thread.sleep(5);
			}
			assertEquals(3, failures.getInt(cover), "the third failure was never recorded");
			for (int i = 0; i < 40; i++) {
				later += CoverCache.RETRY_NS * 2;
				assertNull(cache.get(url, later));
				Thread.sleep(5);
			}
			assertEquals(3, reads.get());
		} finally {
			cache.close();
		}
	}

	@Test
	void networkTroubleIsRetriedForEverAndNeverCounted(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
		java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
		CoverCache cache = new CoverCache(dir, url -> {
			reads.incrementAndGet();
			throw new java.net.ConnectException("offline");
		});
		try {
			String url = "https://example.test/offline";
			long later = System.nanoTime();
			long end = System.nanoTime() + 10_000_000_000L;
			while (reads.get() < 6 && System.nanoTime() < end) {
				later += CoverCache.RETRY_NS * 2;
				cache.get(url, later);
				Thread.sleep(5);
			}
			assertTrue(reads.get() >= 6, "reads: " + reads.get());
		} finally {
			cache.close();
		}
	}

	@Test
	void onlyPermanentStatusesCountAgainstACover() {
		assertTrue(CoverCache.countsAsFailure(404));
		assertTrue(CoverCache.countsAsFailure(403));
		assertFalse(CoverCache.countsAsFailure(503));
		assertFalse(CoverCache.countsAsFailure(500));
		assertFalse(CoverCache.countsAsFailure(429));
		assertFalse(CoverCache.countsAsFailure(408));
	}

	@Test
	void aCoverThatDownloadsButWontDecodeIsGivenUp(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
		java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
		CoverCache cache = new CoverCache(dir, url -> {
			reads.incrementAndGet();
			throw new javax.imageio.IIOException("Unsupported Image Type");
		});
		try {
			String url = "https://example.test/cmyk";
			long later = System.nanoTime();
			long end = System.nanoTime() + 10_000_000_000L;
			while (reads.get() < 3 && System.nanoTime() < end) {
				later += CoverCache.RETRY_NS * 2;
				cache.get(url, later);
				Thread.sleep(5);
			}
			Object cover = ((java.util.Map<?, ?>) field(cache, "covers").get(cache)).get(url);
			java.lang.reflect.Field failures = field(cover, "failures");
			long settle = System.nanoTime() + 20_000_000_000L;
			while (failures.getInt(cover) < 3 && System.nanoTime() < settle) {
				Thread.sleep(5);
			}
			assertEquals(3, failures.getInt(cover), "decode failures are counted");
			for (int i = 0; i < 20; i++) {
				later += CoverCache.RETRY_NS * 2;
				assertNull(cache.get(url, later));
				Thread.sleep(5);
			}
			assertEquals(3, reads.get());
		} finally {
			cache.close();
		}
	}
}
