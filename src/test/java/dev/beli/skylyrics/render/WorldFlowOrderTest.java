package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** A flowing World line writes its pieces where they are read: a phrase against the line's direction keeps its own order. */
class WorldFlowOrderTest {
	@Test
	void runsOfWords() {
		List<TopBarWords.Piece> hebrew = List.of(p("אני"), p("אוהב"), p("את"), p("NYC"), p("2024"));
		assertArrayEquals(new int[] {0, 0, 0, WorldWords.RUN_FIRST, WorldWords.RUN_LAST}, WorldWords.runs(hebrew, true));
		List<TopBarWords.Piece> english = List.of(p("I"), p("love"), p("שלום"), p("עולם"));
		assertArrayEquals(new int[] {0, 0, WorldWords.RUN_FIRST, WorldWords.RUN_LAST}, WorldWords.runs(english, false));
		List<TopBarWords.Piece> three = List.of(p("I"), p("love"), p("שלום"), p("עולם"), p("יפה"));
		assertArrayEquals(new int[] {0, 0, WorldWords.RUN_FIRST, WorldWords.RUN_MIDDLE, WorldWords.RUN_LAST}, WorldWords.runs(three, false));
	}

	@Test
	void plainLinesHaveNoReversedStretch() {
		assertArrayEquals(new int[4], WorldWords.runs(List.of(p("a"), p("b"), p("c"), p("d")), false));
		assertArrayEquals(new int[4], WorldWords.runs(List.of(p("א"), p("ב"), p("ג"), p("ד")), true));
		assertArrayEquals(new int[1], WorldWords.runs(List.of(p("a")), false));
		assertArrayEquals(new int[0], WorldWords.runs(List.of(), false));
	}

	private static TopBarWords.Piece p(String w) {
		return new TopBarWords.Piece(w, true);
	}

	/** Writes pieces of half width 2 and half height 1 through the World layout's own run handling, returning each one's yaw. */
	private static float[] write(WorldWords.Sentence s, int[] runs) {
		float[] yaw = new float[runs.length], half = new float[runs.length];
		// a piece of half width 2 degrees at the sentence's distance
		java.util.Arrays.fill(half, (float) (s.distance * Math.tan(Math.toRadians(2))));
		for (int i = 0; i < runs.length; i++) {
			WorldWords.openRun(s, runs, half, i, 1f, 1f);
			yaw[i] = s.place(2, 1, 0, 0)[0];
			WorldWords.closeRun(s, runs[i]);
		}
		return yaw;
	}

	@Test
	void hebrewLineWithEnglishPhraseReadsRightToLeftWithThePhraseLeftToRight() {
		WorldWords.Sentence s = new WorldWords.Sentence(-20, 5, 10, 0, 0, true);
		float[] x = write(s, new int[] {0, 0, 0, WorldWords.RUN_FIRST, WorldWords.RUN_LAST});
		// אני אוהב את NYC 2024, as seen from the left: NYC 2024 את אוהב אני
		assertTrue(x[3] < x[4] && x[4] < x[2] && x[2] < x[1] && x[1] < x[0], java.util.Arrays.toString(x));
	}

	@Test
	void englishLineWithHebrewPhraseReadsLeftToRightWithThePhraseReversed() {
		WorldWords.Sentence s = new WorldWords.Sentence(-20, 5, 10, 0, 0, false);
		float[] x = write(s, new int[] {0, 0, WorldWords.RUN_FIRST, WorldWords.RUN_LAST});
		// I love שלום עולם, as seen from the left: I love עולם שלום
		assertTrue(x[0] < x[1] && x[1] < x[3] && x[3] < x[2], java.util.Arrays.toString(x));
	}

	@Test
	void theStretchKeepsItsRoomAndTheWordsAfterItComeAfter() {
		WorldWords.Sentence s = new WorldWords.Sentence(-20, 5, 10, 0, 0, false);
		float[] x = write(s, new int[] {0, WorldWords.RUN_FIRST, WorldWords.RUN_LAST, 0});
		assertTrue(x[2] < x[1] && x[1] < x[3], java.util.Arrays.toString(x));
		// pieces do not overlap: each is 4 wide
		assertTrue(x[1] - x[2] >= 4 - 1e-3f && x[3] - x[1] >= 4 - 1e-3f, java.util.Arrays.toString(x));
	}

	@Test
	void aLineThatStartsOverInTheMiddleOfAStretchOpensItAgainForWhatIsLeft() {
		int[] runs = {0, WorldWords.RUN_FIRST, WorldWords.RUN_MIDDLE, WorldWords.RUN_LAST, 0};
		// The new line's first piece is the stretch's middle one (the first was on the line that was left)
		WorldWords.Sentence s = new WorldWords.Sentence(-20, 5, 10, 0, 0, false);
		float[] half = new float[runs.length];
		java.util.Arrays.fill(half, (float) (s.distance * Math.tan(Math.toRadians(2))));
		float[] x = new float[runs.length];
		for (int i = 2; i < runs.length; i++) {
			WorldWords.openRun(s, runs, half, i, 1f, 1f);
			x[i] = s.place(2, 1, 0, 0)[0];
			WorldWords.closeRun(s, runs[i]);
		}
		assertTrue(x[3] < x[2] && x[2] < x[4], java.util.Arrays.toString(x));
		assertTrue(x[2] - x[3] >= 4 - 1e-3f && x[4] - x[2] >= 4 - 1e-3f, java.util.Arrays.toString(x));
	}

	@Test
	void aLineOfOneDirectionIsWrittenAsBefore() {
		for (boolean rtl : new boolean[] {false, true}) {
			WorldWords.Sentence a = new WorldWords.Sentence(-20, 5, 10, 0, 0, rtl), b = new WorldWords.Sentence(-20, 5, 10, 0, 0, rtl);
			// a goes through the stretch-aware writer with no stretches, b is placed piece by piece as it always was
			float[] x = write(a, new int[9]);
			for (int i = 0; i < 9; i++) {
				assertEquals(b.place(2, 1, 0, 0)[0], x[i], 1e-6f);
			}
		}
	}
}
