package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.Bidi;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

class RtlMarksTest {
	private static final String MARKS = "\u200E\u200F\u061C\u202A\u202B\u202C\u202D\u202E\u2066\u2067\u2068\u2069";

	/** What a right-to-left game shows left to right, direction marks left out. */
	private static String shown(String logical) {
		Bidi bidi = new Bidi(logical, Bidi.DIRECTION_RIGHT_TO_LEFT);
		int n = bidi.getRunCount();
		byte[] levels = new byte[n];
		Integer[] runs = new Integer[n];
		for (int i = 0; i < n; i++) {
			levels[i] = (byte) bidi.getRunLevel(i);
			runs[i] = i;
		}
		Bidi.reorderVisually(levels, 0, runs, 0, n);
		StringBuilder out = new StringBuilder();
		for (int r : runs) {
			String run = logical.substring(bidi.getRunStart(r), bidi.getRunLimit(r));
			out.append((bidi.getRunLevel(r) & 1) == 1 ? new StringBuilder(run).reverse() : run);
		}
		return LyricsFont.stripMarks(out.toString());
	}

	@Test
	void everyMarkIsOneAndLettersAreNot() {
		MARKS.chars().forEach(c -> assertTrue(LyricsFont.isMark(c), Integer.toHexString(c)));
		for (char c : "a-0:\u05D0\u0627\u200D\u2060 ".toCharArray()) {
			assertFalse(LyricsFont.isMark(c), Integer.toHexString(c));
		}
	}

	@Test
	void aMarkedStringMeasuresLikeThePlainOne() {
		StringSplitter splitter = new StringSplitter((cp, style) -> cp == 'i' ? 2 : 5);
		Style s = Style.EMPTY;
		for (String plain : new String[] {"hello", "\u05E9\u05DC\u05D5\u05DD", "-2:35"}) {
			String marked = "\u200F" + plain.substring(0, 2) + "\u200E\u061C" + plain.substring(2) + "\u2067\u2069";
			assertEquals(LyricsFont.width(splitter, s, plain), LyricsFont.width(splitter, s, marked), 0f);
		}
		assertEquals(0f, LyricsFont.width(splitter, s, MARKS), 0f);
		assertSame("plain", LyricsFont.stripMarks("plain"));
	}

	@Test
	void theSinkNeverSeesAMarkAndTheOrderIsKept() {
		String text = "\u05D0\u200F-\u200E12\u061C\u05D1\u2067";
		FormattedCharSequence seq = LyricsFont.dropMarks(FormattedCharSequence.forward(text, Style.EMPTY));
		StringBuilder seen = new StringBuilder();
		assertTrue(seq.accept((i, st, cp) -> {
			seen.appendCodePoint(cp);
			return true;
		}));
		assertEquals("\u05D0-12\u05D1", seen.toString());
		// a sink that stops, stops the walk
		assertFalse(seq.accept((i, st, cp) -> false));
	}

	@Test
	void signedNumbersKeepTheirOrderInARightToLeftGame() {
		// Without the mark the sign and unit go to the wrong side
		assertEquals("2:35-", shown("-2:35"));
		assertEquals("10\u00B0-", shown("-10\u00B0"));
		assertEquals("\u00D70.50", shown("0.50\u00D7"));
		assertEquals("-2:35", shown(LyricsFont.ltr("-2:35")));
		assertEquals("-10\u00B0", shown(LyricsFont.ltrNumber("-10\u00B0")));
		assertEquals("0.50\u00D7", shown(LyricsFont.ltrNumber("0.50\u00D7")));
		assertEquals("10", shown(LyricsFont.ltrNumber("10")));
	}

	@Test
	void valuesWithLettersAreLeftAlone() {
		assertSame("60 fps", LyricsFont.ltrNumber("60 fps"));
		assertSame("-0.30 s", LyricsFont.ltrNumber("-0.30 s"));
	}
}
