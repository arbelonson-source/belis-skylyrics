package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The note the top bar shows in place of a missing album cover. */
class TopBarNoteTest {
	@Test
	void theNoteNeverBlinksWhiteWhileTheBarFades() {
		// Minecraft 1.21.5 and older draw text whose alpha is under 4 fully opaque, so a note that faint would blink
		// white for a frame as the bar appears or hides. The bar draws while it is over 1% visible, and the note dims to
		// 65% at most while the text changes.
		for (int i = 0; i <= 1000; i++) {
			float a = 0.0065f + i * (1 - 0.0065f) / 1000;
			int alpha = NowPlayingHud.noteColor(a) >>> 24;
			assertTrue(alpha >= 4, "the note's alpha is " + alpha + " when it is " + a + " visible");
		}
		assertEquals(230, NowPlayingHud.noteColor(1) >>> 24);
		assertEquals(0xFFFFFF, NowPlayingHud.noteColor(0.5f) & 0xFFFFFF);
	}
}
