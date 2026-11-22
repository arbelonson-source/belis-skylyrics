package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The note in the bar's round space for a song without a cover is a letter of Minecraft's pixel font. Drawn at 0.85, each
 * of its pixels came out 2 or 3 screen pixels by turns at GUI scale 3 (seen in the test game's pictures), so it looked
 * uneven; it now gets whole screen pixels per letter pixel, as the Record card's small text does.
 */
class BarNoteSizeTest {
	@Test
	void theNoteGetsWholeScreenPixels() {
		for (int gui = 1; gui <= 6; gui++) {
			for (float barSize : new float[] {0.75f, 1f, 1.1f, 1.25f, 1.5f}) {
				float pixels = NowPlayingHud.noteSize(gui, barSize) * gui * barSize;
				assertEquals(Math.round(pixels), pixels, 0.001f, "GUI scale " + gui + ", bar size " + barSize + ": " + pixels);
				assertTrue(pixels >= 1, "never smaller than one screen pixel per letter pixel");
				assertTrue(Math.abs(pixels - NowPlayingHud.NOTE_SCALE * gui * barSize) <= 0.5f + 0.001f, "as near 0.85 as whole pixels allow");
			}
		}
	}

	@Test
	void atTheUsualScalesTheNoteIsOneOrThreeQuarters() {
		// GUI scale 2 and 3: 2 and 3 screen pixels a letter pixel (whole size); GUI scale 4: 3 (three quarters)
		assertEquals(1f, NowPlayingHud.noteSize(2, 1), 0.001f);
		assertEquals(1f, NowPlayingHud.noteSize(3, 1), 0.001f);
		assertEquals(0.75f, NowPlayingHud.noteSize(4, 1), 0.001f);
	}
}
