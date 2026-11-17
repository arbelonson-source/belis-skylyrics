package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UiClipTest {
	@Test
	void aFlagAtTheCutIsDroppedWholeNotHalved() {
		// "ab" + Israeli flag (two regional indicators, four UTF-16 units) + "cd", one unit wide per UTF-16 unit
		assertEquals("ab…", Ui.clipBy("ab🇮🇱cd", 5, String::length));
	}

	@Test
	void anAccentStaysWithItsLetter() {
		// "e" + combining acute, then "x"
		assertEquals("a…", Ui.clipBy("aéxyz", 3, String::length));
	}

	@Test
	void shortTextIsUntouchedAndNoRoomIsEmpty() {
		assertEquals("abc", Ui.clipBy("abc", 5, String::length));
		assertEquals("", Ui.clipBy("abc", 0, String::length));
	}

	@Test
	void theSameCutIsNotWorkedOutAgain() {
		Ui.ClipCache cache = new Ui.ClipCache(8);
		Object lang = new Object();
		Object font = new Object();
		int[] runs = {0};
		java.util.function.Supplier<String> work = () -> {
			runs[0]++;
			return "ab…";
		};
		assertEquals("ab…", cache.get(lang, font, "abcdef", 30, work));
		assertEquals("ab…", cache.get(lang, font, "abcdef", 30, work));
		assertEquals(1, runs[0]);
		cache.get(lang, font, "abcdef", 31, work);
		cache.get(lang, new Object(), "abcdef", 30, work);
		assertEquals(3, runs[0]);
	}

	@Test
	void aNewLanguageObjectDropsTheCuts() {
		Ui.ClipCache cache = new Ui.ClipCache(8);
		Object font = new Object();
		int[] runs = {0};
		java.util.function.Supplier<String> work = () -> {
			runs[0]++;
			return "x";
		};
		cache.get("a", font, "t", 5, work);
		cache.get("b", font, "t", 5, work);
		assertEquals(2, runs[0]);
		assertEquals(1, cache.size());
	}

	@Test
	void theCacheStaysBounded() {
		Ui.ClipCache cache = new Ui.ClipCache(4);
		for (int i = 0; i < 20; i++) {
			cache.get("l", "f", "t" + i, 5, () -> "x");
		}
		assertEquals(4, cache.size());
	}
}
