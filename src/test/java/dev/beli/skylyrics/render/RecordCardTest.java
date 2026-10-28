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
	void timesReadLikeAMusicPlayer() {
		assertEquals("0:00", RecordCard.clock(0));
		assertEquals("0:00", RecordCard.clock(-500));
		assertEquals("0:09", RecordCard.clock(9_999));
		assertEquals("3:07", RecordCard.clock(187_000));
		assertEquals("1:02:05", RecordCard.clock(3_725_000));
	}
}
