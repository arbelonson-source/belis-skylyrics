package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.lyrics.LrcParser;
import dev.beli.skylyrics.lyrics.Lyrics;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Fast lines: every word of a short line comes out, and nothing flashes as one leaves. */
class FastLinesTest {
	private static final String TEXT = "alpha bravo charlie delta echo foxtrot";

	/** Plays the world words of line 0 frame by frame up to the moment the next line takes over; how many pieces spawned. */
	private static int spawnedByFlip(long lineMs) {
		Lyrics lyrics = LrcParser.parseSynced("[00:10.00]" + TEXT + "\n[00:" + String.format(java.util.Locale.ROOT, "%05.2f", 10 + lineMs / 1000f) + "]next line here\n[00:30.00]end");
		List<int[]> chunks = WorldWords.chunks(TEXT);
		int total = TEXT.codePointCount(0, TEXT.length());
		int spawned = 0, count = 0;
		// The renderer's time runs LEAD_MS ahead of the song's position
		for (long pos = 9_000; pos < 11_000; pos += 16) {
			long time = pos + LyricsRenderer.LEAD_MS;
			if (lyrics.indexAt(time) != 0) {
				continue;
			}
			float sung = LyricsRenderer.sungChars(lyrics, 0, time, total);
			int due = 0;
			while (due < chunks.size() && chunks.get(due)[0] <= sung) {
				due++;
			}
			int from = WorldWords.skipTo(spawned, due, false, false);
			count += due - from;
			spawned = due;
		}
		return count;
	}

	@Test
	void everyPieceOfAShortLineSpawnsBeforeTheNextOneTakesOver() {
		int all = WorldWords.chunks(TEXT).size();
		assertEquals(all, spawnedByFlip(300), "0.3 s line");
		assertEquals(all, spawnedByFlip(200), "0.2 s line");
		assertEquals(all, spawnedByFlip(500), "0.5 s line");
		assertEquals(all, spawnedByFlip(1000), "1 s line");
	}

	@Test
	void aSlowLinesWordsStillFollowTheVoice() {
		Lyrics lyrics = LrcParser.parseSynced("[00:10.00]" + TEXT + "\n[00:14.00]next\n[00:30.00]end");
		int total = TEXT.codePointCount(0, TEXT.length());
		// Nothing is out before the stamp, about half is out half way through, all of it before the next line
		assertEquals(0f, LyricsRenderer.sungChars(lyrics, 0, 10_000 + LyricsRenderer.LEAD_MS, total), 0.01f);
		float mid = LyricsRenderer.sungChars(lyrics, 0, 12_000 + LyricsRenderer.LEAD_MS, total);
		assertTrue(mid > total * 0.4f && mid < total * 0.6f, "mid " + mid);
		assertTrue(LyricsRenderer.sungChars(lyrics, 0, 14_000 + LyricsRenderer.LEAD_MS - 200, total) >= total * 0.99f);
	}

	@Test
	void aWordNotOutYetFadesInWhileItsLineLeavesInsteadOfFlashing() {
		assertEquals(0f, LyricsRenderer.leaveReveal(0f, 0f), 1e-6f, "no pop at the start of the leaving");
		float some = LyricsRenderer.leaveReveal(0f, 0.1f);
		assertTrue(some > 0f && some < 1f, "ramping: " + some);
		assertTrue(LyricsRenderer.leaveReveal(0f, 0.2f) > some);
		assertEquals(1f, LyricsRenderer.leaveReveal(0f, 0.5f), 1e-6f, "all out before the line is gone");
		assertEquals(1f, LyricsRenderer.leaveReveal(1f, 0f), 1e-6f, "a word already out stays out");
		assertEquals(0.6f, LyricsRenderer.leaveReveal(0.6f, 0f), 1e-6f);
	}

	@Test
	void aLongWordIsLaidOutAtTheSizeItIsDrawnAt() {
		float[] at = {0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180};
		float size = 0.25f, distance = 12;
		float fit = WorldWords.fitted(size, at, 1, distance);
		assertTrue(fit < size, "a long word shrinks: " + fit);
		double degrees = Math.toDegrees(2 * Math.atan(at[at.length - 1] * fit / 2 / distance));
		assertEquals(WorldWords.MAX_CARD_DEGREES, degrees, 0.01);
		assertEquals(size, WorldWords.fitted(size, new float[] {0, 10, 20}, 1, distance), 1e-6f, "a short word keeps its size");
	}
}
