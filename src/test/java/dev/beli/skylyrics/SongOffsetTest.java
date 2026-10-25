package dev.beli.skylyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.media.Track;

class SongOffsetTest {
	private static Track track(String title, String artist, String player) {
		return new Track(title, artist, "", 180_000, "", player);
	}

	@Test
	void eachSongKeepsItsOwnTiming() {
		SkyLyricsConfig cfg = new SkyLyricsConfig();
		cfg.syncOffsetMs = 200;
		Track a = track("Paper Moon", "Nova Lane", "spotify");
		Track b = track("Glass River", "Nova Lane", "spotify");
		cfg.setSongOffset(a, 300);
		assertEquals(500, cfg.offsetFor(a));
		assertEquals(200, cfg.offsetFor(b));
	}

	@Test
	void sameSongFromAnotherPlayerFindsItsTiming() {
		SkyLyricsConfig cfg = new SkyLyricsConfig();
		cfg.setSongOffset(track("Paper Moon", "Nova Lane", "spotify"), -400);
		assertEquals(-400, cfg.songOffset(track("paper moon", "NOVA LANE", "firefox")));
	}

	@Test
	void backToZeroForgetsTheSong() {
		SkyLyricsConfig cfg = new SkyLyricsConfig();
		Track a = track("Paper Moon", "Nova Lane", "spotify");
		cfg.setSongOffset(a, 300);
		cfg.setSongOffset(a, 0);
		assertTrue(cfg.songOffsets.isEmpty());
	}

	@Test
	void noSongMeansNoChange() {
		SkyLyricsConfig cfg = new SkyLyricsConfig();
		cfg.setSongOffset(Track.NONE, 300);
		assertTrue(cfg.songOffsets.isEmpty());
		assertEquals(0, cfg.songOffset(Track.NONE));
	}

	@Test
	void oldestSongsAreDroppedFirst() {
		SkyLyricsConfig cfg = new SkyLyricsConfig();
		for (int i = 0; i < 510; i++) {
			cfg.setSongOffset(track("Song " + i, "Band", "x"), 100);
		}
		assertEquals(500, cfg.songOffsets.size());
		assertFalse(cfg.songOffsets.containsKey(SkyLyricsConfig.songKey(track("Song 0", "Band", "x"))));
		assertTrue(cfg.songOffsets.containsKey(SkyLyricsConfig.songKey(track("Song 509", "Band", "x"))));
	}

	@Test
	void timingIsCapped() {
		SkyLyricsConfig cfg = new SkyLyricsConfig();
		Track a = track("Paper Moon", "Nova Lane", "spotify");
		cfg.setSongOffset(a, 99_000);
		assertEquals(5000, cfg.songOffset(a));
	}
}
