package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The Rainbow colour follows System.nanoTime(), which counts from when the computer started, not from when the game did.
 * Turned into a float before the division, it lost its steps as the computer stayed on: a week in, the lyrics changed colour
 * 16 times a second instead of every frame, a month in 5 times in jumps of 6 degrees, and the Themes tile's faster
 * cycle four times as coarsely.
 */
class RainbowCycleTest {
	private static final long DAY = 86_400_000_000_000L;
	private static final long FRAME = 16_666_667L;
	private static final long LYRICS = LyricsRenderer.RAINBOW_NS;
	private static final long THEME_TILE = 4_000_000_000L;

	/** Distinct colours in one second of 60 frames a second, starting {@code uptime} after the computer started. */
	private static int colours(long uptime, long period) {
		java.util.Set<Float> seen = new java.util.HashSet<>();
		for (int i = 0; i < 60; i++) {
			seen.add(SkyClock.cycle(uptime + i * FRAME, period));
		}
		return seen.size();
	}

	/** The biggest change between two frames, as a fraction of the cycle (going round past 1 counts the short way). */
	private static float biggestStep(long uptime, long period) {
		float most = 0;
		for (int i = 0; i < 60; i++) {
			float d = Math.abs(SkyClock.cycle(uptime + (i + 1) * FRAME, period) - SkyClock.cycle(uptime + i * FRAME, period));
			most = Math.max(most, Math.min(d, 1 - d));
		}
		return most;
	}

	@Test
	void everyFrameMovesTheColourHoweverLongTheComputerHasBeenOn() {
		for (long days : new long[] {0, 2, 7, 14, 30, 52}) {
			long uptime = days * DAY + 3_600_000_000_000L;
			for (long period : new long[] {LYRICS, THEME_TILE}) {
				int n = colours(uptime, period);
				assertEquals(60, n, "colours in a second at " + days + " days, cycle " + period / 1_000_000_000L + " s");
				float step = biggestStep(uptime, period);
				// One frame's share of the cycle, as on a computer that was just started
				assertEquals(FRAME / (double) period, step, 1e-4,
						"biggest step at " + days + " days, cycle " + period / 1_000_000_000L + " s: " + step * 360 + " degrees");
			}
		}
	}

	@Test
	void theCycleRunsFromZeroToOneAndGoesRound() {
		assertEquals(0f, SkyClock.cycle(0, LYRICS));
		assertEquals(0.25f, SkyClock.cycle(4_000_000_000L, LYRICS), 1e-6);
		assertEquals(0.25f, SkyClock.cycle(30 * DAY + 4_000_000_000L, LYRICS), 1e-6); // 30 days is a whole number of cycles
		assertEquals(0.9375f, SkyClock.cycle(LYRICS - 1_000_000_000L, LYRICS), 1e-6);
		// A nanosecond short of a full cycle rounds to 1 as a float: that is the start again, never past it
		float last = SkyClock.cycle(LYRICS - 1, LYRICS);
		assertTrue(last >= 0f && last < 1f, "just before a full cycle: " + last);
		// nanoTime may be below zero on some systems: still within 0 to 1
		float before = SkyClock.cycle(-4_000_000_000L, LYRICS);
		assertEquals(0.75f, before, 1e-6);
	}
}
