package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Elytra mode turns the rest of a flowing line along with you. A phrase in the other reading direction that is half
 * written when you turn keeps its remaining words beside its first one, not where they would have gone before the turn.
 */
class WorldFlowRunTurnTest {
	/** A word before the phrase, a phrase of three words, and a word after it. */
	private static final int[] RUNS = {WorldWords.RUN_NONE, WorldWords.RUN_FIRST, WorldWords.RUN_MIDDLE, WorldWords.RUN_LAST, WorldWords.RUN_NONE};

	/** Writes the pieces (half width 2, half height 1 degrees), turning the line by {@code turn} and {@code tilt} after piece {@code after}. */
	private static float[][] write(boolean rtl, int after, float turn, float tilt) {
		WorldWords.Sentence s = new WorldWords.Sentence(-20, 5, 10, 0, 0, rtl);
		float[] half = new float[RUNS.length];
		Arrays.fill(half, (float) (s.distance * Math.tan(Math.toRadians(2))));
		float[][] out = new float[RUNS.length + 1][];
		for (int i = 0; i < RUNS.length; i++) {
			WorldWords.openRun(s, RUNS, half, i, 1f, 1f);
			float[] at = s.place(2, 1, 0, 0);
			out[i] = new float[] {at[0], at[1]};
			WorldWords.closeRun(s, RUNS[i]);
			if (i == after) {
				float before = s.runPitch - s.top;
				s.swing(turn, tilt);
				// How far the room's pitch got from the rows the line is now on
				out[RUNS.length] = new float[] {s.runPitch - s.top - before};
			}
		}
		return out;
	}

	@Test
	void turningWhileAPhraseIsWrittenTurnsTheRestOfItAlong() {
		for (boolean rtl : new boolean[] {false, true}) {
			float[][] still = write(rtl, -1, 0, 0);
			for (int after = 1; after <= 2; after++) {
				float[][] turned = write(rtl, after, 10, 0);
				for (int i = after + 1; i < RUNS.length; i++) {
					assertEquals(still[i][0] + 10, turned[i][0], 1e-3f, (rtl ? "rtl" : "ltr") + " piece " + i + " after a 10 degree turn at piece "
						+ after + ": the words before it turned by " + (turned[after][0] - still[after][0]));
				}
			}
		}
	}

	@Test
	void tiltingWhileAPhraseIsWrittenTakesItsRoomDownWithTheRows() {
		for (boolean rtl : new boolean[] {false, true}) {
			float[][] still = write(rtl, -1, 0, 0), tilted = write(rtl, 1, 0, 6);
			for (int i = 2; i < RUNS.length; i++) {
				assertEquals(still[i][1] + 6, tilted[i][1], 1e-3f, (rtl ? "rtl" : "ltr") + " piece " + i + " pitch after a 6 degree tilt");
			}
			assertEquals(0, tilted[RUNS.length][0], 1e-4f, (rtl ? "rtl" : "ltr") + " the phrase's room stayed at its old pitch while its rows moved down");
		}
	}
}
