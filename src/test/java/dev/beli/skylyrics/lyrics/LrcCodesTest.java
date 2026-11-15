package dev.beli.skylyrics.lyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LrcCodesTest {
	@Test
	void syncedLinesLoseFormattingCodes() {
		Lyrics l = LrcParser.parseSynced("[00:01.00]§kHello\n[00:02.00]A§cB\n[00:03.00]End§\n[00:04.00]¶ Ș");
		assertEquals("Hello", l.lines().get(0).text());
		assertEquals("AB", l.lines().get(1).text());
		assertEquals("End", l.lines().get(2).text());
		assertEquals("¶ Ș", l.lines().get(3).text());
	}

	@Test
	void plainLinesLoseFormattingCodes() {
		Lyrics l = LrcParser.parsePlain("§kHello\nA§cB\nEnd§", 60000);
		assertEquals("Hello", l.lines().get(0).text());
		assertEquals("AB", l.lines().get(1).text());
		assertEquals("End", l.lines().get(2).text());
	}
}
