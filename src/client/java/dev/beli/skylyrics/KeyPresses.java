package dev.beli.skylyrics;

/**
 * Tells a fresh press of a key from the key repeat of a held one. The game clicks a bound key about thirty times a second
 * while it is held; an action that should happen once per press only acts when the key was up at the last look.
 */
final class KeyPresses {
	/** Whether the key acts on every click, repeats too, instead of once per press. */
	private final boolean steps;
	private boolean wasDown;

	KeyPresses() {
		this(false);
	}

	private KeyPresses(boolean steps) {
		this.steps = steps;
	}

	/**
	 * For our key of this name ({@code key.skylyrics.<name>}): the timing keys step on every repeat while held, every
	 * other key acts once per press (a held toggle would flip back and forth and land anywhere).
	 */
	static KeyPresses of(String name) {
		return new KeyPresses(name.equals("lyrics_late") || name.equals("lyrics_early"));
	}

	/** Call once per tick with the clicks drained since the last tick and whether the key is down now. */
	boolean fires(int clicks, boolean down) {
		boolean fire = clicks > 0 && !wasDown;
		wasDown = down;
		return fire;
	}

	/** Like {@link #fires}, as how many times the key acts this tick: every click for a key that steps, else once per press. */
	int acts(int clicks, boolean down) {
		if (steps) {
			wasDown = down;
			return clicks;
		}
		return fires(clicks, down) ? 1 : 0;
	}
}
