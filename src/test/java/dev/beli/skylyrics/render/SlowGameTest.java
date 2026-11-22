package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The game draws F3's text (and the lines pinned to show with F3 closed) after the HUD, so the bar and the Record card
 * read what it drew in the frame before. Anything older than a quarter of a second counted as gone, and the test game
 * on 1.21.11 runs at about 4 frames a second (a quarter of a second or more each): there the pinned lines counted as gone
 * on about every other frame, so the top bar and a top right Record card stayed under the pinned system lines.
 */
class SlowGameTest {
	private static final long MS = 1_000_000L;
	/** Any start time: nanoTime counts from an arbitrary point. */
	private static final long T0 = 50_000 * MS;

	private static final float[][] FPS = DebugText.boxes(new int[] {95, -1}, true, 640);
	private static final float[][] SPECS = DebugText.boxes(new int[] {110, 280, 190, 210, 300}, false, 640);

	/** The game draws both columns once a frame, {@code frameMs} apart, for {@code frames} frames from {@link #T0}. */
	private static long playFrames(int frames, long frameMs) {
		long t = T0;
		for (int i = 0; i < frames; i++) {
			t = T0 + i * frameMs * MS;
			DebugText.record(FPS, true, t);
			DebugText.record(SPECS, false, t + MS);
		}
		return t;
	}

	@Test
	void atFourFramesASecondTheLastFramesLinesStillCount() {
		// 4.4 frames a second on average, the slowest at 4 (the 1.21.11 test game): 230 to 270 ms a frame
		for (long frameMs : new long[] {230, 250, 270, 300}) {
			long last = playFrames(5, frameMs);
			float[][] lines = DebugText.recent(last + frameMs * MS);
			assertNotNull(lines, "the frame after one " + frameMs + " ms long");
			assertEquals(FPS.length + SPECS.length, lines.length, "both columns, " + frameMs + " ms");
		}
	}

	@Test
	void linesTheGameStoppedDrawingAreGone() {
		// F3 closed with nothing pinned: the game draws no column any more
		long last = playFrames(5, 250);
		assertNull(DebugText.recent(last + 2 * 250 * MS + 2 * MS), "two slow frames later");
		last = playFrames(30, 16);
		assertNotNull(DebugText.recent(last + 16 * MS));
		assertNull(DebugText.recent(last + 260 * MS), "a smooth game: a quarter of a second, as before");
	}

	@Test
	void theRuleItself() {
		assertTrue(DrawnLately.drawn(T0 + 520 * MS, T0 + 260 * MS, T0), "the last of two frames 260 ms apart");
		assertFalse(DrawnLately.drawn(T0 + 800 * MS, T0 + 260 * MS, T0), "three frames on");
		assertTrue(DrawnLately.drawn(T0 + 240 * MS, T0, T0 - 16 * MS), "a quarter of a second at least");
		assertFalse(DrawnLately.drawn(T0 + 260 * MS, T0, T0 - 16 * MS));
		assertFalse(DrawnLately.drawn(T0 + 1_100 * MS, T0, T0 - 10_000 * MS), "never more than a second");
		assertFalse(DrawnLately.drawn(T0, 0, 0), "never drawn");
	}
}
