package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.config.SkyLyricsConfig.CardCorner;
import org.junit.jupiter.api.Test;

class RecordCardTest {
	@Test
	void theCardSitsInTheChosenCorner() {
		int w = 640, h = 360;
		float m = RecordCard.MARGIN;
		assertArrayEquals(new float[] {m, m}, RecordCard.corner(CardCorner.TOP_LEFT, w, h));
		assertArrayEquals(new float[] {w - m - RecordCard.W, m}, RecordCard.corner(CardCorner.TOP_RIGHT, w, h));
		assertArrayEquals(new float[] {m, h - m - RecordCard.H}, RecordCard.corner(CardCorner.BOTTOM_LEFT, w, h));
		assertArrayEquals(new float[] {w - m - RecordCard.W, h - m - RecordCard.H}, RecordCard.corner(CardCorner.BOTTOM_RIGHT, w, h));
	}

	@Test
	void onANarrowScreenABottomCardClimbsAboveTheHotbar() {
		int w = 427, h = 240;
		for (CardCorner c : new CardCorner[] {CardCorner.BOTTOM_LEFT, CardCorner.BOTTOM_RIGHT}) {
			float[] at = RecordCard.corner(c, w, h);
			assertTrue(at[1] + RecordCard.H <= h - RecordCard.HOTBAR_UP + 1e-3f, c.name());
		}
		// The top corners never move
		assertEquals(RecordCard.MARGIN, RecordCard.corner(CardCorner.TOP_LEFT, w, h)[1]);
	}

	@Test
	void aTopRightCardMovesUnderTheEffectIcons() {
		int w = 640, h = 360;
		assertEquals(RecordCard.MARGIN, RecordCard.corner(CardCorner.TOP_RIGHT, w, h, 0)[1]);
		assertTrue(RecordCard.corner(CardCorner.TOP_RIGHT, w, h, RecordCard.GOOD_ROW)[1] > RecordCard.GOOD_ROW);
		assertTrue(RecordCard.corner(CardCorner.TOP_RIGHT, w, h, RecordCard.BAD_ROW)[1] > RecordCard.BAD_ROW);
		// The icons only sit on the right, so the other corners stay put
		assertEquals(RecordCard.MARGIN, RecordCard.corner(CardCorner.TOP_LEFT, w, h, RecordCard.BAD_ROW)[1]);
		assertArrayEquals(RecordCard.corner(CardCorner.BOTTOM_RIGHT, w, h),
				RecordCard.corner(CardCorner.BOTTOM_RIGHT, w, h, RecordCard.BAD_ROW));
	}

	@Test
	void theLyricStripMakesTheCardTallerWithoutLeavingTheScreen() {
		float tall = RecordCard.height(true);
		assertEquals(RecordCard.H + RecordCard.LYRIC_H, tall);
		assertEquals(RecordCard.H, RecordCard.height(false));
		for (int[] screen : new int[][] {{640, 360}, {427, 240}}) {
			int w = screen[0], h = screen[1];
			for (CardCorner c : CardCorner.values()) {
				float[] at = RecordCard.corner(c, w, h, RecordCard.BAD_ROW, tall);
				assertTrue(at[1] >= 0 && at[1] + tall <= h, c.name() + " at " + w + "x" + h);
			}
			// Bottom cards grow upwards, so their bottom edge stays where it was
			assertEquals(RecordCard.corner(CardCorner.BOTTOM_RIGHT, w, h)[1] + RecordCard.H,
					RecordCard.corner(CardCorner.BOTTOM_RIGHT, w, h, 0, tall)[1] + tall, 1e-3f);
		}
	}

