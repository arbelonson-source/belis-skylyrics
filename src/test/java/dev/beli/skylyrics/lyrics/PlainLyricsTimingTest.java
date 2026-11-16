package dev.beli.skylyrics.lyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Where lyrics without timestamps are placed, since nothing but this tells them when to show. */
class PlainLyricsTimingTest {
	private static long[] times(Lyrics l) {
		return l.lines().stream().mapToLong(LyricLine::timeMs).toArray();
	}

	@Test
	void linesAreSpreadEvenlyAfterAShortIntro() {
		Lyrics l = LrcParser.parsePlain("one\ntwo\nthree\nfour", 120_000);
		assertFalse(l.synced());
		// the intro is a twelfth of the song up to 8 s, then each line gets an equal share of what is left
		assertEquals(8_000, l.get(0).timeMs());
		assertEquals(36_000, l.get(1).timeMs());
		assertEquals(64_000, l.get(2).timeMs());
		assertEquals(92_000, l.get(3).timeMs());
	}

	@Test
	void theIntroIsAtMostEightSecondsOnALongSongAndAtMostATwelfthOnAShortOne() {
		assertEquals(8_000, LrcParser.parsePlain("a\nb", 600_000).get(0).timeMs());
		assertEquals(5_000, LrcParser.parsePlain("a\nb", 60_000).get(0).timeMs());
	}

	@Test
	void aSongOfUnknownLengthGivesEachLineAboutThreeAndAHalfSeconds() {
		long[] t = times(LrcParser.parsePlain("one\ntwo\nthree", 0));
		assertEquals(875, t[0]);
		assertEquals(875 + 3_208, t[1]);
		assertEquals(875 + 2 * 3_208, t[2]);
	}

	@Test
	void linesAreNeverCloserThanAboutAndAFifthSeconds() {
		// 20 lines in a 10 s song cannot fit evenly; the spacing stops at 1.2 s instead of flashing past
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < 20; i++) {
			text.append("line ").append(i).append('\n');
		}
		long[] t = times(LrcParser.parsePlain(text.toString(), 10_000));
		for (int i = 1; i < t.length; i++) {
			assertEquals(1_200, t[i] - t[i - 1]);
		}
	}

	@Test
	void aBlankLineInTheMiddleTakesItsOwnSlot() {
		Lyrics l = LrcParser.parsePlain("one\n\ntwo", 120_000);
		assertEquals(3, l.lines().size());
		assertTrue(l.get(1).isBreak());
		assertEquals("two", l.get(2).text());
		assertTrue(l.get(2).timeMs() > l.get(1).timeMs());
	}

	@Test
	void timesNeverGoBackwardsAndAreWhereTheySayAtEachMoment() {
		Lyrics l = LrcParser.parsePlain("one\ntwo\nthree\nfour", 120_000);
		assertEquals(-1, l.indexAt(7_999));
		assertEquals(0, l.indexAt(8_000));
		assertEquals(0, l.indexAt(35_999));
		assertEquals(1, l.indexAt(36_000));
		assertEquals(3, l.indexAt(119_000));
	}
}
