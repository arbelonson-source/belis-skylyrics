package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A flowing line's phrase in the other reading direction (an English phrase in a Hebrew line, a Hebrew one in an English
 * line) is placed round older words as one piece: when any part of the room it needs is taken, the whole phrase moves on
 * along the row, its words still side by side in their own order, and with nothing in the way it is written exactly as
 * the line would write it anyway.
 */
class WorldFlowRunRoomTest {
	/** A word before the phrase, a phrase of three words, and a word after it. */
	private static final int[] RUNS = {WorldWords.RUN_NONE, WorldWords.RUN_FIRST, WorldWords.RUN_MIDDLE, WorldWords.RUN_LAST, WorldWords.RUN_NONE};
	private static final int FIRST = 1, LAST = 3;

	private static WorldWords.Sentence line(boolean rtl) {
		return new WorldWords.Sentence(-20, 5, 10, 0, 0, rtl);
	}

	/** Writes pieces of half width 2 and half height 1 (degrees) round {@code taken}; each piece's yaw and pitch. */
	private static float[][] write(WorldWords.Sentence s, List<float[]> taken) {
		float[][] out = new float[RUNS.length][];
		float[] half = new float[RUNS.length];
		Arrays.fill(half, (float) (s.distance * Math.tan(Math.toRadians(2))));
		for (int i = 0; i < RUNS.length; i++) {
			WorldWords.openRun(s, RUNS, half, i, 1f, 1f);
			float[] at = WorldWords.placeClear(s, 2, 1, 0, 0, taken);
			out[i] = new float[] {at[0], at[1]};
			WorldWords.closeRun(s, RUNS[i]);
		}
		return out;
	}

	/** The same pieces written straight along the line, with nothing to go round, as {@link WorldFlowOrderTest} does. */
	private static float[][] plain(WorldWords.Sentence s) {
		float[][] out = new float[RUNS.length][];
		float[] half = new float[RUNS.length];
		Arrays.fill(half, (float) (s.distance * Math.tan(Math.toRadians(2))));
		for (int i = 0; i < RUNS.length; i++) {
			WorldWords.openRun(s, RUNS, half, i, 1f, 1f);
			float[] at = s.place(2, 1, 0, 0);
			out[i] = new float[] {at[0], at[1]};
			WorldWords.closeRun(s, RUNS[i]);
		}
		return out;
	}

	private static String show(float[][] at) {
		StringBuilder s = new StringBuilder();
		for (float[] a : at) {
			s.append(String.format(" %.2f/%.2f", a[0], a[1]));
		}
		return s.toString();
	}

	@Test
	void withNothingInTheWayAPhraseIsWrittenAsTheLineWritesIt() {
		for (boolean rtl : new boolean[] {false, true}) {
			float[][] want = plain(line(rtl)), got = write(line(rtl), List.of());
			for (int i = 0; i < RUNS.length; i++) {
				assertEquals(want[i][0], got[i][0], 1e-4f, (rtl ? "rtl" : "ltr") + " piece " + i + ": wanted" + show(want) + ", got" + show(got));
				assertEquals(want[i][1], got[i][1], 1e-4f, (rtl ? "rtl" : "ltr") + " piece " + i + ": wanted" + show(want) + ", got" + show(got));
			}
			// Read against the line: the phrase's first word is the one furthest along the line
			float way = rtl ? -1 : 1;
			assertTrue(way * got[0][0] < way * got[LAST][0] && way * got[LAST][0] < way * got[2][0] && way * got[2][0] < way * got[FIRST][0]
				&& way * got[FIRST][0] < way * got[4][0], (rtl ? "rtl" : "ltr") + " out of order:" + show(got));
		}
	}

	/**
	 * Only the room's near end (where its last word goes) is taken on the first try, its far end (its first word) is clear:
	 * the whole phrase still moves on, so its last word does not land on the older one.
	 */
	@Test
	void aPhraseWhoseRoomIsTakenOnlyAtItsNearEndMovesOnAsOne() {
		for (boolean rtl : new boolean[] {false, true}) {
			float[][] free = plain(line(rtl));
			List<float[]> taken = List.<float[]>of(new float[] {free[LAST][0], free[LAST][1], 0.5f, 0.5f});
			movesOnAsOne(rtl, free, taken, write(line(rtl), taken));
		}
	}

