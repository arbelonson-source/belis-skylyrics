package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.config.SkyLyricsConfig.CardCorner;
import org.junit.jupiter.api.Test;

/** The pieces of the game's HUD that are drawn over the Record card and the top bar, and where the card goes for them. */
class HudDodgeTest {
	private static final float W = RecordCard.W, H = RecordCard.H;

	@Test
	void theSidebarBoxIsWhereTheGameDrawsIt() {
		// 10 lines: bottom = 80 + 30, top = bottom - 90 - 10, left = width - 80 - 5, right 1 short of the edge
		assertArrayEquals(new float[] {199, 10, 283, 110}, TopBarLayout.sidebarBox(284, 160, 10, 80));
		// More than 15 entries still show 15
		assertArrayEquals(TopBarLayout.sidebarBox(284, 160, 15, 80), TopBarLayout.sidebarBox(284, 160, 40, 80));
		// Its width is the widest of the title and each name with its score after ": "
		assertEquals(60, TopBarLayout.sidebarWidth(40, 4, new int[] {30, 50}, new int[] {0, 6}));
		assertEquals(41, TopBarLayout.sidebarWidth(30, 4, new int[] {30, 20}, new int[] {7, 2}));
	}

	@Test
	void aCardInTheWayOfTenSidebarLinesMovesOutOfIt() {
		int w = 284, h = 160;
		float[] box = TopBarLayout.sidebarBox(w, h, 10, 80);
		float[] at = RecordCard.corner(CardCorner.BOTTOM_RIGHT, w, h);
		assertTrue(TopBarLayout.overlaps(box, at[0], at[1], W, H));
		float y = TopBarLayout.dodgeY(at[0], at[1], W, H, 6, h, box);
		// There is no room above it (the box starts at 10), so it goes under the box, 3 below
		assertEquals(113, y, 1e-3f);
		assertFalse(TopBarLayout.overlaps(box, at[0], y, W, H));
		assertTrue(y + H <= h);
	}

	@Test
	void aCardMovesUpWhenThereIsRoomAbove() {
		int w = 480, h = 270;
		float[] box = TopBarLayout.sidebarBox(w, h, 3, 60);
		// Card on the right, in the way of 3 lines: middle of the screen is 135, so the box is 135+9 - 27 - 10 .. 144
		float y = TopBarLayout.dodgeY(w - 6 - W, 90, W, H, 6, h, box);
		assertFalse(TopBarLayout.overlaps(box, w - 6 - W, y, W, H));
		assertEquals(box[1] - 3 - H, y, 1e-3f);
	}

	@Test
	void aCardThatCannotFitFadesOut() {
		int w = 284, h = 160;
		float[] box = TopBarLayout.sidebarBox(w, h, 15, 80);
		float[] at = RecordCard.corner(CardCorner.BOTTOM_RIGHT, w, h);
		assertTrue(Float.isNaN(TopBarLayout.dodgeY(at[0], at[1], W, H, 6, h, box)));
	}

	@Test
	void withNothingInTheWayTheCardStaysPut() {
		for (CardCorner c : CardCorner.values()) {
			float[] at = RecordCard.corner(c, 480, 270);
			assertEquals(at[1], TopBarLayout.dodgeY(at[0], at[1], W, H, 6, 270, (float[]) null, null), 0f);
		}
		// A sidebar further from the card than its width leaves it alone
		float[] box = TopBarLayout.sidebarBox(1000, 400, 10, 80);
		assertEquals(50, TopBarLayout.dodgeY(100, 50, W, H, 6, 400, box), 0f);
	}

	@Test
	void aBottomRightCardLiftsAboveTheSubtitlesShowing() {
		int w = 640, h = 360;
		float[] at = RecordCard.corner(CardCorner.BOTTOM_RIGHT, w, h);
		assertEquals(308, at[1], 0f);
		// One subtitle, text 80 wide, margin 18: half 49, box 98 wide + 1 each side, centred 2 short of the right edge
		float[] one = TopBarLayout.subtitleBox(w, h, 1, 80, 18);
		assertArrayEquals(new float[] {539, 320, 639, 330}, one);
		assertEquals(320 - 3 - H, TopBarLayout.dodgeY(at[0], at[1], W, H, 6, h, null, one), 1e-3f);
		// Three at once reach 20 higher: guiH - 35 - 2 * 10 - 5
		float[] three = TopBarLayout.subtitleBox(w, h, 3, 80, 18);
		assertEquals(h - 35 - 2 * 10 - 5, three[1], 0f);
		float y = TopBarLayout.dodgeY(at[0], at[1], W, H, 6, h, null, three);
		assertTrue(y + H <= three[1], "the card's bottom is above the top of the third box");
		assertFalse(TopBarLayout.overlaps(three, at[0], y, W, H));
	}

	@Test
	void withNoSubtitleShowingTheCardStaysDown() {
		int w = 640, h = 360;
		assertNull(TopBarLayout.subtitleBox(w, h, 0, 80, 18));
		float[] at = RecordCard.corner(CardCorner.BOTTOM_RIGHT, w, h);
		assertEquals(at[1], TopBarLayout.dodgeY(at[0], at[1], W, H, 6, h, (float[]) null, TopBarLayout.subtitleBox(w, h, 0, 80, 18)), 0f);
		// Only when they can't be read is one narrow box assumed
		assertNotNull(TopBarLayout.subtitleBoxGuess(w, h));
	}

