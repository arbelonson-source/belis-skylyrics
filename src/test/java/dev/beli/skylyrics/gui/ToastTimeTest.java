package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ToastTimeTest {
	@Test
	void theOptionScalesTheTime() {
		assertEquals(3f, ToastTime.shownFor(3, 1.0), 1e-6);
		assertEquals(6f, ToastTime.shownFor(3, 2.0), 1e-6);
		assertEquals(1.5f, ToastTime.shownFor(3, 0.5), 1e-6);
	}

	@Test
	void aBrokenValueStaysInMinecraftsRange() {
		assertEquals(30f, ToastTime.shownFor(3, 99), 1e-6);
		assertEquals(0f, ToastTime.shownFor(3, -4), 1e-6);
		assertEquals(3f, ToastTime.shownFor(3, Double.NaN), 1e-6);
	}
}
