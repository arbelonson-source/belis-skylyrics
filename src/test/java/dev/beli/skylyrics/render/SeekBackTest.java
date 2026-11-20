package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SeekBackTest {
	private static final long SEC = 1_000_000_000L;

	@Test
	void anAskedSeekBackCountsFromJustAboveTheTrackersJitter() {
		assertTrue(LyricsRenderer.jumpedBack(60_000, 59_000, true), "1000 ms");
		assertTrue(LyricsRenderer.jumpedBack(60_000, 59_400, true), "600 ms");
		assertTrue(LyricsRenderer.jumpedBack(60_000, 20_000, true));
		assertFalse(LyricsRenderer.jumpedBack(60_000, 59_700, true), "300 ms is noise");
		assertFalse(LyricsRenderer.jumpedBack(60_000, 60_000 - LyricsRenderer.SEEK_BACK_MS, true), "right at the limit");
		assertFalse(LyricsRenderer.jumpedBack(60_000, 60_500, true), "forward");
	}

	/** The review's case: a lagging player position snaps back by a second during normal playback. */
	@Test
	void aDropWithoutAskedSeekNeedsMoreThan1500() {
		assertFalse(LyricsRenderer.jumpedBack(60_000, 59_000, false), "1000 ms, no seek");
		assertFalse(LyricsRenderer.jumpedBack(60_000, 58_600, false), "1400 ms, no seek");
		assertFalse(LyricsRenderer.jumpedBack(60_000, 58_500, false), "right at 1500");
		assertTrue(LyricsRenderer.jumpedBack(60_000, 58_000, false), "2000 ms, no seek");
		assertTrue(LyricsRenderer.jumpedBack(200_000, 0, false), "a loop");
	}

	@Test
	void aSeekCountsAsAskedOnlyInsideTheWindow() {
		long now = 100 * SEC;
		assertTrue(LyricsRenderer.seekRecent(now - 1 * SEC, now));
		assertTrue(LyricsRenderer.seekRecent(now - LyricsRenderer.SEEK_WINDOW_NANOS + 1, now));
		assertFalse(LyricsRenderer.seekRecent(now - LyricsRenderer.SEEK_WINDOW_NANOS, now), "older: like no seek");
		assertFalse(LyricsRenderer.seekRecent(now - 10 * SEC, now));
		assertFalse(LyricsRenderer.seekRecent(0, now), "never asked");
	}

	@Test
	void positionNoiseAPauseAndGoingForwardDoNot() {
		for (long drop = 0; drop <= 400; drop += 50) {
			assertFalse(LyricsRenderer.jumpedBack(60_000, 60_000 - drop, true), "asked, drop " + drop);
			assertFalse(LyricsRenderer.jumpedBack(60_000, 60_000 - drop, false), "unasked, drop " + drop);
		}
		assertFalse(LyricsRenderer.jumpedBack(10_000, 10_016, false)); // an ordinary frame
		assertFalse(LyricsRenderer.jumpedBack(10_000, 90_000, true)); // a fast skip forward
	}

	@Test
	void noEarlierFrameIsNoJump() {
		assertFalse(LyricsRenderer.jumpedBack(Long.MIN_VALUE, 0, true));
		assertFalse(LyricsRenderer.jumpedBack(Long.MIN_VALUE, 50_000, false));
	}
}
