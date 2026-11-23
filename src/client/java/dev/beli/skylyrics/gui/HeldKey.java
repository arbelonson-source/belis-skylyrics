package dev.beli.skylyrics.gui;

/**
 * The key that last pressed one of the settings' own buttons (Reset all, Preview) and is still down. The game hands a screen
 * a held key's repeats as more presses (that is how a held Backspace keeps deleting), so Enter held on Reset all asked to
 * be pressed again and, from the computer's repeat delay on, reset every setting by itself; held on Preview it showed and
 * hid the preview over and over.
 */
final class HeldKey {
	private static final int NONE = Integer.MIN_VALUE;
	private int held = NONE;

	/**
	 * Whether a press of {@code key} is a repeat of the held key that last pressed a button. A press of any other key ends
	 * that: the computer repeats only the last key pressed.
	 */
	boolean isRepeat(int key) {
		if (key == held) {
			return true;
		}
		held = NONE;
		return false;
	}

	/** {@code key} just pressed a button: its repeats press nothing until it is let go. */
	void hold(int key) {
		held = key;
	}

	/** {@code key} was let go. */
	void released(int key) {
		if (key == held) {
			held = NONE;
		}
	}
}
