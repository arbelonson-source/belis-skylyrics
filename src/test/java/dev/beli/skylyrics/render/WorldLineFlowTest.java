package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

class WorldLineFlowTest {
	@Test
	void aLongLineDoesNotLoseItselfAsItsRowsGoDown() {
		WorldWords.Sentence s = new WorldWords.Sentence(-17, -12, 10, 0);
		for (int k = 0; k < 40; k++) {
			s.place(5, 3, 0, 0);
		}
		assertTrue(s.rows >= 6, "rows " + s.rows);
		assertFalse(s.lost(0, -12, 36));
	}

	@Test
	void lookingStraightDownOrUpDoesNotLoseALineThatStartedAtTheClamp() {
		assertFalse(new WorldWords.Sentence(-17, 40, 10, 0).lost(0, 78, 36));
		assertFalse(new WorldWords.Sentence(-17, -70, 10, 0).lost(0, -102, 36));
	}


	/** A line on its second row, starting at {@code top}, asked against what a view at {@code pitch} and {@code fov} shows. */
	private static boolean fullAt(float top, int rows, float pitch, double fov) {
		WorldWords.Sentence s = new WorldWords.Sentence(-17, top, 10, 0);
		s.rows = rows;
		return s.full(WorldWords.lowestSeen(pitch, WorldWords.halfView(0, fov)));
	}

	@Test
	void aRowBelowTheScreenStartsTheLineOverWhereTheHorizonIsLooked() {
		assertTrue(fullAt(38, 1, 0, 70));
		assertFalse(fullAt(30, 1, 0, 70));
	}

	@Test
	void lookingUpMovesTheLimitUpAndAWiderViewMovesItDown() {
		assertTrue(fullAt(8, 1, -30, 70));
		assertFalse(fullAt(0, 1, -30, 70));
		assertTrue(fullAt(45, 1, 0, 70));
		assertFalse(fullAt(45, 1, 0, 110));
		assertTrue(fullAt(52, 1, 0, 110));
	}

	@Test
	void theFirstRowNeverCountsAsFullBelowTheScreen() {
		assertFalse(fullAt(45, 0, 0, 70));
	}

	@Test
	void theViewsHalfHeightComesFromTheProjectionWhenThereIsOne() {
		assertEquals(35, WorldWords.halfView(0, 70), 1e-3);
		assertEquals(10, WorldWords.halfView((float) Math.tan(Math.toRadians(10)), 70), 1e-3);
	}

	@Test
	void aLongLineAtABigSizeNeverGoesBehindYourFeet() {
		WorldWords.Sentence s = new WorldWords.Sentence(-17, 0, 10, 0);
		s.width = 50;
		float lowest = -999;
		for (int k = 0; k < 60; k++) {
			if (s.full()) {
				s = new WorldWords.Sentence(-17, 0, 10, 0);
				s.width = 50;
			}
			lowest = Math.max(lowest, s.place(5, 3, 0, 0)[1]);
		}
		assertTrue(lowest < 70, "lowest piece at " + lowest);
	}

	@Test
	void changingTheFontStartsTheWordsOverButTheSameStyleDoesNot() {
		WorldWords w = new WorldWords();
		assertFalse(w.restyled(Style.EMPTY));
		assertFalse(w.restyled(Style.EMPTY));
		assertTrue(w.restyled(Style.EMPTY.withBold(true)));
		assertFalse(w.restyled(Style.EMPTY.withBold(true)));
	}

	@Test
	void atTheHorizonAndLookingUpTheStartAndTheLimitsStayAsTheyWere() {
		for (float pitch : new float[] {-90, -30, 0, 30, 55}) {
			assertEquals(40, WorldWords.Sentence.startDown(pitch), 1e-3, "start at " + pitch);
			assertEquals(50, WorldWords.Sentence.rowLimit(pitch), 1e-3, "limit at " + pitch);
			assertEquals(Math.min(50, pitch + 35 - WorldWords.ROW_MARGIN), WorldWords.lowestSeen(pitch, 35), 1e-3);
		}
	}

	@Test
	void lookingDownAtSeventyFiveAStartingLineIsOnScreenAndItsFirstRowIsNotFull() {
		float view = 75, half = WorldWords.halfView(0, 70);
		float top = WorldWords.Sentence.startDown(view);
		assertTrue(Math.abs(top - view) < half, "starts at " + top);
		WorldWords.Sentence s = new WorldWords.Sentence(-17, top, 10, 0);
		assertFalse(s.full(WorldWords.lowestSeen(view, half)));
		assertFalse(s.lost(0, view - 12, 36));
		s.place(5, 3, 0, 0);
		assertFalse(s.full(WorldWords.lowestSeen(view, half)));
		assertFalse(s.lost(0, view - 12, 36));
		assertTrue(Math.abs(s.top - view) < half, "first row at " + s.top);
	}

	@Test
	void lookingStraightDownTheLimitsStayUnderEightyFive() {
		assertTrue(WorldWords.Sentence.startDown(90) < 85);
		assertTrue(WorldWords.Sentence.rowLimit(90) < 85);
		assertTrue(WorldWords.lowestSeen(90, 55) < 85);
		WorldWords.Sentence s = new WorldWords.Sentence(-17, WorldWords.Sentence.startDown(90), 10, 0);
		float low = WorldWords.lowestSeen(90, WorldWords.halfView(0, 70));
		for (int k = 0; k < 40 && !s.full(low); k++) {
			s.place(5, 3, 0, 0);
		}
		assertTrue(s.full(low), "the line fills up");
		assertTrue(s.top < 90, "rows end at " + s.top);
	}
}
