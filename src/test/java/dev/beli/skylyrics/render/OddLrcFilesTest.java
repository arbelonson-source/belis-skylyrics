package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.lyrics.LrcParser;
import dev.beli.skylyrics.lyrics.LyricLine;
import dev.beli.skylyrics.lyrics.Lyrics;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Odd lyrics files as people and tools really write them, each read by the parser and then played through the code
 * that times and draws the lines: which line is on show (the renderer's time runs {@link LyricsRenderer#LEAD_MS}
 * ahead of the song), the karaoke fill, the World layout's pieces, the stale fade and the top bar's "still singing".
 */
class OddLrcFilesTest {
	private static final long LEAD = LyricsRenderer.LEAD_MS;
	/** A stand-in font: every letter 6 units wide (the real one is narrower, so real rows are no wider). */
	private static final java.util.function.ToDoubleFunction<String> SIX = s -> s.codePointCount(0, s.length()) * 6.0;

	/** The text on show at song position {@code positionMs}, as every layout picks it ("" for a pause, null for none). */
	private static String shownAt(Lyrics l, long positionMs) {
		LyricLine line = l.get(l.indexAt(positionMs + LEAD));
		return line == null ? null : line.text();
	}

	/** The share of line {@code i}'s letters the World layout has out at {@code positionMs}. */
	private static float worldOut(Lyrics l, int i, long positionMs) {
		String text = l.get(i).text();
		int total = text.codePointCount(0, text.length());
		return Math.min(1, LyricsRenderer.sungChars(l, i, positionMs + LEAD, total) / total);
	}

	/** How far the Lines layout's karaoke fill is through line {@code i} at {@code positionMs}. */
	private static float fill(Lyrics l, int i, long positionMs) {
		return Math.min(1, LyricsRenderer.progress(positionMs + LEAD, l.get(i).timeMs(), l.sungEnd(i)));
	}

	@Test
	void aRepeatedChorusOnOneLineShowsAtEachOfItsStamps() {
		Lyrics l = LrcParser.parseSynced("[00:12.00][00:45.00]Chorus words here\n[00:20.00]Verse words");
		assertEquals("Chorus words here", shownAt(l, 13_000));
		assertEquals("Verse words", shownAt(l, 30_000));
		assertEquals("Chorus words here", shownAt(l, 46_000));
		int second = l.indexAt(46_000 + LEAD);
		assertEquals(1f, worldOut(l, second, 50_000), 1e-3f);
		assertEquals(1f, fill(l, second, 50_000), 1e-3f);
	}

	@Test
	void linesOutOfOrderAndOnTheSameStampPlayInTimeOrder() {
		Lyrics l = LrcParser.parseSynced("[00:30.00]third\n[00:10.00]first\n[00:20.00]second\n[00:20.00]translation");
		assertEquals("first", shownAt(l, 11_000));
		assertEquals("second / translation", shownAt(l, 21_000));
		assertEquals("third", shownAt(l, 31_000));
		assertEquals(1f, LyricsRenderer.staleFade(l, l.indexAt(21_000 + LEAD), 21_000 + LEAD), 0f);
	}

	@Test
	void everyStampSpellingLandsOnItsTime() {
		String lrc = "[00:01]a\n[00:02.5]b\n[00:03.25]c\n[00:04.125]d\n[00:05:50]e\n[00:06,75]f\n[01:00:00.00]g\n[75:02.10]h";
		Lyrics l = LrcParser.parseSynced(lrc);
		long[] want = {1_000, 2_500, 3_250, 4_125, 5_500, 6_750, 3_600_000, 4_502_100};
		assertEquals(want.length, l.lines().size());
		for (int i = 0; i < want.length; i++) {
			assertEquals(want[i], l.get(i).timeMs(), l.get(i).text());
			assertEquals(i, l.indexAt(want[i]));
		}
		// Past an hour the order still holds: minute 75 comes after the one-hour stamp
		assertEquals("h", shownAt(l, 4_502_100));
	}

