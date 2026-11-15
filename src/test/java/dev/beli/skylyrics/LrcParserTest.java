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
		assertEquals("verse", l.get(1).text());
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

	@Test
	void syncedTextOfOnlyNotesOrBlanksIsInstrumental() {
		for (String lrc : new String[] {"[00:00.00] ♪", "[00:00.00]\n[00:30.00]♪ ♪\n[01:00.00]", "[00:05.00][Intro]"}) {
			Lyrics l = LrcParser.parseSynced(lrc);
			assertTrue(l.instrumental(), lrc);
			assertTrue(l.isEmpty(), lrc);
		}
		assertFalse(LrcParser.parseSynced("[00:00.00]♪\n[00:10.00]words").instrumental());
	}

	@Test
	void threeNumberStampsStayMinutesSecondsAndCentiseconds() {
		// "[mm:ss:xx]" is what some files use; no test in the repo uses hh:mm:ss, so that reading stays unsupported
		assertEquals(62_030, LrcParser.parseSynced("[01:02:03]x").lines().getFirst().timeMs());
	}

	@Test
	void aTimestampInTheMiddleOfALineIsNotShown() {
		Lyrics l = LrcParser.parseSynced("[00:12.00]Hello [00:14.00]world\n[00:20]a [00:21] b");
		assertEquals(2, l.lines().size());
		assertEquals(12000, l.get(0).timeMs());
		assertEquals("Hello world", l.get(0).text());
		assertEquals("a b", l.get(1).text());
	}

	@Test
	void wordStampsAndOtherBracketsInsideALineStayHandled() {
		Lyrics l = LrcParser.parseSynced("[00:12.00]<00:12.34>Hello <00:13.00>[x2] world [Chorus]");
		assertEquals("Hello [x2] world [Chorus]", l.get(0).text());
	}

	@Test
	void linesWithTheSameStampAreShownTogether() {
		Lyrics l = LrcParser.parseSynced("[00:10.00]Hello world\n[00:10.00]Hola mundo\n[00:15.00]Next");
		assertEquals(2, l.lines().size());
		assertEquals("Hello world / Hola mundo", l.get(l.indexAt(10_000)).text());
		assertEquals("Hello world / Hola mundo", l.get(l.indexAt(12_000)).text());
		assertEquals("Next", l.get(l.indexAt(15_000)).text());
	}

	@Test
	void aRepeatedStampOfTheSameWordsStaysOneLine() {
		Lyrics l = LrcParser.parseSynced("[00:10.00]Oh\n[00:10.00]Oh\n[00:15.00]Next");
		assertEquals(2, l.lines().size());
		assertEquals("Oh", l.get(0).text());
	}

	@Test
	void aLyricWithRomanizationAndTranslationOnOneStampShowsAllThree() {
		Lyrics l = LrcParser.parseSynced("[00:10.00]君が好き\n[00:10.00]Kimi ga suki\n[00:10.00]I like you\n[00:15.00]Next");
		assertEquals(2, l.lines().size());
		assertEquals("君が好き / Kimi ga suki / I like you", l.get(l.indexAt(11_000)).text());
	}

	@Test
	void manyLinesOnOneStampAreSpreadOutNotJoined() {
		Lyrics l = LrcParser.parseSynced("[00:00.00]a\n[00:00.00]b\n[00:00.00]c\n[00:00.00]d\n[00:10.00]e");
		assertEquals(5, l.lines().size());
		assertEquals("a", l.get(l.indexAt(1_000)).text());
		assertEquals("b", l.get(l.indexAt(3_000)).text());
		assertEquals("d", l.get(l.indexAt(9_000)).text());
		assertEquals("e", l.get(l.indexAt(10_000)).text());
	}

	@Test
	void aFileWithEveryLineOnOneStampStillReadsLineByLine() {
		Lyrics l = LrcParser.parseSynced("[00:00.00]a\n[00:00.00]b\n[00:00.00]c\n[00:00.00]d\n[00:00.00]e");
		assertEquals(5, l.lines().size());
		assertEquals("a", l.get(l.indexAt(1_000)).text());
		assertEquals("c", l.get(l.indexAt(7_100)).text());
		assertEquals("e", l.get(l.indexAt(60_000)).text());
	}

	@Test
	void hourStampsFourDigitFractionsAndFullWidthBracketsParse() {
		Lyrics l = LrcParser.parseSynced("[ar:Someone]\n[00:05.1234]a\n[01:00:02.50]b\n［00：09．00］c");
		assertEquals(3, l.lines().size());
		assertEquals(5123, l.get(0).timeMs());
		assertEquals("c", l.get(1).text());
		assertEquals(9000, l.get(1).timeMs());
		assertEquals(3_602_500, l.get(2).timeMs());
		assertEquals("b", l.get(2).text());
	}

	@Test
	void instrumentalMarkerLinesAreBreaks() {
		Lyrics l = LrcParser.parseSynced("[00:01.00]words\n[00:05.00](Instrumental)\n[00:09.00][music]\n[00:12.00]Music of the night\n[00:15.00]end");
		assertEquals("", l.get(1).text());
		assertEquals(4, l.lines().size());
		assertEquals("Music of the night", l.get(2).text());
	}

	@Test
	void aBareMusicLineIsSungButABareInstrumentalIsAPause() {
		Lyrics l = LrcParser.parseSynced("[00:01.00]words\n[00:03.00]Music\n[00:05.00]Instrumental\n[00:09.00]end");
		assertEquals("Music", l.get(1).text());
		assertEquals("", l.get(2).text());
	}

	@Test
	void longWordStampsAreStripped() {
		Lyrics l = LrcParser.parseSynced("[00:01.00]<00:01.0000>hel<00:01.5000>lo <00:00:02.50>there");
		assertEquals("hello there", l.get(0).text());
	}

	@Test
	void invisibleAndNoBreakSpacesBeforeAStampAreIgnored() {
		assertEquals(1, LrcParser.parseSynced("\u00A0[00:05.00]Hi").lines().size());
		assertEquals(1, LrcParser.parseSynced("\u200B[00:05.00]Hi").lines().size());
		assertEquals(1, LrcParser.parseSynced("[00:01.00]a\n\u2060[00:05.00]Hi").lines().size() - 1);
		assertEquals("Hi", LrcParser.parseSynced("[00:01.00]a\n\uFEFF[00:05.00]Hi").get(1).text());
		assertEquals("Hi", LrcParser.parseSynced("\u00A0\u200B [00:05.00]Hi").get(0).text());
	}

	@Test
	void spacesInsideTheBracketsAreAllowed() {
		Lyrics l = LrcParser.parseSynced("[ 00:12.00 ]a\n[00:13.00 ]b\n[ 00:14.00]c");
		assertEquals(3, l.lines().size());
		assertEquals(12000, l.get(0).timeMs());
		assertEquals("b", l.get(1).text());
		assertEquals(14000, l.get(2).timeMs());
	}

	@Test
	void controlCharactersAreRemovedAndLineSeparatorsBreakLines() {
		assertEquals("ab", LrcParser.parseSynced("[00:01.00]a\u0001b").get(0).text());
		Lyrics l = LrcParser.parseSynced("[00:01.00]a\u2028[00:02.00]b\u2029[00:03.00]c");
		assertEquals(3, l.lines().size());
		assertEquals("a", l.get(0).text());
		assertEquals("ab", LrcParser.parsePlain("a\u0007b", 0).get(0).text());
		assertEquals("a b", LrcParser.parseSynced("[00:01.00]a\tb").get(0).text());
	}

	private static void assertPause(String line) {
		Lyrics l = LrcParser.parseSynced("[00:01.00]sing\n[00:05.00]" + line + "\n[00:09.00]more");
		assertTrue(l.get(1).isBreak(), "should be a pause: " + line);
	}

	private static void assertLyric(String line) {
		Lyrics l = LrcParser.parseSynced("[00:01.00]sing\n[00:05.00]" + line + "\n[00:09.00]more");
		assertEquals(line, l.get(1).text());
	}

	@Test
	void moreWholeLineMarkersArePauses() {
		for (String m : new String[] {"\uD83C\uDFB5", "\uD83C\uDFB6", "\uD83C\uDFB5 \uD83C\uDFB6 \uD83C\uDFB5", "\uD83C\uDFB5\uD83C\uDFB5", "\u266A instrumental \u266A", "(Intro)", "(Outro)",
				"(Guitar Solo)", "(Solo)", "[Instrumental Break]", "[Intro]"}) {
			assertPause(m);
		}
	}

	@Test
	void aLyricThatMerelyHasTheWordStaysALyric() {
		assertLyric("Intro to love");
		assertLyric("solo dolo");
		assertLyric("Music");
		assertLyric("Outro of my heart");
		assertLyric("instrumental in my love");
	}

	@Test
	void aLeadingPartTagIsDropped() {
		assertEquals("We will rock you", LrcParser.parseSynced("[00:01.00][Chorus] We will rock you").get(0).text());
		assertEquals("words", LrcParser.parseSynced("[00:01.00][Verse 1] words").get(0).text());
		assertEquals("words", LrcParser.parseSynced("[00:01.00][Pre-Chorus]words").get(0).text());
		assertEquals("words", LrcParser.parseSynced("[00:01.00][Bridge: Someone] words").get(0).text());
		assertEquals("words", LrcParser.parseSynced("[00:01.00][hook] words").get(0).text());
		assertEquals("words", LrcParser.parseSynced("[00:01.00][Outro] words").get(0).text());
		assertEquals("[Laughs] ha", LrcParser.parseSynced("[00:01.00][Laughs] ha").get(0).text());
	}

	@Test
	void hostileLinesParseFast() {
		String[] lines = {"\u266A".repeat(50_000) + "x", "\u266A" + " ".repeat(50_000) + "instrumentalx", "[Chorus:" + " ".repeat(50_000) + " words",
				"(" + "a".repeat(50_000) + " solo", "\uD83C\uDFB5".repeat(50_000) + "x"};
		for (String line : lines) {
			org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(15),
					() -> LrcParser.parseSynced("[00:01.00]" + line + "\n[00:05.00]more"));
		}
	}
}
