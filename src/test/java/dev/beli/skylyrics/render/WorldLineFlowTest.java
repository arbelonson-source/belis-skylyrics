package dev.beli.skylyrics.render;

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
