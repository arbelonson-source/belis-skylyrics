package dev.beli.skylyrics.gui;

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
}
