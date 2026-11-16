package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.Bidi;
import org.junit.jupiter.api.Test;

class BaseDirectionTest {
	private static final String HE = "מציג"; // מציג

	@Test
	void rightToLeftLanguageWithHebrewIsRightToLeft() {
		assertTrue(Ui.baseRtl(true, "Beli's SkyLyrics " + HE + "."));
		assertTrue(Ui.baseRtl(true, "J " + HE + " Esc"));
		assertTrue(Ui.baseRtl(true, HE));
	}

	@Test
	void rightToLeftLanguageWithOnlyLatinIsNotForced() {
		assertFalse(Ui.baseRtl(true, "Lyrics from LRCLIB, 3 songs."));
		assertFalse(Ui.baseRtl(true, "-10°"));
	}

	@Test
	void leftToRightLanguageFollowsTheFirstStrongLetter() {
		assertTrue(Ui.baseRtl(false, "1. " + HE + " Beli"));
		assertFalse(Ui.baseRtl(false, "Beli " + HE));
		assertFalse(Ui.baseRtl(false, "J א Esc"));
	}

	@Test
	void arabicCountsAsRightToLeft() {
		assertTrue(Ui.baseRtl(false, "مرحبا Beli"));
		assertTrue(Ui.baseRtl(true, "Beli مرحبا"));
	}

	@Test
	void emptyAndLetterlessTextIsLeftToRight() {
		assertFalse(Ui.baseRtl(true, ""));
		assertFalse(Ui.baseRtl(false, ""));
		assertFalse(Ui.baseRtl(true, "12 : 30 ."));
	}

	@Test
	void theMarkMatchesTheDirection() {
		assertEquals("‏abc", Ui.marked("abc", true));
		assertEquals("‎abc", Ui.marked("abc", false));
	}

	/** Visual left-to-right order of a line, like the reorder the game does (right-to-left runs reversed). */
	private static String visualOrder(String s) {
		Bidi bidi = new Bidi(s, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
		int n = bidi.getRunCount();
		byte[] levels = new byte[n];
		String[] runs = new String[n];
		for (int i = 0; i < n; i++) {
			levels[i] = (byte) bidi.getRunLevel(i);
			String run = s.substring(bidi.getRunStart(i), bidi.getRunLimit(i));
			runs[i] = (levels[i] & 1) == 1 ? new StringBuilder(run).reverse().toString() : run;
		}
		Bidi.reorderVisually(levels, 0, runs, 0, n);
		return String.join("", runs).replace("‎", "").replace("‏", "");
	}

	@Test
	void aLatinFirstLineInAHebrewParagraphIsLaidOutRightToLeft() {
		String line = "Beli's SkyLyrics " + HE + ".";
		String reversed = new StringBuilder(HE).reverse().toString();
		// Without a mark the line starts with a Latin letter and is read left to right: the period ends up at the wrong end
		assertEquals("Beli's SkyLyrics " + reversed + ".", visualOrder(line));
		// With the paragraph's mark the name is at the right end and the period at the left
		String marked = visualOrder(Ui.marked(line, Ui.baseRtl(true, line)));
		assertTrue(marked.startsWith("."), marked);
		assertTrue(marked.endsWith("Beli's SkyLyrics"), marked);
	}

	@Test
	void englishTextComesOutExactlyAsBefore() {
		for (String s : new String[] {"Works with Sodium, Iris and shader packs.", "J or Esc to close", "Bottom right", "", "-10°"}) {
			boolean rtl = Ui.baseRtl(false, s);
			assertFalse(rtl, s);
			assertEquals(visualOrder(s), visualOrder(Ui.marked(s, rtl)), s);
		}
	}
}
