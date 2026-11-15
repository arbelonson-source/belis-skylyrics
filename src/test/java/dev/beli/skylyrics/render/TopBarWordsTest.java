package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class TopBarWordsTest {
	private static int[] order(String line) {
		return TopBarWords.visualOrder(TopBarWords.split(line));
	}

	@Test
	void plainEnglishKeepsItsOrder() {
		assertArrayEquals(new int[] {0, 1, 2}, order("I love sky"));
	}

	@Test
	void hebrewLineIsMirrored() {
		assertArrayEquals(new int[] {2, 1, 0}, order("אור שמיים כחול"));
	}

	@Test
	void englishAndNumbersInsideHebrewKeepTheirOrder() {
		// "שלום world 5" is seen as "world 5 שלום"
		assertArrayEquals(new int[] {1, 2, 0}, order("שלום world 5"));
	}

	@Test
	void hebrewInsideEnglishReadsRightToLeftWithinItsRun() {
		// "I love שלום עולם now" is seen as "I love עולם שלום now"
		assertArrayEquals(new int[] {0, 1, 3, 2, 4}, order("I love שלום עולם now"));
	}

	@Test
	void baseDirectionComesFromTheFirstStrongLetter() {
		assertTrue(TopBarWords.baseRtl("שלום world"));
		assertFalse(TopBarWords.baseRtl("world שלום"));
		assertFalse(TopBarWords.baseRtl(""));
	}

	@Test
	void wordsAreLaidOutBySeenOrder() {
		List<TopBarWords.Piece> p = TopBarWords.split("שלום world 5");
		float[] x = TopBarWords.leftEdges(p, new float[] {10, 20, 4}, 3);
		// world 5 שלום from the left
		assertArrayEquals(new float[] {30, 0, 23}, x, 0.001f);
		assertEquals(23f, x[2], 0.001f);
	}

	private static List<String> texts(String line) {
		return TopBarWords.split(line).stream().map(TopBarWords.Piece::text).toList();
	}

	@Test
	void chineseLineIsSplitIntoCharacters() {
		assertEquals(List.of("我", "爱", "你", "世", "界"), texts("我爱你世界"));
	}

	@Test
	void japaneseKeepsMarksWithTheirCharacter() {
		assertEquals(List.of("今", "日", "は。"), texts("今日は。"));
	}

	@Test
	void spacedWordsAndChineseMix() {
		assertEquals(List.of("hello", "你", "好", "world"), texts("hello 你好 world"));
	}

	@Test
	void chineseCharactersSitAgainstEachOtherButWordsKeepTheirGap() {
		List<TopBarWords.Piece> p = TopBarWords.split("hi 你好");
		float[] x = TopBarWords.leftEdges(p, new float[] {10, 8, 8}, 3);
		assertArrayEquals(new float[] {0, 13, 21}, x, 0.001f);
	}

	@Test
	void plainLineIsUntouched() {
		assertEquals(List.of("hello", "sky", "tonight"), texts("  hello   sky tonight "));
	}
}
