package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

class RtlMarksTest {
	private static final String MARKS = "\u200E\u200F\u061C\u202A\u202B\u202C\u202D\u202E\u2066\u2067\u2068\u2069";

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
}