	@Test
	void playedAndLeftAlwaysMakeTheSongLength() {
		assertArrayEquals(new String[] {"0:00", "-3:40"}, RecordCard.times(0, 220_400));
		assertArrayEquals(new String[] {"0:01", "-3:39"}, RecordCard.times(1_500, 220_400));
		assertArrayEquals(new String[] {"1:05", "-2:35"}, RecordCard.times(65_999, 219_600));
		// A player that reports a little past the end, or a time before the start
		assertArrayEquals(new String[] {"3:40", "0:00"}, RecordCard.times(221_000, 220_000));
		assertArrayEquals(new String[] {"3:40", "0:00"}, RecordCard.times(220_000, 220_000));
		assertArrayEquals(new String[] {"3:39", "-0:01"}, RecordCard.times(219_000, 220_000));
		assertArrayEquals(new String[] {"0:00", "-3:40"}, RecordCard.times(-300, 220_000));
	}

	@Test
	void aLongLineSlidesFromItsStartToItsEnd() {
		assertEquals(0, RecordCard.lyricShift(100, 140, 0.5f));
		assertEquals(0, RecordCard.lyricShift(200, 140, 0));
		assertEquals(0, RecordCard.lyricShift(200, 140, 0.1f));
		assertEquals(60, RecordCard.lyricShift(200, 140, 0.8f), 1e-4);
		assertEquals(60, RecordCard.lyricShift(200, 140, 1), 1e-4);
		float last = -1;
		for (float t = 0; t <= 1; t += 0.05f) {
			float shift = RecordCard.lyricShift(200, 140, t);
			assertTrue(shift >= last);
			last = shift;
		}
	}

	@Test
	void timesReadLikeAMusicPlayer() {
		assertEquals("0:00", RecordCard.clock(0));
		assertEquals("0:00", RecordCard.clock(-500));
		assertEquals("0:09", RecordCard.clock(9_999));
		assertEquals("3:07", RecordCard.clock(187_000));
		assertEquals("1:02:05", RecordCard.clock(3_725_000));
	}

	@Test
	void timesKeepPlainDigitsInEveryLanguage() {
		java.util.Locale was = java.util.Locale.getDefault();
		java.util.Locale wasFormat = java.util.Locale.getDefault(java.util.Locale.Category.FORMAT);
		java.util.Locale wasDisplay = java.util.Locale.getDefault(java.util.Locale.Category.DISPLAY);
		try {
			java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG-u-nu-arab"));
			assertEquals("3:07", RecordCard.clock(187_000));
		} finally {
			// setDefault(Locale) set the formatting and display languages too: each goes back as it was, for the tests after
			java.util.Locale.setDefault(was);
			java.util.Locale.setDefault(java.util.Locale.Category.FORMAT, wasFormat);
			java.util.Locale.setDefault(java.util.Locale.Category.DISPLAY, wasDisplay);
		}
	}

	private static boolean climbs(CardCorner c, int w, int h, boolean rightHanded, boolean indicator) {
		float[] at = RecordCard.corner(c, w, h, 0, RecordCard.H, rightHanded, indicator);
		return at[1] + RecordCard.H <= h - RecordCard.HOTBAR_UP + 1e-3f;
	}

	@Test
	void aBottomLeftCardClearsTheOffHandSlotOfARightHandedPlayer() {
		// 1600x900 at GUI scale 3 is 533 wide: the card (6..162) is clear of the hotbar (176..358) but not of the off-hand slot (147..169)
		assertTrue(climbs(CardCorner.BOTTOM_LEFT, 533, 300, true, false));
		assertTrue(climbs(CardCorner.BOTTOM_LEFT, 506, 285, true, false));
		// Wide enough for the slot too: stays in the corner
		assertTrue(!climbs(CardCorner.BOTTOM_LEFT, 640, 360, true, false));
		// The right side has no off-hand slot for a right-handed player, so it can stay low
		assertTrue(!climbs(CardCorner.BOTTOM_RIGHT, 533, 300, true, false));
	}

	@Test
	void aLeftHandedPlayerHasTheOffHandSlotOnTheRight() {
		assertTrue(climbs(CardCorner.BOTTOM_RIGHT, 533, 300, false, false));
		assertTrue(!climbs(CardCorner.BOTTOM_LEFT, 533, 300, false, false));
	}

