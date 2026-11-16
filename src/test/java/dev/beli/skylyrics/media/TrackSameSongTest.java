package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** When two readings from the player are the same song, which decides if lyrics are fetched again. */
class TrackSameSongTest {
	private static Track of(String title, String artist, String album, String player, String art, String uri) {
		return new Track(title, artist, album, 200_000, art, player, uri);
	}

	@Test
	void anotherPlayerOrCoverIsStillTheSameSong() {
		Track a = of("Paper Moon", "Nova Lane", "Night", "spotify", "https://x/a.png", "");
		Track b = of("Paper Moon", "Nova Lane", "Night", "firefox", "https://x/b.png", "");
		assertTrue(a.sameSong(b));
	}

	@Test
	void anotherTitleArtistOrAlbumIsAnotherSong() {
		Track a = of("Paper Moon", "Nova Lane", "Night", "spotify", "", "");
		assertFalse(a.sameSong(of("Glass River", "Nova Lane", "Night", "spotify", "", "")));
		assertFalse(a.sameSong(of("Paper Moon", "Other Band", "Night", "spotify", "", "")));
		assertFalse(a.sameSong(of("Paper Moon", "Nova Lane", "Day", "spotify", "", "")));
	}

	@Test
	void caseMattersBecauseTheTextIsKeptAsThePlayerSaidIt() {
		Track a = of("Paper Moon", "Nova Lane", "", "spotify", "", "");
		assertFalse(a.sameSong(of("paper moon", "Nova Lane", "", "spotify", "", "")));
	}

	@Test
	void twoDifferentSpotifyAddressesAreTwoSongsEvenWithTheSameNames() {
		Track a = of("Interlude", "Nova Lane", "Night", "spotify", "", "spotify:track:1");
		Track b = of("Interlude", "Nova Lane", "Night", "spotify", "", "spotify:track:2");
		assertTrue(a.otherUri(b));
		assertFalse(a.sameSong(b));
		assertTrue(a.sameSong(a));
	}

	@Test
	void aSpotifyAddressOnlyOneSideHasDoesNotSeparateTheSongs() {
		Track a = of("Interlude", "Nova Lane", "Night", "spotify", "", "spotify:track:1");
		Track b = of("Interlude", "Nova Lane", "Night", "spotify", "", "");
		assertFalse(a.otherUri(b));
		assertTrue(a.sameSong(b));
		assertTrue(b.sameSong(a));
	}

	@Test
	void differentBrowserPageAddressesNeverSeparateTheSongs() {
		Track a = of("Paper Moon", "Nova Lane", "", "firefox", "", "https://music.example/watch?v=1&index=1");
		Track b = of("Paper Moon", "Nova Lane", "", "firefox", "", "https://music.example/watch?v=1&index=2");
		assertFalse(a.otherUri(b));
		assertTrue(a.sameSong(b));
	}

	@Test
	void nothingPlayingIsNone() {
		assertTrue(Track.NONE.isNone());
		assertFalse(of("Paper Moon", "Nova Lane", "", "spotify", "", "").isNone());
		assertTrue(Track.NONE.sameSong(Track.NONE));
	}
}
