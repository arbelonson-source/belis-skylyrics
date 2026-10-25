package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PulseTest {
	@Test
	void restsBeforeAndAfter() {
		assertEquals(0, LyricsRenderer.pulse(-0.1f));
		assertEquals(0, LyricsRenderer.pulse(0));
		assertEquals(0, LyricsRenderer.pulse(2));
	}

	@Test
	void swellsQuicklyThenFadesSlowly() {
		float peak = LyricsRenderer.pulse(0.07f);
		assertTrue(peak > 0.95f, "peak " + peak);
		assertTrue(LyricsRenderer.pulse(0.03f) < peak);
		float mid = LyricsRenderer.pulse(0.3f);
		assertTrue(mid > 0.1f && mid < peak, "mid " + mid);
		assertTrue(LyricsRenderer.pulse(0.6f) < mid);
	}
}
