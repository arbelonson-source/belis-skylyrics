package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GlowCopiesTest {
	@Test
	void strongGlowKeepsBothFullRings() {
		assertEquals(6, LyricsRenderer.glowCopies(1, 1f, false));
		assertEquals(4, LyricsRenderer.glowCopies(2, 1f, false));
	}

	@Test
	void faintGlowDrawsFewerCopies() {
		assertEquals(4, LyricsRenderer.glowCopies(1, 0.15f, false));
		assertEquals(0, LyricsRenderer.glowCopies(2, 0.15f, false));
	}

	@Test
	void copiesNeverGrowAsTheGlowFades() {
		int last = Integer.MAX_VALUE;
		for (float s = 1f; s >= 0; s -= 0.05f) {
			int total = LyricsRenderer.glowCopies(1, s, false) + LyricsRenderer.glowCopies(2, s, false);
			assertTrue(total <= last);
			last = total;
		}
	}

	@Test
	void lightEffectsDrawOneSmallRing() {
		for (float s = 0; s <= 1; s += 0.25f) {
			assertTrue(LyricsRenderer.glowCopies(1, s, true) <= LyricsRenderer.glowCopies(1, s, false));
			assertEquals(0, LyricsRenderer.glowCopies(2, s, true));
		}
	}
}