	@Test
	void anOffsetTagMovesTheLinesTheWayLrcMeansIt() {
		// [offset:] is in milliseconds; a positive one shows the lyrics sooner, a negative one later
		assertEquals("x", shownAt(LrcParser.parseSynced("[offset:+500]\n[00:10.00]x"), 9_500 - LEAD));
		assertEquals(null, shownAt(LrcParser.parseSynced("[offset:+500]\n[00:10.00]x"), 9_499 - LEAD));
		assertEquals(null, shownAt(LrcParser.parseSynced("[offset:-250]\n[00:10.00]x"), 10_249 - LEAD));
		assertEquals("x", shownAt(LrcParser.parseSynced("[offset:-250]\n[00:10.00]x"), 10_250 - LEAD));
	}

	@Test
	void metadataAndUnknownTagsAreNotShown() {
		Lyrics l = LrcParser.parseSynced("[ar:Someone]\n[ti:A Song]\n[al:An Album]\n[length: 03:45]\n[by:a fan]\n[re:Some Tool]\n[ve:1.0]\n"
				+ "[#:a comment]\n[tool:x]\n[00:05.00]words");
		assertEquals(1, l.lines().size());
		assertEquals("words", shownAt(l, 6_000));
		assertSame(Lyrics.NONE, LrcParser.parseSynced("[ar:Someone]\n[ti:A Song]\n[length:03:45]"));
	}

	@Test
	void aStampWithNoTextAndANoteLineAreBothPauses() {
		Lyrics l = LrcParser.parseSynced("[00:01.00]sing\n[00:05.00]\n[00:09.00]more\n[00:13.00]♪\n[00:17.00]end");
		assertEquals("", shownAt(l, 6_000));
		assertEquals("", shownAt(l, 14_000));
		// The top bar bridges the pauses instead of hiding between the lines
		assertTrue(NowPlayingHud.singing(l, 6_000 + LEAD));
		assertTrue(NowPlayingHud.singing(l, 14_000 + LEAD));
		assertEquals(1f, LyricsRenderer.staleFade(l, l.indexAt(14_000 + LEAD), 16_000 + LEAD), 0f);
	}

	@Test
	void aFirstLineAtZeroIsOnShowFromTheStart() {
		Lyrics l = LrcParser.parseSynced("[00:00.00]From the start\n[00:04.00]next");
		assertEquals("From the start", shownAt(l, 0));
		assertFalse(LyricsRenderer.introNotes(l.get(0).timeMs()), "no notes for a wait that isn't there");
		assertTrue(NowPlayingHud.singing(l, LEAD));
	}

	@Test
	void aFirstLineStampedBeforeTheStartIsOnShowFromTheStart() {
		Lyrics l = LrcParser.parseSynced("[-00:01.00]Before the start\n[00:04.00]next");
		assertEquals("Before the start", shownAt(l, 0));
		assertEquals("next", shownAt(l, 4_000));
		assertEquals(1f, worldOut(l, 0, 3_800), 1e-3f);
	}

	@Test
	void wordStampsAreNotShownAndTheFillCountsOnlyTheWords() {
		Lyrics l = LrcParser.parseSynced("[00:01.00]<00:01.00>Hel<00:01.20>lo <00:01.50>bright <00:02.00>world<00:02.50>\n[00:04.00]next");
		assertEquals("Hello bright world", shownAt(l, 1_000));
		assertEquals(3, WorldWords.chunks(l.get(0).text()).size());
		assertEquals(1f, worldOut(l, 0, 3_800), 1e-3f);
		assertTrue(fill(l, 0, 3_849) > 0.99f);
	}

	@Test
	void byteOrderMarkLineEndsTabsAndInvisibleCharactersLeaveCleanLines() {
		String lrc = "﻿[00:01.00]\tglass​ tide  \r\n[00:03.00] open⁠ing \t\r[00:05.00]end　\n";
		Lyrics l = LrcParser.parseSynced(lrc);
		assertEquals("glass tide", shownAt(l, 1_000));
		assertEquals("opening", shownAt(l, 3_000));
		assertEquals("end", shownAt(l, 5_000));
		assertEquals(2, WorldWords.chunks(l.get(0).text()).size());
	}

