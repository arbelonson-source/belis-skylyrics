package dev.beli.skylyrics.media;

/**
 * One of the user's playlists in the song library.
 *
 * @param openable false for playlists made by someone else, which Spotify won't let apps read
 */
public record LibraryPlaylist(String id, String uri, String name, String coverUrl, int songs, boolean openable) {
}
