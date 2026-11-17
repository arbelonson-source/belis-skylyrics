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
}
