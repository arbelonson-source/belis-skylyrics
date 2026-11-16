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
		lib.record(new Track("שיר ראשון", "להקה", "אלבום", 123_000, "https://img.example/c.png", "spotify", "https://open.spotify.com/track/abc"));
		lib.record(song("Two", "B"));
		lib.flush();
		HeardLibrary again = library();
		assertEquals(List.of("Two", "שיר ראשון"), titles(again));
		LibraryItem hebrew = again.liked(0).join().items().get(1);
		assertEquals("להקה", hebrew.artists());
		assertEquals(123_000, hebrew.lengthMs());
		assertEquals("https://img.example/c.png", hebrew.coverUrl());
		assertEquals("https://open.spotify.com/track/abc", hebrew.uri());
	}

	/** The Windows helper's cover is a temporary file the cover cache can't load and that is deleted soon: no dead path in the list. */
	@Test
	void aFileCoverIsNotRemembered() {
		HeardLibrary lib = library();
		lib.record(new Track("One", "A", "", 1000, "file:///C:/Users/x/cover-12-1.img", "edge"));
		lib.record(new Track("Two", "B", "", 1000, "https://img.example/b.jpg", "edge"));
		lib.record(new Track("Three", "C", "", 1000, "FILE:///tmp/c.img", "edge"));
		lib.flush();
		List<LibraryItem> items = library().liked(0).join().items();
		assertEquals("", items.get(2).coverUrl());
		assertEquals("https://img.example/b.jpg", items.get(1).coverUrl());
		assertEquals("", items.get(0).coverUrl());
	}

	/** An older file may still hold a file: cover; hearing the song again without a cover must not carry that dead path over. */
	@Test
	void theSameSongHeardAgainWithoutACoverDoesNotKeepAnOldFileCover() throws IOException {
		Files.writeString(file(), HeardLibrary.toJson(List.of(
				new HeardLibrary.Entry("One", "A", "Album", 1000, "file:///C:/Users/x/cover-12-1.img", "uri:one", "edge"))));
		HeardLibrary lib = library();
		lib.record(new Track("One", "A", "Album", 1000, "", "edge", "uri:one"));
		lib.flush();
		List<LibraryItem> items = library().liked(0).join().items();
		assertEquals(1, items.size());
		assertEquals("", items.get(0).coverUrl());
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

	@Test
	void aDamagedFileIsKeptAsBadAndBadEntriesAreSkippedOneByOne() throws IOException {
		for (String damaged : new String[] {"{\"songs\": [{\"title\": \"A\"", "[1,2,3]", "{\"songs\": 5}"}) {
			Files.writeString(file(), damaged);
			assertTrue(HeardLibrary.read(file()).isEmpty());
			assertEquals(damaged, Files.readString(dir.resolve("heard.json.bad")), damaged);
			Files.delete(dir.resolve("heard.json.bad"));
		}
		Files.writeString(file(), "{}");
		assertTrue(HeardLibrary.read(file()).isEmpty());
		Files.writeString(file(), "{\"songs\": null}");
		assertTrue(HeardLibrary.read(file()).isEmpty());
		assertFalse(Files.exists(dir.resolve("heard.json.bad")));

		Files.writeString(file(), "{\"songs\": [5, null, {\"title\": {\"x\": 1}}, {\"title\": \"Good\", \"artist\": \"Me\"}]}");
		assertEquals(1, HeardLibrary.read(file()).size());
		assertEquals("Good", HeardLibrary.read(file()).get(0).title());
	}

	@Test
	void anUnreadableFileIsRetriedAndNeverOverwrittenThisSession() throws IOException {
		String real = HeardLibrary.toJson(List.of(new HeardLibrary.Entry("Old", "A", "B", 1000, "", "uri:old", "mpv")));
		Files.writeString(file(), real);
		int[] calls = {0};
		HeardLibrary lib = new HeardLibrary(file(), () -> true, HeardLibrary.NO_OPENER, f -> {
			calls[0]++;
			throw new IOException("locked");
		});
		made.add(lib);
		assertTrue(calls[0] >= 3);
		lib.record(song("New", "X"));
		lib.flush();
		assertEquals(real, Files.readString(file()));
	}

	@Test
	void damagedFileDoesNotReplaceABetterBadCopyAndGetsItsOwn() throws IOException {
		Path bad = dir.resolve("heard.json.bad");
		Files.writeString(bad, "{\"songs\": [{\"title\": \"Kept\"");
		Files.writeString(file(), "{");
		assertTrue(HeardLibrary.read(file()).isEmpty());
		assertEquals("{\"songs\": [{\"title\": \"Kept\"", Files.readString(bad));
		assertEquals("{", Files.readString(dir.resolve("heard.json.bad.1")));
	}

	@Test
	void writeRetriesWhenTheTargetIsBrieflyLocked() throws IOException {
		int[] calls = {0};
		HeardLibrary.write(file(), List.of(new HeardLibrary.Entry("T", "A", "B", 1, "", "u", "mpv")), (from, to, opts) -> {
			if (++calls[0] == 1) throw new IOException("locked");
			Files.move(from, to, opts);
		});
		assertEquals(1, HeardLibrary.read(file()).size());
		try (Stream<Path> s = Files.list(dir)) {
			assertTrue(s.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")));
		}
	}

	@Test
	void bytesThatAreNotTextCountAsDamageNotALock() throws IOException {
		Files.write(file(), new byte[] {(byte) 0xC3, 0x28});
		HeardLibrary lib = make(file(), () -> true, HeardLibrary.NO_OPENER);
		assertTrue(Files.exists(dir.resolve("heard.json.bad")));
		lib.record(song("New", "X"));
		lib.flush();
		assertEquals(1, HeardLibrary.read(file()).size());
	}

	@Test
	void aBlockedLibraryMergesTheFileWithThisSessionsSongsWhenItReadsAgain() throws IOException {
		HeardLibrary.write(file(), List.of(
				new HeardLibrary.Entry("A", "X", "", 1, "", "uri:a", "mpv"),
				new HeardLibrary.Entry("B", "X", "", 1, "", "uri:b", "mpv"),
				new HeardLibrary.Entry("C", "X", "", 1, "", "uri:c", "mpv")));
		boolean[] locked = {true};
		HeardLibrary lib = new HeardLibrary(file(), () -> true, HeardLibrary.NO_OPENER, f -> {
			if (locked[0]) throw new IOException("locked");
			return Files.readString(f);
		});
		made.add(lib);
		lib.record(song("Fresh", "X"));
		lib.flush();
		assertEquals(3, HeardLibrary.read(file()).size());
		locked[0] = false;
		lib.record(song("Fresh", "X")); // heard again: no duplicate
		lib.flush();
		List<String> saved = HeardLibrary.read(file()).stream().map(HeardLibrary.Entry::title).toList();
		assertEquals(List.of("Fresh", "A", "B", "C"), saved);
	}

	@Test
	void twoGamesOnOneFileKeepEachOthersSongs() {
		HeardLibrary a = library();
		HeardLibrary b = library();
		a.record(song("X", "Me"));
		b.record(song("Y", "Me"));
		a.flush();
		b.flush();
		a.record(song("X2", "Me"));
		a.flush();
		b.record(song("Y2", "Me"));
		b.flush();
		List<String> saved = HeardLibrary.read(file()).stream().map(HeardLibrary.Entry::title).toList();
		assertEquals(java.util.Set.of("X", "Y", "X2", "Y2"), new java.util.HashSet<>(saved));
		assertEquals(4, saved.size());
		assertEquals("Y2", saved.get(0));
	}

	@Test
	void aSongTheOtherGameAddedComesAfterOursAndTheFilesOrderHolds() {
		HeardLibrary a = library();
		HeardLibrary b = library();
		b.record(song("P", "Me"));
		b.record(song("Q", "Me"));
		b.flush();
		a.record(song("X", "Me"));
		a.flush();
		assertEquals(List.of("X", "Q", "P"), HeardLibrary.read(file()).stream().map(HeardLibrary.Entry::title).toList());
		assertEquals(List.of("X", "Q", "P"), titles(a));
	}

	@Test
	void clearStaysEffectiveWhileTheFileStillListsTheSongs() {
		HeardLibrary b = library();
		b.record(song("X", "Me"));
		b.flush();
		HeardLibrary a = library(); // read x at start
		a.clear();
		a.flush();
		assertTrue(HeardLibrary.read(file()).isEmpty());
		assertTrue(titles(a).isEmpty());
	}

	@Test
	void clearedSongsDoNotComeBackFromTheFileButTheOtherGamesNewOnesDo() throws IOException {
		HeardLibrary a = library();
		a.record(song("X", "Me"));
		a.record(song("W", "Me"));
		a.flush();
		// Another game wrote the file: still x and w, plus its own new n
		HeardLibrary.write(file(), List.of(new HeardLibrary.Entry("X", "Me", "Album", 1, "", "u", "mpv"),
				new HeardLibrary.Entry("N", "Me", "Album", 1, "", "u", "mpv"),
				new HeardLibrary.Entry("W", "Me", "Album", 1, "", "u", "mpv")));
		a.clear();
		a.flush();
		assertEquals(List.of("N"), HeardLibrary.read(file()).stream().map(HeardLibrary.Entry::title).toList());
	}

	@Test
	void aFailedWriteDoesNotLetClearedSongsComeBackOnTheNextSave() {
		HeardLibrary b = library();
		b.record(song("X", "Me"));
		b.record(song("W", "Me"));
		b.flush();
		boolean[] failing = {false};
		HeardLibrary a = make2(failing);
		failing[0] = true;
		a.clear();
		a.flush(); // this save's write fails: the file still lists x and w
		assertEquals(2, HeardLibrary.read(file()).size());
		failing[0] = false;
		a.record(song("N", "Me"));
		a.flush();
		assertEquals(List.of("N"), HeardLibrary.read(file()).stream().map(HeardLibrary.Entry::title).toList());
		assertEquals(List.of("N"), titles(a));
	}

	@Test
	void aSongHeardWhileTheFileIsBrieflyLockedIsStillSaved() throws IOException {
		HeardLibrary b = library();
		b.record(song("X", "Me"));
		b.flush();
		int[] failures = {0};
		boolean[] armed = {false};
		HeardLibrary a = new HeardLibrary(file(), () -> true, HeardLibrary.NO_OPENER, f -> {
			if (armed[0] && failures[0]++ == 0) throw new IOException("locked");
			return Files.readString(f, java.nio.charset.StandardCharsets.UTF_8);
		});
		made.add(a);
		armed[0] = true;
		a.record(song("Y", "Me"));
		a.flush();
		assertEquals(List.of("Y", "X"), HeardLibrary.read(file()).stream().map(HeardLibrary.Entry::title).toList());
	}

	private HeardLibrary make2(boolean[] failing) {
		HeardLibrary lib = new HeardLibrary(file(), () -> true, HeardLibrary.NO_OPENER, dev.beli.skylyrics.util.SafeFile::readText,
				(from, to, opts) -> {
					if (failing[0]) throw new IOException("locked");
					Files.move(from, to, opts);
				});
		made.add(lib);
		return lib;
	}

	@Test
	void aDamagedFileAtSaveTimeDoesNotLoseOurList() throws IOException {
		HeardLibrary a = library();
		a.record(song("X", "Me"));
		a.flush();
		Files.writeString(file(), "{\"songs\": [");
		a.record(song("Y", "Me"));
		a.flush();
		assertEquals(List.of("Y", "X"), HeardLibrary.read(file()).stream().map(HeardLibrary.Entry::title).toList());
	}

	@Test
	void aMissingFileAtSaveTimeIsWrittenAgain() throws IOException {
		HeardLibrary a = library();
		a.record(song("X", "Me"));
		a.flush();
		Files.delete(file());
		a.record(song("Y", "Me"));
		a.flush();
		assertEquals(List.of("Y", "X"), HeardLibrary.read(file()).stream().map(HeardLibrary.Entry::title).toList());
	}

	@Test
	void aFileNobodyCanReadIsMovedAsideButALockedOneIsNot() throws IOException {
		Files.writeString(file(), "{\"songs\": []}");
		HeardLibrary.Loaded locked = HeardLibrary.load(file(), f -> {
			throw new IOException("locked");
		}, 1, 0, p -> true);
		assertTrue(locked.failed());
		assertTrue(Files.exists(file()));
		HeardLibrary.Loaded denied = HeardLibrary.load(file(), f -> {
			throw new java.nio.file.AccessDeniedException(f.toString());
		}, 1, 0, p -> false);
		assertFalse(denied.failed());
		assertFalse(Files.exists(file()));
		assertTrue(Files.exists(dir.resolve("heard.json.bad")));
	}

	@Test
	void aNegativeOffsetIsTheFirstPage() {
		List<Integer> all = List.of(1, 2, 3);
		assertEquals(0, HeardLibrary.page(all, -5).offset());
		assertEquals(3, HeardLibrary.page(all, -5).items().size());
	}

	@Test
	void staleTempOfAKilledGameIsCleanedAtStartButAFreshOneStays() throws IOException {
		Path stale = dir.resolve("heard.json.123.tmp"), fresh = dir.resolve("heard.json.456.tmp");
		Files.writeString(stale, "x");
		Files.writeString(fresh, "x");
		Files.setLastModifiedTime(stale, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() - 2 * dev.beli.skylyrics.util.Leftovers.AGE_MS));
		HeardLibrary.read(file());
		assertFalse(Files.exists(stale));
		assertTrue(Files.exists(fresh));
	}

	@Test
	void saveTempFollowsTheCleanedNamePattern() throws IOException {
		int[] calls = {0};
		HeardLibrary.write(file(), List.of(new HeardLibrary.Entry("T", "A", "B", 1, "", "u", "mpv")), (from, to, opts) -> {
			calls[0]++;
			assertTrue(from.getFileName().toString().matches("heard\\.json\\.\\d+\\.tmp"), from.getFileName().toString());
			Files.move(from, to, opts);
		});
		assertEquals(1, calls[0], "the save goes through the temp mover exactly once");
		assertEquals(1, HeardLibrary.read(file()).size());
	}
}
