package dev.beli.skylyrics.gui;

/** Key decisions of the settings and library screens, kept free of the screens so they can be tested. */
final class ScreenKeys {
	private ScreenKeys() {
	}

	/** Keys that type a character on a keyboard: space, digits, punctuation and letters. */
	static boolean typesCharacter(int key) {
		//? if >=26.3 {
		/*// SDL scancodes: letters 4 to 29, digits 30 to 39, then space and punctuation up to 56, plus the extra key of
		// European keyboards ("<>", 100), like GLFW's 161 and 162 below
		return key >= 4 && key <= 39 || key >= 44 && key <= 56 || key == 100;
		*///?} else {
		// GLFW codes 32 to 96, plus 161 and 162
		return key >= 32 && key <= 96 || key == 161 || key == 162;
		//?}
	}

	/** Whether the modifier bits of a key event include Ctrl or Alt (GLFW bits, or SDL's left and right bits on 26.3). */
	static boolean ctrlOrAlt(int modifiers) {
		//? if >=26.3 {
		/*return (modifiers & (0xC0 | 0x300)) != 0;
		*///?} else {
		return (modifiers & 6) != 0;
		//?}
	}

	/**
	 * Whether the library's own key closes it. A letter typed on the library starts a search, so a bound letter key
	 * (like N) closes only with Ctrl or Alt held; Esc is the plain way out. Not while typing or when the key is still held from opening.
	 */
	static boolean libraryKeyCloses(boolean typing, boolean canSearch, int key, boolean ctrlOrAlt, boolean heldFromOpen) {
		if (typing || heldFromOpen) {
			return false;
		}
		return ctrlOrAlt || !canSearch || !typesCharacter(key);
	}

	/** Which way the keyboard focus moves: Shift reverses only Tab, the arrows go by their own direction. */
	static int focusDir(boolean cycleFocus, boolean shift, boolean up) {
		return cycleFocus ? (shift ? -1 : 1) : (up ? -1 : 1);
	}

	/** The library's search text after pasting {@code clip}: odd spaces become one, and what typing would refuse is dropped. */
	static String libraryPasted(String query, String clip) {
		return SettingsSearch.pasted(query, clip);
	}

	/** Ctrl+C (0) or Ctrl+X (1) on the search box; -1 for any other key. */
	static int copyOrCut(int key, boolean ctrl) {
		return !ctrl ? -1 : key == com.mojang.blaze3d.platform.InputConstants.KEY_C ? 0 : key == com.mojang.blaze3d.platform.InputConstants.KEY_X ? 1 : -1;
	}

	/** A typed character is ignored while the key that opened the library is still held: its repeats are not a search. */
	static boolean acceptsTypedChar(boolean heldFromOpen) {
		return !heldFromOpen;
	}
}
