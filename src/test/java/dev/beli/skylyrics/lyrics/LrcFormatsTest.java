package dev.beli.skylyrics.lyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

/** Lyric files in the shapes found in the wild. */
class LrcFormatsTest {
	private static String dump(Lyrics l) {
		StringBuilder b = new StringBuilder();
		for (LyricLine x : l.lines()) {
			b.append(x.timeMs()).append('=').append(x.text()).append('|');
		}
		return b.toString();
	}

	@Test
	void aCommaBeforeTheFractionIsAFraction() {
		assertEquals("10500=hello|20500=world|", dump(LrcParser.parseSynced("[00:10,50]hello\n[00:20,50]world")));
		assertEquals("10500=hello|", dump(LrcParser.parseSynced("[00:10.50]hello")));
	}

	@Test
	void linesPushedBeforeTheStartKeepTheirOrderAsSeparateLines() {
		Lyrics l = LrcParser.parseSynced("[offset:+2000]\n[00:01.00]one\n[00:01.80]two\n[00:05.00]three");
		assertEquals("0=one|1=two|3000=three|", dump(l));
	}

	@Test
	void wordTagsBetweenSpacedWordsLeaveASpace() {
		assertEquals("Hello world", LrcParser.parseSynced("[00:01.00]<00:01.00>Hello<00:01.50>world").get(0).text());
		assertEquals("привет мир", LrcParser.parseSynced("[00:01.00]<00:01.00>привет<00:01.50>мир").get(0).text());
	}

	@Test
	void wordTagsNeverSplitScriptsWithoutSpaces() {
		assertEquals("你好", LrcParser.parseSynced("[00:01.00]<00:01.00>你<00:01.50>好").get(0).text());
		assertEquals("こんにちは", LrcParser.parseSynced("[00:01.00]<00:01.00>こん<00:01.50>にちは").get(0).text());
		assertEquals("สวัสดี", LrcParser.parseSynced("[00:01.00]<00:01.00>สวั<00:01.50>สดี").get(0).text());
	}

	@Test
	void wordTagsWithTheSpaceAlreadyThereDoNotDouble() {
		assertEquals("Hello world", LrcParser.parseSynced("[00:01.00]<00:01.00>Hello <00:01.50>world").get(0).text());
		assertEquals("Hello world", LrcParser.parseSynced("[00:01.00]<00:01.00>Hello<00:01.50> world").get(0).text());
		assertFalse(LrcParser.parseSynced("[00:01.00]<00:01.00>Hello<00:01.50>, world").get(0).text().contains(" ,"));
	}
}
