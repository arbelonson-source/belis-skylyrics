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
		assertFalse(ScreenKeys.libraryKeyCloses(false, true, N, false, false, false));
	}

	@Test
	void aLetterKeyWithCtrlStillCloses() {
		assertTrue(ScreenKeys.libraryKeyCloses(false, true, N, true, false, false));
	}

	@Test
	void altGrOrOptionPlusTheLetterKeyDoesNotCloseButStartsASearch() {
		// AltGr arrives as Ctrl+Alt on Windows (Polish AltGr+N types n with an ogonek); Option on a Mac is Alt
		assertFalse(ScreenKeys.libraryKeyCloses(false, true, N, true, true, false));
		assertFalse(ScreenKeys.libraryKeyCloses(false, true, N, false, true, false));
	}

	@Test
	void altGrOnANonLetterKeyStillCloses() {
		assertTrue(ScreenKeys.libraryKeyCloses(false, true, F9, true, true, false));
	}

	@Test
	void aNonTypingKeyOrNoSearchStillCloses() {
		assertTrue(ScreenKeys.libraryKeyCloses(false, true, F9, false, false, false));
		assertTrue(ScreenKeys.libraryKeyCloses(false, false, N, false, false, false));
	}

	@Test
	void typingOrAKeyHeldFromOpeningNeverCloses() {
		assertFalse(ScreenKeys.libraryKeyCloses(true, true, F9, true, false, false));
		assertFalse(ScreenKeys.libraryKeyCloses(false, true, F9, false, false, true));
	}

	@Test
	void typesCharacterCoversLettersDigitsAndPunctuationButNotFunctionKeys() {
		//? if >=26.3 {
		/*// SDL scancodes: 4-39 letters and digits, 40-43 are Enter, Esc, Backspace, Tab, 44-56 space and punctuation, 57 Caps Lock
		for (int key : new int[] {4, 39, 44, 56, 100}) {
			assertTrue(ScreenKeys.typesCharacter(key), "key " + key);
		}
		for (int key : new int[] {3, 40, 43, 57, 99, 101, 58}) {
			assertFalse(ScreenKeys.typesCharacter(key), "key " + key);
		}
		*///?} else {
		// GLFW: 32 (space) to 96 (grave), plus 161 and 162
		for (int key : new int[] {32, 96, 161, 162}) {
			assertTrue(ScreenKeys.typesCharacter(key), "key " + key);
		}
		for (int key : new int[] {31, 97, 160, 163, com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE, F9}) {
			assertFalse(ScreenKeys.typesCharacter(key), "key " + key);
		}
		//?}
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
		assertEquals(0, ScreenKeys.copyOrCut(ScreenKeys.LETTER_C, true, false, false));
		assertEquals(1, ScreenKeys.copyOrCut(ScreenKeys.LETTER_X, true, false, false));
		assertEquals(-1, ScreenKeys.copyOrCut(ScreenKeys.LETTER_C, false, false, false));
		assertEquals(-1, ScreenKeys.copyOrCut(com.mojang.blaze3d.platform.InputConstants.KEY_V, true, false, false));
	}

	@Test
	void altGrOptionAndShiftDoNotCopyOrCut() {
		// Polish AltGr+C and AltGr+X type c and z with an accent: Ctrl+Alt on Windows, Alt (Option) on a Mac
		assertEquals(-1, ScreenKeys.copyOrCut(ScreenKeys.LETTER_C, true, false, true));
		assertEquals(-1, ScreenKeys.copyOrCut(ScreenKeys.LETTER_X, true, false, true));
		assertEquals(-1, ScreenKeys.copyOrCut(ScreenKeys.LETTER_X, false, false, true));
		assertEquals(-1, ScreenKeys.copyOrCut(ScreenKeys.LETTER_C, true, true, false));
	}

	@Test
	void ctrlFNeedsCtrlAndTheLetterF() {
		assertTrue(ScreenKeys.isCtrlF(ScreenKeys.LETTER_F, true, false, false));
		assertFalse(ScreenKeys.isCtrlF(ScreenKeys.LETTER_F, false, false, false));
		assertFalse(ScreenKeys.isCtrlF(ScreenKeys.LETTER_C, true, false, false));
	}

	@Test
	void altGrFAndCtrlShiftFDoNotOpenTheSettingsSearch() {
		assertFalse(ScreenKeys.isCtrlF(ScreenKeys.LETTER_F, true, false, true));
		assertFalse(ScreenKeys.isCtrlF(ScreenKeys.LETTER_F, false, false, true));
		assertFalse(ScreenKeys.isCtrlF(ScreenKeys.LETTER_F, true, true, false));
	}

	//? if >=26.3 {
	/*@Test
	void onSdlTheScancodeOfCsPlaceIsNotTheLetterC() {
		// Dvorak: the key in the QWERTY C place types another letter; its scancode must not copy, and the keycode of C must
		assertEquals(-1, ScreenKeys.copyOrCut(com.mojang.blaze3d.platform.InputConstants.KEY_C, true, false, false));
		assertEquals(-1, ScreenKeys.copyOrCut(com.mojang.blaze3d.platform.InputConstants.KEYCODE_V, true, false, false));
		assertEquals(0, ScreenKeys.copyOrCut(com.mojang.blaze3d.platform.InputConstants.KEYCODE_C, true, false, false));
		assertFalse(ScreenKeys.isCtrlF(com.mojang.blaze3d.platform.InputConstants.KEY_F, true, false, false));
	}

	*///?}
	@Test
	void lettersFromTheOpeningKeyStillHeldDoNotTypeIntoTheSearch() {
		assertFalse(ScreenKeys.acceptsTypedChar(true));
		assertTrue(ScreenKeys.acceptsTypedChar(false));
	}

	//? if <1.21.9 {
	/*@Test
	void theLegacyEventsControlIsTheRealCtrlBitNotCmd() {
		int tab = com.mojang.blaze3d.platform.InputConstants.KEY_TAB;
		assertTrue(new LegacyInput.KeyEvent(tab, 0, 2).hasControlDown());
		assertFalse(new LegacyInput.KeyEvent(tab, 0, 8).hasControlDown()); // Super (Cmd)
		assertFalse(new LegacyInput.KeyEvent(tab, 0, 1).hasControlDown());
	}
	*///?}

	@Test
	void theRealCtrlBitIsCtrlAndNotCmdOrShift() {
		// GLFW's modifier bits, written out because 26.3 has no GLFW: Shift 1, Ctrl 2, Alt 4, Super (Cmd) 8
		int shift = 1, ctrl = 2, alt = 4, superKey = 8;
		assertTrue(ScreenKeys.hasCtrlBit(ctrl));
		assertTrue(ScreenKeys.hasCtrlBit(ctrl | shift));
		assertFalse(ScreenKeys.hasCtrlBit(superKey));
		assertFalse(ScreenKeys.hasCtrlBit(shift | alt));
		assertFalse(ScreenKeys.hasCtrlBit(0));
	}
}
