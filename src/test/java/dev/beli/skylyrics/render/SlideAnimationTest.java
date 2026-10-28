package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SlideAnimationTest {
	@Test
	void aNewLineGlidesInAndSettlesInTheMiddle() {
		assertEquals(LyricsRenderer.SLIDE_PART, LyricsRenderer.slideIn(0), 1e-6);
		assertEquals(0, LyricsRenderer.slideIn(1), 1e-6);
		assertTrue(LyricsRenderer.slideIn(0.5f) < LyricsRenderer.SLIDE_PART / 2, "most of the way in by halfway");
		float last = Float.MAX_VALUE;
		for (int i = 0; i <= 20; i++) {
			float x = LyricsRenderer.slideIn(i / 20f);
			assertTrue(x <= last, "never slides back out");
			last = x;
		}
	}

	@Test
	void theOldLineLeavesFromWhereItWas() {
		assertEquals(0, LyricsRenderer.slideOut(0), 1e-6);
		assertEquals(LyricsRenderer.SLIDE_PART, LyricsRenderer.slideOut(1), 1e-6);
		assertEquals(0, LyricsRenderer.slideIn(2), 1e-6);
	}
}
