package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class RoundRectTest {
	@Test
	void discIsSolidInsideAndEmptyInTheCorner() {
		assertEquals(1f, RoundRect.coverage(32, 32, 64));
		assertEquals(0f, RoundRect.coverage(0, 0, 64));
	}

	@Test
	void edgePixelsArePartlyCovered() {
		// On the diagonal the curve passes through pixel (9, 9) of a 64-wide disc
		float edge = RoundRect.coverage(9, 9, 64);
		assertTrue(edge > 0 && edge < 1, "edge " + edge);
	}

	@Test
	void picksTheSmallestCornerThatIsBigEnough() {
		assertEquals(0, RoundRect.sizeIndex(1));
		assertEquals(0, RoundRect.sizeIndex(4));
		assertEquals(1, RoundRect.sizeIndex(5));
		assertEquals(4, RoundRect.sizeIndex(33));
		assertEquals(5, RoundRect.sizeIndex(500));
	}

	/** The pixels as the old code made them: all six sizes in one pass, each pixel straight from coverage. */
	private static int[][] allAtOnce() {
		int[] sizes = {4, 8, 16, 32, 64, 128};
		int[][] all = new int[sizes.length][];
		for (int i = 0; i < sizes.length; i++) {
			int d = 2 * sizes[i];
			all[i] = new int[d * d];
			for (int py = 0; py < d; py++) {
				for (int px = 0; px < d; px++) {
					all[i][py * d + px] = Math.round(RoundRect.coverage(px, py, d) * 255) << 24 | 0xFFFFFF;
				}
			}
		}
		return all;
	}

	@Test
	void eachSizeBuiltOnItsOwnMatchesTheOldAllAtOnceResult() {
		int[][] old = allAtOnce();
		assertEquals(old.length, RoundRect.sizeCount());
		for (int i = 0; i < old.length; i++) {
			assertArrayEquals(old[i], RoundRect.cornerPixels(i), "size index " + i);
		}
	}
}
