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
		assertEquals(4, Controls.Segmented.pad(150, 60, 200, 6));
		assertEquals(4, Controls.Segmented.pad(0, 60, 200, 6), "before the row is laid out");
	}
}