	@Test
	void aVeryLongLineAndAVeryLongWordStayInsideTheirRows() {
		String longLine = ("lanterns drift over the sleepy harbour town ").repeat(7).strip();
		String longWord = "a".repeat(100);
		Lyrics l = LrcParser.parseSynced("[00:01.00]" + longLine + "\n[00:13.00]" + longWord + "\n[00:20.00]end");
		assertEquals(longLine, l.get(0).text());
		for (String text : new String[] {l.get(0).text(), l.get(1).text()}) {
			List<String> rows = LyricsRenderer.wrap(SIX, text, LyricsRenderer.SKY_WRAP);
			assertTrue(rows.size() > 1, text);
			for (String row : rows) {
				assertTrue(SIX.applyAsDouble(row) <= LyricsRenderer.SKY_WRAP, row);
			}
			assertEquals(text.replace(" ", ""), String.join("", rows).replace(" ", ""), "no letter lost");
		}
		// The World layout makes one card of the long word, which it shrinks to fit the view (FastLinesTest)
		assertEquals(1, WorldWords.chunks(longWord).size());
		assertEquals(1f, worldOut(l, 0, 12_800), 1e-3f);
	}

	@Test
	void emptyAndInstrumentalFilesShowNoLines() {
		assertSame(Lyrics.NONE, LrcParser.parseSynced(""));
		assertSame(Lyrics.NONE, LrcParser.parseSynced("﻿"));
		assertSame(Lyrics.INSTRUMENTAL, LrcParser.parseSynced("[00:00.00]Instrumental"));
		assertSame(Lyrics.INSTRUMENTAL, LrcParser.parseSynced("[00:00.00]♪\n[03:00.00]"));
		assertFalse(NowPlayingHud.singing(Lyrics.INSTRUMENTAL, 10_000));
	}

	@Test
	void untimedLyricsWithPartNamesShowOnlyTheWords() {
		Lyrics l = LrcParser.parsePlain("[Verse 1: Someone]\nfirst words\nsecond words\n\n[Chorus]\n[Chorus] sung words\n", 120_000);
		for (LyricLine line : l.lines()) {
			assertFalse(line.text().contains("["), line.text());
		}
		assertEquals("sung words", l.lines().getLast().text());
	}

	@Test
	void aTwentyMinuteSongOfFifteenHundredLinesPlaysLineByLine() {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < 1_500; i++) {
			long ms = 2_000 + i * 800L;
			b.append(String.format(Locale.ROOT, "[%02d:%02d.%02d]line %d of the long song%n", ms / 60_000, ms / 1000 % 60, ms / 10 % 100, i));
		}
		Lyrics l = LrcParser.parseSynced(b.toString());
		assertEquals(1_500, l.lines().size());
		// Every frame of the song at 60 fps, as the layouts ask each frame
		assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
			int last = -1;
			for (long pos = 0; pos < 21 * 60_000; pos += 16) {
				long time = pos + LEAD;
				int i = l.indexAt(time);
				assertTrue(i >= last && i <= last + 1, "at " + pos);
				if (i >= 0) {
					LyricsRenderer.sungChars(l, i, time, 20);
					LyricsRenderer.staleFade(l, i, time);
					NowPlayingHud.singing(l, time);
				}
				last = i;
			}
			assertEquals(1_499, last);
		});
		assertEquals("line 1000 of the long song", shownAt(l, 2_000 + 1000 * 800L));
	}

	@Test
	void fastRapGetsEveryWordOutBeforeTheNextLine() {
		// Eight words in 0.8 s, line after line: ten words a second
		String line = "fast lines keep coming every single beat now";
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < 20; i++) {
			long ms = 10_000 + i * 800L;
			b.append(String.format(Locale.ROOT, "[00:%02d.%02d]%s%n", ms / 1000 % 60, ms / 10 % 100, line));
		}
		Lyrics l = LrcParser.parseSynced(b.toString());
		List<int[]> chunks = WorldWords.chunks(line);
		int total = line.codePointCount(0, line.length());
		for (int i = 0; i < l.lines().size() - 1; i++) {
			int spawned = 0, count = 0;
			for (long pos = l.get(i).timeMs() - 500; pos < l.get(i + 1).timeMs(); pos += 16) {
				long time = pos + LEAD;
				if (l.indexAt(time) != i) {
					continue;
				}
				float sung = LyricsRenderer.sungChars(l, i, time, total);
				int due = 0;
				while (due < chunks.size() && chunks.get(due)[0] <= sung) {
					due++;
				}
				count += due - WorldWords.skipTo(spawned, due, false, false);
				spawned = due;
			}
			assertEquals(chunks.size(), count, "line " + i);
			assertEquals(1f, fill(l, i, l.get(i + 1).timeMs() - LEAD - 1), 0.01f);
		}
	}
}
