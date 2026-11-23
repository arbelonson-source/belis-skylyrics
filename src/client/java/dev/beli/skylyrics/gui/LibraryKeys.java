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

	/**
	 * Which section tab a click at {@code mx} on the tabs' line picks: the tabs are {@code widths} wide, the first at
	 * {@code left} and each {@code gap} from the next; -1 for none. None either for the second click of a double-click on
	 * "‹ Playlists", which sits where the first tabs are: {@code sinceBackNs} after that button closed the playlist, the
	 * click is still the same double-click (see {@link #tooSoonAfterOpen}), so it stays on the playlists.
	 */
	static int sectionAt(double mx, int left, int[] widths, int gap, long sinceBackNs) {
		if (tooSoonAfterOpen(sinceBackNs)) {
			return -1;
		}
		int x = left;
		for (int i = 0; i < widths.length; i++) {
			if (mx >= x && mx < x + widths[i]) {
				return i;
			}
			x += widths[i] + gap;
		}
		return -1;
	}

	/** A click this soon after one opened a playlist is the double-click's second half and must not pick a song of it. */
	static boolean tooSoonAfterOpen(long sinceOpenNs) {
		return sinceOpenNs >= 0 && sinceOpenNs < DOUBLE_CLICK_NS;
	}

	/**
	 * Moves the keyboard over the header stops (the Open Music tab button, the grid/shelf/carousel buttons, the recent
	 * searches): back goes toward the first and stays there, forward past the last leaves them for the songs (-1).
	 */
	static int stopMove(int current, int count, int dir) {
		if (count <= 0) {
			return -1;
		}
		int to = current + dir;
		return to < 0 ? 0 : to >= count ? -1 : to;
	}

	/** Where the keyboard lands on the header stops: the first recent search when there are no songs to look at, else the current view's button. */
	static int enterStop(boolean hasSongs, int viewStop, int firstChip) {
		return !hasSongs && firstChip >= 0 ? firstChip : viewStop;
	}

	/** Whether Up has nowhere left to go in the songs: none chosen, the top row of a grid or of a shelf column, or any carousel. */
	static boolean atTop(dev.beli.skylyrics.config.SkyLyricsConfig.LibraryView view, int selected, int cols, int shelfRows) {
		if (selected < 0) {
			return true;
		}
		return switch (view) {
			case GRID -> selected < cols;
			case SHELF -> LibraryViews.shelfRow(selected, shelfRows) == 0;
			case CAROUSEL -> true;
		};
	}

	/** Where the keyboard goes when an opened playlist closes: back on the playlist it was opened from, or nowhere. */
	static int afterClose(int openedFrom, int count) {
		return openedFrom < 0 || count <= 0 ? -1 : Math.min(openedFrom, count - 1);
	}

	/**
	 * The header stop the keyboard is on once Esc has closed a playlist, from {@code stop} (-1 for none): off the header,
	 * so it is back on the playlist's card like Esc from the songs, and the Narrator names the playlist and the section.
	 */
	static int stopAfterClose(int stop) {
		return -1;
	}

	/**
	 * Whether an arrow takes the keyboard onto the line under the songs that says why the next page failed (where Enter
	 * tries again, as a click does): the arrow toward that line ({@code towardIt}: Down under the grid, Right after the
	 * shelf's last column), from a card it can't move past ({@code stuck}), while the line shows ({@code failed}).
	 */
	static boolean ontoFooter(boolean towardIt, boolean stuck, boolean failed) {
		return towardIt && stuck && failed;
	}

	/** Whether Enter (or Space) presses Try again: on the line under the songs, or in an empty list that failed to load. */
	static boolean triesAgain(boolean onFooter, boolean emptyAndFailed) {
		return onFooter || emptyAndFailed;
	}
}
