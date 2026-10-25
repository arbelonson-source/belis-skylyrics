package dev.beli.skylyrics.media;

/**
 * A song as reported by the music player.
 *
 * @param lengthMs 0 when the player doesn't say
 * @param artUrl   may be empty; usually https:// or file://
 * @param player   short player name, e.g. "spotify" or "firefox"
 */
public record Track(String title, String artist, String album, long lengthMs, String artUrl, String player) {
	public static final Track NONE = new Track("", "", "", 0, "", "");

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
