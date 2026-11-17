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

	/**
	 * Whether the library's own key closes it. A letter typed on the library starts a search, so a bound letter key
	 * (like N) closes only with Ctrl (Cmd on a Mac) held and Alt not: Ctrl+Alt is AltGr and Option is Alt, which type letters; Esc is the plain way out. Not while typing or when the key is still held from opening.
	 */
	static boolean libraryKeyCloses(boolean typing, boolean canSearch, int key, boolean ctrl, boolean alt, boolean heldFromOpen) {
		if (typing || heldFromOpen) {
			return false;
		}
		return ctrl && !alt || !canSearch || !typesCharacter(key);
	}

	/** Which way the keyboard focus moves: Shift reverses only Tab, the arrows go by their own direction. */
	static int focusDir(boolean cycleFocus, boolean shift, boolean up) {
		return cycleFocus ? (shift ? -1 : 1) : (up ? -1 : 1);
	}

	/** Modifier bits as GLFW (and so the key events) give them: Shift 1, Ctrl 2, Alt 4, Super 8. */
	static boolean shiftIn(int modifiers) {
		return (modifiers & 1) != 0;
	}

	static boolean altIn(int modifiers) {
		return (modifiers & 4) != 0;
	}

	/** The edit-shortcut key as Minecraft 1.21.9+ reads it from the event: Super (Cmd) on a Mac, Ctrl elsewhere. */
	static boolean editShortcutIn(int modifiers, boolean onMac) {
		return (modifiers & (onMac ? 8 : 2)) != 0;
	}

	/** The library's search text after pasting {@code clip}: odd spaces become one, and what typing would refuse is dropped. */
	static String libraryPasted(String query, String clip) {
		return SettingsSearch.pasted(query, clip);
	}

	/**
	 * The code of the letter a shortcut compares: on 26.3 the keycode of the letter the layout types
	 * ({@code event.shortcutKey()}), because {@code event.key()} is the scancode of the key's place; before it the GLFW key code.
	 */
	static final int LETTER_C;
	static final int LETTER_X;
	static final int LETTER_F;

	static {
		//? if >=26.3 {
		/*LETTER_C = com.mojang.blaze3d.platform.InputConstants.KEYCODE_C;
		LETTER_X = com.mojang.blaze3d.platform.InputConstants.KEYCODE_X;
		LETTER_F = com.mojang.blaze3d.platform.InputConstants.KEYCODE_F;
		*///?} else {
		LETTER_C = com.mojang.blaze3d.platform.InputConstants.KEY_C;
		LETTER_X = com.mojang.blaze3d.platform.InputConstants.KEY_X;
		LETTER_F = com.mojang.blaze3d.platform.InputConstants.KEY_F;
		//?}
	}

	/** Ctrl+C (0) or Ctrl+X (1) on the search box; -1 for any other key. {@code letter} is as for {@link #LETTER_C}. */
	static int copyOrCut(int letter, boolean ctrl, boolean shift, boolean alt) {
		// Like vanilla: Ctrl+Alt is AltGr (and Option on a Mac is Alt), which types letters
		return !ctrl || shift || alt ? -1 : letter == LETTER_C ? 0 : letter == LETTER_X ? 1 : -1;
	}

	/** Whether the key is Ctrl+F, the settings search. {@code letter} is as for {@link #LETTER_C}. */
	static boolean isCtrlF(int letter, boolean ctrl, boolean shift, boolean alt) {
		return ctrl && !shift && !alt && letter == LETTER_F;
	}

	/** A typed character is ignored while the key that opened the library is still held: its repeats are not a search. */
	static boolean acceptsTypedChar(boolean heldFromOpen) {
		return !heldFromOpen;
	}
}