	@Test
	void theHotbarAttackIndicatorSitsOnTheOtherSide() {
		// Right-handed: the indicator is right of the hotbar, 24 units past it
		assertTrue(climbs(CardCorner.BOTTOM_RIGHT, 520, 300, true, true));
		assertTrue(!climbs(CardCorner.BOTTOM_RIGHT, 520, 300, true, false));
		// Left-handed: it is on the left
		assertTrue(climbs(CardCorner.BOTTOM_LEFT, 520, 300, false, true));
		assertTrue(!climbs(CardCorner.BOTTOM_LEFT, 520, 300, false, false));
	}

	@Test
	void theCardNeverOverlapsWhatSitsBesideTheHotbarAtAnyWidth() {
		for (int w = 300; w <= 700; w += 7) {
			for (boolean right : new boolean[] {true, false}) {
				for (boolean ind : new boolean[] {true, false}) {
					for (CardCorner c : new CardCorner[] {CardCorner.BOTTOM_LEFT, CardCorner.BOTTOM_RIGHT}) {
						float[] at = RecordCard.corner(c, w, 300, 0, RecordCard.H, right, ind);
						boolean low = at[1] + RecordCard.H > 300 - RecordCard.HOTBAR_UP + 1e-3f;
						if (!low) {
							continue;
						}
						boolean leftSide = c == CardCorner.BOTTOM_LEFT;
						float reach = RecordCard.sideReach(leftSide, right, ind);
						if (leftSide) {
							assertTrue(at[0] + RecordCard.W <= w / 2f - reach + 1e-3f, w + " " + c);
						} else {
							assertTrue(at[0] >= w / 2f + reach - 1e-3f, w + " " + c);
						}
					}
				}
			}
		}
	}

	@Test
	void threeLinesMakeTheStripTallerByTwoDimmedRows() {
		assertEquals(RecordCard.LYRIC_H, RecordCard.stripHeight(1));
		assertEquals(RecordCard.LYRIC_H + 2 * RecordCard.SIDE_H, RecordCard.stripHeight(3));
		assertEquals(RecordCard.H + RecordCard.stripHeight(3), RecordCard.height(true, 3));
		assertEquals(RecordCard.H, RecordCard.height(false, 3));
		assertEquals(RecordCard.height(true), RecordCard.height(true, 1));
		// The current line sits under the dimmed one above it, and the rows add up to the strip
		assertEquals(0, RecordCard.currentRowTop(1));
		assertEquals(RecordCard.SIDE_H, RecordCard.currentRowTop(3));
		assertEquals(RecordCard.stripHeight(3), RecordCard.currentRowTop(3) + RecordCard.LYRIC_H + RecordCard.SIDE_H);
	}

	@Test
	void threeLinesFallBackToOneOnAScreenTooShort() {
		assertEquals(1, RecordCard.lines(1, 540));
		assertEquals(3, RecordCard.lines(3, 240));
		assertEquals(3, RecordCard.lines(3, 540));
		assertEquals(1, RecordCard.lines(3, 80));
		assertEquals(1, RecordCard.lines(0, 540));
	}

	@Test
	void aThreeLineCardStaysOnTheScreenAndClearOfTheHotbarInEveryCorner() {
		for (int[] screen : new int[][] {{533, 300}, {640, 360}, {427, 240}, {320, 180}, {250, 130}}) {
			int w = screen[0], h = screen[1];
			float tall = RecordCard.height(true, RecordCard.lines(3, h));
			for (CardCorner c : CardCorner.values()) {
				for (boolean right : new boolean[] {true, false}) {
					float[] at = RecordCard.corner(c, w, h, RecordCard.BAD_ROW, tall, right, true);
					assertTrue(at[1] >= 0 && at[1] + tall <= h, c + " " + w + "x" + h);
					assertTrue(at[0] >= 0 && at[0] + RecordCard.W <= w, c + " " + w + "x" + h);
				}
			}
			// A bottom card still grows upwards from the same bottom edge when it clears the hotbar
			float[] low = RecordCard.corner(CardCorner.BOTTOM_RIGHT, 640, 360, 0, tall, true, false);
			assertEquals(360 - RecordCard.MARGIN, low[1] + tall, 1e-3f);
		}
	}

