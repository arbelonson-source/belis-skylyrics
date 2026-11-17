package dev.beli.skylyrics;

/**
 * Tells a fresh press of a key from the key repeat of a held one. The game clicks a bound key about thirty times a second
 * while it is held; an action that should happen once per press only acts when the key was up at the last look.
 */
final class KeyPresses {
	private boolean wasDown;

	/** Call once per tick with the clicks drained since the last tick and whether the key is down now. */
	boolean fires(int clicks, boolean down) {
		boolean fire = clicks > 0 && !wasDown;
		wasDown = down;
		return fire;
	}
}
