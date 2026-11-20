package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A flowing row where the whole line is one stretch against the row's direction (an rtl line of left-to-right pieces,
 * and the reverse): at high or low pitch, neighbouring pieces are as far apart as they need at the pitch they are drawn at.
 */
class WorldFlowWholeRunTest {
	private static final float[] HALVES = {1.2f, 2.5f, 0.8f, 3f, 1.6f, 2f, 0.9f, 2.8f};

	private static int[] whole(int n) {
		int[] runs = new int[n];
		for (int i = 0; i < n; i++) {
			runs[i] = i == 0 ? WorldWords.RUN_FIRST : i == n - 1 ? WorldWords.RUN_LAST : WorldWords.RUN_MIDDLE;
		}
		return runs;
	}

	/** Writes the pieces as {@code runs} marks them; returns {yaw, drawn pitch} of each. */
	private static float[][] write(WorldWords.Sentence s, int[] runs, float[] halves, int[] rowOf) {
		float[] half = new float[halves.length];
		for (int i = 0; i < halves.length; i++) {
			half[i] = (float) (s.distance * Math.tan(Math.toRadians(halves[i])));
		}
		float[][] out = new float[halves.length][];
		for (int i = 0; i < halves.length; i++) {
			WorldWords.openRun(s, runs, half, i, 1f, 1f);
			float[] at = s.place(halves[i], 1, 0, 0);
			out[i] = new float[] {at[0], at[1]};
			rowOf[i] = s.rows;
			WorldWords.closeRun(s, runs[i]);
		}
		return out;
	}

	/** Pieces of one row, in the order they stand from left to right: each must clear the next at its own pitch. */
	static void check(StringBuilder bad, String name, float[][] at, float[] halves, int[] rowOf) {
		Integer[] order = new Integer[at.length];
		for (int i = 0; i < order.length; i++) {
			order[i] = i;
		}
		java.util.Arrays.sort(order, (a, b) -> Float.compare(at[a][0], at[b][0]));
		for (int k = 0; k + 1 < order.length; k++) {
			int a = order[k], b = order[k + 1];
			if (rowOf[a] != rowOf[b]) {
				continue;
			}
			float need = WorldWords.Sentence.yawHalf(halves[a], at[a][1]) + WorldWords.Sentence.yawHalf(halves[b], at[b][1]);
			float have = at[b][0] - at[a][0];
			if (have < need - 0.02f) {
				bad.append(name).append(" pieces ").append(a).append(" and ").append(b).append(": ").append(have).append(" apart, need ").append(need)
					.append(" (pitches ").append(at[a][1]).append(", ").append(at[b][1]).append(")\n");
			}
		}
	}

	@Test
	void aWholeLineAgainstTheRowDoesNotOverlapAtAnyPitch() {
		StringBuilder bad = new StringBuilder();
		int cases = 0;
		for (boolean rtl : new boolean[] {false, true}) {
			for (float top : new float[] {0, 30, -30, 40, -40, 45, -45, 50, -50}) {
				for (float slope : new float[] {0, 3, -3}) {
					for (float bend : new float[] {0, 2, -2}) {
						for (float width : new float[] {200, 34}) {
							WorldWords.Sentence s = new WorldWords.Sentence(-60, top, 10, 0, slope, rtl, bend);
							s.width = width;
							int[] rowOf = new int[HALVES.length];
							float[][] at = write(s, whole(HALVES.length), HALVES, rowOf);
							cases++;
							check(bad, "rtl " + rtl + " top " + top + " slope " + slope + " bend " + bend + " width " + width, at, HALVES, rowOf);
						}
					}
				}
			}
		}
		assertTrue(cases > 100, "cases " + cases);
		assertTrue(bad.length() == 0, "overlapping neighbours in a whole-line stretch:\n" + bad);
	}
}
