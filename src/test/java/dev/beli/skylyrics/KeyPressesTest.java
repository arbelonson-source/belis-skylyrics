package dev.beli.skylyrics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KeyPressesTest {
	@Test
	void aHeldKeyActsOnceNotOncePerRepeat() {
		KeyPresses k = new KeyPresses();
		assertTrue(k.fires(1, true));
		assertFalse(k.fires(2, true));
		assertFalse(k.fires(1, true));
	}

	@Test
	void manyClicksInOneTickActOnce() {
		KeyPresses k = new KeyPresses();
		assertTrue(k.fires(3, true));
	}

	@Test
	void aTapThatEndsBeforeTheTickStillActsAndAnotherPressActsAgain() {
		KeyPresses k = new KeyPresses();
		assertTrue(k.fires(1, false));
		assertFalse(k.fires(0, false));
		assertTrue(k.fires(1, true));
		assertFalse(k.fires(0, false));
		assertTrue(k.fires(1, false));
	}
}
