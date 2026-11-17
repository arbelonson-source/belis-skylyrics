package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScreenKeysTest {
	private static final int N = 78;
	private static final int F9 = 298;

	@Test
	void aLetterKeyDoesNotCloseTheLibraryItWouldStartASearchWith() {
		assertFalse(ScreenKeys.libraryKeyCloses(false, true, N, false, false));
	}

	@Test
	void aLetterKeyWithCtrlOrAltStillCloses() {
		assertTrue(ScreenKeys.libraryKeyCloses(false, true, N, true, false));
	}

	@Test
	void aNonTypingKeyOrNoSearchStillCloses() {
		assertTrue(ScreenKeys.libraryKeyCloses(false, true, F9, false, false));
		assertTrue(ScreenKeys.libraryKeyCloses(false, false, N, false, false));
	}

	@Test
	void typingOrAKeyHeldFromOpeningNeverCloses() {
		assertFalse(ScreenKeys.libraryKeyCloses(true, true, F9, true, false));
		assertFalse(ScreenKeys.libraryKeyCloses(false, true, F9, false, true));
	}

	@Test
	void shiftReversesOnlyTab() {
		assertEquals(-1, ScreenKeys.focusDir(true, true, false));
		assertEquals(1, ScreenKeys.focusDir(true, false, false));
		assertEquals(1, ScreenKeys.focusDir(false, true, false));
		assertEquals(-1, ScreenKeys.focusDir(false, false, true));
		assertEquals(-1, ScreenKeys.focusDir(false, true, true));
	}
}
