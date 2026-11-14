package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RowStackTest {
	@Test
	void roomyRowsKeepTheControlBesideTheLabel() {
		// A normal 440px window: the view is about 270 wide, the swatches 184
		assertFalse(Controls.Row.stacks(270, 184, 90));
		assertFalse(Controls.Row.stacks(270, 22, 120));
	}

	@Test
	void aWideControlOnANarrowRowGoesUnderTheLabel() {
		// A 320px window: swatches would leave 150-28-184 < 0 for the label
		assertTrue(Controls.Row.stacks(150, 184, 70));
		assertTrue(Controls.Row.stacks(200, 150, 70), "only 22px left for the label");
	}

	@Test
	void aShortLabelOnlyNeedsItsOwnWidth() {
		assertFalse(Controls.Row.stacks(120, 60, 20), "'Font' needs 20px and gets 32");
	}

	@Test
	void notLaidOutYetNeverStacks() {
		assertFalse(Controls.Row.stacks(0, 184, 70));
		assertFalse(Controls.Row.stacks(150, 0, 70));
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
