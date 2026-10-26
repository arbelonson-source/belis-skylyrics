package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
