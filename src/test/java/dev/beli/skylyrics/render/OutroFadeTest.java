package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.lyrics.LyricLine;
import dev.beli.skylyrics.lyrics.Lyrics;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Lines layout after the song's last line: once that line has been over for a while it fades, and the line above it
 * (the previous line) must go with it instead of staying up through the whole outro.
 */
class OutroFadeTest {
	private static final long AFTER = LyricsRenderer.STALE_AFTER_MS, FADE = LyricsRenderer.STALE_FADE_MS;

	/** Made-up lines; the last one starts at 20 s and the song goes on long after it. */
	private static final Lyrics SONG = new Lyrics(List.of(
			new LyricLine(10_000, "first made up line"),
			new LyricLine(15_000, "second made up line"),
			new LyricLine(20_000, "last made up line")), true, false);

	@Test
	void theLineAboveTheLastOneGoesWithIt() {
		long over = SONG.lineEnd(2) + AFTER;
		assertEquals(1f, LyricsRenderer.slotFade(SONG, 2, 1, 21_000), 0f, "up while the last line is sung");
		assertEquals(1f, LyricsRenderer.slotFade(SONG, 2, 1, over), 0f, "and until the last line starts to fade");
		assertEquals(0f, LyricsRenderer.slotFade(SONG, 2, 2, over + FADE), 0f, "the last line is gone");
		assertEquals(0f, LyricsRenderer.slotFade(SONG, 2, 1, over + FADE), 0f, "the line above it is gone too");
		assertEquals(0f, LyricsRenderer.slotFade(SONG, 2, 1, 200_000), 0f, "and stays gone through the outro");
		float half = LyricsRenderer.slotFade(SONG, 2, 1, over + FADE / 2);
		assertTrue(half > 0.4f && half < 0.6f, "it fades along with the last line: " + half);
		assertEquals(LyricsRenderer.slotFade(SONG, 2, 2, over + FADE / 2), half, 1e-6f);
	}

	/** Many files end with an empty stamp: the notes of that closing pause, and the last words above them, go too. */
	@Test
	void aClosingPauseAndTheLineAboveItGo() {
		Lyrics closing = new Lyrics(List.of(
				new LyricLine(10_000, "first made up line"),
				new LyricLine(15_000, "last made up line"),
				new LyricLine(19_000, "")), true, false);
		long gone = closing.lineEnd(2) + AFTER + FADE;
		assertEquals(1f, LyricsRenderer.slotFade(closing, 2, 2, 22_000), 0f, "the notes fill up first");
		assertEquals(1f, LyricsRenderer.slotFade(closing, 2, 1, 22_000), 0f);
		assertEquals(0f, LyricsRenderer.slotFade(closing, 2, 2, gone), 0f, "the notes go");
		assertEquals(0f, LyricsRenderer.slotFade(closing, 2, 1, gone), 0f, "the last words go");
	}

	/** In the middle of the song the next line is still to come: only the line being sung fades, as before. */
	@Test
	void midSongOnlyTheLineBeingSungFades() {
		Lyrics gap = new Lyrics(List.of(
				new LyricLine(10_000, "first made up line"),
				new LyricLine(15_000, "second made up line"),
				new LyricLine(71_000, "third made up line")), true, false);
		assertEquals(0f, LyricsRenderer.slotFade(gap, 1, 1, 40_000), 0f);
		assertEquals(1f, LyricsRenderer.slotFade(gap, 1, 0, 40_000), 0f);
		assertEquals(1f, LyricsRenderer.slotFade(gap, 1, 2, 40_000), 0f);
		// The pass that draws the line before (a fade or rise just after the change) leaves its neighbours alone too
		assertEquals(1f, LyricsRenderer.slotFade(SONG, 1, 2, 20_500), 0f);
	}

	@Test
	void unsyncedLyricsAndTheWaitBeforeTheFirstLineStay() {
		Lyrics plain = new Lyrics(SONG.lines(), false, false);
		assertEquals(1f, LyricsRenderer.slotFade(plain, 2, 1, 999_000), 0f);
		assertEquals(1f, LyricsRenderer.slotFade(plain, 2, 2, 999_000), 0f);
		assertEquals(1f, LyricsRenderer.slotFade(SONG, -1, -1, 0), 0f);
		assertEquals(1f, LyricsRenderer.slotFade(SONG, -1, 0, 0), 0f);
	}
}
