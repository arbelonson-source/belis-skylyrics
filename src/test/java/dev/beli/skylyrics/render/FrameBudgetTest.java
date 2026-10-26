package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FrameBudgetTest {
	private static final int UNCAPPED = 260;

	/** Runs the given seconds of frames at this frame rate. */
	private static void run(FrameBudget budget, float fps, float seconds, int limit) {
		for (float t = 0; t < seconds; t += 1 / fps) {
			budget.update(1 / fps, limit);
		}
	}

	@Test
	void smoothGameKeepsFullEffects() {
		FrameBudget budget = new FrameBudget();
		run(budget, 90, 30, UNCAPPED);
		assertFalse(budget.light());
		assertEquals(90, budget.fps(), 1);
	}

	@Test
	void aShortHitchIsIgnored() {
		FrameBudget budget = new FrameBudget();
		run(budget, 90, 5, UNCAPPED);
		run(budget, 20, 0.5f, UNCAPPED);
		run(budget, 90, 5, UNCAPPED);
		assertFalse(budget.light());
	}

	@Test
	void slowGameGoesLightThenComesBack() {
		FrameBudget budget = new FrameBudget();
		run(budget, 30, 3, UNCAPPED);
		assertTrue(budget.light());
		run(budget, 80, FrameBudget.FIRST_WAIT - 1, UNCAPPED);
		assertTrue(budget.light());
		run(budget, 80, 2, UNCAPPED);
		assertFalse(budget.light());
	}

	@Test
	void slowAgainSoonAfterWaitsLonger() {
		FrameBudget budget = new FrameBudget();
		run(budget, 30, 3, UNCAPPED);
		run(budget, 80, FrameBudget.FIRST_WAIT + 1, UNCAPPED);
		assertFalse(budget.light());
		run(budget, 30, 3, UNCAPPED); // the full effects were too much
		assertTrue(budget.light());
		run(budget, 80, FrameBudget.FIRST_WAIT + 1, UNCAPPED);
		assertTrue(budget.light(), "waits twice as long the second time");
		run(budget, 80, FrameBudget.FIRST_WAIT, UNCAPPED);
		assertFalse(budget.light());
	}

	@Test
	void aCappedGameIsNotSlow() {
		FrameBudget budget = new FrameBudget();
		run(budget, 30, 10, 30); // the player set 30 frames a second, or the game throttles in the background
		assertFalse(budget.light());
	}
}
