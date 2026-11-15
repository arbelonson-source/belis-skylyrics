package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IntroFitTest {
	@Test
	void usualWindowKeepsTheUsualPanel() {
		assertEquals(300, IntroScreen.fit(427, 300));
		assertEquals(190, IntroScreen.fit(240, 190));
		assertEquals(100, IntroScreen.vy(100, 190));
		assertEquals(84, IntroScreen.bw(84, 300));
	}

	@Test
	void smallWindowShrinksThePanelInsideTheWindow() {
		// 854x480 at GUI scale 4 is 213x120
		assertEquals(112, IntroScreen.fit(120, 190));
		assertEquals(205, IntroScreen.fit(213, 300));
		int ph = IntroScreen.fit(120, 190);
		int py = Math.max(4, (120 - ph) / 2);
		int bottom = py + ph - 28;
		assertTrue(bottom + 20 <= 120, "buttons stay on screen");
		assertTrue(IntroScreen.vy(100, ph) < 100);
		assertTrue(IntroScreen.bw(120, 205) < 120);
	}
}
