package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScreenKeysTest {
	private static final int N = com.mojang.blaze3d.platform.InputConstants.KEY_N;
	private static final int F9 = com.mojang.blaze3d.platform.InputConstants.KEY_F9;

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

	@Test
	void pastedTextLosesNewlinesControlCharactersAndTheSectionSign() {
		assertEquals("abc one two", ScreenKeys.libraryPasted("abc", " one\r\n\ttwo\u0007\u00a7\u200b"));
		assertEquals("", ScreenKeys.libraryPasted("", "\u0000\u007f"));
	}

	@Test
	void ctrlCAndCtrlXCopyAndCutOnlyWithCtrlHeld() {
		assertEquals(0, ScreenKeys.copyOrCut(ScreenKeys.LETTER_C, true));
		assertEquals(1, ScreenKeys.copyOrCut(ScreenKeys.LETTER_X, true));
		assertEquals(-1, ScreenKeys.copyOrCut(ScreenKeys.LETTER_C, false));
		assertEquals(-1, ScreenKeys.copyOrCut(com.mojang.blaze3d.platform.InputConstants.KEY_V, true));
	}

	@Test
	void ctrlFNeedsCtrlAndTheLetterF() {
		assertTrue(ScreenKeys.isCtrlF(ScreenKeys.LETTER_F, true));
		assertFalse(ScreenKeys.isCtrlF(ScreenKeys.LETTER_F, false));
		assertFalse(ScreenKeys.isCtrlF(ScreenKeys.LETTER_C, true));
	}

	//? if >=26.3 {
	/*@Test
	void onSdlTheScancodeOfCsPlaceIsNotTheLetterC() {
		// Dvorak: the key in the QWERTY C place types another letter; its scancode must not copy, and the keycode of C must
		assertEquals(-1, ScreenKeys.copyOrCut(com.mojang.blaze3d.platform.InputConstants.KEY_C, true));
		assertEquals(-1, ScreenKeys.copyOrCut(com.mojang.blaze3d.platform.InputConstants.KEYCODE_V, true));
		assertEquals(0, ScreenKeys.copyOrCut(com.mojang.blaze3d.platform.InputConstants.KEYCODE_C, true));
		assertFalse(ScreenKeys.isCtrlF(com.mojang.blaze3d.platform.InputConstants.KEY_F, true));
	}

	*///?}
	@Test
	void lettersFromTheOpeningKeyStillHeldDoNotTypeIntoTheSearch() {
		assertFalse(ScreenKeys.acceptsTypedChar(true));
		assertTrue(ScreenKeys.acceptsTypedChar(false));
	}
}
