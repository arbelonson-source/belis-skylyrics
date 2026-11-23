package dev.beli.skylyrics.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.media.TitleCleaner;
import dev.beli.skylyrics.media.Track;
import dev.beli.skylyrics.util.SafeFile;

/**
 * v0.8.0 kept a song's whole artist and title in its timing key; today a name over {@link Track#MAX_TEXT} letters is cut.
 * A timing v0.8.0 saved for such a song is still found, and v0.8.0 still finds it after this version saved the file.
 */
class OldLongSongKeyTest {
	@TempDir
	Path dir;

	private Path file() {
		return dir.resolve("skylyrics.json");
	}

	private SkyLyricsConfig open() {
		return SkyLyricsConfig.load(file(), SafeFile::readText);
	}

	private static Track playing(String title, String artist) {
		return TitleCleaner.clean(new Track(title, artist, "", 0, "", "spotify", ""));
	}

	/** 30 names, 658 letters: a charity single's whole credit, as v0.8.0 saved it. */
	private static final String CREDIT = String.join(", ", Collections.nCopies(30, "Featured Singer Name"));
	/** A movement title of 559 letters. */
	private static final String TITLE = String.join(" ", Collections.nCopies(70, "Allegro"));

	@Test
	void a080TimingForAnArtistCreditOver500LettersIsFound() throws Exception {
		assertTrue(CREDIT.length() > Track.MAX_TEXT);
		Config080 old = new Config080();
		old.setSongOffset("Long Song", CREDIT, 400);
		Files.writeString(file(), old.toJson());
		assertEquals(400, open().songOffset(playing("Long Song", CREDIT)));
	}

	@Test
	void a080TimingForATitleOver500LettersIsFound() throws Exception {
		assertTrue(TITLE.length() > Track.MAX_TEXT);
		Config080 old = new Config080();
		old.setSongOffset(TITLE, "Orchestra", -300);
		old.setSongOffset(TITLE, "", 200); // a file with no artist tag
		Files.writeString(file(), old.toJson());
		SkyLyricsConfig c = open();
		assertEquals(-300, c.songOffset(playing(TITLE, "Orchestra")));
		assertEquals(200, c.songOffset(playing(TITLE, "")));
	}

	@Test
	void afterASaveBothVersionsFindItAndAChangeReachesBoth() throws Exception {
		Config080 old = new Config080();
		old.setSongOffset("Long Song", CREDIT, 400);
		Files.writeString(file(), old.toJson());
		SkyLyricsConfig c = open();
		c.keysMigrated = true;
		c.save(file());
		assertEquals(400, Config080.parse(Files.readString(file())).config().songOffset("Long Song", CREDIT), Files.readString(file()));
		c = open();
		c.setSongOffset(playing("Long Song", CREDIT), 900);
		c.save(file());
		assertEquals(900, open().songOffset(playing("Long Song", CREDIT)));
		assertEquals(1, open().songOffsets.size(), Files.readString(file()));
		assertEquals(900, Config080.parse(Files.readString(file())).config().songOffset("Long Song", CREDIT));
	}

	@Test
	void aLongKeyTodaysNamesCanMakeStaysAsItIs() throws Exception {
		// An artist with " - " in it and a title of 495 letters: split at the artist's own dash the title side would be too
		// long, but split at the real one both sides fit, so it is today's key and is not cut
		String artist = "Before - After Band";
		String title = String.join(" ", Collections.nCopies(61, "Allegro")) + " xxxxxxx";
		assertEquals(495, title.length());
		Track t = playing(title, artist);
		SkyLyricsConfig seed = new SkyLyricsConfig();
		seed.setSongOffset(t, 250);
		seed.save(file());
		assertEquals(250, open().songOffset(t));
		assertEquals(SkyLyricsConfig.makeKey(t), open().songOffsets.keySet().iterator().next());
	}
}
