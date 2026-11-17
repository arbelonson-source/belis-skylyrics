package dev.beli.skylyrics.lyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/** Synced text that is only time stamps, no words, next to plain lyrics that have some. */
class StampsOnlyLyricsTest {
	private static final String STAMPS = "[00:00.00]\n[00:30.00]";

	@Test
	void plainLyricsWithWordsAreUsedWhenTheSyncedTextHasNone() {
		Lyrics l = LyricsService.parse(new LrclibClient.Result(STAMPS, "real words here\nmore words", false), 200_000);
		assertFalse(l.isEmpty());
		assertFalse(l.instrumental());
	}

	@Test
	void noWordsAnywhereStaysInstrumental() {
		assertSame(Lyrics.INSTRUMENTAL, LyricsService.parse(new LrclibClient.Result(STAMPS, "", false), 200_000));
		assertSame(Lyrics.INSTRUMENTAL, LyricsService.parse(new LrclibClient.Result(STAMPS, null, false), 200_000));
	}

	@Test
	void theSitesInstrumentalFlagStillWins() {
		assertSame(Lyrics.INSTRUMENTAL, LyricsService.parse(new LrclibClient.Result(STAMPS, "real words here", true), 200_000));
	}

	@Test
	void syncedLyricsWithWordsAreStillPreferred() {
		Lyrics l = LyricsService.parse(new LrclibClient.Result("[00:01.00]sung", "plain", false), 200_000);
		assertEquals(1, l.lines().size());
	}

	@Test
	void aPlainTextThatIsOnlyAMarkerIsNotWords() {
		for (String marker : new String[] {"Instrumental", "(Instrumental)", "\u266a Instrumental \u266a", "[Instrumental]", "(music)", "\uD83C\uDFB5"}) {
			assertSame(Lyrics.INSTRUMENTAL, LyricsService.parse(new LrclibClient.Result(STAMPS, marker, false), 200_000), marker);
		}
	}

	@Test
	void aPlainTextWithWordsBesideAMarkerIsStillLyrics() {
		Lyrics l = LyricsService.parse(new LrclibClient.Result(STAMPS, "(Instrumental)\nreal words here", false), 200_000);
		assertFalse(l.isEmpty());
	}
}
