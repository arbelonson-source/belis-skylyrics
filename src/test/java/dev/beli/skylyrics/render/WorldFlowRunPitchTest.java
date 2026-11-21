package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A stretch of words against a flowing line's direction takes the room its pieces really span at the pitch the row sits
 * at, so high in the sky its pieces are not drawn over the word before or after it.
 */
class WorldFlowRunPitchTest {
	private static final float HALF = 2f;

	/** Writes pieces of half width 2 on a row at {@code pitch}; returns each one's yaw. */
	private static float[] write(WorldWords.Sentence s, int[] runs) {
		float[] yaw = new float[runs.length], half = new float[runs.length];
		java.util.Arrays.fill(half, (float) (s.distance * Math.tan(Math.toRadians(HALF))));
		for (int i = 0; i < runs.length; i++) {
			WorldWords.openRun(s, runs, half, i, 1f, 1f);
			yaw[i] = s.place(HALF, 1, 0, 0)[0];
			WorldWords.closeRun(s, runs[i]);
		}
		return yaw;
	}

	private static String overlaps(float[] x, float pitch) {
		float w = WorldWords.Sentence.yawHalf(HALF, pitch);
		StringBuilder bad = new StringBuilder();
		for (int a = 0; a < x.length; a++) {
			for (int b = a + 1; b < x.length; b++) {
				if (Math.abs(x[a] - x[b]) < 2 * w - 1e-3f) {
					bad.append(a).append('/').append(b).append(" at ").append(x[a]).append(',').append(x[b]).append(" need ").append(2 * w).append("; ");
				}
			}
		}
		return bad.toString();
	}

	@Test
	void aStretchAgainstTheLineDoesNotOverlapItsNeighboursAtAnyPitch() {
		int[][] shapes = {
			{0, 0, WorldWords.RUN_FIRST, WorldWords.RUN_LAST, 0},
			{0, WorldWords.RUN_FIRST, WorldWords.RUN_MIDDLE, WorldWords.RUN_LAST, 0, 0},
			{WorldWords.RUN_FIRST, WorldWords.RUN_MIDDLE, WorldWords.RUN_LAST, 0, 0}
		};
		for (float pitch : new float[] {0, 30, 45, -30, -45}) {
			for (boolean rtl : new boolean[] {false, true}) {
				for (int[] shape : shapes) {
					WorldWords.Sentence s = new WorldWords.Sentence(-30, pitch, 10, 0, 0, rtl);
					// one row: nothing wraps
					s.width = 200;
					float[] x = write(s, shape);
					String bad = overlaps(x, pitch);
					assertTrue(bad.isEmpty(), "pitch " + pitch + " rtl " + rtl + " " + java.util.Arrays.toString(shape) + ": " + bad);
				}
			}
		}
	}
}
