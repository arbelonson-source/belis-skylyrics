package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The carousel's steps from the mouse wheel, a smooth wheel and a trackpad. */
class ScrollStepsTest {
	private static int moved(ScrollSteps steps, double amount, int times) {
		int moved = 0;
		for (int i = 0; i < times; i++) {
			moved += steps.step(amount);
		}
		return moved;
	}

	@Test
	void aTrackpadsSmallAmountsAddUpToOneCoverANotch() {
		// A Mac trackpad or a smooth wheel sends many small amounts for one swipe: 40 of an eighth are five notches'
		// worth, so five covers, not forty (the carousel raced through the whole list on a small swipe)
		ScrollSteps steps = new ScrollSteps();
		assertEquals(5, moved(steps, -0.125, 40), "down goes on");
		assertEquals(-5, moved(steps, 0.125, 40), "up goes back");
	}

	@Test
	void aWheelNotchStillMovesOneCover() {
		ScrollSteps steps = new ScrollSteps();
		assertEquals(1, steps.step(-1));
		assertEquals(-1, steps.step(1));
		// a high Scroll Sensitivity makes a notch 3 or more, still one cover a notch (as the hotbar's slots)
		assertEquals(1, steps.step(-3));
		assertEquals(-1, steps.step(4.5));
		assertEquals(0, steps.step(0), "no scroll this way");
	}

	@Test
	void turningBackDoesNotUseWhatWasLeftTheOtherWay() {
		ScrollSteps steps = new ScrollSteps();
		assertEquals(0, moved(steps, -0.25, 3), "three quarters of a notch on is not a cover yet");
		// a quarter back right after is a quarter back, not the rest of a notch on
		assertEquals(0, steps.step(0.25));
		assertEquals(-1, moved(steps, 0.25, 3));
	}
}
