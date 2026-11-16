package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DisplayMirrorTest {
	private static String shown(String row, boolean rtl) {
		int[] cps = LyricsRenderer.Display.of(row, rtl).cps();
		return new String(cps, 0, cps.length);
	}

	@Test
	void quotesAndComparisonsMirrorInARightToLeftRun() {
		// Reversed for drawing, and each mark turned to face the other way: the closing mark of the text is drawn on the left
		assertEquals("«םולש»", shown("«שלום»", true));
		assertEquals("‹םולש›", shown("‹שלום›", true));
		assertEquals("≤םולש≥", shown("≤שלום≥", true));
	}

	@Test
	void bracketsStillMirror() {
		assertEquals("(םולש)", shown("(שלום)", true));
		assertEquals("[םולש]", shown("[שלום]", true));
	}

	@Test
	void leftToRightTextKeepsItsMarks() {
		assertEquals("«hi»", shown("«hi»", false));
	}
}
