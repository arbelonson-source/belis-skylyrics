package dev.beli.skylyrics.gui;

/** Small keyboard and hover decisions of the song library, kept free of the screen so they can be tested. */
final class LibraryKeys {
	private LibraryKeys() {
	}

	/** Space plays the chosen song, unless it is being typed into the search box or nothing is chosen. */
	static boolean spacePlays(boolean typing, int selected, int count) {
		return !typing && selected >= 0 && selected < count;
	}

	/** Moves the keyboard focus over {@code count} buttons, wrapping; from nothing it lands on the first (or last going back). */
	static int nextButton(int current, int count, int dir) {
		if (count <= 0) {
			return -1;
		}
		if (current < 0) {
			return dir > 0 ? 0 : count - 1;
		}
		return Math.floorMod(current + dir, count);
	}

	/** The chosen cover after the mouse moved: the one under it, or the same as before when it is over none. */
	static int afterHover(int selected, int hit) {
		return hit >= 0 ? hit : selected;
	}

	/** How long after a click opened a playlist a second click is still part of the same double-click. */
	static final long DOUBLE_CLICK_NS = 400_000_000L;

	/** The search shelf still shows the answer to an older query while a new one is being typed: it can't be chosen. */
	static boolean staleResults(boolean searchView, boolean searchDirty) {
		return searchView && searchDirty;
	}

	/** A click this soon after one opened a playlist is the double-click's second half and must not pick a song of it. */
	static boolean tooSoonAfterOpen(long sinceOpenNs) {
		return sinceOpenNs >= 0 && sinceOpenNs < DOUBLE_CLICK_NS;
	}
}
