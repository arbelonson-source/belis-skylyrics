package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
	void fromNothingTheKeyboardStartsAtTheFirstRowOrTheLastStop() {
		// the Lyrics switch, Preview, three rows (the middle one hidden), Reset all, Done
		boolean[] ring = {true, true, true, false, true, true, true};
		assertEquals(2, Focus.step(ring, -1, 1, 2));
		assertEquals(6, Focus.step(ring, -1, -1, 2));
		assertEquals(4, Focus.step(ring, 2, 1, 2));
		assertEquals(0, Focus.step(ring, 6, 1, 2));
	}

	@Test
	void leavingTheSearchBoxLandsOnTheFirstResult() {
		// the search results' stops: the Lyrics switch, Preview, three results, Reset all, Done
		boolean[] ring = {true, true, true, true, true, true, true};
		int done = 6;
		// Tab to Done, Ctrl+F, type a word, then Enter (or Tab or Down): the first result, not round to the Lyrics switch
		assertEquals(2, Focus.step(ring, Focus.fromSearchBox(done, true), 1, 2));
		// the keyboard was on the second result, which is still found: the first result, not the third
		assertEquals(2, Focus.step(ring, Focus.fromSearchBox(3, true), 1, 2));
		// Shift+Tab or Up: the last stop, as from nothing
		assertEquals(6, Focus.step(ring, Focus.fromSearchBox(3, true), -1, 2));
		// nothing typed (the tab's own rows still show): Tab carries on from where the keyboard was
		assertEquals(4, Focus.step(ring, Focus.fromSearchBox(3, false), 1, 2));
	}

	@Test
	void backOnATabTheKeyboardSkipsARowHiddenMeanwhile() {
		// the Look tab's stops: the Lyrics switch, Preview, Pick a look, Depth (hidden since the layout left Arc), Glow,
		// Reset all, Done; true marks the rows that show
		boolean[] rows = {false, false, true, false, true, false, false};
		// the keyboard was on Depth when Ctrl+Tab left the tab: coming back it is on Glow, not on the hidden Depth
		assertEquals(4, Focus.backOnTab(rows, 3));
		// a row that still shows keeps it
		assertEquals(4, Focus.backOnTab(rows, 4));
		assertEquals(2, Focus.backOnTab(rows, 2));
		// the last rows hidden: round to the first row that shows, never Reset all or Done
		assertEquals(2, Focus.backOnTab(new boolean[] {false, false, true, true, false, false, false}, 4));
		// no row remembered, or none of the tab's rows shows: nowhere
		assertEquals(-1, Focus.backOnTab(rows, -1));
		assertEquals(-1, Focus.backOnTab(new boolean[] {false, false, false, false, false}, 2));
	}

	@Test
	void aRowHiddenUnderTheKeyboardHandsItToTheNextRowThatShows() {
		// The Music tab's Player card at its end: the Lyrics switch, Preview, Timing, This song (the keyboard is on it, and
		// the song just stopped), Reset all, Done. rows marks the rows that show, available every stop the keys reach.
		boolean[] rows = {false, false, true, false, false, false};
		boolean[] available = {true, true, true, false, true, true};
		// round to Timing, the next row that shows, not on to Reset all (where Enter would ask to reset)
		assertEquals(2, Focus.afterHidden(rows, available, 3));
		// a row that shows after it takes the keyboard: Demo song after This song
		assertEquals(4, Focus.afterHidden(new boolean[] {false, false, true, false, true, false, false},
				new boolean[] {true, true, true, false, true, true, true}, 3));
		// no row of the tab shows any more: Reset all, as before
		assertEquals(3, Focus.afterHidden(new boolean[] {false, false, false, false, false},
				new boolean[] {true, true, false, true, true}, 2));
	}

	@Test
	void aRowsPlaceCountsOnlyTheRowsThatShow() {
		// the Lyrics switch, Preview, Layout, Depth (hidden), Glow, Shadow, Reset all, Done
		boolean[] rows = {false, false, true, false, true, true, false, false};
		assertEquals(new Focus.Place(1, 3), Focus.place(rows, 2));
		// Glow is the second row that shows, not the third row of the tab
		assertEquals(new Focus.Place(2, 3), Focus.place(rows, 4));
		assertEquals(new Focus.Place(3, 3), Focus.place(rows, 5));
		// a hidden row, the buttons, nothing: no place
		assertNull(Focus.place(rows, 3));
		assertNull(Focus.place(rows, 0));
		assertNull(Focus.place(rows, 6));
		assertNull(Focus.place(rows, -1));
		// a lone row says no place, as a vanilla list of one doesn't
		assertNull(Focus.place(new boolean[] {false, false, true, false, false}, 2));
	}

	@Test
	void onlyOneThingLooksFocusedWhileTyping() {
		// keys moved the keyboard onto a row or a button: its ring shows
		assertTrue(Focus.ringShows(true, false));
		// Ctrl+F or a click puts the keyboard in the search box, which has its own outline: the row's ring goes
		assertFalse(Focus.ringShows(true, true));
		// after the mouse, no ring either way
		assertFalse(Focus.ringShows(false, false));
		assertFalse(Focus.ringShows(false, true));
	}
}
