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

	@Test
	void cornersNeverOverlapOnOddSizes() {
		// A pill as tall as its radius is round (r = h / 2): with an odd height the two corners used to be rounded up to
		// half a pixel more each, so they shared a row, and a see-through bar was drawn twice there (a darker line)
		for (int h = 1; h <= 41; h++) {
			for (int w = h; w <= h + 3; w++) {
				int c = RoundRect.corner(w, h, h / 2f);
				assertTrue(2 * c <= h && 2 * c <= w, "box " + w + "x" + h + ": corner " + c + " overlaps");
				assertTrue(c >= h / 2, "box " + w + "x" + h + ": corner " + c + " too small");
			}
		}
		assertEquals(4, RoundRect.corner(100, 30, 4.4f));
		assertEquals(5, RoundRect.corner(100, 30, 4.6f));
	}
}
