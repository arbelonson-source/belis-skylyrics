package dev.beli.skylyrics.gui;

/** Key decisions of the settings and library screens, kept free of the screens so they can be tested. */
final class ScreenKeys {
	private ScreenKeys() {
	}

	/** Keys that type a character on a keyboard: space, digits, punctuation and letters (GLFW codes 32 to 96, plus 161 and 162). */
	static boolean typesCharacter(int key) {
		return key >= 32 && key <= 96 || key == 161 || key == 162;
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
}
