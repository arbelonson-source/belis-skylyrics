package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CalmTest {
	private static final long SETTLE = 450_000_000L;
	private static final long MS = 1_000_000L;

	@Test
	void hideLightningFlashesGivesTheSteadyClockAndNoWordFlash() {
		assertTrue(Float.isNaN(Calm.flashSince(true, 0.1f)));
		assertEquals(0.1f, Calm.flashSince(false, 0.1f));
		assertEquals(0f, Calm.flash(true, 0.8f));
		assertEquals(0.8f, Calm.flash(false, 0.8f));
	}

	@Test
	void distortionEffectsScaleMotionFromStillToUnchanged() {
		assertEquals(1.2f, Calm.around(1.2f, 1, Calm.motion(1.0)), 1e-6f);
		assertEquals(1f, Calm.around(1.2f, 1, Calm.motion(0.0)), 0f);
		assertEquals(1.1f, Calm.around(1.2f, 1, Calm.motion(0.5)), 1e-6f);
		assertEquals(1f, Calm.motion(7.0), 0f);
		assertEquals(0f, Calm.motion(-1.0), 0f);
		assertEquals(1f, Calm.motion(Double.NaN), 0f);
	}

	/** Counts the flashes that start when a new line begins every {@code gapMs}, for {@code lines} lines. */
	private static int flashes(long gapMs, int lines) {
		LineClock clock = new LineClock();
		clock.frame(true, false, 0, 0, SETTLE);
		int flashes = 0;
		for (int i = 1; i <= lines; i++) {
			long now = i * gapMs * MS;
			clock.frame(true, false, i, now, SETTLE);
			if (clock.flashSince(now) == 0f) {
				flashes++;
			}
		}
		return flashes;
	}

	@Test
	void linesTwoTenthsApartFlashAtMostEveryThirdOfASecond() {
		// 3 s of lines at 0.2 s: flashes at 0.2 (first, settled before), then 0.6, 1.0, ... every 0.4 s
		int flashes = flashes(200, 15);
		assertTrue(flashes <= 10, "flashes " + flashes);
		assertTrue(flashes >= 7, "flashes " + flashes);
	}

	@Test
	void linesHalfASecondApartFlashEveryLine() {
		assertEquals(10, flashes(500, 10));
	}

	@Test
	void aSkippedFlashLetsTheRunningOneContinueButTheLineStillMoves() {
		LineClock clock = new LineClock();
		clock.frame(true, false, 0, 0, SETTLE);
		clock.frame(true, false, 1, 1_000 * MS, SETTLE);
		clock.frame(true, false, 2, 1_200 * MS, SETTLE);
		assertEquals(0.2f, clock.flashSince(1_200 * MS), 1e-4f);
		assertEquals(0f, clock.secondsSince(1_200 * MS), 1e-6f);
		assertEquals(2, clock.last());
		assertEquals(1, clock.previous());
		assertFalse(Calm.flashDue(1_000 * MS, 1_300 * MS));
		assertTrue(Calm.flashDue(1_000 * MS, 1_334 * MS));
	}
}
