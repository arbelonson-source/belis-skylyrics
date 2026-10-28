package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CometSkyTest {
	private static int sum(int rgb) {
		return (rgb >> 16 & 255) + (rgb >> 8 & 255) + (rgb & 255);
	}

	@Test
	void headIsBrightestInTheMiddleAndFadesToNothing() {
		int last = sum(CometSky.head(0));
		for (float r = 0.1f; r <= 1.001f; r += 0.1f) {
			int now = sum(CometSky.head(r));
			assertTrue(now <= last, "brighter going out at " + r);
			last = now;
		}
		assertEquals(0, CometSky.head(1));
	}

	@Test
	void tailsHaveNoHardEdges() {
		for (float s = 0; s <= 1.001f; s += 0.1f) {
			assertEquals(0, CometSky.tail(s, 1), 1e-3f, "edge at " + s);
			assertEquals(0, CometSky.tail(s, -1), 1e-3f, "edge at " + s);
			assertTrue(CometSky.tail(s, 0) >= CometSky.tail(s, 0.5f), "middle dimmer at " + s);
		}
		assertEquals(0, CometSky.tail(1, 0), 1e-3f, "the tip must fade out");
		assertTrue(CometSky.tail(0.2f, 0) > CometSky.tail(0.8f, 0));
	}

	@Test
	void streamersRunOutwardWithTime() {
		// What is at one spot now is further out along the tail a little later
		float later = 1 / CometSky.DRIFT * 0.1f;
		for (float w = -0.8f; w <= 0.8f; w += 0.4f) {
			assertEquals(CometSky.streak(0.3f, w, 0), CometSky.streak(0.4f, w, later), 1e-4f);
		}
	}
}
