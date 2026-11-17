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

	/** Collects the corners handed on, as bits so that nothing may differ at all. */
	private static final class Recorder extends SkyCanvas {
		final java.util.List<int[]> corners = new java.util.ArrayList<>();

		@Override
		protected void put(float x, float y, float z, int argb) {
			corners.add(new int[] {Float.floatToIntBits(x), Float.floatToIntBits(y), Float.floatToIntBits(z), argb});
		}
	}

	/** The galaxy as it was drawn before the grid points were shared: every corner worked out by itself. */
	private static void drawEachCornerAlone(GalaxySky galaxy, SkyCanvas c, float radius, float baseYaw, float level, float time) {
		SkyCanvas.SkyFrame frame = new SkyCanvas.SkyFrame().aim(baseYaw + GalaxySky.YAW, GalaxySky.ELEV);
		int n = GalaxySky.SEGMENTS;
		float[] cos = new float[n + 1], sin = new float[n + 1], ring = new float[GalaxySky.RINGS + 1];
		float spin = SkyClock.phase(time, GalaxySky.SPIN, 0);
		float cs = (float) Math.cos(spin), sn = (float) Math.sin(spin);
		for (int j = 0; j <= n; j++) {
			double a = 2 * Math.PI * j / n;
			float cj = (float) Math.cos(a), sj = (float) Math.sin(a);
			cos[j] = cj * cs - sj * sn;
			sin[j] = sj * cs + cj * sn;
		}
		for (int i = 0; i <= GalaxySky.RINGS; i++) {
			float t = (float) i / GalaxySky.RINGS;
			ring[i] = t * t * 0.6f + t * 0.4f;
		}
		float r = radius * GalaxySky.LAYER;
		float size = (float) Math.tan(GalaxySky.RADIUS * (float) (Math.PI / 180));
		c.fadeBelowHorizon(3);
		for (int i = 0; i < GalaxySky.RINGS; i++) {
			for (int j = 0; j < n; j++) {
				int[][] at = {{i, j}, {i, j + 1}, {i + 1, j + 1}, {i + 1, j}};
				for (int[] p : at) {
					float out = ring[p[0]] * size;
					float[] s = galaxy.slant(out * cos[p[1]], out * sin[p[1]]);
					c.at(frame, s[0], s[1], r, SkyColors.argb(GalaxySky.light(ring[p[0]], 360f * p[1] / n), level));
				}
			}
		}
		c.fadeBelowHorizon(0);
	}

	@Test
	void sharedGridPointsDrawTheSameQuadsAsCornersWorkedOutAlone() {
		for (float[] in : new float[][] {{100, 0, 1, 0}, {80, 135, 0.4f, 37.5f}, {64, -90, 0.9f, 1234.5f}}) {
			GalaxySky galaxy = new GalaxySky();
			Recorder now = new Recorder(), before = new Recorder();
			galaxy.draw(now, in[0], in[1], in[2], in[3]);
			drawEachCornerAlone(galaxy, before, in[0], in[1], in[2], in[3]);
			assertEquals(before.corners.size(), now.corners.size());
			assertEquals(GalaxySky.RINGS * GalaxySky.SEGMENTS * 4, now.corners.size());
			for (int k = 0; k < now.corners.size(); k++) {
				org.junit.jupiter.api.Assertions.assertArrayEquals(before.corners.get(k), now.corners.get(k), "corner " + k);
			}
		}
	}
}
