package dev.beli.skylyrics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Whether anything on screen shows the song, which Spotify and the Windows and Mac readers ask on their own thread before
 * each read. The first read runs the moment the mod starts, while Minecraft is still being built: on 26.2 and later the
 * game's GUI, which holds the open screen, only comes after the mods have started, so reading the screen there failed
 * every read until then ("Couldn't read the music player" in the log at every start). There is no game here at all.
 */
class LookingOffThreadTest {
	private static boolean askedFromAReader(boolean lyricsOn) throws Exception {
		ExecutorService reader = Executors.newSingleThreadExecutor(r -> new Thread(r, "SkyLyrics media"));
		try {
			return reader.submit(() -> SkyLyricsClient.looking(lyricsOn)).get(10, TimeUnit.SECONDS);
		} finally {
			reader.shutdownNow();
		}
	}

	@Test
	void beforeTheGameIsBuiltTheLyricsSettingAnswers() throws Exception {
		assertTrue(askedFromAReader(true), "Lyrics on: the song is shown");
		assertFalse(askedFromAReader(false), "Lyrics off and no song screen seen yet: nobody looks");
	}

	@Test
	void aSongScreenTheGameTickSawCountsWithLyricsOff() throws Exception {
		try {
			SkyLyricsClient.songScreen(true); // the settings, the song library or the tour is open
			assertTrue(askedFromAReader(false), "a song screen is open: it shows the song");
			SkyLyricsClient.songScreen(false);
			assertFalse(askedFromAReader(false), "closed again");
		} finally {
			SkyLyricsClient.songScreen(false);
		}
	}
}
