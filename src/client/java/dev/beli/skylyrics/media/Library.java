package dev.beli.skylyrics.media;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Where the song library gets its songs: the signed-in Spotify account, or made-up songs for trying the screen out. */
public interface Library {
	CompletableFuture<SpotifyLibrary.Page<LibraryItem>> search(String query, int offset);

	CompletableFuture<SpotifyLibrary.Page<LibraryItem>> liked(int offset);

	CompletableFuture<SpotifyLibrary.Page<LibraryPlaylist>> playlists(int offset);

	CompletableFuture<SpotifyLibrary.Page<LibraryItem>> playlist(LibraryPlaylist playlist, int offset);

	/** Completes with an empty string when the song started, otherwise with a short message for the user. */
	CompletableFuture<String> play(LibraryItem item, List<LibraryItem> after);
}
