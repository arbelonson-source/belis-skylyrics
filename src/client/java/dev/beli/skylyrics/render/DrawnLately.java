package dev.beli.skylyrics.render;

/**
 * Whether something drawn every frame (a column of F3's text, the bar) was drawn lately enough to still be on screen,
 * for whatever reads it in the next frame. A quarter of a second used to be the limit, but a slow game (a weak computer,
 * a software-drawn test game) takes that long for one frame: at 4 frames a second everything drawn in the last frame
 * already counted as gone on every other frame, so the bar and the Record card jumped back and forth. Now it is a
 * quarter of a second or twice the time between its last two draws, whichever is longer, and never more than a second.
 */
final class DrawnLately {
	/** The least it counts for: a quarter of a second, as before for a smooth game. */
	static final long QUICK_NS = 250_000_000L;
	/** The most: a second, so something drawn only now and then is soon taken as gone. */
	static final long MOST_NS = 1_000_000_000L;

	private DrawnLately() {
	}

	/**
	 * @param at when it was last drawn (0 for never)
	 * @param before when it was drawn the time before that (0 for never)
	 */
	static boolean drawn(long now, long at, long before) {
		if (at == 0) {
			return false;
		}
		long window = Math.max(QUICK_NS, Math.min(2 * (at - before), MOST_NS));
		return now - at <= window;
	}
}
