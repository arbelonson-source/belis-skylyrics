package dev.beli.skylyrics.lyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** A lyric line must not be able to flip itself with an override, while the marks Hebrew and Arabic sites use stay. */
class LrcBidiTest {
	@Test
	void overridesEmbeddingsAndIsolatesAreStripped() {
		assertEquals("hello world", LrcParser.tidy("hel‮lo ‪‫‬‭world⁦⁧⁨⁩"));
	}

	@Test
	void syncedAndPlainLinesLoseThem() {
		assertEquals("abc", LrcParser.parseSynced("[00:01.00]‮abc").lines().get(0).text());
		assertEquals("abc", LrcParser.parsePlain("‮abc", 60000).lines().get(0).text());
	}

	@Test
	void directionMarksAndHebrewStay() {
		String he = "שלום עולם";
		assertEquals(he, LrcParser.tidy(he));
		assertEquals("a‏b‎c؜d", LrcParser.tidy("a‏b‎c؜d"));
	}
}
