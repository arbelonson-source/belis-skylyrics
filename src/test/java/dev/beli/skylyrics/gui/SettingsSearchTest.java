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

	@Test
	void oddSpacesAndDirectionMarksAreIgnored() {
		assertTrue(SettingsSearch.matches("Glow colour", "glow\u00a0colour"));
		assertTrue(SettingsSearch.matches("Glow colour", "\u200fglow\u200f"));
		assertTrue(SettingsSearch.matches("Glow\u00a0colour", "glow colour"));
		assertTrue(SettingsSearch.matches("\u05e6\u05d1\u05e2\u200f \u05d4\u05d6\u05d5\u05d4\u05e8", "\u05d6\u05d5\u05d4\u05e8\u3000"));
		assertEquals("a b", SettingsSearch.pasted("", "a\u00a0\u200f\u200bb"));
		assertFalse(SettingsSearch.allowed(0x200f));
	}

	@Test
	void accentsNiqqudAndFinalLettersAreFolded() {
		assertTrue(SettingsSearch.matches("M\u00fasica", "musica"));
		assertTrue(SettingsSearch.matches("Music", "m\u00fasic"));
		assertTrue(SettingsSearch.matches("\u05e9\u05dc\u05d5\u05dd", "\u05e9\u05dc\u05d5\u05de"));
		assertTrue(SettingsSearch.matches("\u05e9\u05dc\u05d5\u05de", "\u05e9\u05dc\u05d5\u05dd"));
		assertTrue(SettingsSearch.matches("\u05e9\u05c1\u05b8\u05dc\u05d5\u05b9\u05dd", "\u05e9\u05dc\u05d5\u05dd"));
	}
}
