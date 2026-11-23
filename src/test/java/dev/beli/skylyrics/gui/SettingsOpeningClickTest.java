package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A double-click on a button that brings the settings up (the intro's All settings, a button of the song library's Spotify
 * card) opens them on the first click; the game hands the second one to the settings, at the same place.
 */
class SettingsOpeningClickTest {
	private static final long MS = 1_000_000L;

	@Test
	void theSecondClickOfADoubleClickThatOpenedTheSettingsChangesNothing() {
		// The game counts up to 0.25 s as a double-click. In a 1920x1080 window at GUI scale Auto, with Arc or Lines picked
		// in the intro, All settings sits over the Lyrics tab's Lazy follow row, and a click anywhere on a switch's row
		// flips it: the double-click turned Lazy follow on
		assertTrue(SkyLyricsScreen.secondClickOfOpening(0));
		assertTrue(SkyLyricsScreen.secondClickOfOpening(120 * MS));
		assertTrue(SkyLyricsScreen.secondClickOfOpening(250 * MS));
	}

	@Test
	void aClickOfItsOwnStillCounts() {
		assertFalse(SkyLyricsScreen.secondClickOfOpening(400 * MS));
		assertFalse(SkyLyricsScreen.secondClickOfOpening(3000 * MS));
		// The screen not put up yet (Long.MIN_VALUE / 2 as the time it was): any click is its own
		assertFalse(SkyLyricsScreen.secondClickOfOpening(System.nanoTime() - Long.MIN_VALUE / 2));
	}
}