	@Test
	void aNarrowSubtitleBoxLeavesACardFurtherLeftAlone() {
		// 20 wide text: the box is 2 * 19 + 2 = 40 wide, so a card ending at 300 on a 640 screen is clear
		float[] box = TopBarLayout.subtitleBox(640, 360, 3, 20, 18);
		assertFalse(TopBarLayout.overlaps(box, 300 - W, 300, W, H));
		assertTrue(TopBarLayout.overlaps(TopBarLayout.subtitleBox(640, 360, 3, 400, 18), 300 - W + 200, 300, W, H));
	}

	@Test
	void anOpenChatLiftsBottomCardsAboveItsTextField() {
		int h = 540;
		float[] at = RecordCard.corner(CardCorner.BOTTOM_RIGHT, 960, h);
		assertEquals(h - 14 - 2 - H, TopBarLayout.aboveChatInput(true, at[1], H, h), 0f);
		assertEquals(at[1], TopBarLayout.aboveChatInput(false, at[1], H, h), 0f);
		// A card already clear of it stays
		assertEquals(50, TopBarLayout.aboveChatInput(true, 50, H, h), 0f);
	}

	@Test
	void chatSetToHiddenDoesNotFadeACard() {
		int h = 270;
		float[] at = RecordCard.corner(CardCorner.BOTTOM_LEFT, 480, h);
		assertTrue(TopBarLayout.chatBoxCovers(true, false, h, 320, 180, at[0], at[1], W, H));
		assertFalse(TopBarLayout.chatBoxCovers(true, true, h, 320, 180, at[0], at[1], W, H));
		assertFalse(TopBarLayout.chatBoxCovers(false, false, h, 320, 180, at[0], at[1], W, H));
	}

	@Test
	void theOpenChatCoversABottomLeftCard() {
		int h = 270;
		float[] at = RecordCard.corner(CardCorner.BOTTOM_LEFT, 480, h);
		// Default chat: 320 wide, 180 tall (focused), up from 40 above the bottom
		assertTrue(TopBarLayout.chatCovers(h, 320, 180, at[0], at[1], W, H));
		// A tiny chat box that stays above the card's top leaves it alone
		assertFalse(TopBarLayout.chatCovers(h, 320, 10, at[0], at[1], W, H));
		// A narrow chat leaves a card further right than its width alone
		assertFalse(TopBarLayout.chatCovers(h, 40, 180, 100, at[1], W, H));
	}

	@Test
	void theDemoMovesTheEffectRowsAndKeepsTopThingsBelowItsTimer() {
		assertEquals(25, TopBarLayout.effectRow(RecordCard.GOOD_ROW, false));
		assertEquals(40, TopBarLayout.effectRow(RecordCard.GOOD_ROW, true));
		assertEquals(66, TopBarLayout.effectRow(RecordCard.BAD_ROW, true));
		// Timer text 120 wide starts at 480 - 130; a bar or a card reaching it starts below y 16
		assertEquals(350, TopBarLayout.demoTimerLeft(480, 120), 0f);
		assertEquals(16, TopBarLayout.demoClear(true, 480, 120, 474), 0f);
		assertEquals(0, TopBarLayout.demoClear(true, 480, 120, 300), 0f);
		assertEquals(0, TopBarLayout.demoClear(false, 480, 120, 474), 0f);
	}

	/** 1280x720 at GUI scale 3, the card with its lyric line, a right-handed player with full health. */
	private static final int NARROW_W = 427, NARROW_H = 240;
	private static final float CARD_H = RecordCard.height(true, 1);

	private static float[] cornerOn(CardCorner c, int w, int h) {
		return RecordCard.corner(c, w, h, 0, CARD_H, true, false, 0, RecordCard.liftUp(true, 39), null);
	}

	private static float[] placed(CardCorner c, int w, int h, boolean chatOpen, boolean chatHidden, float[] sidebar) {
		return RecordCard.place(c, h, CARD_H, k -> cornerOn(k, w, h), chatOpen, chatHidden, 320, 180, sidebar, null);
	}

