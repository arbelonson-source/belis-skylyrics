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

	/** Control characters become a space, text-direction embeddings and overrides go, runs of space become one, and the ends are trimmed. */
	static String clean(String s) {
		if (s == null) {
			return "";
		}
		StringBuilder b = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c >= '\u202A' && c <= '\u202E' || c >= '\u2066' && c <= '\u2069') {
				continue;
			}
			b.append(Character.getType(c) == Character.CONTROL ? ' ' : c);
		}
		return b.toString().replaceAll("[\\s\\p{Z}]+", " ").strip();
	}

	public static final Track NONE = new Track("", "", "", 0, "", "");

	public Track(String title, String artist, String album, long lengthMs, String artUrl, String player) {
		this(title, artist, album, lengthMs, artUrl, player, "");
	}

	public boolean isNone() {
		return title.isEmpty();
	}

	/** Both sides name their song and the names differ: two songs that share title, artist and album (two "Interlude"s). */
	public boolean otherUri(Track other) {
		return !uri.isEmpty() && !other.uri.isEmpty() && !uri.equals(other.uri);
	}

	/** Same song (ignores the player and art, which can change mid-song; a different address on both sides is another song). */
	public boolean sameSong(Track other) {
		return title.equals(other.title) && artist.equals(other.artist) && album.equals(other.album) && !otherUri(other);
	}

	public String key() {
		return artist + " – " + title;
	}
}
