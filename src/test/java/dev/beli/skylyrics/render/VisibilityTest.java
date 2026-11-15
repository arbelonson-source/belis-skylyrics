package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VisibilityTest {
	@Test
	void openSkyIsNeverACave() {
		assertFalse(Visibility.underground(false, 15));
		assertFalse(Visibility.underground(true, 15));
	}

	@Test
	void darkSkyLightIsACave() {
		assertTrue(Visibility.underground(false, 0));
		assertTrue(Visibility.underground(false, Visibility.HIDE_AT));
	}

	@Test
	void edgeOfACaveDoesNotFlicker() {
		int between = (Visibility.HIDE_AT + Visibility.SHOW_AT) / 2;
		assertTrue(between > Visibility.HIDE_AT && between < Visibility.SHOW_AT);
		assertFalse(Visibility.underground(false, between));
		assertTrue(Visibility.underground(true, between));
		assertFalse(Visibility.underground(true, Visibility.SHOW_AT));
	}

	@Test
	void theNetherCountsAsUndergroundButTheEndDoesNot() {
		assertTrue(Visibility.canBeUnderground(true, false));
		// Nether: no sky light, bedrock ceiling
		assertTrue(Visibility.canBeUnderground(false, true));
		// End: no sky light, no ceiling
		assertFalse(Visibility.canBeUnderground(false, false));
	}

	@Test
	void divingDeepIsNotACave() {
		assertFalse(Visibility.underground(false, 2, true));
		assertFalse(Visibility.underground(true, 2, true));
		assertTrue(Visibility.underground(false, 2, false));
	}
}
