package dev.beli.skylyrics.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BurstPlanTest {
	@Test
	void parsesNameCountAndInterval() {
		assertEquals(new BurstPlan.Burst("flash", 12, 50), BurstPlan.parse("flash 12 50"));
		assertEquals(new BurstPlan.Burst("a", 60, 0), BurstPlan.parse("  a   60   0 "));
	}

	@Test
	void rejectsBadCountsIntervalsAndNames() {
		assertThrows(IllegalArgumentException.class, () -> BurstPlan.parse("x 0 10"));
		assertThrows(IllegalArgumentException.class, () -> BurstPlan.parse("x 61 10"));
		assertThrows(IllegalArgumentException.class, () -> BurstPlan.parse("x 5 -1"));
		assertThrows(IllegalArgumentException.class, () -> BurstPlan.parse("x 5"));
		assertThrows(IllegalArgumentException.class, () -> BurstPlan.parse("x five 10"));
		assertThrows(IllegalArgumentException.class, () -> BurstPlan.parse(""));
		assertThrows(IllegalArgumentException.class, () -> BurstPlan.parse("../x 5 10"));
		assertThrows(IllegalArgumentException.class, () -> BurstPlan.parse("a/b 5 10"));
	}

	@Test
	void everyFrameWhenTheIntervalIsZero() {
		assertTrue(BurstPlan.due(0, 0, 0));
		assertTrue(BurstPlan.due(7, 1, 0));
	}

	@Test
	void theFirstFrameIsGrabbedAtOnce() {
		assertTrue(BurstPlan.due(0, 0, 100));
	}

	@Test
	void laterFramesWaitForTheirOwnTimeOnTheStartClock() {
		assertFalse(BurstPlan.due(1, 99_000_000L, 100));
		assertTrue(BurstPlan.due(1, 100_000_000L, 100));
		assertFalse(BurstPlan.due(3, 299_999_999L, 100));
		assertTrue(BurstPlan.due(3, 300_000_000L, 100));
	}

	@Test
	void filesAreNumberedWithTwoDigits() {
		assertEquals("flash_00", BurstPlan.fileName("flash", 0));
		assertEquals("flash_09", BurstPlan.fileName("flash", 9));
		assertEquals("flash_59", BurstPlan.fileName("flash", 59));
	}

	@Test
	void aNewLineIsASharpDropOfTheClock() {
		assertTrue(BurstPlan.lineChanged(3.2f, 0.02f));
		assertFalse(BurstPlan.lineChanged(3.2f, 3.25f), "clock still running");
		assertFalse(BurstPlan.lineChanged(Float.NaN, 0.02f), "first look has nothing to compare");
		assertFalse(BurstPlan.lineChanged(9f, 0.6f), "a first line settling is not a change");
	}

	@Test
	void waitingGivesUpAfterTenSeconds() {
		assertFalse(BurstPlan.gaveUp(9_999_999_999L));
		assertTrue(BurstPlan.gaveUp(10_000_000_000L));
	}
}
