package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ArcCjkTest {
	private static final float EM = 10.5f;

	private static float[] widths(List<String> words) {
		float[] w = new float[words.size()];
		for (int i = 0; i < w.length; i++) {
			w[i] = words.get(i).codePointCount(0, words.get(i).length()) * (words.get(i).matches("\\p{IsHan}+.*") ? EM : EM * 0.55f);
		}
		return w;
	}

	@Test
	void chineseLineSplitsIntoCharactersWithNoGaps() {
		String line = "我爱你我爱你我爱你我爱你我爱你我爱你我爱你我爱你我爱你我爱你";
		LyricsRenderer.ArcWords split = LyricsRenderer.arcWords(line);
		assertEquals(30, split.words().size());
		assertEquals("我", split.words().get(0));
		for (int i = 0; i < 30; i++) {
			assertEquals(i, split.first()[i]);
			assertEquals(i < 29, split.tight()[i], "gap after " + i);
		}
	}

	@Test
	void thirtyCharacterLineWrapsIntoRows() {
		LyricsRenderer.ArcWords split = LyricsRenderer.arcWords("我爱你".repeat(10));
		float[] w = widths(split.words());
		int[] rows = ArcLayout.rows(w, new boolean[w.length], split.tight());
		assertTrue(rows.length >= 2, "rows " + rows.length);
		for (int r = 0; r < rows.length; r++) {
			int to = r + 1 < rows.length ? rows[r + 1] : w.length;
			assertTrue(ArcLayout.width(w, split.tight(), rows[r], to, ArcLayout.GAP) <= ArcLayout.ROW_WIDTH + EM);
		}
	}

	@Test
	void mixedLineKeepsLatinWordWhole() {
		LyricsRenderer.ArcWords split = LyricsRenderer.arcWords("我爱你 baby 我爱你");
		assertEquals(List.of("我", "爱", "你", "baby", "我", "爱", "你"), split.words());
		assertArrayEquals(new int[] {0, 1, 2, 4, 9, 10, 11}, split.first());
		assertArrayEquals(new boolean[] {true, true, false, false, true, true, false}, split.tight());
		float[] w = widths(split.words());
		assertEquals(1, ArcLayout.rows(w, new boolean[w.length], split.tight()).length);
	}

	@Test
	void latinHebrewArabicLinesAreUnchanged() {
		for (String line : List.of("hello big world", "שלום עולם יפה", "مرحبا بالعالم الجميل")) {
			LyricsRenderer.ArcWords split = LyricsRenderer.arcWords(line);
			assertEquals(List.of(line.split(" ")), split.words());
			for (boolean t : split.tight()) {
				assertEquals(false, t);
			}
		}
	}
}
