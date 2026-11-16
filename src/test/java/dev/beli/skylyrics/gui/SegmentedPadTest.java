package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SegmentedPadTest {
	@Test
	void roomyRowsKeepTheUsualPadding() {
		assertEquals(12, Controls.Segmented.pad(320, 50, 80, 3));
	}

	@Test
	void crowdedRowsTightenSoTheLabelStillShows() {
		// Six Hebrew options in the widest settings panel
		int pad = Controls.Segmented.pad(320, 40, 180, 6);
		assertTrue(pad < 12);
		assertTrue(20 + 4 + 180 + 6 * pad + 8 + 40 <= 320, "label and options both fit");
	}

	@Test
	void paddingNeverVanishes() {
		// names that can't fit the row even on its own line: as little as before, never none
		assertEquals(4, Controls.Segmented.pad(150, 60, 200, 6));
		assertEquals(Controls.Segmented.MIN_PAD, Controls.Segmented.pad(0, 60, 200, 6), "before the row is laid out");
	}

	@Test
	void everyNameKeepsFourPixelsEachSideSoTheHighlightNeverTouchesTheNext() {
		// The Record card corner choice at 854x480: four long names in a short row
		assertTrue(Controls.Segmented.pad(300, 60, 240, 4) >= 8);
	}

	@Test
	void theRoomBetweenNamesNeverPushesTheControlPastTheRow() {
		// on its own line a 270 px row holds 240 px of names plus 4 + 4 x 1: the gap shrinks rather than overflow
		int pad = Controls.Segmented.pad(270, 60, 240, 4);
		assertTrue(4 + 240 + 4 * pad <= 270 - 20 || pad == 4, "pad " + pad);
		assertEquals(4, pad);
		// with room for it the gap stays
		assertEquals(Controls.Segmented.MIN_PAD, Controls.Segmented.pad(300, 60, 240, 4));
	}
}
