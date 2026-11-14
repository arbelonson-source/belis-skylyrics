package dev.beli.skylyrics.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TextCutTest {
	@Test
	void anEmojiAtTheCutIsKeptWholeOrDropped() {
		String text = "ab🎵cd"; // ab + musical note emoji + cd
		// Room for 3 UTF-16 units would land inside the emoji.
		assertEquals("ab", TextCut.prefix(text, t -> t.length() <= 3));
		assertEquals("ab🎵", TextCut.prefix(text, t -> t.length() <= 4));
		assertEquals("ab", TextCut.limit(text, 3));
	}

	@Test
	void aLetterKeepsItsAccent() {
		String text = "aéf"; // e + combining acute
		assertEquals("a", TextCut.prefix(text, t -> t.length() <= 2));
	}

	@Test
	void aFlagIsNotSplit() {
		String text = "x🇯🇵y"; // regional indicators J, P
		assertEquals("x", TextCut.prefix(text, t -> t.length() <= 3));
	}

	@Test
	void theEndIsCutOnWholeCharactersToo() {
		String text = "a🎵bc";
		assertEquals("bc", TextCut.suffix(text, t -> t.length() <= 3));
		assertEquals("", TextCut.suffix(text, t -> false));
	}

	@Test
	void shortTextIsUntouched() {
		assertEquals("hello", TextCut.prefix("hello", t -> true));
		assertEquals("hello", TextCut.limit("hello", 80));
	}
}
