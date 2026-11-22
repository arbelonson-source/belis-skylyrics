package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.config.SkyLyricsConfig.CardCorner;
import org.junit.jupiter.api.Test;

/**
 * A Record card in a bottom corner also steps aside for F3's charts, which the game draws along the bottom of the screen
 * over the HUD: the frame time or network chart from the bottom left corner, the tick time or ping chart and the
 * profiler's pie from the bottom right. Before, a bottom card stayed and the charts were drawn over it.
 */
class CardF3ChartsTest {
	/** F3's text ending well above the cards (1.21.11 at 640x360 or taller). */
	private static final float[][] SHORT_TEXT = DebugText.boxes(new int[] {150, 150, 150, 150, 150, 150}, true, 640);

	private static float[] card(CardCorner corner, int guiW, int guiH) {
		float[] at = RecordCard.corner(corner, guiW, guiH);
		return new float[] {at[0], at[1], at[0] + RecordCard.W, at[1] + RecordCard.H};
	}

	@Test
	void theLineChartsCoverBothBottomCorners() {
		for (int[] screen : new int[][] {{640, 360}, {960, 540}}) {
			int w = screen[0], h = screen[1];
			float[][] charts = DebugCharts.lineCharts(w, h);
			for (CardCorner corner : new CardCorner[] {CardCorner.BOTTOM_LEFT, CardCorner.BOTTOM_RIGHT}) {
				float[] box = card(corner, w, h);
				assertFalse(RecordCard.f3Covers(corner, box, true, SHORT_TEXT), "F3's text alone leaves it");
				assertTrue(RecordCard.f3Covers(corner, box, true, SHORT_TEXT, charts), corner + " at " + w + "x" + h);
			}
		}
	}

	@Test
	void thePieCoversTheBottomRightCard() {
		// From 1.21.2 the pie's box sits in the bottom right corner, 10 above the bottom
		float[][] pie = {DebugCharts.pie(960, 540, DebugCharts.PIE_LIFT, DebugCharts.PIE_HALF)};
		assertTrue(RecordCard.f3Covers(CardCorner.BOTTOM_RIGHT, card(CardCorner.BOTTOM_RIGHT, 960, 540), true, SHORT_TEXT, pie));
		assertFalse(RecordCard.f3Covers(CardCorner.BOTTOM_LEFT, card(CardCorner.BOTTOM_LEFT, 960, 540), true, SHORT_TEXT, pie),
				"it is far from the bottom left one");
	}

	@Test
	void aCardAboveTheChartsStays() {
		// A card lifted above a bottom bar at 640x360 stands above the charts' 69 tall strip
		float[] box = {6, 200, 6 + RecordCard.W, 200 + RecordCard.H};
		assertFalse(RecordCard.f3Covers(CardCorner.BOTTOM_LEFT, box, true, SHORT_TEXT, DebugCharts.lineCharts(640, 360)));
		assertFalse(RecordCard.f3Covers(CardCorner.BOTTOM_LEFT, card(CardCorner.BOTTOM_LEFT, 640, 360), false, SHORT_TEXT,
				DebugCharts.lineCharts(640, 360)), "F3 closed");
	}
}
