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
		Lyrics lyrics = LrcParser.parseSynced("[00:10.00]" + TEXT + "\n[00:" + String.format("%05.2f", 10 + lineMs / 1000f) + "]next line here\n[00:30.00]end");
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

}
