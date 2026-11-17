package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ButtonFitTest {
	@Test
	void aLongLabelGetsAButtonWiderThanItself() {
		assertTrue(Controls.buttonW(147, 100) >= 147 + Controls.BUTTON_PAD);
	}

	@Test
	void englishWidthsKeepTheirMinimum() {
		assertEquals(100, Controls.buttonW(60, 100));
		assertEquals(56, Controls.buttonW(24, 56));
		assertEquals(80, Controls.buttonW(50, 80));
	}

	@Test
	void twoButtonsThatFitSitSideBySideCentred() {
		int[] l = Controls.signInLayout(150, 50, 100, 100, 300);
		assertEquals(l[1], l[4]);
		assertEquals(150 - 104, l[0]);
		assertEquals(l[0] + 108, l[3]);
		assertEquals(80, l[6]);
	}

	@Test
	void twoButtonsThatDoNotFitStack() {
		int[] l = Controls.signInLayout(150, 50, 119, 161, 300);
		assertTrue(l[4] > l[1] + 18, "second button below the first");
		assertTrue(l[6] > 80, "card grows");
		assertArrayEquals(new int[] {150 - 119 / 2, 150 - 161 / 2}, new int[] {l[0], l[3]});
	}
}
