package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RoundRectTest {
	@Test
	void discIsSolidInsideAndEmptyInTheCorner() {
		assertEquals(1f, RoundRect.coverage(32, 32, 64));
		assertEquals(0f, RoundRect.coverage(0, 0, 64));
	}

	@Test
	void edgePixelsArePartlyCovered() {
		// On the diagonal the curve passes through pixel (9, 9) of a 64-wide disc
		float edge = RoundRect.coverage(9, 9, 64);
		assertTrue(edge > 0 && edge < 1, "edge " + edge);
	}

	@Test
	void picksTheSmallestCornerThatIsBigEnough() {
		assertEquals(0, RoundRect.sizeIndex(1));
		assertEquals(0, RoundRect.sizeIndex(4));
		assertEquals(1, RoundRect.sizeIndex(5));
		assertEquals(4, RoundRect.sizeIndex(33));
		assertEquals(5, RoundRect.sizeIndex(500));
	}
}
