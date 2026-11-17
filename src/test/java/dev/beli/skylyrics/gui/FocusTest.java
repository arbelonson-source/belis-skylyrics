package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FocusTest {
	// stops: 0 yes, 1 hidden, 2 yes, 3 hidden, 4 yes
	private static final boolean[] MIXED = {true, false, true, false, true};

	@Test
	void forwardSkipsHiddenRows() {
		assertEquals(2, Focus.next(MIXED, 0, 1));
		assertEquals(4, Focus.next(MIXED, 2, 1));
	}

	@Test
	void backSkipsHiddenRows() {
		assertEquals(2, Focus.next(MIXED, 4, -1));
		assertEquals(0, Focus.next(MIXED, 2, -1));
	}

	@Test
	void wrapsAroundBothWays() {
		assertEquals(0, Focus.next(MIXED, 4, 1));
		assertEquals(4, Focus.next(MIXED, 0, -1));
	}

	@Test
	void fromNothingLandsOnTheFirstOrLastShownRow() {
		boolean[] lead = {false, true, false, true, false};
		assertEquals(1, Focus.next(lead, -1, 1));
		assertEquals(3, Focus.next(lead, -1, -1));
	}

	@Test
	void aLoneRowStaysFocusedAndNoRowsGivesNothing() {
		assertEquals(2, Focus.next(new boolean[] {false, false, true}, 2, 1));
		assertEquals(2, Focus.next(new boolean[] {false, false, true}, 2, -1));
		assertEquals(-1, Focus.next(new boolean[] {false, false}, 0, 1));
		assertEquals(-1, Focus.next(new boolean[0], -1, 1));
	}

	@Test
	void aRowStaysWhereItIsWhenAlreadyInView() {
		assertEquals(50f, Focus.scrollIntoView(50, 80, 104, 100, 10, 400));
	}

	@Test
	void aRowAboveTheViewScrollsUpToItWithItsMargin() {
		assertEquals(70f, Focus.scrollIntoView(120, 80, 104, 100, 10, 400));
	}

	@Test
	void aRowBelowTheViewScrollsDownJustEnough() {
		assertEquals(54f, Focus.scrollIntoView(0, 120, 144, 100, 10, 400));
	}

	@Test
	void scrollStaysBetweenTheTopAndTheEnd() {
		assertEquals(0f, Focus.scrollIntoView(30, 4, 28, 100, 10, 400));
		assertEquals(400f, Focus.scrollIntoView(300, 480, 504, 100, 10, 400));
	}

	@Test
	void aRowTallerThanTheViewShowsItsTop() {
		assertEquals(190f, Focus.scrollIntoView(0, 200, 400, 100, 10, 400));
	}

	@Test
	void pagesMoveByAScreenLessALineAndStayInRange() {
		assertEquals(76f, Focus.page(0, 1, 100, 500));
		assertEquals(24f, Focus.page(100, -1, 100, 500));
		assertEquals(0f, Focus.page(10, -1, 100, 500));
		assertEquals(500f, Focus.page(480, 1, 100, 500));
		assertEquals(0f, Focus.page(0, 1, 100, 0));
	}

	@Test
	void withNothingChosenAndTheMouseStillTheKeyboardStartsAtTheFirstRow() {
		assertEquals(Focus.KeyTarget.FOCUSED, Focus.keyTarget(true, false));
		assertEquals(Focus.KeyTarget.FOCUSED, Focus.keyTarget(true, true));
		assertEquals(Focus.KeyTarget.FIRST_ROW, Focus.keyTarget(false, false));
		assertEquals(Focus.KeyTarget.MOUSE_ROW, Focus.keyTarget(false, true));
	}

	@Test
	void aPixelOfMouseJitterIsNotMovement() {
		assertEquals(false, Focus.mouseMoved(100, 100, 101, 100));
		assertEquals(true, Focus.mouseMoved(100, 100, 100, 103));
		assertEquals(true, Focus.mouseMoved(100, 100, 90, 100));
		// exactly 2 px is already movement, just under it is not, on either axis and either way
		assertEquals(true, Focus.mouseMoved(100, 100, 102, 100));
		assertEquals(true, Focus.mouseMoved(100, 100, 98, 100));
		assertEquals(true, Focus.mouseMoved(100, 100, 100, 102));
		assertEquals(true, Focus.mouseMoved(100, 100, 100, 98));
		assertEquals(false, Focus.mouseMoved(100, 100, 101.9, 98.1));
	}

	@Test
	void aKeyOnAHoveredRowDoesNotPinTheKeyboardThere() {
		// Hover A and press a key, then hover B and press one: no focus was taken, so B still gets the key
		assertEquals(Focus.KeyTarget.MOUSE_ROW, Focus.keyTarget(false, true));
		assertEquals(Focus.KeyTarget.MOUSE_ROW, Focus.keyTarget(false, true));
	}
}
