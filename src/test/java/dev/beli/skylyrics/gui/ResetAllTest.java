package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Reset all asks to be pressed again before it resets every setting. */
class ResetAllTest {
	private static final long SECOND = 1_000_000_000L;

	@Test
	void aDoubleClickDoesNotResetEverySetting() {
		// The first press asks for another, for 3 seconds. A double-click's second click came 0.1 to 0.25 s after the
		// first and reset everything at once, as if "Click again to reset" had been read and clicked
		long armedAt = 50 * SECOND;
		long until = armedAt + 3 * SECOND;
		assertFalse(SkyLyricsScreen.resetConfirms(armedAt + SECOND / 10, until));
		assertFalse(SkyLyricsScreen.resetConfirms(armedAt + SECOND / 4, until));
	}

	@Test
	void aSecondPressOfItsOwnResets() {
		long armedAt = 50 * SECOND;
		long until = armedAt + 3 * SECOND;
		assertTrue(SkyLyricsScreen.resetConfirms(armedAt + LibraryKeys.DOUBLE_CLICK_NS, until));
		assertTrue(SkyLyricsScreen.resetConfirms(armedAt + 2 * SECOND, until));
		assertFalse(SkyLyricsScreen.resetConfirms(until, until), "after 3 seconds it asks again");
		assertFalse(SkyLyricsScreen.resetConfirms(armedAt, 0), "a first press only asks");
	}
}
