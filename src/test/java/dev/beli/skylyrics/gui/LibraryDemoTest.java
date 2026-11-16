package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** When the song library shows the made-up songs, and when it goes back to the real ones. */
class LibraryDemoTest {
	@Test
	void tryMadeUpSongsLastsUntilSpotifyCanBeBrowsed() {
		assertTrue(LibraryScreen.stillTrying(true, false));
		assertFalse(LibraryScreen.stillTrying(true, true), "signed in to Spotify in the settings: the real library");
	}

	@Test
	void theDemoSettingAloneNeverSticks() {
		// Opened with Demo song on, then switched off in the settings: nothing was tried, so the real library comes back
		assertFalse(LibraryScreen.stillTrying(false, false));
	}
}
