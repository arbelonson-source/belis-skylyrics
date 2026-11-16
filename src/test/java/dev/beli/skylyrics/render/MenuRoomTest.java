package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MenuRoomTest {
	// A 427-wide title screen (854 px window at GUI scale 2): vanilla's 200-wide buttons in the middle, the footer at 228
	private static final int W = 427, BOTTOM = 228;
	private static final int[] QUIT = {113, 200, 168};

	@Test
	void theLyricsGoUnderTheMiddleButtons() {
		MenuRoom.Room room = MenuRoom.of(W, BOTTOM, new int[][] {{113, 200, 120}, QUIT});
		assertEquals(168, room.top());
		assertEquals(W - 2 * MenuRoom.SIDE, room.maxWidth());
	}

	@Test
	void aCornerButtonDoesNotPushTheLyricsDownButKeepsThemNarrow() {
		// another mod's 20 px button in the bottom-left corner, lower than the middle buttons
		MenuRoom.Room room = MenuRoom.of(W, BOTTOM, new int[][] {QUIT, {4, 20, 210}});
		assertEquals(168, room.top(), "the corner button would have left only 18 px");
		assertEquals(2 * MenuRoom.HALF_COLUMN, room.maxWidth());
		assertTrue(4 + 20 < W / 2 - room.maxWidth() / 2, "the narrowed lyrics stay clear of it");
	}

	@Test
	void aCornerButtonAboveTheMiddleButtonsChangesNothing() {
		MenuRoom.Room room = MenuRoom.of(W, BOTTOM, new int[][] {{4, 20, 30}, QUIT});
		assertEquals(168, room.top());
		assertEquals(W - 2 * MenuRoom.SIDE, room.maxWidth());
	}

	@Test
	void aButtonReachingIntoTheColumnStillCounts() {
		MenuRoom.Room room = MenuRoom.of(W, BOTTOM, new int[][] {QUIT, {60, 40, 200}});
		assertEquals(200, room.top());
	}

	@Test
	void theFooterRowIsLeftOut() {
		MenuRoom.Room room = MenuRoom.of(W, BOTTOM, new int[][] {QUIT, {300, 120, 238}});
		assertEquals(168, room.top());
	}

	@Test
	void aNarrowWindowCapsTheSizeByWidth() {
		// tall and narrow: lots of height, 266 wide: at most 250 / 260 of size 1, not 1.5
		float scale = LyricsRenderer.menuScale(400, 266 - 2 * MenuRoom.SIDE, 1);
		assertEquals(250f / 260, scale, 1e-6f);
		// a wide window keeps the height limit
		assertEquals(1.5f, LyricsRenderer.menuScale(400, 1000, 1), 1e-6f);
	}
}
