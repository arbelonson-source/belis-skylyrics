package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.lyrics.LrcParser;
import dev.beli.skylyrics.lyrics.Lyrics;

/** With "Song name only on change" on, pausing keeps the top bar as it is, between lines as on a line. */
class TopBarPauseTest {
	/** Made-up lines with a break between them (from 9 s to 12 s) and the last line at 16 s. */
	private static final Lyrics SONG = LrcParser.parseSynced("""
			[00:02.00]Paper boats along the river
			[00:06.00]Lanterns drifting out of view
			[00:09.00]
			[00:12.00]Counting stars until the morning
			[00:16.00]Singing softly back to you
			""");

	@Test
	void betweenLinesTheBarStaysWhetherPlayingOrPaused() {
		// In the break the bar shows the song's name; a pause there keeps it, as a line stays on the bar while paused.
		// It no longer asks whether the music plays at all (it hid when paused between lines, and only there).
		assertTrue(NowPlayingHud.singing(SONG, 10_000), "between lines");
	}

	@Test
	void beforeTheFirstLineAndLongAfterTheLastItStillHides() {
		assertFalse(NowPlayingHud.singing(SONG, 1_000), "before the first line");
		assertTrue(NowPlayingHud.singing(SONG, 20_000), "just after the last line");
		assertFalse(NowPlayingHud.singing(SONG, 16_000 + 9_000), "a long outro");
	}
}
