package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SeekBackTest {
	@Test
	void aSeekBackOrARestartCounts() {
		assertTrue(LyricsRenderer.jumpedBack(60_000, 20_000));
		assertTrue(LyricsRenderer.jumpedBack(200_000, 0)); // a loop
		assertTrue(LyricsRenderer.jumpedBack(10_000, 10_000 - LyricsRenderer.SEEK_BACK_MS - 1));
	}

	/** A seek back of a second inside a line (the report: the demo song, back from "keep" to "Neon skyline,") counts. */
	@Test
	void aSeekBackOfAboutASecondCounts() {
		assertTrue(LyricsRenderer.jumpedBack(60_000, 59_000));
		assertTrue(LyricsRenderer.jumpedBack(60_000, 59_200));
	}

	/** The tracker lets 350 ms pass as jitter, so a frame-to-frame drop up to twice that never counts. */
	@Test
	void positionNoiseUpToTwiceTheTrackersJitterDoesNotCount() {
		for (long drop = 0; drop <= 700; drop += 50) {
			assertFalse(LyricsRenderer.jumpedBack(60_000, 60_000 - drop), "a drop of " + drop + " ms");
		}
	}

	@Test
	void positionNoiseAPauseAndGoingForwardDoNot() {
		assertFalse(LyricsRenderer.jumpedBack(10_000, 10_000)); // paused
		assertFalse(LyricsRenderer.jumpedBack(10_000, 9_700)); // 0.3 s of noise
		assertFalse(LyricsRenderer.jumpedBack(10_000, 10_000 - LyricsRenderer.SEEK_BACK_MS)); // right at the threshold
		assertFalse(LyricsRenderer.jumpedBack(10_000, 10_016)); // an ordinary frame
		assertFalse(LyricsRenderer.jumpedBack(10_000, 90_000)); // a fast skip forward
	}

	@Test
	void noEarlierFrameIsNoJump() {
		assertFalse(LyricsRenderer.jumpedBack(Long.MIN_VALUE, 0));
		assertFalse(LyricsRenderer.jumpedBack(Long.MIN_VALUE, 50_000));
	}
}
