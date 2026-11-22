package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * The Record card is drawn before the bar in each frame (Fabric puts each element attached after the boss bars right
 * after them, so the card, attached second, comes first), so it keeps clear of the box the bar drew in the frame before.
 * That box counted as gone after a quarter of a second, and at about 4 frames a second (the 1.21.11 test game) it was
 * gone on about every other frame: a top left card under a top bar slid between its corner and its place below the bar
 * (the old code's picture caught it a third of the way down).
 */
class CardBesideBarSlowGameTest {
	private static final long MS = 1_000_000L;
	private static final long T0 = 70_000 * MS;
	/** The top bar's widest box at 640x360 with its clock: it reaches over a top left card's column. */
	private static final float[] TOP_BAR = {138, 5, 520, 5 + TopBarLayout.PILL_H};

	private static long barFrames(int frames, long frameMs) {
		long t = T0;
		for (int i = 0; i < frames; i++) {
			t = T0 + i * frameMs * MS;
			NowPlayingHud.boxesDrawn(null, TOP_BAR, t);
		}
		return t;
	}

	@Test
	void atFourFramesASecondATopLeftCardStaysBelowTheBar() {
		for (long frameMs : new long[] {16, 230, 270, 300}) {
			long last = barFrames(5, frameMs);
			// The next frame: the card first, the bar after it
			float[] bar = NowPlayingHud.topBox(last + frameMs * MS);
			assertArrayEquals(TOP_BAR, bar, "frames " + frameMs + " ms apart");
			assertEquals(TOP_BAR[3] + RecordCard.BAR_GAP, RecordCard.belowTopBar(bar, RecordCard.MARGIN, RecordCard.MARGIN), 0.001f);
		}
	}

	@Test
	void aBarThatStoppedIsGone() {
		long last = barFrames(5, 270);
		assertNull(NowPlayingHud.topBox(last + 3 * 270 * MS));
		last = barFrames(30, 16);
		assertNull(NowPlayingHud.topBox(last + 260 * MS), "a smooth game: a quarter of a second, as before");
		assertNull(NowPlayingHud.bottomBox(last + 16 * MS), "a top bar has no bottom box");
	}
}