	@Test
	void aTallCardClimbsAboveTheHotbarToo() {
		float tall = RecordCard.height(true, 3);
		float[] at = RecordCard.corner(CardCorner.BOTTOM_LEFT, 533, 300, 0, tall, true, false);
		assertEquals(300 - RecordCard.HOTBAR_UP, at[1] + tall, 1e-3f);
	}

	@Test
	void aNarrowScreensTopCardMovesBelowTheBossBars() {
		int w = 400, h = 240;
		float clear = RecordCard.bossClear(CardCorner.TOP_LEFT, w, h, 1);
		assertEquals(NowPlayingHud.topBelowBossBars(1, h), clear);
		assertEquals(clear, RecordCard.corner(CardCorner.TOP_LEFT, w, h, 0, RecordCard.H, true, false, clear)[1]);
		assertEquals(clear, RecordCard.corner(CardCorner.TOP_RIGHT, w, h, 0, RecordCard.H, true, false,
				RecordCard.bossClear(CardCorner.TOP_RIGHT, w, h, 1))[1]);
	}

	@Test
	void bossBarsDontMoveACardThatIsClearOfThem() {
		assertEquals(0f, RecordCard.bossClear(CardCorner.TOP_LEFT, 600, 240, 2));
		assertEquals(0f, RecordCard.bossClear(CardCorner.TOP_LEFT, 400, 240, 0));
		assertEquals(0f, RecordCard.bossClear(CardCorner.BOTTOM_LEFT, 400, 240, 1));
	}

	private static float[] barAt(int w, int h, boolean hurt) {
		TopBarLayout.Fit fit = TopBarLayout.fit(w, 1, 30, 40);
		float top = TopBarLayout.bottomTop(h, hurt, 49, false);
		return TopBarLayout.barBox(w, 1, fit.pill(), 30, fit.clock(), 40, fit.stats(), top);
	}

	private static float[] atBottom(CardCorner c, int w, int h, float cardH, float[] bar) {
		return RecordCard.corner(c, w, h, 0, cardH, true, false, 0, RecordCard.liftUp(true, 49), bar);
	}

	private static boolean overlaps(float[] at, float cardH, float[] bar) {
		return at[0] < bar[2] && at[0] + RecordCard.W > bar[0] && at[1] < bar[3] && at[1] + cardH > bar[1];
	}

	@Test
	void aBottomCardSitsAboveTheBottomBar() {
		for (int w : new int[] {320, 427, 480, 640}) {
			for (CardCorner c : new CardCorner[] {CardCorner.BOTTOM_LEFT, CardCorner.BOTTOM_RIGHT}) {
				for (float cardH : new float[] {RecordCard.height(false, 1), RecordCard.height(true, 1), RecordCard.height(true, 3)}) {
					int h = 360;
					float[] bar = barAt(w, h, true);
					float[] at = atBottom(c, w, h, cardH, bar);
					assertTrue(!overlaps(at, cardH, bar), "w=" + w + " " + c + " h=" + cardH);
					assertTrue(at[1] >= 0 && at[1] + cardH <= h);
					assertTrue(at[0] + RecordCard.W <= bar[0] || at[0] >= bar[2] || at[1] >= bar[3] || at[1] + cardH <= bar[1] - RecordCard.BAR_GAP + 1e-3f,
							"w=" + w + " " + c + " h=" + cardH);
				}
			}
		}
	}

