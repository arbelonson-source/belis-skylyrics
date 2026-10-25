package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EclipseSkyTest {
	private static int sum(int rgb) {
		return (rgb >> 16 & 255) + (rgb >> 8 & 255) + (rgb & 255);
	}

	@Test
	void discStaysDark() {
		assertEquals(0, EclipseSky.light(0, 0));
		assertEquals(0, EclipseSky.light(0.5f, -0.6f));
	}

	@Test
	void coronaFadesOutwards() {
		int rim = sum(EclipseSky.light(0, 1.05f));
		assertTrue(rim > sum(EclipseSky.light(0, 2)));
		assertTrue(sum(EclipseSky.light(0, 2)) > sum(EclipseSky.light(0, 3)));
		assertEquals(0, EclipseSky.light(0, 8));
		assertEquals(0, EclipseSky.light(-8, 0));
	}

	@Test
	void flareIsLopsided() {
		// The left arm reaches further and is brighter than the right one
		assertTrue(sum(EclipseSky.light(-3, 0.2f)) > sum(EclipseSky.light(3, -0.2f)));
		assertTrue(sum(EclipseSky.light(-5.5f, 0.4f)) > 0);
		assertTrue(sum(EclipseSky.light(-3, 0)) > sum(EclipseSky.light(0, 3)));
	}

	@Test
	void milkyWayIsBrightestAlongItsMiddle() {
		float mid = 0, edge = 0;
		for (int a = 0; a < 360; a += 5) {
			mid += EclipseSky.band(a, 0);
			edge += EclipseSky.band(a, 15);
			assertTrue(EclipseSky.band(a, 0) >= 0);
		}
		assertTrue(mid > 4 * edge);
	}

	@Test
	void milkyWayJoinsUpAfterAFullTurn() {
		for (float across = -10; across <= 10; across += 5) {
			assertEquals(EclipseSky.band(0, across), EclipseSky.band(360, across), 1e-4f);
		}
	}
}
