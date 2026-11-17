package dev.beli.skylyrics.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TextCutInitialTest {
	@Test
	void hundredsOfCombiningMarksDrawOnlyTheBaseLetter() {
		assertEquals("a", TextCut.initial("a" + "́".repeat(300) + "bc"));
	}

	@Test
	void ordinaryFirstCharactersStayWhole() {
		assertEquals("é", TextCut.initial("  école"));
		assertEquals("👨‍👩‍👧‍👦", TextCut.initial("👨‍👩‍👧‍👦 x"));
		assertEquals("", TextCut.initial("   "));
	}

	@Test
	void baseCutKeepsASurrogatePair() {
		assertEquals("😀", TextCut.initial("😀" + "́".repeat(50)));
	}
}
