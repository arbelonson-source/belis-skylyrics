package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A bar at the bottom also steps aside for F3's charts (F3+1, F3+2, F3+3), which the game draws along the bottom of the
 * screen over the HUD: before, only F3's text was looked at, so where that text ends above the bar (1.21.11 at 640x360)
 * the bar stayed, and the charts were drawn over it.
 */
class BottomBarF3ChartsTest {
	private static final float[][] NO_CHARTS = new float[0][];

	/** The bar's box with the clock and stats widths of the reference look; {@code survival} puts hearts under it. */
	private static float[] bottomBar(int guiW, int guiH, boolean survival) {
		float top = TopBarLayout.bottomTop(guiH, survival);
		TopBarLayout.Fit fit = TopBarLayout.fit(guiW, 1, 30, 45);
		return TopBarLayout.barBox(guiW, 1, fit.pill(), 30, fit.clock(), 45, fit.stats(), top);
	}

	/** F3's text as 1.21.11 shows it at the top (24 rows on the left, 15 on the right): it ends well above a bar at 640x360. */
	private static float[][] f3Text(int guiW) {
		int[] left = new int[24], right = new int[15];
		java.util.Arrays.fill(left, 150);
		java.util.Arrays.fill(right, 120);
		float[][] l = DebugText.boxes(left, true, guiW), r = DebugText.boxes(right, false, guiW);
		float[][] both = new float[l.length + r.length][];
		System.arraycopy(l, 0, both, 0, l.length);
		System.arraycopy(r, 0, both, l.length, r.length);
		return both;
	}

	@Test
	void theFrameTimeChartsReachTheBarAt640x360() {
		for (boolean survival : new boolean[] {false, true}) {
			float[] bar = bottomBar(640, 360, survival);
			assertFalse(NowPlayingHud.f3Hides(true, true, bar, f3Text(640), NO_CHARTS), "F3's text alone leaves the bar");
			float[][] charts = DebugCharts.boxes(640, 360, 3, true, false, false);
			assertTrue(DebugText.covers(bar, charts), "survival " + survival);
			assertTrue(NowPlayingHud.f3Hides(true, true, bar, f3Text(640), charts), "the charts hide the bar, survival " + survival);
		}
	}

	@Test
	void theProfilersPieReachesTheBarAt640x360() {
		float[] bar = bottomBar(640, 360, true);
		// From 1.21.2 the pie has a box in the bottom right corner, 10 above the bottom alone or on top of a line chart
		for (int lift : new int[] {DebugCharts.PIE_LIFT, DebugCharts.CHART_H}) {
			float[][] pie = {DebugCharts.pie(640, 360, lift, DebugCharts.PIE_HALF)};
			assertTrue(NowPlayingHud.f3Hides(true, true, bar, f3Text(640), pie), "lift " + lift);
		}
		// 1.21.1's pie, drawn in the window's pixels: 110 wide at GUI scale 3, so only on a narrower screen (1280x720)
		assertFalse(DebugText.covers(bar, new float[][] {DebugCharts.pie121(640, 360, 3)}));
		assertTrue(DebugText.covers(bottomBar(426, 240, true), new float[][] {DebugCharts.pie121(426, 240, 3)}));
	}

	@Test
	void onAWideScreenTheChartsStayBesideTheBar() {
		// 1920x1080 at GUI scale 2: each chart is 242 wide, the bar keeps to the middle
		float[] bar = bottomBar(960, 540, true);
		float[][] all = DebugCharts.boxes(960, 540, 2, true, true, false);
		assertFalse(DebugText.covers(bar, all));
		assertFalse(NowPlayingHud.f3Hides(true, true, bar, f3Text(960), all));
		assertFalse(NowPlayingHud.f3Hides(true, true, bar, f3Text(960), new float[][] {DebugCharts.pie121(960, 540, 2)}));
	}

	@Test
	void withoutChartsOrF3NothingChanges() {
		float[] bar = bottomBar(640, 360, true);
		assertFalse(NowPlayingHud.f3Hides(true, true, bar, f3Text(640), DebugCharts.boxes(640, 360, 3, false, false, false)));
		assertFalse(NowPlayingHud.f3Hides(true, false, bar, f3Text(640), DebugCharts.boxes(640, 360, 3, true, true, true)));
		assertTrue(NowPlayingHud.f3Hides(false, true, null, null, NO_CHARTS), "a top bar always hides for F3");
	}

	@Test
	void chartsSitWhereTheGameDrawsThem() {
		float[][] two = DebugCharts.lineCharts(640, 360);
		assertArrayEquals(new float[] {0, 291, 242, 360}, two[0]);
		assertArrayEquals(new float[] {398, 291, 640, 360}, two[1]);
		float[][] narrow = DebugCharts.lineCharts(426, 240);
		assertEquals(213, narrow[0][2], "at most half the screen");
		// 1.21.11: the pie's box from 105 + 10 + 105 + 5 left of the right edge, 10 above the bottom, 139 tall at the least
		assertArrayEquals(new float[] {640 - 225, 360 - 10 - 139, 635, 350}, DebugCharts.pie(640, 360, DebugCharts.PIE_LIFT, 105));
		// above a line chart it sits on top of it
		assertEquals(360 - DebugCharts.CHART_H, DebugCharts.pie(640, 360, DebugCharts.CHART_H, 105)[3]);
		assertArrayEquals(new float[] {640 - 67, 360 - 67, 639, 359}, DebugCharts.lightmap(640, 360));
	}
}