	@Test
	void aCardWithNoRoomAboveTheBarKeepsItsPlace() {
		float[] bar = {0, 20, 400, 40};
		assertEquals(27f, RecordCard.aboveBar(new float[] {0, 60, 400, 80}, 10, 70, 30));
		// the card's own place is kept when it would have to leave the screen
		assertEquals(25f, RecordCard.aboveBar(bar, 10, 25, 30));
		// a card far from the bar's sides is left alone
		assertEquals(25f, RecordCard.aboveBar(new float[] {300, 20, 500, 40}, 10, 25, 30));
	}

	@Test
	void theLiftClearsArmourAndTheHeldItemName() {
		// two rows of hearts (a golden apple) with armour: the armour row is 59 up
		assertTrue(RecordCard.liftUp(true, TopBarLayout.healthTopUp(20, 4, 5)) >= 59 + 3);
		assertTrue(RecordCard.liftUp(true, TopBarLayout.healthTopUp(40, 4, 5)) >= 67 + 3);
		// the name's top is 60 up for a player that can be hurt, 46 otherwise
		assertTrue(RecordCard.liftUp(true, 39) >= 62);
		assertTrue(RecordCard.liftUp(false, 0) >= 48);
		assertEquals(RecordCard.HOTBAR_UP, RecordCard.liftUp(false, 0));
		float[] at = RecordCard.corner(CardCorner.BOTTOM_LEFT, 320, 240, 0, RecordCard.H, true, false, 0,
				RecordCard.liftUp(true, 59), null);
		assertTrue(240 - (at[1] + RecordCard.H) >= 62);
	}

	@Test
	void theCardEasesTowardsItsPlaceInsteadOfJumping() {
		assertEquals(80f, RecordCard.ease(Float.NaN, 80, 0.016f));
		float step = RecordCard.ease(10, 110, 0.016f);
		assertTrue(step > 10 && step < 110);
		assertEquals(110f, RecordCard.ease(109.99f, 110, 0.016f));
		float y = 0;
		for (int i = 0; i < 120; i++) {
			y = RecordCard.ease(y, 100, 1 / 60f);
		}
		assertEquals(100f, y);
		assertEquals(10f, RecordCard.ease(10, 110, 0f));
	}

	@Test
	void aLiftedCardClearsTheActionBarText() {
		float lift = RecordCard.liftUp(true, 39);
		float[] with = RecordCard.corner(CardCorner.BOTTOM_LEFT, 320, 240, 0, RecordCard.H, true, false, 0, lift, null, true);
		assertTrue(240 - (with[1] + RecordCard.H) >= 75);
		float[] without = RecordCard.corner(CardCorner.BOTTOM_LEFT, 320, 240, 0, RecordCard.H, true, false, 0, lift, null, false);
		assertEquals(lift, 240 - (without[1] + RecordCard.H));
		// a card far from the middle is left alone
		float[] wide = RecordCard.corner(CardCorner.BOTTOM_LEFT, 640, 360, 0, RecordCard.H, true, false, 0, lift, null, true);
		assertEquals(RecordCard.MARGIN, 360 - (wide[1] + RecordCard.H));
	}

	@Test
	void aboveBarEdges() {
		float[] bar = {100, 60, 400, 80};
		// below the bar's bottom, it stays
		assertEquals(90f, RecordCard.aboveBar(bar, 10, 90, 30));
		// exactly at the bar's side, no overlap
		assertEquals(65f, RecordCard.aboveBar(new float[] {166, 60, 400, 80}, 10, 65, 30));
		// the card's lower edge exactly BAR_GAP above the bar's top, no move
		assertEquals(27f, RecordCard.aboveBar(bar, 10, 27, 30));
		// room for exactly nothing above: it goes to 0
		assertEquals(0f, RecordCard.aboveBar(new float[] {100, 33, 400, 50}, 10, 35, 30));
		// only the corner under the bar moves
		assertEquals(27f, RecordCard.aboveBar(bar, 6, 65, 30));
		assertEquals(65f, RecordCard.aboveBar(bar, 478, 65, 30));
	}

