package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GlowCopiesTest {
	@Test
	void strongGlowKeepsBothFullRings() {
		assertEquals(6, LyricsRenderer.glowCopies(1, 1f));
		assertEquals(4, LyricsRenderer.glowCopies(2, 1f));
	}

	@Test
	void faintGlowDrawsFewerCopies() {
		assertEquals(4, LyricsRenderer.glowCopies(1, 0.15f));
		assertEquals(0, LyricsRenderer.glowCopies(2, 0.15f));
	}

	@Test
	void copiesNeverGrowAsTheGlowFades() {
		int last = Integer.MAX_VALUE;
		for (float s = 1f; s >= 0; s -= 0.05f) {
			int total = LyricsRenderer.glowCopies(1, s) + LyricsRenderer.glowCopies(2, s);
			assertTrue(total <= last);
			last = total;
		}
	}
}
