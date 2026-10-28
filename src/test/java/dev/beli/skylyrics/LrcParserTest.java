package dev.beli.skylyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.lyrics.LrcParser;
import dev.beli.skylyrics.lyrics.Lyrics;
import dev.beli.skylyrics.media.DemoSource;

class LrcParserTest {
	@Test
	void parsesTimestampsInOrder() {
		Lyrics l = LrcParser.parseSynced("[00:12.50] second\n[00:01.00] first\n[01:02.345] third");
		assertTrue(l.synced());
		assertEquals(3, l.lines().size());
		assertEquals(1000, l.get(0).timeMs());
		assertEquals("first", l.get(0).text());
		assertEquals(12500, l.get(1).timeMs());
		assertEquals(62345, l.get(2).timeMs());
	}

	@Test
	void fractionDigitsMeanTheRightThing() {
		Lyrics l = LrcParser.parseSynced("[00:01.5]a\n[00:02.45]b\n[00:03.456]c\n[00:04]d");
		assertEquals(1500, l.get(0).timeMs());
		assertEquals(2450, l.get(1).timeMs());
		assertEquals(3456, l.get(2).timeMs());
		assertEquals(4000, l.get(3).timeMs());
	}

	@Test
	void repeatedTimestampsOnOneLine() {
		Lyrics l = LrcParser.parseSynced("[00:05.00][00:30.00] chorus");
		assertEquals(2, l.lines().size());
		assertEquals("chorus", l.get(1).text());
		assertEquals(30000, l.get(1).timeMs());
	}

	@Test
	void oddSpacesBecomePlainOnes() {
		Lyrics l = LrcParser.parseSynced("[00:05.00] <00:05.00> neon\u2005lights <00:06.00>\u00A0glow\u200B\n[00:09.00]\u3000");
		assertEquals("neon lights glow", l.get(0).text());
		assertTrue(l.get(1).isBreak());
		assertEquals("city sky", LrcParser.parsePlain("city\u202F\tsky", 10_000).get(0).text());
	}

	@Test
	void spacesBetweenRepeatedTimestamps() {
		Lyrics l = LrcParser.parseSynced("[00:05.00] [00:30.00]  chorus\n[00:10.00] verse [00:20.00]");
		assertEquals(3, l.lines().size());
		assertEquals("chorus", l.get(0).text());
		assertEquals("verse [00:20.00]", l.get(1).text());
		assertEquals("chorus", l.get(2).text());
		assertEquals(30000, l.get(2).timeMs());
	}

	@Test
	void offsetTagShiftsEarlier() {
		Lyrics l = LrcParser.parseSynced("[offset:+500]\n[00:02.00] x");
		assertEquals(1500, l.get(0).timeMs());
	}

	@Test
	void wordTimesAreRemoved() {
		Lyrics l = LrcParser.parseSynced("[00:01.00] <00:01.00>hello <00:01.50>there");
		assertEquals("hello there", l.get(0).text());
	}

	@Test
	void metadataAndBlankLinesAreIgnored() {
		Lyrics l = LrcParser.parseSynced("[ar:Someone]\n[ti:Song]\n\n[00:01.00] a\n[00:02.00]\n[00:03.00]\n[00:04.00] b");
		assertEquals(3, l.lines().size(), "two empty lines in a row become one break");
		assertTrue(l.get(1).isBreak());
	}

	@Test
	void emptyInputGivesNone() {
		assertSame(Lyrics.NONE, LrcParser.parseSynced(""));
		assertSame(Lyrics.NONE, LrcParser.parseSynced(null));
		assertSame(Lyrics.NONE, LrcParser.parseSynced("no timestamps here"));
		assertSame(Lyrics.NONE, LrcParser.parsePlain("  \n ", 1000));
	}

	@Test
	void plainLyricsAreSpreadOverTheSong() {
		Lyrics l = LrcParser.parsePlain("one\ntwo\nthree\nfour\n\n", 120_000);
		assertFalse(l.synced());
		assertEquals(4, l.lines().size());
		for (int i = 1; i < l.lines().size(); i++) {
			assertTrue(l.get(i).timeMs() > l.get(i - 1).timeMs());
		}
		assertTrue(l.get(3).timeMs() < 120_000);
	}

	@Test
	void plainSongPartNamesAndLeadingBlanksAreSkipped() {
		Lyrics l = LrcParser.parsePlain("\n\n[Verse 1]\none\ntwo\n\n[Chorus: Someone]\nthree [softly]\n[ar:Nobody]", 60_000);
		assertEquals(4, l.lines().size());
		assertEquals("one", l.get(0).text());
		assertTrue(l.get(2).isBreak());
		assertEquals("three [softly]", l.get(3).text());
	}

	@Test
	void indexAtFindsTheCurrentLine() {
		Lyrics l = LrcParser.parseSynced("[00:01.00]a\n[00:02.00]b\n[00:03.00]c");
		assertEquals(-1, l.indexAt(500));
		assertEquals(0, l.indexAt(1000));
		assertEquals(1, l.indexAt(2999));
		assertEquals(2, l.indexAt(60_000));
	}

	private static final String BOM = String.valueOf((char) 0xFEFF);

	@Test
	void byteOrderMarkAndIndentDontDropLines() {
		Lyrics l = LrcParser.parseSynced(BOM + "[00:01.00]first\n  [00:02.00]second\n\t[00:03.00]third");
		assertEquals(3, l.lines().size());
		assertEquals("first", l.get(0).text());
		assertEquals(2000, l.get(1).timeMs());
		assertEquals("third", l.get(2).text());
	}

	@Test
	void oldMacLineEndingsSplitLines() {
		Lyrics synced = LrcParser.parseSynced("[00:01.00]a\r[00:02.00]b\r[00:03.00]c");
		assertEquals(3, synced.lines().size());
		Lyrics plain = LrcParser.parsePlain(BOM + "one\rtwo", 60_000);
		assertEquals(2, plain.lines().size());
		assertEquals("one", plain.get(0).text());
	}

	@Test
	void demoSongParses() {
		Lyrics l = LrcParser.parseSynced(DemoSource.LYRICS);
		assertTrue(l.synced());
		assertTrue(l.lines().size() > 8);
		assertTrue(l.lines().getLast().timeMs() < DemoSource.TRACK.lengthMs());
	}

	@Test
	void brokenOrMisplacedOffsetKeepsTheLyrics() {
		Lyrics huge = LrcParser.parseSynced("[offset:99999999999999999999]\n[00:02.00] x");
		assertEquals(1, huge.lines().size());
		assertEquals(2000, huge.lines().getFirst().timeMs());
		Lyrics inside = LrcParser.parseSynced("[00:02.00] sing [offset:500] along");
		assertEquals(1, inside.lines().size());
		assertEquals(2000, inside.lines().getFirst().timeMs());
	}

	@Test
	void timedNotesAndPartNamesAreBreaks() {
		Lyrics l = LrcParser.parseSynced("[00:01.00]one\n[00:05.00] ♪\n[00:09.00][Chorus]\n[00:12.00]♫ ♫\n[00:15.00]two ♪");
		assertEquals(3, l.lines().size());
		assertEquals("one", l.lines().get(0).text());
		assertTrue(l.lines().get(1).isBreak());
		assertEquals(5000, l.lines().get(1).timeMs());
		assertEquals("two ♪", l.lines().get(2).text());
	}
}