	@Test
	void onlyTopCornersAreUnderTheDebugScreen() {
		assertTrue(RecordCard.topCorner(CardCorner.TOP_LEFT));
		assertTrue(RecordCard.topCorner(CardCorner.TOP_RIGHT));
		assertFalse(RecordCard.topCorner(CardCorner.BOTTOM_LEFT));
		assertFalse(RecordCard.topCorner(CardCorner.BOTTOM_RIGHT));
	}

	private static float[] topBarAt(int w, float size) {
		TopBarLayout.Fit fit = TopBarLayout.fit(w, size, 30, 40);
		return TopBarLayout.topBarBox(w, size, fit.pill(), 30, fit.clock(), 40, fit.stats(), 5);
	}

	private static float[] atTop(CardCorner c, int w, float[] topBar) {
		return RecordCard.corner(c, w, 240, 0, 60, true, false, 0, RecordCard.liftUp(true, 49), null, false, topBar);
	}

	@Test
	void aTopCardSitsBelowTheTopBarOnANarrowScreen() {
		for (float size : new float[] {1, 1.5f}) {
			for (CardCorner c : new CardCorner[] {CardCorner.TOP_LEFT, CardCorner.TOP_RIGHT}) {
				float[] bar = topBarAt(427, size);
				float[] without = atTop(c, 427, null);
				assertTrue(overlaps(without, 60, bar), "control: they overlap without the box, " + c + " " + size);
				float[] at = atTop(c, 427, bar);
				assertEquals(bar[3] + RecordCard.BAR_GAP, at[1], 1e-3f, c + " " + size);
				assertTrue(!overlaps(at, 60, bar));
			}
		}
	}

	@Test
	void aTopCardStaysInTheCornerWhenTheBarIsFarAway() {
		float[] bar = topBarAt(1920, 1);
		assertEquals(RecordCard.MARGIN, atTop(CardCorner.TOP_LEFT, 1920, bar)[1], 1e-3f);
		assertEquals(RecordCard.MARGIN, atTop(CardCorner.TOP_RIGHT, 1920, bar)[1], 1e-3f);
		// and with no top bar at all
		assertEquals(RecordCard.MARGIN, atTop(CardCorner.TOP_LEFT, 427, null)[1], 1e-3f);
	}

	@Test
	void theTopBoxHangsDownFromTheBar() {
		float[] b = topBarAt(480, 1.5f);
		assertEquals(5f, b[1]);
		assertEquals(5f + TopBarLayout.PILL_H * 1.5f, b[3], 1e-3f);
	}

	@Test
	void f3HidesTopCardsAndTallBottomLeftCards() {
		assertTrue(RecordCard.f3Covers(CardCorner.TOP_LEFT, 4, true));
		assertTrue(RecordCard.f3Covers(CardCorner.BOTTOM_LEFT, 200, true));
		assertFalse(RecordCard.f3Covers(CardCorner.BOTTOM_LEFT, 400, true));
		assertTrue(RecordCard.f3Covers(CardCorner.BOTTOM_LEFT, 319, true)); // just inside F3_REACH (320)
		assertFalse(RecordCard.f3Covers(CardCorner.BOTTOM_LEFT, 320, true)); // 320 is the first row clear of F3
		assertFalse(RecordCard.f3Covers(CardCorner.BOTTOM_RIGHT, 200, true));
		assertFalse(RecordCard.f3Covers(CardCorner.BOTTOM_LEFT, 200, false));
	}

	@Test
	void aSongLengthOverADayIsUnknown() {
		assertEquals(-1f, TopBarLayout.progress(1000, 1L << 62));
		assertEquals(-1f, TopBarLayout.progress(1000, TopBarLayout.MAX_LENGTH_MS + 1));
		assertEquals(0.5f, TopBarLayout.progress(500, 1000));
	}
}
