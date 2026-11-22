package dev.beli.skylyrics.lyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Untimed lyrics copied from lyrics sites often name the song part in front of its first words. Timed lyrics drop
 * that tag ("[Chorus] words" shows "words"); untimed ones must too, or the tag floats in the sky with the words.
 */
class PlainPartTagTest {
	private static String first(String plain) {
		return LrcParser.parsePlain(plain, 60_000).lines().get(0).text();
	}

	@Test
	void aPartTagInFrontOfWordsIsDropped() {
		assertEquals("We sail tonight", first("[Chorus] We sail tonight\nmore"));
		assertEquals("words", first("[Verse 1: Someone] words\nmore"));
		assertEquals("words", first("[Pre-Chorus]words\nmore"));
	}

	@Test
	void theSameLineReadsAlikeTimedAndUntimed() {
		for (String line : new String[] {"[Chorus] We sail tonight", "[Bridge: Someone] words", "[Laughs] ha", "three [softly]"}) {
			assertEquals(LrcParser.parseSynced("[00:01.00]" + line).lines().get(0).text(), first(line + "\nmore"), line);
		}
	}

	@Test
	void otherBracketsInFrontStay() {
		assertEquals("[Laughs] ha", first("[Laughs] ha\nmore"));
	}
}
