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
}
