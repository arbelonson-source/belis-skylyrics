package dev.beli.skylyrics.lyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class LyricsTest {
	private static final Lyrics SONG = new Lyrics(List.of(
			new LyricLine(1_000, "Neon on the water"),
			new LyricLine(4_000, "City lights are calling me home tonight"),
			new LyricLine(30_000, "Hold on"),
			new LyricLine(36_000, "Skyline")), true, false);

	@Test
	void aLineLastsTillTheNextWithinLimits() {
		assertEquals(4_000, SONG.lineEnd(0));
		assertEquals(16_000, SONG.lineEnd(1));
		assertEquals(36_000, SONG.lineEnd(2));
		assertEquals(41_000, SONG.lineEnd(3));
	}

	@Test
	void wordsAreSungWithinTheLineWhenThereIsLittleTime() {
		assertEquals(4_000, SONG.sungEnd(0));
	}

	@Test
	void aLineBeforeALongGapIsntSungAcrossAllOfIt() {
		int chars = "City lights are calling me home tonight".length();
		assertEquals(4_000 + Lyrics.SLOWEST_EXTRA_MS + Lyrics.SLOWEST_MS_PER_CHAR * chars, SONG.sungEnd(1));
		assertEquals(30_000 + Lyrics.SLOWEST_EXTRA_MS + Lyrics.SLOWEST_MS_PER_CHAR * 7, SONG.sungEnd(2));
	}
}
