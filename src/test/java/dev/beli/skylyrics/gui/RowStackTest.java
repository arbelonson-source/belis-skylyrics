package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RowStackTest {
	@Test
	void roomyRowsKeepTheControlBesideTheLabel() {
		// A normal 440px window: the view is about 270 wide, the swatches 184
		assertFalse(Controls.Row.stacks(270, 184, 90, 0));
		assertFalse(Controls.Row.stacks(270, 22, 120, 0));
	}

	@Test
	void aWideControlOnANarrowRowGoesUnderTheLabel() {
		// A 320px window: swatches would leave 150-28-184 < 0 for the label
		assertTrue(Controls.Row.stacks(150, 184, 70, 0));
		assertTrue(Controls.Row.stacks(200, 150, 70, 0), "only 22px left for the label");
	}

	@Test
	void aShortLabelOnlyNeedsItsOwnWidth() {
		assertFalse(Controls.Row.stacks(120, 60, 20, 0), "'Font' needs 20px and gets 32");
	}

	@Test
	void theColourRowGoesUnderItsNameWhenTheDescriptionWouldBeCut() {
		// Look tab, 440px panel: row 320 wide, five choices 259 wide leave 33px, the description needs about 130
		assertTrue(Controls.Row.stacks(320, 259, 32, 130));
	}

	@Test
	void aShortToggleRowStaysBesideItsText() {
		assertFalse(Controls.Row.stacks(320, 22, 90, 140), "a toggle leaves 270px");
		assertFalse(Controls.Row.stacks(184, 22, 60, 120), "even in the narrowest panel a toggle leaves 134px");
	}

	@Test
	void aDescriptionThatAlreadyFitsChangesNothing() {
		// Room 150 for a 130px description, and a description exactly filling the room
		assertFalse(Controls.Row.stacks(320, 142, 40, 130));
		assertFalse(Controls.Row.stacks(320, 192, 40, 100), "room 100 = description");
		// A short description is never asked for more than it has
		assertFalse(Controls.Row.stacks(320, 230, 30, 60), "room 62 for 60");
		// Without a description the old rule holds
		assertFalse(Controls.Row.stacks(200, 120, 70, 0), "52px left for the label");
	}

	@Test
	void notLaidOutYetNeverStacks() {
		assertFalse(Controls.Row.stacks(0, 184, 70, 0));
		assertFalse(Controls.Row.stacks(150, 0, 70, 0));
	}

	@Test
	void swatchesWrapOnlyWhenTheRowIsTooNarrowEvenStacked() {
		assertEquals(14, Controls.Swatches.perLine(270, 14, false));
		assertEquals(14, Controls.Swatches.perLine(270, 14, true), "184 fits in 250");
		int per = Controls.Swatches.perLine(150, 14, true);
		assertTrue(per < 14 && per >= 1);
		assertTrue(per * 17 - 5 + 2 <= 150 - 20, "a wrapped line fits the row");
	}

	@Test
	void swatchLinesCountMatchesWrapping() {
		assertEquals(1, Controls.Swatches.lines(270, 14, true));
		assertEquals(2, Controls.Swatches.lines(150, 14, true));
	}
}
