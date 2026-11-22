package dev.beli.skylyrics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClosedByKeyTest {
	@Test
	void holdingTheKeyThatClosedTheScreenDoesNotOpenItAgain() {
		ClosedByKey k = new ClosedByKey();
		assertTrue(k.opens(1, true));
		// J pressed again in the settings closes them, and is held on: once no screen is open the game turns its repeats into clicks
		k.closed();
		assertFalse(k.opens(0, true));
		assertFalse(k.opens(1, true));
		assertFalse(k.opens(2, true));
		// Let go (a repeat may still have come in before), then pressed again: that opens it
		assertFalse(k.opens(1, false));
		assertTrue(k.opens(1, true));
	}

	@Test
	void aQuickTapThatClosedTheScreenLeavesTheNextPressAlone() {
		ClosedByKey k = new ClosedByKey();
		k.closed();
		assertFalse(k.opens(0, false));
		assertFalse(k.opens(0, false));
		assertTrue(k.opens(1, false));
	}

	@Test
	void withoutAClosePressesOpenAsAlways() {
		ClosedByKey k = new ClosedByKey();
		assertFalse(k.opens(0, false));
		assertTrue(k.opens(1, false));
		assertTrue(k.opens(1, true));
	}

	@Test
	void anUnboundKeyIsNotAskedAbout() {
		// Unbound is -1 on 1.21.1 to 26.2 (GLFW's unknown key): asking GLFW about it logs three ERROR lines every tick
		assertFalse(ClosedByKey.asksKeyboard(true, -1));
		assertTrue(ClosedByKey.asksKeyboard(true, com.mojang.blaze3d.platform.InputConstants.KEY_J));
		assertTrue(ClosedByKey.asksKeyboard(true, com.mojang.blaze3d.platform.InputConstants.KEY_N));
		// A mouse button is not on the keyboard
		assertFalse(ClosedByKey.asksKeyboard(false, 0));
	}
}
