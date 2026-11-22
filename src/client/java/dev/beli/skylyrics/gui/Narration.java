package dev.beli.skylyrics.gui;

/** The words the Narrator says for the settings screen, built from texts that are already translated. */
final class Narration {
	private Narration() {
	}

	/** The parts as one phrase: blanks left out and a part that only repeats the one before it said once. */
	static String join(String... parts) {
		StringBuilder out = new StringBuilder();
		String last = null;
		for (String part : parts) {
			if (part == null || part.isBlank() || part.strip().equalsIgnoreCase(last)) {
				continue;
			}
			if (!out.isEmpty()) {
				out.append(", ");
			}
			out.append(part.strip());
			last = part.strip();
		}
		return out.toString();
	}

	/** "Layout, choice, Arc, 1 of 3": a picker's name, what it is, what is picked and where it sits (already said, like "1 of 3"). */
	static String choice(String name, String kind, String value, String place) {
		return join(name, kind, value, place);
	}

	/**
	 * What the settings screen says while the preview may show: then only the preview's own words are on screen (what it
	 * shows and how to go back), so those, not the Preview button the keyboard is still on; else {@code otherwise}.
	 */
	static String previewOr(boolean previewing, String preview, String otherwise) {
		return previewing ? preview : otherwise;
	}

	/** A button the keyboard is on: "Try again, button". */
	static String button(String label, String kind) {
		return join(label, kind);
	}

	/** The search box: "Search, text box, what is typed so far". */
	static String searchBox(String name, String kind, String typed) {
		return join(name, kind, typed);
	}

	/** A song or playlist card: the section when it just changed, an error if one shows, title, artist (or song count) and "3 of 50". */
	static String card(String section, String error, String title, String under, String place) {
		return join(section, error, title, under, place);
	}

	/** A value as it is said aloud: "40%" is "40 percent", "-10°" "-10 degrees", "1.5×" "1.5 times", "+0.50 s" "+0.50 seconds". */
	static String speakable(String value, String percent, String degrees, String times, String seconds) {
		return value.replace("%", " " + percent).replace("°", " " + degrees).replace("×", " " + times)
				.replaceAll("(\\d) s$", "$1 " + java.util.regex.Matcher.quoteReplacement(seconds));
	}

	/** What the Narrator says after Ctrl+Tab: the tab, and the row the keyboard lands on there when there is one. */
	static String tab(String name, String kind, String place, String row) {
		return join(choice(name, kind, "", place), row);
	}

	/** A row the keyboard is on; one that is listed in the search but hidden in its tab says it is not in use now. */
	static String row(String narration, boolean hidden, String notInUse) {
		return join(narration, hidden ? notInUse : "");
	}

	/** The section or playlist the library names once it has just changed, or nothing. */
	static String section(boolean say, boolean playlistOpen, String playlistName, String sectionName) {
		return !say ? "" : playlistOpen ? playlistName : sectionName;
	}

	/** What an empty list is doing: loading, an error, or empty. Nothing when there are cards. */
	static String shelfState(int count, boolean loaded, boolean busy, String error, String loading, String empty) {
		if (count > 0 || !error.isEmpty()) {
			return "";
		}
		return busy || !loaded ? loading : empty;
	}

	/** The number of search results, said on the search box line; nothing before anything is typed. */
	static String resultCount(boolean typed, boolean busy, int count, String searching, String none, String one, String many) {
		if (!typed) {
			return "";
		}
		if (busy) {
			return searching;
		}
		return count == 1 ? one : count == 0 ? none : many;
	}

	/** The sign-in card: what it says and the button the keyboard is on (Enter presses the first one before any arrow key). */
	static String signIn(String text, String open, String made, int focus, String kind) {
		return join(text, button(focus <= 0 ? open : made, kind));
	}

	/** The line under a song's title, with "Now playing" added when it is the song that is playing. */
	static String underPlaying(String under, boolean current, String nowPlaying) {
		return join(under, current ? nowPlaying : "");
	}

	/** A row's words with its hint after them, for the moment the row gets the keyboard (not on every value change). */
	static String hinted(String narration, String hint) {
		return join(narration, hint);
	}

	/** Whether the search's result count is spoken: while typing, or just after a recent search was picked. */
	static boolean countsResults(boolean typing, boolean afterChip) {
		return typing || afterChip;
	}

	/** A song or playlist tile as the keyboard lands on it: the card and what Enter does with it. */
	static String tile(String card, String action) {
		return join(card, action);
	}
}
