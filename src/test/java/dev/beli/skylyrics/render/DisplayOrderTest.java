package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DisplayOrderTest {
	private static String visual(String row) {
		LyricsRenderer.Display d = LyricsRenderer.Display.of(row);
		return new String(d.cps(), 0, d.cps().length);
	}

	@Test
	void leftToRightStaysAsIs() {
		LyricsRenderer.Display d = LyricsRenderer.Display.of("hello sky");
		assertEquals("hello sky", new String(d.cps(), 0, d.cps().length));
		assertArrayEquals(new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8}, d.logical());
	}

	@Test
	void hebrewIsReversedForDrawing() {
		// "שיר" (song): drawn left to right as ר י ש
		assertEquals("ריש", visual("שיר"));
		assertArrayEquals(new int[] {2, 1, 0}, LyricsRenderer.Display.of("שיר").logical());
	}

	@Test
	void hebrewWordsKeepTheirOrderRightToLeft() {
		// "אור שמיים" (light of the sky): the first word ends up on the right
		assertEquals("םיימש רוא", visual("אור שמיים"));
	}

	@Test
	void numbersInsideHebrewStayReadable() {
		assertEquals("12 רוא", visual("אור 12"));
	}

	@Test
	void bracketsAreMirroredInHebrew() {
		assertEquals("(רוא)", visual("(אור)"));
	}
}
