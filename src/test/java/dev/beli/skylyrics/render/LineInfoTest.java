package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.junit.jupiter.api.Test;

/** What a line's text alone decides is counted once and kept; it has to come out as when every frame counted it again. */
class LineInfoTest {
	/** The old per-frame counting, copied as it was. */
	private static int oldWordCount(String row) {
		int[] rank = LyricsRenderer.computeWordRanks(row);
		boolean any = row.chars().anyMatch(c -> c != ' ');
		return any ? rank[rank.length - 1] + 1 : 0;
	}

	private static final String[][] LINES = {
			{"hello sky tonight"},
			{"hello sky", "tonight"},
			{"שלום עולם", "OK"},
			{"سلام لا", "كلام"},
			{"夜空を見上げて", "星が降る"},
			{"hi 你好 world", "ok"},
			{"party 🎉🎉 time", "yes"},
			{"  spaced  out  "},
			{"x"},
	};

	private static String join(String[] rows) {
		return String.join(" ", rows);
	}

	@Test
	void matchesTheOldCounting() {
		for (String[] r : LINES) {
			List<String> rows = List.of(r);
			String text = join(r);
			LyricsRenderer.LineInfo info = LyricsRenderer.LineInfo.of(text, rows);
			int[] starts = LyricsRenderer.rowStarts(text, rows);
			assertArrayEquals(starts, info.starts, text);
			int last = rows.size() - 1;
			assertEquals(starts[last] + rows.get(last).codePointCount(0, rows.get(last).length()), info.total, text);
			int words = 0;
			for (int i = 0; i < rows.size(); i++) {
				assertEquals(oldWordCount(rows.get(i)), info.rowWords[i], text);
				assertEquals(oldWordCount(rows.get(i)), LyricsRenderer.wordCount(rows.get(i)), text);
				words += oldWordCount(rows.get(i));
			}
			assertEquals(words, info.words, text);
			assertEquals(WorldWords.rightToLeft(text), info.rtl, text);
		}
	}

	@Test
	void displayKeepsDirectionsApart() {
		String row = "hello, שלום,";
		LyricsRenderer.Display rtl = LyricsRenderer.Display.of(row, true);
		LyricsRenderer.Display ltr = LyricsRenderer.Display.of(row, false);
		LyricsRenderer.Display plain = LyricsRenderer.Display.of(row);
		assertSame(rtl, LyricsRenderer.Display.of(row, true));
		assertSame(ltr, LyricsRenderer.Display.of(row, false));
		assertNotSame(rtl, ltr);
		// The same mixed row really is laid out differently by direction (the comma goes to the other side of שלום)
		assertFalse(java.util.Arrays.equals(rtl.logical(), ltr.logical()), "orders differ");
		assertFalse(java.util.Arrays.equals(rtl.cps(), ltr.cps()), "drawn characters differ");
		assertEquals(',', ltr.cps()[ltr.cps().length - 1], "left to right: the comma ends the row");
		assertEquals(',', rtl.cps()[0], "right to left: the comma starts it");
		assertEquals(plain.cps().length, rtl.cps().length);
	}
}
