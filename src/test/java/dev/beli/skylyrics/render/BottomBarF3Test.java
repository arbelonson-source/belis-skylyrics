package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A bar at the bottom steps aside for the debug screen (F3) only when it would sit on F3's text. */
class BottomBarF3Test {
	/** The bar's clock and stats widths at the reference look; creative, so no hearts under it. */
	private static float[] bottomBar(int guiW, int guiH) {
		float top = TopBarLayout.bottomTop(guiH, false);
		TopBarLayout.Fit fit = TopBarLayout.fit(guiW, 1, 30, 45);
		return TopBarLayout.barBox(guiW, 1, fit.pill(), 30, fit.clock(), 45, fit.stats(), top);
	}

	/** About what 1.21.11 shows (the test game's pictures): 24 rows on the left with two empty ones, 15 on the right. */
	private static float[][] f3(int guiW) {
		int[] left = new int[24], right = new int[15];
		java.util.Arrays.fill(left, 150);
		left[4] = -1;
		left[21] = -1;
		java.util.Arrays.fill(right, 120);
		float[][] l = DebugText.boxes(left, true, guiW), r = DebugText.boxes(right, false, guiW);
		float[][] both = new float[l.length + r.length][];
		System.arraycopy(l, 0, both, 0, l.length);
		System.arraycopy(r, 0, both, l.length, r.length);
		return both;
	}

	@Test
	void atGuiScale3TheBottomBarSitsOnF3sTextAndHides() {
		// 1280x720 at GUI scale 3: the bar's clock ran into F3's "Off-Heap" line in the test game
		float[] bar = bottomBar(426, 240);
		assertTrue(DebugText.covers(bar, f3(426)));
		assertTrue(NowPlayingHud.f3Hides(true, true, bar, f3(426)));
	}

	@Test
	void atGuiScale2F3sTextEndsAboveTheBottomBarSoItStays() {
		float[] bar = bottomBar(640, 360);
		assertFalse(DebugText.covers(bar, f3(640)));
		assertFalse(NowPlayingHud.f3Hides(true, true, bar, f3(640)));
	}

	@Test
	void unmeasuredF3HidesABarThatReachesAsHighAsF3TextUsuallyDoes() {
		// A mod that draws F3 its own way: no boxes. The same guess as for a Record card in the bottom left
		assertTrue(NowPlayingHud.f3Hides(true, true, bottomBar(426, 240), null));
		assertFalse(NowPlayingHud.f3Hides(true, true, bottomBar(960, 540), null));
	}

	@Test
	void aTopBarAlwaysHidesForF3AndNoBarHidesWithoutIt() {
		assertTrue(NowPlayingHud.f3Hides(false, true, null, null));
		assertFalse(NowPlayingHud.f3Hides(true, false, bottomBar(426, 240), f3(426)));
		assertFalse(NowPlayingHud.f3Hides(false, false, null, null));
	}

	@Test
	void linesSitWhereTheGameDrawsThem() {
		float[][] l = DebugText.boxes(new int[] {100, -1, 40}, true, 426);
		assertEquals(2, l.length, "an empty line takes its row but draws nothing");
		assertArrayEquals(new float[] {1, 1, 103, 10}, l[0]);
		assertArrayEquals(new float[] {1, 19, 43, 28}, l[1]);
		float[][] r = DebugText.boxes(new int[] {60}, false, 426);
		assertArrayEquals(new float[] {426 - 2 - 60 - 1, 1, 426 - 1, 10}, r[0]);
	}
}
