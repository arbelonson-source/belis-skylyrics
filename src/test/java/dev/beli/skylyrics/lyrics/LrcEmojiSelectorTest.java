package dev.beli.skylyrics.lyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * A lyric line with an emoji's colour-style selector (the VS16 phones put after a heart) drew a dashed box with "VS16"
 * in it after the emoji: in the sky (Lines, Arc and World), in the top bar's lyric line and on the Record card. None of
 * the bundled fonts has a variation selector, so the game draws Unifont's box for it. Lyrics drop them as song names do
 * (Track.clean); the word joiner, zero-width space and byte order mark were already dropped. Made-up lines only.
 */
class LrcEmojiSelectorTest {
	private static String synced(String text) {
		return LrcParser.parseSynced("[00:01.00]" + text).lines().get(0).text();
	}

	@Test
	void anEmojiStyleSelectorIsDropped() {
		assertEquals("Paper hearts \u2764 in the rain", synced("Paper hearts \u2764\uFE0F in the rain"));
		assertEquals("\u2728 glass tide \u2600", synced("\u2728\uFE0F glass tide \u2600\uFE0E"));
		assertEquals("a\u6F22b", synced("a\u6F22\uDB40\uDD00b")); // VS17, a Han variant selector
		assertEquals("Paper hearts \u2764", LrcParser.parsePlain("Paper hearts \u2764\uFE0F", 60000).lines().get(0).text());
	}

	/**
	 * The karaoke fill, the growing word and the World layout's pieces all count the line's own letters and split it at
	 * its spaces, so the line must come out exactly as it would have without the selector: a selector between two words
	 * leaves one space, one inside a word leaves the word whole, and word stamps ("enhanced" LRC) still go.
	 */
	@Test
	void theLineIsTheSameAsWithoutTheSelector() {
		assertEquals(synced("glow bright"), synced("glow \uFE0F bright"));
		assertEquals(synced("glow"), synced("gl\uFE0Fow"));
		assertEquals(synced("\u2764 glow"), synced("\u2764\uFE0F\u2060 glow"));
		assertEquals(synced("Paper hearts"), synced("<00:01.00>Paper\uFE0F <00:01.50>hearts\uFE0F"));
		// Nothing else on the line: a break, as an empty line is
		assertEquals("", LrcParser.parseSynced("[00:01.00]\uFE0F\n[00:05.00]glow").lines().get(0).text());
	}

	@Test
	void joinersAndDirectionMarksStay() {
		// Persian writing and emoji sequences need the joiners; right-to-left lines need their marks
		assertEquals("\u0645\u06CC\u200C\u062E\u0648", synced("\u0645\u06CC\u200C\u062E\u0648"));
		assertEquals("\uD83D\uDC69\u200D\uD83C\uDFA4 sings", synced("\uD83D\uDC69\u200D\uD83C\uDFA4 sings"));
		assertEquals("a\u200Fb\u200Ec", synced("a\u200Fb\u200Ec"));
	}
}
