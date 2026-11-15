package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

	@Test
	void arabicLettersJoinUp() {
		// "مرحبا" (hello): each letter in its joined form, drawn right to left
		assertEquals("\uFE8E\uFE92\uFEA3\uFEAE\uFEE3", visual("مرحبا"));
	}

	@Test
	void lamAlefBecomesOneLetter() {
		// "سلام" (peace): lam and alef share one letter, which keeps the lam's place for the highlight
		LyricsRenderer.Display d = LyricsRenderer.Display.of("سلام");
		assertEquals("\uFEE1\uFEFC\uFEB3", visual("سلام"));
		assertArrayEquals(new int[] {3, 1, 0}, d.logical());
	}

	@Test
	void vowelMarksAreLeftOutButLettersStillJoin() {
		// The fatha on the meem would only leave a gap, and the meem still joins the ra after it
		assertEquals(visual("مرحبا"), visual("مَرحبا"));
		assertArrayEquals(new int[] {5, 4, 3, 2, 0}, LyricsRenderer.Display.of("مَرحبا").logical());
	}

	private static String visual(String row, boolean rtl) {
		LyricsRenderer.Display d = LyricsRenderer.Display.of(row, rtl);
		return new String(d.cps(), 0, d.cps().length);
	}

	@Test
	void punctuationStaysAtTheRightEndOfAHebrewWordInARightToLeftLine() {
		// A comma after the word is on its left as drawn (reading left to right), a "!" before it is at the far right
		assertEquals(",םולש", visual("שלום,", true));
		assertEquals("םולש!", visual("!שלום", true));
	}

	@Test
	void aNumberAfterHebrewWordsStaysOnTheLeftInARightToLeftLine() {
		assertEquals("5 ינא", visual("אני 5", true));
	}

	@Test
	void aPieceStartingWithLatinLettersKeepsTheRightToLeftLineBase() {
		// "hello" first would make the piece read left to right on its own; in a Hebrew line it sits at the right
		assertEquals("םולש hello", visual("hello שלום", true));
		assertEquals(",םולש hello", visual("hello שלום,", true));
	}

	@Test
	void theCommaOfAHebrewPieceSitsAtTheRightEndInALeftToRightLine() {
		// In "hello שלום," the comma follows the word, so as drawn it is the rightmost character
		assertEquals("םולש,", visual("שלום,", false));
		assertEquals("םולש!", visual("שלום!", false));
	}

	@Test
	void anEnglishPieceInALeftToRightLineIsUnchanged() {
		assertEquals("hello", visual("hello", false));
		assertEquals(visual("hello sky"), visual("hello sky", false));
		assertArrayEquals(LyricsRenderer.Display.of("hello, sky").logical(), LyricsRenderer.Display.of("hello, sky", false).logical());
	}

	@Test
	void aPureHebrewPieceInARightToLeftLineIsUnchanged() {
		assertEquals("םולש", visual("שלום", true));
		assertEquals(visual("שלום"), visual("שלום", true));
		assertArrayEquals(LyricsRenderer.Display.of("שלום").logical(), LyricsRenderer.Display.of("שלום", true).logical());
	}

	@Test
	void theDefaultOfAWholeLineStillFollowsItsFirstLetter() {
		// No known direction: a bare Hebrew piece reads right to left, so its comma lands on the left
		assertEquals(",םולש", visual("שלום,"));
	}

	@Test
	void aLeftToRightAndADefaultRowDoNotShareACacheEntry() {
		assertEquals(",םולש", visual("שלום,"));
		assertEquals("םולש,", visual("שלום,", false));
		assertEquals(",םולש", visual("שלום,"));
	}

	@Test
	void wrappedArabicRowsStartWhereTheTextDoes() {
		// Lam-alef is one drawn letter but two letters of the line, so row two starts after row one's two letters
		java.util.List<String> rows = java.util.List.of("سلام لا", "كلام");
		int[] starts = LyricsRenderer.rowStarts(rows);
		assertEquals(0, starts[0]);
		assertEquals(7, starts[1]);
		assertTrue(LyricsRenderer.Display.of(rows.get(0)).cps().length < 7);
	}
}
