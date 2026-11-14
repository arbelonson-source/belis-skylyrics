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
}
