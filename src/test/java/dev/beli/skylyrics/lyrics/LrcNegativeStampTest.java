package dev.beli.skylyrics.lyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Some editors write a stamp before the song's start as a negative time ("[-00:01.00]"), typically after shifting a
 * whole file earlier. The line is meant for the very start, like a line an [offset:] pushes before it.
 */
class LrcNegativeStampTest {
	private static String dump(Lyrics l) {
		StringBuilder b = new StringBuilder();
		for (LyricLine x : l.lines()) {
			b.append(x.timeMs()).append('=').append(x.text()).append('|');
		}
		return b.toString();
	}

	@Test
	void aNegativeStampKeepsItsLineAtTheStart() {
		assertEquals("0=first|5000=second|", dump(LrcParser.parseSynced("[-00:01.00]first\n[00:05.00]second")));
	}

	@Test
	void severalNegativeStampsKeepTheirOrderBeforeTheFirstRealOne() {
		Lyrics l = LrcParser.parseSynced("[-00:02.00]a\n[-00:00.50]b\n[00:00.20]c\n[00:05.00]d");
		assertEquals("0=a|1=b|200=c|5000=d|", dump(l));
	}

	@Test
	void aNegativeStampAmongARepeatedLinesStamps() {
		Lyrics l = LrcParser.parseSynced("[-00:00.50][00:30.00]chorus\n[00:10.00]verse");
		assertEquals("0=chorus|10000=verse|30000=chorus|", dump(l));
	}

	@Test
	void aNegativeWordStampIsNotShown() {
		// A file with word timings, shifted earlier as a whole: its first words are stamped before the start too
		assertEquals("0=Hello world|1000=next|",
				dump(LrcParser.parseSynced("[-00:00.50]<-00:00.50>Hello <00:00.20>world<00:00.60>\n[00:01.00]<00:01.00>next")));
	}

	@Test
	void aNegativeStampInsideALineIsNotShown() {
		assertEquals("0=Hello world|", dump(LrcParser.parseSynced("[00:00.00]Hello [-00:00.10]world")));
	}
}
