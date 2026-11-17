package dev.beli.skylyrics.lyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Lyrics come from anyone who can upload to a lyrics site: one line or file must not be able to freeze the game. */
class LrcLimitsTest {
	private static final Duration BOUND = Duration.ofSeconds(10);

	@Test
	void syncedLongLineIsCapped() {
		Lyrics l = LrcParser.parseSynced("[00:01.00]" + "a".repeat(200_000) + "\n[00:02.00]short");
		assertEquals(LrcParser.MAX_LINE, l.lines().get(0).text().length());
		assertEquals("short", l.lines().get(1).text());
	}

	@Test
	void plainLongLineIsCapped() {
		Lyrics l = LrcParser.parsePlain("あ".repeat(200_000) + "\nnext", 60000);
		assertEquals(LrcParser.MAX_LINE, l.lines().get(0).text().length());
	}

	@Test
	void cutNeverSplitsASurrogatePairOrAGrapheme() {
		// A pair straddling the limit, and an emoji family (one character made of several code points) too
		String pairs = "x".repeat(LrcParser.MAX_LINE - 1) + "😀".repeat(1000);
		String t = LrcParser.parsePlain(pairs, 60000).lines().get(0).text();
		assertFalse(Character.isHighSurrogate(t.charAt(t.length() - 1)));
		assertEquals(LrcParser.MAX_LINE - 1, t.length());
		String family = "👨‍👩‍👧";
		String f = LrcParser.parsePlain("x".repeat(LrcParser.MAX_LINE - 2) + family.repeat(1000), 60000).lines().get(0).text();
		assertEquals("x".repeat(LrcParser.MAX_LINE - 2), f);
	}

	@Test
	void normalLinesStayWhole() {
		String line = "word ".repeat(80).strip();
		assertEquals(line, LrcParser.parsePlain(line, 60000).lines().get(0).text());
	}

	@Test
	void hugeSyncedFileKeepsOnlyTheLineLimit() {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < 100_000; i++) {
			b.append(String.format("[%02d:%02d.00]line %d\n", i / 600, i / 10 % 60, i));
		}
		Lyrics l = assertTimeoutPreemptively(BOUND, () -> LrcParser.parseSynced(b.toString()));
		assertEquals(LrcParser.MAX_LINES, l.lines().size());
	}

	@Test
	void hugePlainFileKeepsOnlyTheLineLimit() {
		String text = "line\n".repeat(100_000);
		Lyrics l = assertTimeoutPreemptively(BOUND, () -> LrcParser.parsePlain(text, 60000));
		assertEquals(LrcParser.MAX_LINES, l.lines().size());
	}

	@Test
	void manyLinesAtOneStampJoinFast() {
		List<LyricLine> in = new ArrayList<>();
		for (int i = 0; i < 80_000; i++) {
			in.add(new LyricLine(0, "line " + i));
		}
		List<LyricLine> out = assertTimeoutPreemptively(BOUND, () -> LrcParser.joinSameStamp(in));
		assertEquals(80_000, out.size());
	}
}
