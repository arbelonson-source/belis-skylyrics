package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The page offset of the heard list, and what hearing the same song again keeps. */
class HeardLibraryPagingTest {
	@TempDir
	Path dir;

	private final List<HeardLibrary> made = new ArrayList<>();

	@AfterEach
	void settle() {
		made.forEach(HeardLibrary::flush);
	}

	private HeardLibrary library() {
		HeardLibrary lib = new HeardLibrary(dir.resolve("heard.json"), () -> true, HeardLibrary.NO_OPENER);
		made.add(lib);
		return lib;
	}

	private static List<String> titles(SpotifyLibrary.Page<LibraryItem> page) {
		return page.items().stream().map(LibraryItem::title).toList();
	}

	@Test
	void theOffsetPicksTheNextFiftyAndReportsWhereItStarted() {
		HeardLibrary lib = library();
		for (int i = 0; i < 120; i++) {
			lib.record(new Track("Song " + i, "Band", "Album", 1000, "", "mpv"));
		}
		var first = lib.liked(0).join();
		assertEquals(50, first.items().size());
		assertEquals("Song 119", first.items().getFirst().title());
		assertEquals(0, first.offset());
		assertEquals(120, first.total());

		var second = lib.liked(50).join();
		assertEquals(50, second.items().size());
		assertEquals("Song 69", second.items().getFirst().title());
		assertEquals(50, second.offset());

		var last = lib.liked(100).join();
		assertEquals(20, last.items().size());
		assertEquals("Song 19", last.items().getFirst().title());
		assertEquals("Song 0", last.items().getLast().title());
		assertEquals(100, last.offset());
		assertEquals(120, last.total());
	}

	@Test
	void anOffsetPastTheEndGivesAnEmptyPageNotACrash() {
		HeardLibrary lib = library();
		lib.record(new Track("Only", "Band", "Album", 1000, "", "mpv"));
		var page = lib.liked(500).join();
		assertTrue(page.items().isEmpty());
		assertEquals(1, page.total());
		assertEquals(1, page.offset());
		assertTrue(lib.liked(1).join().items().isEmpty());
	}

	@Test
	void pagesNeverRepeatOrSkipASong() {
		HeardLibrary lib = library();
		for (int i = 0; i < 75; i++) {
			lib.record(new Track("T" + i, "B", "A", 1, "", "mpv"));
		}
		List<String> all = new ArrayList<>(titles(lib.liked(0).join()));
		all.addAll(titles(lib.liked(50).join()));
		assertEquals(75, all.size());
		assertEquals(75, all.stream().distinct().count());
	}

	@Test
	void hearingTheSameSongWithoutDetailsKeepsTheCoverAddressAndAlbumOfTheEarlierTime() {
		HeardLibrary lib = library();
		lib.record(new Track("Song", "Band", "Album", 200_000, "https://example.test/cover.jpg", "spotify", "spotify:track:1"));
		// The same song again, from a player that says nothing but the title and artist
		lib.record(new Track("Song", "Band", "", 0, "", "", ""));
		var items = lib.liked(0).join().items();
		assertEquals(1, items.size());
		LibraryItem item = items.getFirst();
		assertEquals("https://example.test/cover.jpg", item.coverUrl());
		assertEquals("spotify:track:1", item.uri());
		assertEquals("Album", item.album());
		assertEquals(200_000, item.lengthMs());
	}

	@Test
	void hearingTheSameSongWithNewDetailsTakesTheNewOnes() {
		HeardLibrary lib = library();
		lib.record(new Track("Song", "Band", "Old album", 100_000, "https://example.test/old.jpg", "mpv", "file:///old.mp3"));
		lib.record(new Track("Song", "Band", "New album", 200_000, "https://example.test/new.jpg", "mpv", "file:///new.mp3"));
		LibraryItem item = lib.liked(0).join().items().getFirst();
		assertEquals("https://example.test/new.jpg", item.coverUrl());
		assertEquals("file:///new.mp3", item.uri());
		assertEquals("New album", item.album());
		assertEquals(200_000, item.lengthMs());
	}

	@Test
	void theSearchIgnoresAccentsAndCase() {
		HeardLibrary lib = library();
		lib.record(new Track("Halo", "Beyonc\u00e9", "I Am", 1000, "", "mpv"));
		lib.record(new Track("Other", "Band", "Album", 1000, "", "mpv"));
		assertEquals(List.of("Halo"), titles(lib.search("BEYONCE", 0).join()));
		assertEquals(List.of("Halo"), titles(lib.search("beyonc\u00e9", 0).join()));
	}
}
