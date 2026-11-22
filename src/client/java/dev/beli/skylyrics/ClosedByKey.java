package dev.beli.skylyrics;

/**
 * Whether the key that opens one of our screens (settings, library) opens it this tick. The screen closes on that
 * key's press too; held on, the game turns the key's repeats into clicks again once no screen is open, which opened
 * the screen straight back. So after such a close the key's clicks count for nothing until a tick finds it let go.
 */
final class ClosedByKey {
	private boolean held;

	/** The screen just closed on this key's press. */
	void closed() {
		held = true;
	}

	/** Call once per tick with the key's clicks and whether it is down on the keyboard now: whether the screen opens. */
	boolean opens(int clicks, boolean keyDown) {
		if (held) {
			held = keyDown;
			return false; // a repeat that came in before the key was let go still doesn't count
		}
		return clicks > 0;
	}

	/**
	 * Whether to ask the keyboard if the key is down: a keyboard key only (a mouse button is not on the keyboard), and a
	 * bound one. An unbound key is -1 (GLFW's unknown key) on 1.21.1 to 26.2, and asking GLFW about -1 is an "Invalid key"
	 * error that the game writes to its log as three ERROR lines, so an unbound key filled the log every tick.
	 */
	static boolean asksKeyboard(boolean keyboardKey, int value) {
		return keyboardKey && value >= 0;
	}
}
