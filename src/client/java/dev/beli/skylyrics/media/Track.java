package dev.beli.skylyrics.media;

/**
 * A song as reported by the music player.
 *
 * @param lengthMs 0 when the player doesn't say
 * @param artUrl   may be empty; usually https:// or file://
 * @param player   short player name, e.g. "spotify" or "firefox"
 * @param uri      what the player says this song is (MPRIS xesam:url: a file://, https:// or spotify: address); may be empty
 */
public record Track(String title, String artist, String album, long lengthMs, String artUrl, String player, String uri) {
	/** Players can send line breaks, tabs and text-direction marks in a title; those draw as boxes or flip the title. */
	public Track {
		title = cap(clean(title), MAX_TEXT);
		if (!anyVisible(title)) {
			title = "";
		}
		artist = cap(clean(artist), MAX_TEXT);
		album = cap(clean(album), MAX_TEXT);
	}

	private static final java.util.regex.Pattern SPACES = java.util.regex.Pattern.compile("[\\s\\p{Z}]+");

	/**
	 * Control characters become a space (no length limit: saved timing keys are cleaned with this and must come back whole;
	 * the fields are cut in the constructor), text-direction marks, embeddings and overrides (LRM, RLM, ALM too) go, and so
	 * do the invisible characters the game draws as a dashed box with their name in it: an emoji's style selector (the
	 * VS16 after a "\u2764"), the other variation selectors, a word joiner, a zero-width space and a byte order mark. Runs of
	 * space become one, and the ends are trimmed.
	 */
	public static String clean(String s) {
		if (s == null) {
			return "";
		}
		s = stripCodes(s);
		StringBuilder b = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c >= '\u202A' && c <= '\u202E' || c >= '\u2066' && c <= '\u2069'
					|| c == '\u200E' || c == '\u200F' || c == '\u061C' || drawnAsBox(s, i)) {
				continue;
			}
			b.append(Character.getType(c) == Character.CONTROL ? ' ' : c);
		}
		return SPACES.matcher(b).replaceAll(" ").strip();
	}

	/**
	 * The text without the invisible characters the game draws as a dashed box with their name in it (see
	 * {@link #drawnAsBox}): an emoji's style selector and the other variation selectors, the word joiner, the zero-width
	 * space and the byte order mark. Lyric lines are cleaned with this; unlike {@link #clean} it keeps the direction marks
	 * and the spaces as they are. The same instance when there is none.
	 */
	public static String dropBoxes(String s) {
		for (int i = 0; i < s.length(); i++) {
			if (drawnAsBox(s, i)) {
				StringBuilder b = new StringBuilder(s.length()).append(s, 0, i);
				for (; i < s.length(); i++) {
					if (!drawnAsBox(s, i)) {
						b.append(s.charAt(i));
					}
				}
				return b.toString();
			}
		}
		return s;
	}

	/**
	 * Whether the char at {@code i} is (part of) an invisible character Minecraft's Unifont draws as a dashed box with its
	 * name in it, which no bundled smooth font covers: the variation selectors (VS1 to VS16, and the high half or the low
	 * half of VS17 to VS256) and the word joiner; also the zero-width space and the byte order mark, boxes in the pixel font.
	 * The joiners (ZWJ, ZWNJ) stay: Persian writing and emoji sequences need them, and the smooth fonts draw them as nothing.
	 */
	private static boolean drawnAsBox(String s, int i) {
		char c = s.charAt(i);
		if (c >= '\uFE00' && c <= '\uFE0F' || c == '\u2060' || c == '\u200B' || c == '\uFEFF') {
			return true;
		}
		// VS17 to VS256: U+E0100 to U+E01EF, a high half of DB40 and a low half from DD00 to DDEF
		if (c == '\uDB40' && i + 1 < s.length()) {
			char low = s.charAt(i + 1);
			return low >= '\uDD00' && low <= '\uDDEF';
		}
		return c >= '\uDD00' && c <= '\uDDEF' && i > 0 && s.charAt(i - 1) == '\uDB40';
	}

	/** A title, artist or album longer than this is cut: a player can send anything, and it is drawn and compared all the time. */
	public static final int MAX_TEXT = 500;

	/**
	 * Cuts text to at most {@code max} chars, at a space when there is one in the last half, and never inside a surrogate
	 * pair or between a character and its combining marks, joiners or variation selectors (a half emoji draws as a box).
	 */
	public static String cap(String s, int max) {
		if (s.length() <= max) {
			return s;
		}
		int cut = max;
		int space = s.lastIndexOf(' ', max);
		if (space >= max / 2) {
			cut = space;
		}
		while (cut > 0 && (Character.isLowSurrogate(s.charAt(cut)) || joins(s, cut))) {
			cut--;
		}
		if (cut == 0) {
			cut = Character.isLowSurrogate(s.charAt(max)) ? max - 1 : max; // marks all the way: a plain cut beats an empty title
		}
		return s.substring(0, cut).stripTrailing();
	}

	/** False for text made only of spaces, zero-width and other invisible format characters (joiners inside real text don't matter: something else is visible). */
	private static boolean anyVisible(String s) {
		for (int i = 0; i < s.length(); ) {
			int cp = s.codePointAt(i);
			i += Character.charCount(cp);
			if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp) && Character.getType(cp) != Character.FORMAT
					&& cp != 0x3164 && cp != 0x2800 && cp != 0x115F && cp != 0x1160 && cp != 0x17B4 && cp != 0x17B5 && cp != 0xFFA0) {
				return true;
			}
		}
		return false;
	}

	private static boolean joins(String s, int cut) {
		int next = s.codePointAt(cut);
		int type = Character.getType(next);
		return next == 0x200D || (next >= 0xFE00 && next <= 0xFE0F) || next >= 0x1F3FB && next <= 0x1F3FF
				|| type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK || type == Character.COMBINING_SPACING_MARK
				|| s.charAt(cut - 1) == 0x200D;
	}

	/**
	 * Minecraft reads a section sign and the character after it as a formatting code (colour, bold, flickering "obfuscated"),
	 * so both go; a section sign at the very end goes too. Text from players and lyric sites must never carry them to the screen.
	 */
	public static String stripCodes(String s) {
		int at = s.indexOf('\u00A7');
		if (at < 0) {
			return s;
		}
		StringBuilder b = new StringBuilder(s.length());
		int i = 0;
		while (i < s.length()) {
			int cp = s.codePointAt(i);
			i += Character.charCount(cp);
			if (cp == 0xA7) {
				if (i < s.length()) {
					i += Character.charCount(s.codePointAt(i));
				}
			} else {
				b.appendCodePoint(cp);
			}
		}
		return b.toString();
	}

	public static final Track NONE = new Track("", "", "", 0, "", "");

	public Track(String title, String artist, String album, long lengthMs, String artUrl, String player) {
		this(title, artist, album, lengthMs, artUrl, player, "");
	}

	public boolean isNone() {
		return title.isEmpty();
	}

	/**
	 * Both sides carry a Spotify song or episode id and the ids differ: two songs that share title, artist and album
	 * (two "Interlude"s). Other addresses (a browser's page url can change mid-song) never tell songs apart.
	 */
	public boolean otherUri(Track other) {
		return uri.startsWith("spotify:") && other.uri.startsWith("spotify:") && !uri.equals(other.uri);
	}

	/** Same song (ignores the player and art, which can change mid-song; a different address on both sides is another song). */
	public boolean sameSong(Track other) {
		return title.equals(other.title) && artist.equals(other.artist) && album.equals(other.album) && !otherUri(other);
	}

	public String key() {
		return artist + " – " + title;
	}
}
