package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GrowWordTest {
	private static final float FULL = 1 + LyricsRenderer.GROW;

	/** A word on letters 10..15 of the line. */
	private static float at(float sung) {
		return LyricsRenderer.wordGrowth(sung, 10, 15, 1);
	}

	@Test
	void normalSizeBeforeAndAfterItsTurn() {
		assertEquals(1, at(0));
		assertEquals(1, at(10));
		assertEquals(1, at(16.5f));
		assertEquals(1, at(40));
	}

	@Test
	void swellsWhileSungThenSettles() {
		assertEquals(FULL, at(12), 1e-5);
		assertEquals(FULL, at(15), 1e-5);
		float rising = at(10.75f), settling = at(15.75f);
		assertTrue(rising > 1 && rising < FULL, "rising " + rising);
		assertTrue(settling > 1 && settling < FULL, "settling " + settling);
		assertTrue(at(10.3f) < rising && rising < at(11.2f), "grows smoothly");
	}

	/** The last word ends where the line ends; it must still settle once the line's time is up. */
	@Test
	void lastWordSettlesAfterTheLine() {
		int total = 20; // letters in the line; the last word is letters 15..20
		float sungAtEnd = LyricsRenderer.progress(10_000, 6_000, 10_000) * total;
		assertEquals(FULL, LyricsRenderer.wordGrowth(sungAtEnd, 15, 20, 1), 1e-5);
		float sungLater = LyricsRenderer.progress(10_400, 6_000, 10_000) * total;
		assertEquals(1, LyricsRenderer.wordGrowth(sungLater, 15, 20, 1), "back to normal 0.4 s after the line");
		assertEquals(2, LyricsRenderer.progress(60_000, 6_000, 10_000), "stops counting eventually");
		assertEquals(0, LyricsRenderer.progress(5_000, 6_000, 10_000));
	}

	@Test
	void dimLinesDontGrow() {
		assertEquals(1, LyricsRenderer.wordGrowth(12, 10, 15, 0));
		assertEquals(1 + LyricsRenderer.GROW / 2, LyricsRenderer.wordGrowth(12, 10, 15, 0.5f), 1e-5);
	}
}
