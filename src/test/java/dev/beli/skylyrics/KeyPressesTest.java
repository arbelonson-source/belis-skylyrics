package dev.beli.skylyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

	/** Ticks of a key held down: the press, then the game's repeat clicks (about 1.5 a tick); the total times it acted. */
	private static int heldFor(KeyPresses k, int ticks) {
		int acted = k.acts(1, true);
		for (int t = 1; t < ticks; t++) {
			acted += k.acts(t % 2 == 0 ? 2 : 1, true);
		}
		return acted + k.acts(0, false);
	}

	@Test
	void holdingAToggleKeyFlipsItOnceNotOnEveryRepeat() {
		// Held past the repeat delay, the now-playing and lock keys flipped back and forth and landed anywhere
		for (String name : new String[] {"toggle", "now_playing", "lock_view", "play_pause", "next", "previous"}) {
			KeyPresses k = KeyPresses.of(name);
			assertEquals(1, heldFor(k, 20), name);
			assertEquals(1, heldFor(k, 3), name + ", pressed again");
		}
	}

	@Test
	void theTimingKeysStepOnEveryRepeatWhileHeld() {
		// 20 ticks held: the press and 28 repeats
		for (String name : new String[] {"lyrics_late", "lyrics_early"}) {
			assertEquals(29, heldFor(KeyPresses.of(name), 20), name);
		}
	}
}
