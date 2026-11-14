package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.media.Track;

class SameSongTest {
	private static Track track(String title, String artist, long length) {
		return new Track(title, artist, "", length, "", "spotify");
	}

	@Test
	void sameObjectOrSameTitleAndArtistCountsAsAnnounced() {
		Track a = track("Paper Moon", "Nova Lane", 1);
		assertTrue(LyricsRenderer.sameSong(a, a));
		assertTrue(LyricsRenderer.sameSong(track("Paper Moon", "Nova Lane", 2), a));
	}

	@Test
	void anotherSongOrNothingYetIsNot() {
		Track a = track("Paper Moon", "Nova Lane", 1);
		assertFalse(LyricsRenderer.sameSong(track("Glass River", "Nova Lane", 1), a));
		assertFalse(LyricsRenderer.sameSong(track("Paper Moon", "Other", 1), a));
		assertFalse(LyricsRenderer.sameSong(a, null));
	}
}
