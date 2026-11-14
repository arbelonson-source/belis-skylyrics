package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DimContrastTest {
	private static double lin(int c8) {
		double c = c8 / 255.0;
		return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
	}

	private static double lum(int rgb) {
		return 0.2126 * lin(rgb >> 16 & 255) + 0.7152 * lin(rgb >> 8 & 255) + 0.0722 * lin(rgb & 255);
	}

	private static double contrast(int a, int b) {
		double x = lum(a), y = lum(b);
		return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
	}

	@Test
	void dimTextReadsOnEveryPanelAndCardColour() {
		for (int bg : new int[] {Ui.SIDEBAR, Ui.PANEL, Ui.CARD, Ui.CARD_HOVER}) {
			double c = contrast(Ui.DIM, bg);
			assertTrue(c >= 4.5, String.format("DIM on %06X is %.2f:1", bg & 0xFFFFFF, c));
		}
	}

	@Test
	void dimStaysBelowMutedSoTheTwoLevelsStillDiffer() {
		assertTrue(lum(Ui.DIM) < lum(Ui.MUTED));
	}
}
