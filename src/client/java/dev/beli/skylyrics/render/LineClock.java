package dev.beli.skylyrics.render;

/**
 * When the lyrics last moved to a new line. The line animations read the previous and current index from it, and the
 * things that flare up with each new line (crosshair, sky pulse, hat glow) read how long ago it was. It has to be fed
 * every frame the lyrics are shown, whatever the layout.
 */
final class LineClock {
	static final int NONE = Integer.MIN_VALUE;

	private int last = NONE;
	private int previous = NONE;
	private long changedAt;

	/**
	 * Feeds in the line being sung this frame. A new line restarts the clock; the first line after new lyrics counts as
	 * already settled (no flash on start). The same line again changes nothing.
	 */
	void note(int index, long now, long settleNs) {
		if (index != last) {
			previous = last;
			last = index;
			changedAt = previous == NONE ? now - settleNs : now;
		}
	}

	/**
	 * The per-frame call from the renderer, for every layout: only while the lyrics are actually on show, and never for
	 * an instrumental (it has no lines).
	 */
	void frame(boolean shown, boolean instrumental, int index, long now, long settleNs) {
		if (shown && !instrumental) {
			note(index, now, settleNs);
		}
	}

	/**
	 * While the lyrics are hidden but still loaded: keeps up with the song without restarting the clock, so showing
	 * them again mid-song isn't taken for a line change.
	 */
	void follow(int index, long now, long settleNs) {
		if (last != NONE && index != last) {
			previous = index;
			last = index;
			changedAt = now - settleNs;
		}
	}

	/** New lyrics: the next line is a first line again. */
	void reset() {
		last = NONE;
	}

	int last() {
		return last;
	}

	int previous() {
		return previous;
	}

	long changedAt() {
		return changedAt;
	}

	float secondsSince(long now) {
		return (now - changedAt) / 1e9f;
	}
}
