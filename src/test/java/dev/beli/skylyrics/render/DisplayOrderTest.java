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
		// Lam-alef is one drawn letter but two letters of the line, so row two starts after row one's seven letters
		// and the space that was dropped where the line broke
		java.util.List<String> rows = java.util.List.of("سلام لا", "كلام");
		int[] starts = LyricsRenderer.rowStarts("سلام لا كلام", rows);
		assertEquals(0, starts[0]);
		assertEquals(8, starts[1]);
		assertTrue(LyricsRenderer.Display.of(rows.get(0)).cps().length < 7);
	}

	@Test
	void rowsAfterABreakCountTheSpaceThatWasDropped() {
		assertArrayEquals(new int[] {0, 3, 6}, LyricsRenderer.rowStarts("aa bb cc", java.util.List.of("aa", "bb", "cc")));
		assertArrayEquals(new int[] {0, 6}, LyricsRenderer.rowStarts("aa bb cc", java.util.List.of("aa bb", "cc")));
	}

	@Test
	void aCutInsideAWordAddsNoSpace() {
		assertArrayEquals(new int[] {0, 3}, LyricsRenderer.rowStarts("abcdef", java.util.List.of("abc", "def")));
	}

	@Test
	void collapsedAndUnwrappedSpacesStayInStep() {
		assertArrayEquals(new int[] {0, 4}, LyricsRenderer.rowStarts("aa  bb", java.util.List.of("aa", "bb")));
		assertArrayEquals(new int[] {0}, LyricsRenderer.rowStarts("aa  bb", java.util.List.of("aa  bb")));
	}
	private static boolean hasMark(int[] cps) {
		for (int cp : cps) {
			if (LyricsRenderer.Display.directionMark(cp)) {
				return true;
			}
		}
		return false;
	}

	@Test
	void marksSteerTheOrderButAreNotDrawn() {
		// An RLM at the start makes an English-first line right to left: the Hebrew ends up on the left, and no box is drawn
		LyricsRenderer.Display d = LyricsRenderer.Display.of("\u200Fhello \u05E9\u05DC\u05D5\u05DD");
		assertEquals("\u05DD\u05D5\u05DC\u05E9 hello", new String(d.cps(), 0, d.cps().length));
		// Letters still count the raw text: the RLM is letter 0
		assertArrayEquals(new int[] {10, 9, 8, 7, 6, 1, 2, 3, 4, 5}, d.logical());
		// Without the mark the same line reads left to right
		assertEquals("hello \u05DD\u05D5\u05DC\u05E9", visual("hello \u05E9\u05DC\u05D5\u05DD"));
	}

	@Test
	void leftToRightMarkOnlyLineHasNoMarkInIt() {
		// requiresBidi is false for these, so the early return has to drop them too
		for (String mark : new String[] {"\u200E", "\u202A", "\u202D", "\u2066", "\u2067", "\u2068"}) {
			LyricsRenderer.Display d = LyricsRenderer.Display.of(mark + "hi" + mark);
			assertEquals("hi", new String(d.cps(), 0, d.cps().length), mark);
			assertArrayEquals(new int[] {1, 2}, d.logical(), mark);
		}
	}

	@Test
	void embeddingsAndIsolatesAreNotDrawnAndKeepTheirIndices() {
		String row = "\u202B\u05D0\u05D1\u202C x \u2067\u05D2\u2069";
		LyricsRenderer.Display d = LyricsRenderer.Display.of(row);
		assertTrue(!hasMark(d.cps()));
		for (int i : d.logical()) {
			assertTrue(!LyricsRenderer.Display.directionMark(row.charAt(i)));
		}
		assertEquals(row.length() - 4, d.cps().length);
	}

	@Test
	void arabicLetterMarkInsideAWordStillJoinsLikeTheUnmarkedWord() {
		String plain = visual("\u0645\u0631\u062D\u0628\u0627");
		for (String mark : new String[] {"\u061C", "\u200F"}) {
			String marked = mark + "\u0645\u0631" + mark + "\u062D\u0628\u0627" + mark;
			LyricsRenderer.Display d = LyricsRenderer.Display.of(marked);
			assertTrue(!hasMark(d.cps()));
			assertEquals(plain, new String(d.cps(), 0, d.cps().length));
		}
		// An LRM splits the word into two runs in a left-to-right line, but the letters are still the joined forms
		LyricsRenderer.Display lrm = LyricsRenderer.Display.of("\u0645\u0631\u200E\u062D\u0628\u0627");
		assertTrue(!hasMark(lrm.cps()));
		assertEquals(plain.codePoints().sorted().boxed().toList(), java.util.Arrays.stream(lrm.cps()).sorted().boxed().toList());
	}

	@Test
	void aRowOfOnlyMarksIsEmptyAndSafe() {
		LyricsRenderer.Display d = LyricsRenderer.Display.of("\u200F\u200E");
		assertEquals(0, d.cps().length);
		assertEquals(0, d.logical().length);
		assertEquals(0, LyricsRenderer.Display.of("\u200F", true).cps().length);
	}

	@Test
	void endsWithPauseSkipsTrailingMarks() {
		assertTrue(LyricsRenderer.endsWithPause("\u05E9\u05DC\u05D5\u05DD,\u200F"));
		assertTrue(LyricsRenderer.endsWithPause("word.\u200E\u200F"));
		assertEquals(false, LyricsRenderer.endsWithPause("word\u200F"));
		assertEquals(false, LyricsRenderer.endsWithPause("\u200F"));
	}
}
