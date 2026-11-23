package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.media.DemoLibrary;
import dev.beli.skylyrics.media.LibraryItem;
import dev.beli.skylyrics.media.SpotifyLibrary;

class LibraryShelfTest {
	@Test
	void aListThatIsAlreadyThereIsNotLoading() {
		// The made-up songs (and Recently heard) answer at once: the first page is in as soon as the list asks for it, so
		// the Narrator's first words when the library opens are how many songs there are, not "Loading"
		LibraryScreen.Shelf shelf = new LibraryScreen.Shelf(new DemoLibrary()::liked, true, () -> 0);
		shelf.more();
		assertNull(shelf.pending, "the page that came at once still counts as loading");
		assertTrue(shelf.loaded);
		assertEquals(12, shelf.items.size());
		assertEquals(28, shelf.size());
	}

	@Test
	void aListStillOnItsWayIsLoadingUntilItComes() {
		CompletableFuture<SpotifyLibrary.Page<LibraryItem>> later = new CompletableFuture<>();
		LibraryScreen.Shelf shelf = new LibraryScreen.Shelf(offset -> later, () -> 0);
		shelf.more();
		assertNotNull(shelf.pending);
		assertFalse(shelf.loaded);
		later.complete(new SpotifyLibrary.Page<>(List.of(new LibraryItem("a", "x:a", "A", "B", "C", "", 1000, "")), 40, 0));
		shelf.poll();
		assertNull(shelf.pending);
		assertEquals(1, shelf.items.size());
		assertEquals(40, shelf.size());
	}

	@Test
	void aPlaylistOfOnlyLocalFilesOrPodcastsDoesNotSayItIsEmpty() {
		// Spotify sent all three of the playlist's entries, but they are local files and podcast episodes: no card for any
		LibraryScreen.Shelf shelf = new LibraryScreen.Shelf(offset -> CompletableFuture.completedFuture(
				new SpotifyLibrary.Page<LibraryItem>(List.of(), 3, 0, 3)), () -> 0);
		shelf.more();
		assertTrue(shelf.items.isEmpty());
		assertEquals(dev.beli.skylyrics.Tr.t("Local files and podcasts don't show here"), LibraryScreen.playlistEmptyText(shelf));
		// A playlist with nothing in it is still empty
		LibraryScreen.Shelf empty = new LibraryScreen.Shelf(offset -> CompletableFuture.completedFuture(SpotifyLibrary.Page.<LibraryItem>empty()), () -> 0);
		empty.more();
		assertEquals(dev.beli.skylyrics.Tr.t("This playlist is empty"), LibraryScreen.playlistEmptyText(empty));
	}

	@Test
	void aPlaylistThatStartsWithLocalFilesIsLoadingWhileItsSongsAreOnTheirWay() {
		// The first 50 entries are all local files; the songs come on the next page, which hasn't come yet
		CompletableFuture<SpotifyLibrary.Page<LibraryItem>> second = new CompletableFuture<>();
		LibraryScreen.Shelf shelf = new LibraryScreen.Shelf(offset -> offset == 0
				? CompletableFuture.completedFuture(new SpotifyLibrary.Page<LibraryItem>(List.of(), 60, 0, 50)) : second, () -> 0);
		shelf.more();
		assertEquals(50, shelf.next);
		shelf.more();
		assertTrue(shelf.items.isEmpty());
		assertTrue(shelf.waiting(), "said the playlist has nothing to show while its songs were still loading");
		second.complete(new SpotifyLibrary.Page<>(List.of(new LibraryItem("a", "spotify:track:a", "A", "B", "C", "", 1000, "")), 60, 50, 10));
		shelf.poll();
		assertFalse(shelf.waiting());
		assertEquals(1, shelf.items.size());
	}

	@Test
	void aPlaylistSpotifyRefusesToListIsToldApartSoItPlaysFromTheTop() {
		// Spotify answered 403 to the songs of a playlist you neither own nor collaborate on: the library goes back to the
		// playlists and plays it from the top, as for a playlist Spotify said that of in the list
		LibraryScreen.Shelf refused = new LibraryScreen.Shelf(offset -> CompletableFuture.failedFuture(new SpotifyLibrary.SongsHidden()), () -> 0);
		refused.more();
		assertTrue(refused.hidden);
		assertEquals(dev.beli.skylyrics.Tr.t("Spotify keeps this playlist's songs hidden, so it plays from the top"), refused.error);
		// Any other failure stays an error with Try again
		LibraryScreen.Shelf failed = new LibraryScreen.Shelf(offset -> CompletableFuture.failedFuture(new java.io.IOException("Spotify didn't answer, try again")), () -> 0);
		failed.more();
		assertFalse(failed.hidden);
	}

	@Test
	void tryAgainAfterAFailedFirstLoadIsLoadingNotAnEmptyList() {
		// Liked songs failed to load; Try again asks once more, and a busy Spotify can take up to 30 s to answer: meanwhile
		// the list must not say "No liked songs yet"
		CompletableFuture<SpotifyLibrary.Page<LibraryItem>> retry = new CompletableFuture<>();
		java.util.concurrent.atomic.AtomicInteger asked = new java.util.concurrent.atomic.AtomicInteger();
		LibraryScreen.Shelf shelf = new LibraryScreen.Shelf(offset -> asked.getAndIncrement() == 0
				? CompletableFuture.failedFuture(new java.io.IOException("Spotify didn't answer, try again")) : retry, true, () -> 0);
		shelf.more();
		assertFalse(shelf.error.isEmpty());
		assertFalse(shelf.waiting(), "the error shows, with its Try again button");
		shelf.more();
		assertTrue(shelf.waiting(), "said the list was empty while trying again");
		retry.complete(SpotifyLibrary.Page.empty());
		shelf.poll();
		assertFalse(shelf.waiting(), "a list that really is empty says so");
	}
}
