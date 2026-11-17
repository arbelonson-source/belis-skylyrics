package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkyClockTest {
	/** About 70 hours of sky time, past where a float clock can no longer take a 60 fps step. */
	private static final double LATE = 250_000.0;
	private static final double STEP = 1 / 60.0;

	@Test
	void ribbonsAndCloudsKeepMovingOnALateClock() {
		assertNotEquals(SkyEffects.ribbonYaw(LATE, 0, 0.5f), SkyEffects.ribbonYaw(LATE + STEP, 0, 0.5f));
		assertNotEquals(SkyEffects.blobYaw(LATE, 1), SkyEffects.blobYaw(LATE + STEP, 1));
		assertNotEquals(SkyEffects.blobElev(LATE, 1), SkyEffects.blobElev(LATE + STEP, 1));
		assertNotEquals(CometSky.streak(0.3f, 0.2f, LATE), CometSky.streak(0.3f, 0.2f, LATE + 50));
	}

	@Test
	void aTwinklePhaseStaysSmoothAndInRangeOnALateClock() {
		float a = SkyClock.phase(LATE, 0.7, 2.3), b = SkyClock.phase(LATE + STEP, 0.7, 2.3);
		assertTrue(a >= 0 && a < 2 * Math.PI && b >= 0 && b < 2 * Math.PI);
		// the sine moves by the same amount as it does early on
		double late = Math.sin(b) - Math.sin(a);
		double early = Math.sin(SkyClock.phase(10 + STEP, 0.7, 2.3)) - Math.sin(SkyClock.phase(10, 0.7, 2.3));
		assertTrue(Math.abs(late) < 0.7 * STEP * 1.01);
		assertEquals(Math.sin(0.7 * LATE + 2.3), Math.sin(a), 1e-4);
		assertTrue(Math.abs(early) < 0.7 * STEP * 1.01);
	}
}
