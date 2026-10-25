package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DaylightTest {
	@Test
	void clearNoonIsFullDaylight() {
		assertEquals(1f, LyricsRenderer.daylight(0f, 0f), 1e-6);
	}

	@Test
	void nightHasNone() {
		assertEquals(0f, LyricsRenderer.daylight(0.5f, 0f), 1e-6);
		assertEquals(0f, LyricsRenderer.daylight(0.8f, 0f), 1e-6);
	}

	@Test
	void duskIsHalfway() {
		assertEquals(0.5f, LyricsRenderer.daylight(0.25f, 0f), 1e-6);
	}

	@Test
	void rainGreysTheSky() {
		assertEquals(0.5f, LyricsRenderer.daylight(0f, 1f), 1e-6);
	}
}
