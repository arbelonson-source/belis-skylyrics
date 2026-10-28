package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class DaylightTest {
	@Test
	void clearNoonIsFullDaylight() {
		assertEquals(1f, LyricsRenderer.daylight(0f, 0f), 1e-6);
	}

	@Test
	void nightHasNone() {
		assertEquals(0f, LyricsRenderer.daylight(0.5f, 0f), 1e-6);
		assertEquals(0f, LyricsRenderer.daylight(0.8f, 0f), 1e-6);
	}

	@Test
	void duskIsHalfway() {
		assertEquals(0.5f, LyricsRenderer.daylight(0.25f, 0f), 1e-6);
	}

	@Test
	void rainGreysTheSky() {
		assertEquals(0.5f, LyricsRenderer.daylight(0f, 1f), 1e-6);
	}

	@Test
	void timeOfDayColourGoesNightTwilightDay() {
		assertEquals(LyricsRenderer.NIGHT, LyricsRenderer.timeOfDay(0));
		assertEquals(LyricsRenderer.TWILIGHT, LyricsRenderer.timeOfDay(0.5f));
		assertEquals(LyricsRenderer.DAY, LyricsRenderer.timeOfDay(1));
		assertEquals(LyricsRenderer.DAY, LyricsRenderer.timeOfDay(3));
		// Twilight is warm: more red than blue
		int dusk = LyricsRenderer.timeOfDay(0.4f);
		assertEquals(true, (dusk >> 16 & 0xFF) > (dusk & 0xFF));
	}

	@Test
	void eachLineGetsTheNextColourAndTheyComeRound() {
		assertEquals(LyricsRenderer.LINE_COLORS[0], LyricsRenderer.lineColor(-1));
		assertEquals(LyricsRenderer.LINE_COLORS[0], LyricsRenderer.lineColor(0));
		int n = LyricsRenderer.LINE_COLORS.length;
		for (int i = 0; i < 2 * n; i++) {
			assertNotEquals(LyricsRenderer.lineColor(i), LyricsRenderer.lineColor(i + 1));
		}
		assertEquals(LyricsRenderer.lineColor(2), LyricsRenderer.lineColor(2 + n));
	}
}
