package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DaylightTest {
	@Test
	void clearNoonIsFullDaylight() {
		assertEquals(1f, LyricsRenderer.daylight(1f, 0f), 1e-6);
		assertEquals(1f, LyricsRenderer.sunUp(1f), 1e-6);
	}

	@Test
	void nightHasNone() {
		assertEquals(0f, LyricsRenderer.daylight(-1f, 0f), 1e-6);
		assertEquals(0f, LyricsRenderer.sunUp(-1f), 1e-6);
	}

	@Test
	void rainAtNightIsStillNight() {
		assertEquals(0f, LyricsRenderer.daylight(-1f, 1f), 1e-6);
		assertEquals(0f, LyricsRenderer.daylight(-1f, 0.5f), 1e-6);
	}

	/** Main's clear-weather daylight: 1 minus the vanilla star brightness (0.5 * (1 - s)^2) over its night peak 0.5. */
	private static float oldClearDaylight(float h) {
		float s = Math.max(0, Math.min(1, 2 * h + 0.25f));
		float stars = 0.5f * (1 - s) * (1 - s);
		return 1 - Math.max(0, Math.min(1, stars / 0.5f));
	}

	/** Sun height at a tick of the day: noon is 6000, the horizon is at 0 and 12000, midnight 18000 (the sun angle is in degrees). */
	private static float heightAt(long tick) {
		return (float) Math.cos(Math.toRadians((tick - 6000) / 24000.0 * 360.0));
	}

	@Test
	void clearWeatherMatchesTheOldCurveAtEveryTimeOfDay() {
		// noon, dusk, 13000, midnight, dawn
		long[] ticks = {6000, 12500, 13000, 18000, 23000};
		for (long tick : ticks) {
			float h = heightAt(tick);
			assertEquals(oldClearDaylight(h), LyricsRenderer.daylight(h, 0f), 1e-5, "tick " + tick);
		}
		for (int i = -20; i <= 20; i++) {
			float h = i * 0.05f;
			assertEquals(oldClearDaylight(h), LyricsRenderer.daylight(h, 0f), 1e-5, "height " + h);
		}
	}

	@Test
	void duskIsInBetween() {
		assertEquals(0.75f, LyricsRenderer.daylight(0.125f, 0f), 1e-6);
		assertEquals(0.375f, LyricsRenderer.daylight(0.125f, 1f), 1e-6);
	}

	@Test
	void rainNeverRaisesNightDaylight() {
		for (long tick : new long[] {13000, 18000, 23000}) {
			float h = heightAt(tick);
			for (float rain = 0; rain <= 1; rain += 0.25f) {
				assertTrue(LyricsRenderer.daylight(h, rain) <= LyricsRenderer.daylight(h, 0f), "tick " + tick + " rain " + rain);
			}
		}
		assertEquals(0f, LyricsRenderer.daylight(heightAt(18000), 1f), 1e-6);
	}

	@Test
	void rainGreysTheDaySky() {
		assertEquals(0.5f, LyricsRenderer.daylight(1f, 1f), 1e-6);
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
