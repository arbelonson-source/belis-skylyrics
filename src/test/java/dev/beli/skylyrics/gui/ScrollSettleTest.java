package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ScrollSettleTest {
	@Test
	void askingForTheEndLandsOnTheRealBottom() {
		// A freshly opened tab: the height is only known after its first layout
		assertEquals(Focus.maxScroll(600, 200), Focus.settleScroll(0, true, 600, 200));
		assertEquals(404f, Focus.settleScroll(0, true, 600, 200));
	}

	@Test
	void aKeptScrollIsOnlyClampedToTheTabsOwnHeight() {
		assertEquals(150f, Focus.settleScroll(150, false, 600, 200));
		assertEquals(404f, Focus.settleScroll(9999, false, 600, 200));
		assertEquals(0f, Focus.settleScroll(50, false, 100, 200), "a short tab has nothing to scroll");
	}
}
