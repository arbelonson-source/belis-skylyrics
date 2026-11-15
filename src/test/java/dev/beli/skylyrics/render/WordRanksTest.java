package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WordRanksTest {
	@Test
	void chineseLineIsOneWordPerCharacter() {
		assertArrayEquals(new int[] {0, 1, 2, 3, 4}, LyricsRenderer.computeWordRanks("我爱你们啊"));
		assertEquals(5, LyricsRenderer.wordCount("我爱你们啊"));
	}

	@Test
	void japaneseMarksStayWithTheirCharacter() {
		assertArrayEquals(new int[] {0, 0, 1, 2, 2}, LyricsRenderer.computeWordRanks("夜、空に。"));
		assertEquals(3, LyricsRenderer.wordCount("夜、空に。"));
	}

	@Test
	void mixedLineKeepsLatinWordsWhole() {
		// spaces count with the word before
		assertArrayEquals(new int[] {0, 1, 2, 2, 3, 3, 3, 3, 3, 4, 5, 6}, LyricsRenderer.computeWordRanks("我爱你 baby 我爱你"));
		assertEquals(7, LyricsRenderer.wordCount("我爱你 baby 我爱你"));
	}

	@Test
	void spacedLinesAreUnchanged() {
		assertArrayEquals(new int[] {0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2}, LyricsRenderer.computeWordRanks("hello big, world"));
		assertEquals(3, LyricsRenderer.wordCount("hello big, world"));
		assertEquals(0, LyricsRenderer.wordCount("   "));
	}
}
