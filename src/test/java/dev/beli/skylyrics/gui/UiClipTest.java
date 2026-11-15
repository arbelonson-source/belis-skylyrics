package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UiClipTest {
	@Test
	void aFlagAtTheCutIsDroppedWholeNotHalved() {
		// "ab" + Israeli flag (two regional indicators, four UTF-16 units) + "cd", one unit wide per UTF-16 unit
		assertEquals("ab…", Ui.clipBy("ab🇮🇱cd", 5, String::length));
	}

	@Test
	void anAccentStaysWithItsLetter() {
		// "e" + combining acute, then "x"
		assertEquals("a…", Ui.clipBy("aéxyz", 3, String::length));
	}

	@Test
	void shortTextIsUntouchedAndNoRoomIsEmpty() {
		assertEquals("abc", Ui.clipBy("abc", 5, String::length));
		assertEquals("", Ui.clipBy("abc", 0, String::length));
	}
}
