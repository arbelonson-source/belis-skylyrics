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
}
