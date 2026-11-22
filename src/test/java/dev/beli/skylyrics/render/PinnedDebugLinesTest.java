package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.config.SkyLyricsConfig.BarPosition;
import dev.beli.skylyrics.config.SkyLyricsConfig.CardCorner;
import org.junit.jupiter.api.Test;

/**
 * From 1.21.9 a player can set any of F3's lines to show always, with F3 closed (the debug options screen; its Performance
 * profile does it for the FPS line). The game draws them in the top corners like F3's text, but the bar and the Record
 * card only knew F3 as open or closed, so with it closed they were drawn under those lines: a top bar with its clock on
 * a narrow screen, and a top left card on any screen. Now a top bar and a top card move down below the pinned lines
 * their width reaches, and anything they reach at the bottom hides, as for F3's text.
 */
class PinnedDebugLinesTest {
	/** The FPS line ("60 fps T: 60 vsync", about 95 wide with the game's letters) and the empty row after it, top left. */
	private static final float[][] FPS = DebugText.boxes(new int[] {95, -1}, true, 480);

	/** {@code left} and {@code right} rows of lines this wide in each column. */
	private static float[][] lines(int guiW, int left, int leftW, int right, int rightW) {
		int[] l = new int[left], r = new int[right];
		java.util.Arrays.fill(l, leftW);
		java.util.Arrays.fill(r, rightW);
		float[][] a = DebugText.boxes(l, true, guiW), b = DebugText.boxes(r, false, guiW);
		float[][] both = new float[a.length + b.length][];
		System.arraycopy(a, 0, both, 0, a.length);
		System.arraycopy(b, 0, both, a.length, b.length);
		return both;
	}

	/** The top bar at its widest with the clock and stats at the reference look, its top at {@code top}. */
	private static float[] topBar(int guiW, float top) {
		TopBarLayout.Fit fit = TopBarLayout.fit(guiW, 1, 30, 45);
		return TopBarLayout.topBarBox(guiW, 1, fit.pill(), 30, fit.clock(), 45, fit.stats(), top);
	}

	private static float[] bottomBar(int guiW, int guiH) {
		TopBarLayout.Fit fit = TopBarLayout.fit(guiW, 1, 30, 45);
		return TopBarLayout.barBox(guiW, 1, fit.pill(), 30, fit.clock(), 45, fit.stats(), TopBarLayout.bottomTop(guiH, false));
	}

	private static float[] cardBox(float[] at) {
		return new float[] {at[0], at[1], at[0] + RecordCard.W, at[1] + RecordCard.H};
	}

	/** A top corner card where it goes with the pinned lines (no effects, boss bars or top bar). */
	private static float[] topCard(CardCorner corner, int guiW, int guiH, float[][] pinned) {
		return cardBox(RecordCard.corner(corner, guiW, guiH, 0, RecordCard.H, true, false, RecordCard.pinnedClear(corner, guiW, pinned)));
	}

	@Test
	void aTopBarOnANarrowScreenMovesBelowAPinnedFpsLine() {
		// Auto GUI scale at 1920x1080 is 480x270: the bar with its clock starts 64 in, the FPS line's box ends 98 in
		float[] bar = topBar(480, 5);
		assertTrue(DebugText.covers(bar, FPS), "where it sat, the line was drawn over the bar");
		float top = NowPlayingHud.topBelowPinned(5, bar, FPS, 270);
		assertFalse(DebugText.covers(topBar(480, top), FPS), "it moves below the line, at " + top);
		assertEquals(FPS[0][3] + TopBarLayout.DODGE_GAP, top, 0.01f, "just below it");
		assertFalse(NowPlayingHud.pinnedHides(false, bar, FPS, 270), "and keeps showing");
	}

	@Test
	void aTopBarThePinnedLinesDontReachStaysWhereItIs() {
		// 1920x1080 at GUI scale 3: the bar starts 144 in, right of the FPS line
		assertEquals(5, NowPlayingHud.topBelowPinned(5, topBar(640, 5), FPS, 360));
		// Already below the line (under a boss bar): no further
		assertEquals(40, NowPlayingHud.topBelowPinned(40, topBar(480, 40), FPS, 270));
		assertEquals(5, NowPlayingHud.topBelowPinned(5, topBar(480, 5), NowPlayingHud.NO_LINES, 270), "nothing pinned");
	}

	@Test
	void aTopBarMovesBelowPinnedLinesOnTheRightToo() {
		// The memory and system lines pinned: six rows down the right side reach the stats at 640 wide
		float[][] right = lines(640, 0, 0, 6, 200);
		float[] bar = topBar(640, 5);
		assertTrue(DebugText.covers(bar, right));
		float top = NowPlayingHud.topBelowPinned(5, bar, right, 360);
		assertFalse(DebugText.covers(topBar(640, top), right), "at " + top);
	}

