package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class LineBreakTest {
	@Test
	void spacedWordsStayWhole() {
		assertEquals(List.of("SKYLINE,"), LyricsRenderer.breakPoints("SKYLINE,"));
	}

	@Test
	void chineseAndJapaneseMayBreakAfterAnyCharacter() {
		assertEquals(List.of("夜", "空", "に", "光", "る"), LyricsRenderer.breakPoints("夜空に光る"));
		assertEquals(List.of("「夜", "空」、"), LyricsRenderer.breakPoints("「夜空」、"), "brackets and commas stay put");
		assertEquals(List.of("ラー", "メ", "ン"), LyricsRenderer.breakPoints("ラーメン"), "a long-vowel mark never starts a row");
		assertEquals(List.of("Tokyo", "の", "夜"), LyricsRenderer.breakPoints("Tokyoの夜"), "a Latin word inside stays whole");
	}
}
