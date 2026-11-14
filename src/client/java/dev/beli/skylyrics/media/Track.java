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
	public static final Track NONE = new Track("", "", "", 0, "", "");

	public Track(String title, String artist, String album, long lengthMs, String artUrl, String player) {
		this(title, artist, album, lengthMs, artUrl, player, "");
	}

	public boolean isNone() {
		return title.isEmpty();
	}

	/** Same song (ignores the player and art, which can change mid-song). */
	public boolean sameSong(Track other) {
		return title.equals(other.title) && artist.equals(other.artist) && album.equals(other.album);
	}

	public String key() {
		return artist + " – " + title;
	}
}
