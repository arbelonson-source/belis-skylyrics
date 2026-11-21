package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The Lines layout wraps a line into rows; a row as wide as the wrap width must still fit across the view when the
 * lyrics are big (Size), the FOV low or the window narrow, or the start and end of the line are off the screen. This
 * is geometry only: how much of the screen a row takes for a given width in text units, whatever font it is drawn in.
 */
class RowFitTest {
	/** Text units per row of the lyrics, as the renderer scales them: a row is this tall at {@link LyricsRenderer#rowAngle}. */
	private static final double ROW_H = 10;

	/** How much of the screen's width a row {@code units} wide takes, seen square on, when a row is {@code angle} degrees tall. */
	private static double share(float units, float angle, double tanHalfWide) {
		return units / 2 * Math.tan(Math.toRadians(angle)) / ROW_H / tanHalfWide;
	}

	private static float sky(float size, double fov, int width, int height) {
		return LyricsRenderer.rowWidth(230, LyricsRenderer.rowAngle(true, size), LyricsRenderer.tanHalfWide(fov, width, height));
	}

	@Test
	void aWholeRowFitsAcrossTheViewAtTheBiggestSize() {
		float angle = LyricsRenderer.rowAngle(true, 3);
		double tan = LyricsRenderer.tanHalfWide(70, 1280, 720);
		float width = sky(3, 70, 1280, 720);
		assertTrue(share(width, angle, tan) <= 0.9, "a row of " + width + " units takes " + share(width, angle, tan) + " of the screen");
		// The usual 230 units would be wider than the screen there
		assertTrue(share(230, angle, tan) > 1, "230 units take " + share(230, angle, tan));
	}

	@Test
	void aLowFovOrANarrowWindowWrapsSooner() {
		for (float size : new float[] {1, 1.5f, 2, 2.5f}) {
			for (int[] window : new int[][] {{1280, 720}, {800, 600}, {1920, 1080}, {1024, 768}}) {
				for (double fov : new double[] {30, 50, 70, 90, 110}) {
					float width = sky(size, fov, window[0], window[1]);
					double taken = share(width, LyricsRenderer.rowAngle(true, size), LyricsRenderer.tanHalfWide(fov, window[0], window[1]));
					assertTrue(width <= 230, "never wider than before");
					assertTrue(width == LyricsRenderer.MIN_WRAP || taken <= 0.9,
							"size " + size + ", FOV " + fov + ", " + window[0] + "x" + window[1] + ": " + taken + " of the screen");
				}
			}
		}
	}

	/** Where a 230-unit row already fits, nothing changes: the usual size, the Karaoke look's 1.5, a wide FOV. */
	@Test
	void wherever230UnitsFitTheRowsStayAsTheyWere() {
		assertEquals(230f, sky(1, 70, 1920, 1080), 0f);
		assertEquals(230f, sky(1, 70, 1280, 720), 0f);
		assertEquals(230f, sky(1.5f, 70, 1920, 1080), 0f);
		assertEquals(230f, sky(2, 110, 1920, 1080), 0f);
		assertEquals(230f, sky(0.3f, 30, 800, 600), 0f);
		assertEquals(190f, LyricsRenderer.rowWidth(190, LyricsRenderer.rowAngle(false, 1), LyricsRenderer.tanHalfWide(70, 1920, 1080)), 0f);
	}

	/** However big the lyrics and narrow the view, a row keeps room for a word or two instead of a letter at a time. */
	@Test
	void aRowKeepsRoomForAFewLetters() {
		assertEquals(LyricsRenderer.MIN_WRAP, sky(3, 30, 800, 600), 0f);
		assertTrue(LyricsRenderer.MIN_WRAP >= 50, "min " + LyricsRenderer.MIN_WRAP);
	}

	/** A window that has no size yet (minimised) counts as a wide one, not as no room at all. */
	@Test
	void aWindowWithoutASizeCountsAsWide() {
		assertEquals(LyricsRenderer.tanHalfWide(70, 1920, 1080), LyricsRenderer.tanHalfWide(70, 0, 0), 1e-9);
		assertEquals(230f, sky(1, 70, 0, 0), 0f);
	}
}
