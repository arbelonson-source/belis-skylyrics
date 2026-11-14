package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HeardLibraryTest {
	@TempDir
	Path dir;

	private Path file() {
		return dir.resolve("heard.json");
	}

	private final List<HeardLibrary> made = new ArrayList<>();

	/** Lets the background writes finish before the temp folder is deleted. */
	@AfterEach
	void settle() {
		made.forEach(HeardLibrary::flush);
	}

	private HeardLibrary make(Path file, java.util.function.BooleanSupplier on, HeardLibrary.Opener opener) {
		HeardLibrary lib = new HeardLibrary(file, on, opener);
		made.add(lib);
		return lib;
	}

	private HeardLibrary library() {
		return make(file(), () -> true, HeardLibrary.NO_OPENER);
	}

	private static Track song(String title, String artist) {
		return new Track(title, artist, "Album", 200_000, "https://example.test/cover.jpg", "mpv", "file:///music/" + title + ".mp3");
	}

	private static List<String> titles(HeardLibrary lib) {
		return lib.liked(0).join().items().stream().map(LibraryItem::title).toList();
	}

	/** A fake player that can open some kinds of address and remembers what it was asked. */
	private static final class FakeOpener implements HeardLibrary.Opener {
		final List<String> schemes;
		final boolean running;
		final List<String> opened = new ArrayList<>();

		FakeOpener(boolean running, List<String> schemes) {
			this.running = running;
			this.schemes = schemes;
		}

		@Override
		public boolean available() {
			return true;
		}

		@Override
		public List<String> schemes(String player) {
			return running ? schemes : null;
		}

		@Override
		public boolean open(String player, String uri) {
			opened.add(player + " " + uri);
			return true;
		}
	}

	private HeardLibrary.Start start(HeardLibrary.Opener opener, Track track) {
		HeardLibrary lib = make(file(), () -> true, opener);
		lib.record(track);
		lib.flush();
		return lib.start(HeardLibrary.read(file()).getFirst());
	}

	@Test
	void sameSongHeardAgainMovesToTopWithoutDuplicates() {
		HeardLibrary lib = library();
		lib.record(song("One", "A"));
		lib.record(song("Two", "B"));
		lib.record(song("Three", "C"));
		assertEquals(List.of("Three", "Two", "One"), titles(lib));
		lib.record(song("one", "a")); // same song, different case
		assertEquals(List.of("one", "Three", "Two"), titles(lib));
		assertEquals(3, lib.size());
	}

	@Test
	void keepsAtMostTwoHundredAndDropsTheOldest() {
		HeardLibrary lib = library();
		for (int i = 0; i < 250; i++) {
			lib.record(song("Song " + i, "Band"));
		}
		lib.flush();
		assertEquals(200, lib.size());
		assertEquals(200, HeardLibrary.read(file()).size());
		List<String> saved = HeardLibrary.read(file()).stream().map(HeardLibrary.Entry::title).toList();
		assertEquals("Song 249", saved.getFirst());
		assertFalse(saved.contains("Song 49"));
		assertEquals("Song 50", saved.getLast());
		// The screen gets them 50 at a time
		assertEquals(50, lib.liked(0).join().items().size());
		assertEquals(200, lib.liked(0).join().total());
	}

	@Test
	void savedFileComesBackAsItWas() {
		HeardLibrary lib = library();
		lib.record(new Track("שיר ראשון", "להקה", "אלבום", 123_000, "file:///c.png", "spotify", "https://open.spotify.com/track/abc"));
		lib.record(song("Two", "B"));
		lib.flush();
		HeardLibrary again = library();
		assertEquals(List.of("Two", "שיר ראשון"), titles(again));
		LibraryItem hebrew = again.liked(0).join().items().get(1);
		assertEquals("להקה", hebrew.artists());
		assertEquals(123_000, hebrew.lengthMs());
		assertEquals("file:///c.png", hebrew.coverUrl());
		assertEquals("https://open.spotify.com/track/abc", hebrew.uri());
	}

	@Test
	void damagedOrEmptyFileStartsEmptyAndStillWorks() throws IOException {
		for (String junk : List.of("", "   ", "{", "not json at all", "[]", "{}", "{\"songs\": 5}", "{\"songs\": [1, null, {\"artist\": \"x\"}]}")) {
			Files.writeString(file(), junk);
			HeardLibrary lib = library();
			assertEquals(0, lib.size(), "junk: " + junk);
			lib.record(song("Fresh", "A"));
			lib.flush();
			assertEquals(List.of("Fresh"), titles(library()), "junk: " + junk);
		}
		Files.delete(file());
		assertEquals(0, library().size());
	}

	@Test
	void writingLeavesNoTemporaryFilesBehind() throws IOException {
		HeardLibrary lib = library();
		lib.record(song("One", "A"));
		lib.record(song("Two", "B"));
		lib.flush();
		try (Stream<Path> files = Files.list(dir)) {
			assertEquals(List.of("heard.json"), files.map(p -> p.getFileName().toString()).toList());
		}
	}

	@Test
	void searchFiltersLocallyIgnoringCaseAndWorksInHebrew() {
		HeardLibrary lib = library();
		lib.record(new Track("Neon Skyline", "The Quiet Biomes", "Shallow Light", 1, "", "mpv"));
		lib.record(new Track("Lantern Hours", "Moss & Copper", "Under the Oaks", 1, "", "mpv"));
		lib.record(new Track("שיר לשלום", "שלמה ארצי", "", 1, "", "mpv"));
		assertEquals(List.of("Neon Skyline"), lib.search("  NEON sky", 0).join().items().stream().map(LibraryItem::title).toList());
		assertEquals(List.of("Neon Skyline"), lib.search("biomes", 0).join().items().stream().map(LibraryItem::title).toList());
		assertEquals(List.of("שיר לשלום"), lib.search("ארצי", 0).join().items().stream().map(LibraryItem::title).toList());
		assertEquals(List.of("Lantern Hours"), lib.search("copper hours", 0).join().items().stream().map(LibraryItem::title).toList());
		assertTrue(lib.search("zzz", 0).join().items().isEmpty());
		assertTrue(lib.search("   ", 0).join().items().isEmpty());
	}

	@Test
	void demoSongNothingPlayingAndSwitchedOffAreNeverRecorded() {
		HeardLibrary lib = library();
		lib.record(DemoSource.TRACK);
		lib.record(Track.NONE);
		assertEquals(0, lib.size());
		boolean[] on = {false};
		HeardLibrary off = make(dir.resolve("off.json"), () -> on[0], HeardLibrary.NO_OPENER);
		off.record(song("One", "A"));
		assertEquals(0, off.size());
		on[0] = true;
		off.record(song("One", "A"));
		assertEquals(1, off.size());
	}

	@Test
	void clearEmptiesTheListAndTheFile() {
		HeardLibrary lib = library();
		lib.record(song("One", "A"));
		lib.clear();
		lib.flush();
		assertEquals(0, lib.size());
		assertEquals(0, library().size());
	}

	@Test
	void cantStartWithoutAnAddressOrWhereThereIsNoOpenUri() {
		FakeOpener linux = new FakeOpener(true, List.of("file"));
		assertEquals(HeardLibrary.Start.CANT, start(linux, new Track("One", "A", "", 1, "", "mpv")));
		assertTrue(linux.opened.isEmpty());
		// Windows and macOS have nothing like OpenUri, even with an address
		assertEquals(HeardLibrary.Start.CANT, start(HeardLibrary.NO_OPENER, song("One", "A")));
	}

	@Test
	void startsOnlyWhatThePlayerSaysItCanOpen() {
		FakeOpener mpv = new FakeOpener(true, List.of("file", "http"));
		assertEquals(HeardLibrary.Start.STARTED, start(mpv, song("One", "A")));
		assertEquals(List.of("mpv file:///music/One.mp3"), mpv.opened);

		// A browser lists no schemes, so a web page address can't be started
		FakeOpener browser = new FakeOpener(true, List.of());
		Track page = new Track("Clip", "Chan", "", 1, "", "firefox", "https://www.youtube.com/watch?v=x");
		assertEquals(HeardLibrary.Start.CANT, start(browser, page));
		assertTrue(browser.opened.isEmpty());

		assertEquals(HeardLibrary.Start.NOT_RUNNING, start(new FakeOpener(false, List.of("file")), song("One", "A")));
	}

	@Test
	void uriSchemeCheck() {
		List<String> file = List.of("file");
		assertEquals("file:///a/b.mp3", HeardLibrary.openableUri("file:///a/b.mp3", file));
		assertEquals("FILE:///a.mp3", HeardLibrary.openableUri("FILE:///a.mp3", file));
		assertNull(HeardLibrary.openableUri("https://example.test/x", file));
		assertNull(HeardLibrary.openableUri("file:///a.mp3", List.of()));
		// mpv's real list has no "file" but it plays files; Spotify lists only its own kind
		assertEquals("file:///a.mp3", HeardLibrary.openableUri("file:///a.mp3", List.of("ftp", "http", "https", "smb")));
		assertNull(HeardLibrary.openableUri("file:///a.mp3", List.of("spotify")));
		assertNull(HeardLibrary.openableUri("/just/a/path.mp3", file));
		assertNull(HeardLibrary.openableUri("", file));
		assertEquals("file", HeardLibrary.schemeOf("File:///x"));
		assertEquals("", HeardLibrary.schemeOf("no scheme here"));
		// Spotify's web address becomes the form the app takes, but only if the player takes spotify:
		assertEquals("spotify:track:4uLU6hMCjMI75M1A2tKUQC",
				HeardLibrary.openableUri("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC?si=1", List.of("spotify")));
		assertNull(HeardLibrary.openableUri("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC", List.of("file")));
		assertEquals("spotify:track:abc", HeardLibrary.openableUri("spotify:track:abc", List.of("spotify")));
	}
}
