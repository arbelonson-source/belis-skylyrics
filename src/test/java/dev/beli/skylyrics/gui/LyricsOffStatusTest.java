package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.InLanguage;
import dev.beli.skylyrics.lyrics.LrcParser;
import dev.beli.skylyrics.lyrics.Lyrics;
import dev.beli.skylyrics.lyrics.LyricsService;
import dev.beli.skylyrics.media.Track;

/**
 * With Lyrics off, a song that starts is not looked up: it waits until Lyrics is turned on. The Music tab's Lyrics row
 * (and the Narrator, which reads it) then says "Lyrics off", in the game's language, not "Waiting for a song" while a
 * song plays.
 */
class LyricsOffStatusTest {
	private static final Track SONG = new Track("Paper Lanterns", "Nova Lane", "Night Roads", 180_000, "", "spotify");
	private static final LyricsService.Snapshot WAITING = new LyricsService.Snapshot(SONG, Lyrics.NONE, LyricsService.State.IDLE);

	/** The text the language file gives the Lyrics key's "off" message. */
	private static String lyricsOff(String code) throws Exception {
		try (Reader r = Files.newBufferedReader(Path.of("src/main/resources/assets/skylyrics/lang", code + ".json"), StandardCharsets.UTF_8)) {
			return new Gson().fromJson(r, JsonObject.class).get("skylyrics.lyrics.off").getAsString();
		}
	}

	@Test
	void aSongWaitingWhileLyricsAreOffSaysLyricsOff() throws Exception {
		try (InLanguage en = InLanguage.of("en_us")) {
			assertEquals("Lyrics off", SkyLyricsScreen.lyricsStatus(SONG, WAITING, false));
		}
		try (InLanguage de = InLanguage.of("de_de")) {
			assertEquals(lyricsOff("de_de"), SkyLyricsScreen.lyricsStatus(SONG, WAITING, false));
		}
	}

	@Test
	void theOtherStatesSayWhatTheyAlwaysSaid() throws Exception {
		try (InLanguage en = InLanguage.of("en_us")) {
			assertEquals(en.says("Waiting for a song"), SkyLyricsScreen.lyricsStatus(Track.NONE, WAITING, false), "nothing playing");
			assertEquals(en.says("Waiting for a song"), SkyLyricsScreen.lyricsStatus(SONG, WAITING, true));
			assertEquals(en.says("Looking for lyrics…"),
					SkyLyricsScreen.lyricsStatus(SONG, new LyricsService.Snapshot(SONG, Lyrics.NONE, LyricsService.State.LOADING), false),
					"turned on, or off while the lookup was on its way");
			Lyrics found = LrcParser.parseSynced("[00:01.00]A made-up line\n[00:05.00]Another made-up line");
			assertEquals(en.says("Found, with timing"),
					SkyLyricsScreen.lyricsStatus(SONG, new LyricsService.Snapshot(SONG, found, LyricsService.State.READY), false),
					"lyrics found before Lyrics went off are still known");
		}
	}
}
