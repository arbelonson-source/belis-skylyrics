package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GalaxySkyTest {
	private static int sum(int rgb) {
		return (rgb >> 16 & 255) + (rgb >> 8 & 255) + (rgb & 255);
	}

	@Test
	void coreIsBrightestAndTheRimFadesToNothing() {
		int core = sum(GalaxySky.light(0, 0));
		for (int a = 0; a < 360; a += 15) {
			assertTrue(core > sum(GalaxySky.light(0.5f, a)));
		}
		for (int a = 0; a < 360; a += 15) {
			assertEquals(0, GalaxySky.light(1, a), "no hard edge at " + a);
		}
	}

	@Test
	void twoArmsWindOutHalfATurnApart() {
		for (float r = 0.2f; r < 0.9f; r += 0.1f) {
			for (int a = 0; a < 360; a += 20) {
				assertEquals(GalaxySky.arm(r, a), GalaxySky.arm(r, a + 180), 1e-4f);
			}
			// Somewhere round each ring is an arm and somewhere is a gap
			float most = 0, least = 1;
			for (int a = 0; a < 360; a += 2) {
				most = Math.max(most, GalaxySky.arm(r, a));
				least = Math.min(least, GalaxySky.arm(r, a));
			}
			assertTrue(most > 0.95f && least < 0.05f, "ring " + r);
		}
	}

	@Test
	void armsJoinUpAfterAFullTurn() {
		for (float r = 0.15f; r < 1; r += 0.1f) {
			assertEquals(sum(GalaxySky.light(r, 0)), sum(GalaxySky.light(r, 360)), 3);
		}
	}

	@Test
	void staysClearOfTheLyricsAndTheHorizon() {
		GalaxySky galaxy = new GalaxySky();
		float lowest = Float.MAX_VALUE, nearest = Float.MAX_VALUE;
		double size = Math.tan(Math.toRadians(GalaxySky.RADIUS));
		for (int a = 0; a < 360; a += 5) {
			float[] p = galaxy.slant((float) (size * Math.cos(Math.toRadians(a))), (float) (size * Math.sin(Math.toRadians(a))));
			// Small-angle offsets from the middle, in degrees: across to the right, then up
			float yaw = GalaxySky.YAW + (float) Math.toDegrees(Math.atan(p[0]));
			float elev = GalaxySky.ELEV + (float) Math.toDegrees(Math.atan(p[1]));
			lowest = Math.min(lowest, elev);
			nearest = Math.min(nearest, (float) Math.hypot(yaw, (elev - SkyTint.WORDS_ELEVATION)));
		}
		assertTrue(lowest > 20, "lowest " + lowest);
		assertTrue(nearest > 15, "nearest " + nearest);
	}
}
