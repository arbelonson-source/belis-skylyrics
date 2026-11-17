package dev.beli.skylyrics.lyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** An [offset:] tag moves every line; none may land before the start of the song. */
class LrcOffsetFloorTest {
	@Test
	void anOffsetLargerThanALinesTimeLeavesItAtZeroNotNegative() {
		Lyrics l = LrcParser.parseSynced("[offset:5000]\n[00:02.00] first\n[00:10.00] second");
		assertEquals(0, l.get(0).timeMs());
		assertEquals("first", l.get(0).text());
		assertEquals(5000, l.get(1).timeMs());
		assertTrue(l.lines().stream().allMatch(line -> line.timeMs() >= 0));
	}

	@Test
	void anOffsetEqualToTheTimeLandsExactlyOnZero() {
		Lyrics l = LrcParser.parseSynced("[offset:2000]\n[00:02.00] first\n[00:04.00] second");
		assertEquals(0, l.get(0).timeMs());
		assertEquals(2000, l.get(1).timeMs());
	}

	@Test
	void aNegativeOffsetShiftsLaterAndNeverHitsTheFloor() {
		Lyrics l = LrcParser.parseSynced("[offset:-1500]\n[00:02.00] first");
		assertEquals(3500, l.get(0).timeMs());
	}

	@Test
	void aHugeOffsetFloorsEverythingAndKeepsTheOrder() {
		Lyrics l = LrcParser.parseSynced("[offset:900000]\n[00:02.00] a\n[00:04.00] b");
		assertEquals(0, l.get(0).timeMs());
		assertEquals("a", l.get(0).text());
		assertEquals("b", l.get(1).text());
		assertTrue(l.get(1).timeMs() > l.get(0).timeMs());
	}

	@Test
	void linesThatSharedAStampBeforeTheFloorStillJoin() {
		Lyrics l = LrcParser.parseSynced("[offset:+500]\n[00:00.20]\u539f\u6587\n[00:00.20]translation\n[00:03.00]next");
		assertEquals("\u539f\u6587 / translation", l.get(0).text());
		assertEquals(0, l.get(0).timeMs());
		assertEquals("next", l.get(1).text());
	}

	@Test
	void floorFollowedByARealLineAtTwoMsStaysSorted() {
		Lyrics l = LrcParser.parseSynced("[offset:1000]\n[00:00.10]a\n[00:00.20]b\n[00:00.30]c\n[00:00.40]d\n[00:00.50]e\n[00:01.002]real");
		for (int i = 1; i < l.lines().size(); i++) {
			assertTrue(l.get(i).timeMs() >= l.get(i - 1).timeMs(), l.lines().toString());
		}
		assertEquals("real", l.get(l.lines().size() - 1).text());
	}

	@Test
	void commaWordTagsAreStripped() {
		assertEquals("Hello world", LrcParser.parseSynced("[00:01,00]<00:01,00>Hello <00:01,50>world").get(0).text());
	}
}