	@Test
	void aRightCardSteppingOutOfTheSidebarNeverDropsOntoTheHotbar() {
		int w = NARROW_W, h = NARROW_H;
		// Lifted above the hotbar's reach on this narrow screen: its bottom is the lowest it may go
		float lowest = cornerOn(CardCorner.BOTTOM_RIGHT, w, h)[1] + CARD_H;
		assertEquals(h - RecordCard.liftUp(true, 39), lowest, 1e-3f);
		// A 10-line sidebar leaves no room above it, and under it the card would sit on the hunger bar: it fades
		float[] ten = TopBarLayout.sidebarBox(w, h, 10, 80);
		for (CardCorner c : new CardCorner[] {CardCorner.BOTTOM_RIGHT, CardCorner.TOP_RIGHT}) {
			assertTrue(TopBarLayout.overlaps(ten, cornerOn(c, w, h)[0], cornerOn(c, w, h)[1], W, CARD_H), "control: in the sidebar's way, " + c);
			float[] at = placed(c, w, h, false, false, ten);
			assertTrue(at == null || at[1] + CARD_H <= lowest + 1e-3f, c + " dropped to " + (at == null ? 0 : at[1] + CARD_H) + ", lowest " + lowest);
		}
		// A 3-line sidebar has room above it, so the card goes up rather than down onto the held item's name
		float[] three = TopBarLayout.sidebarBox(w, h, 3, 80);
		float[] at = placed(CardCorner.BOTTOM_RIGHT, w, h, false, false, three);
		assertNotNull(at);
		assertEquals(three[1] - TopBarLayout.DODGE_GAP - CARD_H, at[1], 1e-3f);
	}

	@Test
	void theOpenChatFadesACardItsBoxCoversInAnyCorner() {
		int w = NARROW_W, h = NARROW_H;
		// The default chat (320 wide, 180 tall when open) reaches x 332 and y 20 here: over every corner's card
		for (CardCorner c : CardCorner.values()) {
			assertNull(placed(c, w, h, true, false, null), c.name());
			// With the chat closed, or set to Hidden (no box), the card stays
			assertNotNull(placed(c, w, h, false, false, null), c.name());
			assertNotNull(placed(c, w, h, true, true, null), c.name());
		}
		// 1920x1080 at GUI scale 3: the right cards and the top cards are clear of the box
		for (CardCorner c : new CardCorner[] {CardCorner.BOTTOM_RIGHT, CardCorner.TOP_RIGHT, CardCorner.TOP_LEFT}) {
			assertNotNull(placed(c, 640, 360, true, false, null), c.name());
		}
		assertNull(placed(CardCorner.BOTTOM_LEFT, 640, 360, true, false, null));
	}

	/**
	 * Where Minecraft draws effect icon {@code i} (1 is the rightmost) of a row, as its Gui does on every version: 25 apart
	 * leftwards from the screen's right edge, 24 square, from y 1, the bad effects' row 26 lower.
	 */
	private static float[] effectIcon(int guiW, int i, boolean bad) {
		float x = guiW - 25 * i, y = bad ? 27 : 1;
		return new float[] {x, y, x + 24, y + 24};
	}

	private static float topLeftTop(int w, int h, int good, int bad) {
		float clear = RecordCard.effectsClear(CardCorner.TOP_LEFT, w, good, bad, false);
		return RecordCard.corner(CardCorner.TOP_LEFT, w, h, 0, CARD_H, true, false, clear)[1];
	}

	@Test
	void aTopLeftCardGoesUnderTheEffectIconsThatReachIt() {
		for (int[] size : new int[][] {{320, 180}, {NARROW_W, NARROW_H}, {480, 270}}) {
			int w = size[0], h = size[1];
			for (int good = 0; good <= 14; good++) {
				for (int bad = 0; bad <= 14; bad++) {
					float y = topLeftTop(w, h, good, bad);
					for (int i = 1; i <= Math.max(good, bad); i++) {
						String what = w + " wide, " + good + " good and " + bad + " bad effects, icon " + i + ", card at " + y;
						assertFalse(i <= good && TopBarLayout.overlaps(effectIcon(w, i, false), RecordCard.MARGIN, y, W, CARD_H), what);
						assertFalse(i <= bad && TopBarLayout.overlaps(effectIcon(w, i, true), RecordCard.MARGIN, y, W, CARD_H), what);
					}
				}
			}
		}
		// 320 wide: six good effects end at x 170, clear of the card's right edge (162), and it stays at the top; the
		// seventh reaches it and the card goes 2 under the row
		assertEquals(RecordCard.MARGIN, topLeftTop(320, 180, 6, 0), 0f);
		assertEquals(RecordCard.GOOD_ROW + 2, topLeftTop(320, 180, 7, 0), 0f);
		// 427 wide (1280x720 at GUI scale 3): ten leave it, eleven reach it
		assertEquals(RecordCard.MARGIN, topLeftTop(NARROW_W, NARROW_H, 10, 0), 0f);
		assertEquals(RecordCard.GOOD_ROW + 2, topLeftTop(NARROW_W, NARROW_H, 11, 0), 0f);
		// Seven bad ones reach it in the second row: under that one
		assertEquals(RecordCard.BAD_ROW + 2, topLeftTop(320, 180, 2, 7), 0f);
		// In the demo the rows sit 15 lower, under its timer
		assertEquals(RecordCard.GOOD_ROW + 15 + 2, RecordCard.effectsClear(CardCorner.TOP_LEFT, 320, 7, 0, true), 0f);
		// The other corners have their own ways (the top right card goes under all the icons) or are far from them
		for (CardCorner c : new CardCorner[] {CardCorner.TOP_RIGHT, CardCorner.BOTTOM_LEFT, CardCorner.BOTTOM_RIGHT}) {
			assertEquals(0, RecordCard.effectsClear(c, 320, 14, 14, false), 0f, c.name());
		}
	}
}
