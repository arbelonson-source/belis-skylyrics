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
		title = clean(title);
		artist = clean(artist);
		album = clean(album);
	}

	/** Control characters become a space, text-direction marks, embeddings and overrides (LRM, RLM, ALM too) go, runs of space become one, and the ends are trimmed. */
	static String clean(String s) {
		if (s == null) {
			return "";
		}
		s = stripCodes(s);
		StringBuilder b = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c >= '\u202A' && c <= '\u202E' || c >= '\u2066' && c <= '\u2069'
					|| c == '\u200E' || c == '\u200F' || c == '\u061C') {
				continue;
			}
			b.append(Character.getType(c) == Character.CONTROL ? ' ' : c);
		}
		return b.toString().replaceAll("[\\s\\p{Z}]+", " ").strip();
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
