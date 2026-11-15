package dev.beli.skylyrics.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FirstCharacterTest {
	@Test
	void aFlagComesWhole() {
		assertEquals("🇮🇱", TextCut.first("🇮🇱 Home"));
	}

	@Test
	void aLetterKeepsItsAccent() {
		assertEquals("é", TextCut.first("école"));
	}

	@Test
	void leadingSpaceIsSkipped() {
		assertEquals("S", TextCut.first("   Song"));
	}

	@Test
	void blankGivesNothing() {
		assertEquals("", TextCut.first("  \t "));
		assertEquals("", TextCut.first(""));
	}
}
