package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SettingsSearchTest {
	@Test
	void everyWordMustBeThereInAnyOrder() {
		assertTrue(SettingsSearch.matches("Glow colour", "glow"));
		assertTrue(SettingsSearch.matches("Glow colour", "COLOUR glow"));
		assertTrue(SettingsSearch.matches("Keep the middle clear", "  mid  "));
		assertFalse(SettingsSearch.matches("Glow colour", "glow size"));
	}

	@Test
	void anEmptySearchFindsNothing() {
		assertFalse(SettingsSearch.matches("Glow colour", ""));
		assertFalse(SettingsSearch.matches("Glow colour", "   "));
	}

	@Test
	void eitherSpellingWorks() {
		assertTrue(SettingsSearch.matches("Glow colour", "color"));
		assertTrue(SettingsSearch.matches("Glow colour", "glow colo"));
		assertTrue(SettingsSearch.matches("Glow colour", "colou"));
		assertTrue(SettingsSearch.matches("Words centred on screen", "centered"));
		assertTrue(SettingsSearch.matches("Words centred on screen", "centre"));
		assertTrue(SettingsSearch.matches("Grey edge", "gray"));
		assertFalse(SettingsSearch.matches("Glow colour", "colors"));
	}

	@Test
	void hebrewWorks() {
		assertTrue(SettingsSearch.matches("צבע הזוהר", "זוהר"));
	}

	@Test
	void anEnglishWordFindsAHebrewLabel() {
		// the screen hands over the shown label and its English source together
		assertTrue(SettingsSearch.matches("צבע הזוהר Glow colour", "glow"));
		assertFalse(SettingsSearch.matches("צבע הזוהר", "glow"));
	}

	@Test
	void pastedTextIsFilteredLikeTyping() {
		assertEquals("ab c", SettingsSearch.pasted("ab", " c"));
		assertEquals("glow", SettingsSearch.pasted("", "g\u00a7l\u0001o\u007fw"));
		assertEquals("a b", SettingsSearch.pasted("", "a\n\n  b"));
	}
}
