package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConstellationsTest {
	@Test
	void linesJoinStarsThatExist() {
		for (Constellations.Pattern p : Constellations.PATTERNS) {
			int stars = p.stars().length / 2;
			assertEquals(0, p.stars().length % 2);
			assertEquals(0, p.lines().length % 2);
			for (int i : p.lines()) {
				assertTrue(i >= 0 && i < stars);
			}
			for (int k = 0; k < p.lines().length; k += 2) {
				assertTrue(p.lines()[k] != p.lines()[k + 1]);
			}
		}
	}

	@Test
	void hasTheThreeShapes() {
		boolean five = false, six = false, four = false;
		for (Constellations.Pattern p : Constellations.PATTERNS) {
			int n = p.stars().length / 2;
			five |= n == 5;
			six |= n == 6;
			four |= n == 4;
			assertTrue(p.elev() > 10 && p.elev() < 70);
		}
		assertTrue(five && six && four);
	}
}
