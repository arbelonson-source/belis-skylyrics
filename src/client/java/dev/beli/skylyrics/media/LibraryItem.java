package dev.beli.skylyrics.media;

/**
 * One song card in the song library: enough to draw it and to start it playing.
 *
 * @param context the playlist to keep playing from after this song, or empty
 */
public record LibraryItem(String id, String uri, String title, String artists, String album, String coverUrl, long lengthMs, String context) {
}
