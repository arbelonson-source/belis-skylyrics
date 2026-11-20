package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A flowing row's pieces are sized for the pitch they are really drawn at, so a right-to-left row (drawn mirrored) that
 * slants or bows does not draw neighbours over each other near the top or bottom of the screen.
 */
class WorldFlowRtlSlopeTest {
	private static final float[] HALVES = {1.2f, 2.5f, 0.8f, 3f, 1.6f, 2f, 0.9f, 2.8f};

	@Test
	void neighboursAreNotDrawnOverEachOtherAtTheirDrawnPitch() {
		int checked = 0;
		StringBuilder bad = new StringBuilder();
		for (boolean rtl : new boolean[] {false, true}) {
			for (float slope : new float[] {-5, 0, 5}) {
				for (float bend : new float[] {0, 3, -3}) {
					for (float top : new float[] {0, 30, -30, 40, -40}) {
						WorldWords.Sentence s = new WorldWords.Sentence(-60, top, 10, 0, slope, rtl, bend);
						s.width = 200;
						float[] yaw = new float[HALVES.length], pit = new float[HALVES.length];
						for (int i = 0; i < HALVES.length; i++) {
							float[] at = s.place(HALVES[i], 1, 0, 0);
							yaw[i] = at[0];
							pit[i] = at[1];
						}
						for (int i = 0; i + 1 < HALVES.length; i++) {
							checked++;
							float need = WorldWords.Sentence.yawHalf(HALVES[i], pit[i]) + WorldWords.Sentence.yawHalf(HALVES[i + 1], pit[i + 1]);
							float have = Math.abs(yaw[i + 1] - yaw[i]);
							if (have < need - 0.02f) {
								bad.append("rtl ").append(rtl).append(" slope ").append(slope).append(" bend ").append(bend).append(" top ").append(top)
									.append(" piece ").append(i).append(": ").append(have).append(" apart, need ").append(need).append('\n');
							}
						}
					}
				}
			}
		}
		assertTrue(checked > 100, "pairs checked " + checked);
		assertTrue(bad.length() == 0, "overlapping neighbours:\n" + bad);
	}
}
