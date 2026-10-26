package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class DemoLibraryTest {
	private final DemoLibrary demo = new DemoLibrary();

	@Test
	void likedSongsComeInPagesUntilTheEnd() {
		Set<String> seen = new HashSet<>();
		int offset = 0;
		int pages = 0;
		while (offset >= 0) {
			SpotifyLibrary.Page<LibraryItem> page = demo.liked(offset).join();
			page.items().forEach(item -> assertTrue(seen.add(item.id()), "repeated " + item.id()));
			offset = page.next();
			pages++;
			assertTrue(pages < 10);
		}
		assertEquals(demo.liked(0).join().total(), seen.size());
		assertEquals("Neon Skyline", demo.liked(0).join().items().getFirst().title());
	}

	@Test
	void searchFindsByTitleOrArtistAndIgnoresCase() {
		List<LibraryItem> found = demo.search("  NEON skyline ", 0).join().items();
		assertFalse(found.isEmpty());
		assertEquals("Neon Skyline", found.getFirst().title());
		assertTrue(demo.search("", 0).join().items().isEmpty());
		assertTrue(demo.search("zzzz no such song", 0).join().items().isEmpty());
	}

	@Test
	void playlistsHoldTheSongsTheySayTheyDo() {
		List<LibraryPlaylist> lists = demo.playlists(0).join().items();
		assertTrue(lists.size() > 1);
		assertFalse(lists.getLast().openable());
		for (LibraryPlaylist list : lists) {
			assertEquals(list.songs(), demo.playlist(list, 0).join().total(), list.name());
		}
	}

	@Test
	void pagingPastTheEndIsEmpty() {
		SpotifyLibrary.Page<Integer> page = DemoLibrary.page(List.of(1, 2, 3), 10);
		assertTrue(page.items().isEmpty());
		assertEquals(-1, page.next());
	}
}
