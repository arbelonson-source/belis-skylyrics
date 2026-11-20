package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A slanted, bowed flowing row with a stretch against its direction in the middle (and the same wrapped onto the next
 * row): every piece, those inside the stretch too, is as far from the one beside it as it needs at the pitch it is drawn at.
 */
class WorldFlowSlopedRunTest {
	private static final float[] HALVES = {1.2f, 2.5f, 0.8f, 3f, 1.6f, 2f, 0.9f, 2.8f, 1.5f, 2.2f};

	private static int[] shape(int n, int from, int to) {
		int[] runs = new int[n];
		for (int i = from; i <= to; i++) {
			runs[i] = i == from ? WorldWords.RUN_FIRST : i == to ? WorldWords.RUN_LAST : WorldWords.RUN_MIDDLE;
		}
		return runs;
	}

	private static void run(StringBuilder bad, int[] counted, int[] runs, float width, boolean[] wrapped) {
		for (boolean rtl : new boolean[] {false, true}) {
			for (float slope : new float[] {-5, -2, 2, 5}) {
				for (float bend : new float[] {3, -3, 6, -6}) {
					for (float top : new float[] {0, 30, -30, 40, -40, 45, -45}) {
						WorldWords.Sentence s = new WorldWords.Sentence(-60, top, 10, 0, slope, rtl, bend);
						s.width = width;
						int[] rowOf = new int[HALVES.length];
						float[][] at = write(s, runs, rowOf);
						if (s.rows > 0) {
							wrapped[0] = true;
						}
						counted[0]++;
						WorldFlowWholeRunTest.check(bad, "rtl " + rtl + " slope " + slope + " bend " + bend + " top " + top + " width " + width + " runs "
								+ java.util.Arrays.toString(runs), at, HALVES, rowOf);
					}
				}
			}
		}
	}

	private static float[][] write(WorldWords.Sentence s, int[] runs, int[] rowOf) {
		float[] half = new float[HALVES.length];
		for (int i = 0; i < HALVES.length; i++) {
			half[i] = (float) (s.distance * Math.tan(Math.toRadians(HALVES[i])));
		}
		float[][] out = new float[HALVES.length][];
		for (int i = 0; i < HALVES.length; i++) {
			WorldWords.openRun(s, runs, half, i, 1f, 1f);
			float[] at = s.place(HALVES[i], 1, 0, 0);
			out[i] = new float[] {at[0], at[1]};
			rowOf[i] = s.rows;
			WorldWords.closeRun(s, runs[i]);
		}
		return out;
	}

	@Test
	void aMixedStretchInTheMiddleOfASlopedRow() {
		StringBuilder bad = new StringBuilder();
		int[] counted = {0};
		boolean[] wrapped = {false};
		for (int[] runs : new int[][] {shape(10, 3, 6), shape(10, 4, 5), shape(10, 2, 7)}) {
			run(bad, counted, runs, 200, wrapped);
		}
		assertTrue(counted[0] > 100, "cases " + counted[0]);
		assertTrue(bad.length() == 0, "overlapping neighbours:\n" + bad);
	}

	@Test
	void aMixedStretchThatWrapsToTheNextRow() {
		StringBuilder bad = new StringBuilder();
		int[] counted = {0};
		boolean[] wrapped = {false};
		for (int[] runs : new int[][] {shape(10, 3, 6), shape(10, 4, 5), shape(10, 2, 7)}) {
			for (float width : new float[] {16, 24, 30}) {
				run(bad, counted, runs, width, wrapped);
			}
		}
		assertTrue(wrapped[0], "no case wrapped: the setup does not exercise a wrap");
		assertTrue(bad.length() == 0, "overlapping neighbours:\n" + bad);
	}
}