	/** The room's far end (where its first word goes) is taken on the first try: the next try takes a whole new room. */
	@Test
	void aPhraseWhoseRoomIsTakenAtItsFarEndMovesOnAsOne() {
		for (boolean rtl : new boolean[] {false, true}) {
			float[][] free = plain(line(rtl));
			List<float[]> taken = List.<float[]>of(new float[] {free[FIRST][0], free[FIRST][1], 0.5f, 0.5f});
			movesOnAsOne(rtl, free, taken, write(line(rtl), taken));
		}
	}

	/** As {@link #write}, each piece stepping up or down off the row as far as the game's steps go. */
	private static float[][] bobbing(WorldWords.Sentence s, List<float[]> taken) {
		float[][] out = new float[RUNS.length][];
		float[] half = new float[RUNS.length];
		Arrays.fill(half, (float) (s.distance * Math.tan(Math.toRadians(2))));
		for (int i = 0; i < RUNS.length; i++) {
			WorldWords.openRun(s, RUNS, half, i, 1f, 1f);
			float[] at = WorldWords.placeClear(s, 2, 1, 0, WorldWords.step(s.pieces, 1f), taken);
			out[i] = new float[] {at[0], at[1]};
			WorldWords.closeRun(s, RUNS[i]);
		}
		return out;
	}

	/**
	 * The phrase's first word steps down off the row and its second steps up, as every flowing line's words do. A lit
	 * word sits just above the first one, clear of it: the second word, a step higher, would land on it, so the phrase
	 * moves on.
	 */
	@Test
	void aPhraseWhoseLaterWordStepsUpOntoAnOlderWordMovesOn() {
		for (boolean rtl : new boolean[] {false, true}) {
			float[][] free = bobbing(line(rtl), List.of());
			assertTrue(free[FIRST + 1][1] < free[FIRST][1] - 1, "the phrase's second word is not a step above its first:" + show(free));
			// Just clear of the first word: its half height 1, the older word's 0.5, a 0.4 margin round older words, and a bit
			List<float[]> taken = List.<float[]>of(new float[] {free[FIRST + 1][0], free[FIRST][1] - 1 - 0.5f - 0.45f, 0.5f, 0.5f});
			assertTrue(WorldWords.clear(free[FIRST][0], free[FIRST][1], 2, 1, taken), "the older word is not clear of the phrase's first word");
			float[][] got = bobbing(line(rtl), taken);
			String what = (rtl ? "rtl" : "ltr") + " older word at " + Arrays.toString(taken.get(0)) + ", free" + show(free) + ", got" + show(got);
			for (int i = FIRST; i <= LAST; i++) {
				assertTrue(WorldWords.clear(got[i][0], got[i][1], 2, 1, taken), "phrase word " + i + " lands on the older word: " + what);
			}
		}
	}

	private static void movesOnAsOne(boolean rtl, float[][] free, List<float[]> taken, float[][] got) {
		String what = (rtl ? "rtl" : "ltr") + " older word at " + taken.get(0)[0] + ", free" + show(free) + ", got" + show(got);
		for (int i = FIRST; i <= LAST; i++) {
			assertTrue(WorldWords.clear(got[i][0], got[i][1], 2, 1, taken), "phrase word " + i + " lands on the older word: " + what);
		}
		// Moved on along the row, all of it by the same amount: side by side and in their order, as with nothing in the way
		float shift = got[FIRST][0] - free[FIRST][0];
		assertTrue((rtl ? -shift : shift) > 4, "phrase did not move on along the row: " + what);
		for (int i = FIRST; i <= LAST; i++) {
			assertEquals(free[i][0] + shift, got[i][0], 1e-3f, "phrase word " + i + " not beside the others: " + what);
			assertEquals(free[i][1], got[i][1], 1e-3f, "phrase word " + i + " off its row: " + what);
		}
		assertEquals(free[0][0], got[0][0], 1e-4f, "the word before the phrase moved: " + what);
	}
}
