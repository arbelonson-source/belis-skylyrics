package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
		assertArrayEquals(new String[] {"3:40", "-0:00"}, RecordCard.times(221_000, 220_000));
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
		try {
			java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG-u-nu-arab"));
			assertEquals("3:07", RecordCard.clock(187_000));
		} finally {
			java.util.Locale.setDefault(was);
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
}