	@Test
	void pinnedLinesPastTheTopThirdHideATopBarAsF3Does() {
		// Twelve wide rows on each side at 480x270 end 109 down, past the top third (90)
		float[][] many = lines(480, 12, 200, 12, 200);
		float[] bar = topBar(480, 5);
		assertTrue(Float.isNaN(NowPlayingHud.topBelowPinned(5, bar, many, 270)));
		assertTrue(NowPlayingHud.pinnedHides(false, bar, many, 270));
	}

	@Test
	void aTopLeftCardMovesBelowAPinnedFpsLineOnAnyScreen() {
		for (int[] size : new int[][] {{480, 270}, {640, 360}, {960, 540}}) {
			int w = size[0], h = size[1];
			assertTrue(DebugText.covers(cardBox(RecordCard.corner(CardCorner.TOP_LEFT, w, h)), FPS), "where it sat, at " + w);
			float[] box = topCard(CardCorner.TOP_LEFT, w, h, FPS);
			assertFalse(DebugText.covers(box, FPS), "moved below the line at " + w + "x" + h);
			assertFalse(RecordCard.pinnedCovers(CardCorner.TOP_LEFT, box, FPS, h), "and shows at " + w + "x" + h);
		}
	}

	@Test
	void aTopRightCardMovesOnlyForLinesOnTheRight() {
		assertEquals(0, RecordCard.pinnedClear(CardCorner.TOP_RIGHT, 640, FPS), "the FPS line is on the left");
		float[][] right = lines(640, 1, 95, 6, 150);
		float[] box = topCard(CardCorner.TOP_RIGHT, 640, 360, right);
		assertFalse(DebugText.covers(box, right));
		assertFalse(RecordCard.pinnedCovers(CardCorner.TOP_RIGHT, box, right, 360));
		assertEquals(0, RecordCard.pinnedClear(CardCorner.BOTTOM_LEFT, 640, right), "a bottom card doesn't move for them");
	}

	@Test
	void pinnedLinesPastTheTopThirdHideATopCard() {
		float[][] many = lines(480, 12, 200, 12, 200);
		float[] box = topCard(CardCorner.TOP_LEFT, 480, 270, many);
		assertTrue(RecordCard.pinnedCovers(CardCorner.TOP_LEFT, box, many, 270));
	}

	@Test
	void atTheBottomTheBarAndCardHideOnlyWhenPinnedLinesReachThem() {
		// 1280x720 at GUI scale 3 (426x240), twenty rows pinned on the left: they run down past the bottom bar and card
		float[][] long20 = lines(426, 20, 150, 0, 0);
		assertTrue(NowPlayingHud.pinnedHides(true, bottomBar(426, 240), long20, 240));
		float[] card = cardBox(RecordCard.corner(CardCorner.BOTTOM_LEFT, 426, 240));
		assertTrue(RecordCard.pinnedCovers(CardCorner.BOTTOM_LEFT, card, long20, 240));
		// The FPS line alone ends far above them
		assertFalse(NowPlayingHud.pinnedHides(true, bottomBar(426, 240), FPS, 240));
		assertFalse(RecordCard.pinnedCovers(CardCorner.BOTTOM_LEFT, card, FPS, 240));
		assertFalse(RecordCard.pinnedCovers(CardCorner.BOTTOM_RIGHT, cardBox(RecordCard.corner(CardCorner.BOTTOM_RIGHT, 426, 240)), FPS,
				240));
	}

	@Test
	void theLinesAreMeasuredWithF3ClosedForATopBarOrCard() {
		SkyLyricsConfig cfg = new SkyLyricsConfig();
		cfg.barPosition = BarPosition.TOP;
		cfg.recordCard = false;
		assertTrue(DebugText.measures(cfg, false), "F3 closed: pinned lines sit where a top bar is");
		assertFalse(DebugText.measures(cfg, true), "F3 open: a top bar hides for it anyway, as before");
		cfg.barPosition = BarPosition.BOTTOM;
		assertTrue(DebugText.measures(cfg, true));
		cfg.nowPlayingHud = false;
		cfg.recordCard = true;
		cfg.cardCorner = CardCorner.TOP_LEFT;
		assertTrue(DebugText.measures(cfg, false));
		assertFalse(DebugText.measures(cfg, true));
		cfg.cardCorner = CardCorner.BOTTOM_LEFT;
		assertTrue(DebugText.measures(cfg, true));
		cfg.enabled = false;
		assertFalse(DebugText.measures(cfg, false));
		assertFalse(DebugText.measures(cfg, true));
	}
}
