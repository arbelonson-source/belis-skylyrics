package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.config.SkyLyricsConfig.CardCorner;
import org.junit.jupiter.api.Test;

/**
 * A Record card in a bottom corner steps aside for the debug screen (F3) when F3's text, as the game drew it, reaches the
 * card, as the bottom bar does. Before, a bottom left card hid whenever its top was less than 320 down (a guess at how far
 * F3's left column reaches), so at 640x360 (1920x1080 at GUI scale 3) it hid although 1.21.11's F3 text ends far above it,
 * and a bottom right card never hid, even under 1.21.1's long right column.
 */
class CardF3TextTest {
	/** F3's text: {@code left} and {@code right} rows of lines this wide in each column. */
	private static float[][] f3(int guiW, int left, int leftW, int right, int rightW) {
		int[] l = new int[left], r = new int[right];
		java.util.Arrays.fill(l, leftW);
		java.util.Arrays.fill(r, rightW);
		float[][] a = DebugText.boxes(l, true, guiW), b = DebugText.boxes(r, false, guiW);
		float[][] both = new float[a.length + b.length][];
		System.arraycopy(a, 0, both, 0, a.length);
		System.arraycopy(b, 0, both, a.length, b.length);
		return both;
	}

	private static float[] card(CardCorner corner, int guiW, int guiH) {
		float[] at = RecordCard.corner(corner, guiW, guiH);
		return new float[] {at[0], at[1], at[0] + RecordCard.W, at[1] + RecordCard.H};
	}

	@Test
	void aBottomLeftCardStaysWhereF3sTextEndsAboveIt() {
		// 1.21.11 at 640x360: 24 rows on the left end 217 down; the card stands at 308
		float[] box = card(CardCorner.BOTTOM_LEFT, 640, 360);
		assertTrue(box[1] < RecordCard.F3_REACH, "the old guess hid it");
		assertFalse(RecordCard.f3Covers(CardCorner.BOTTOM_LEFT, box, true, f3(640, 24, 150, 15, 120)));
	}

	@Test
	void aBottomLeftCardHidesWhereF3sTextReachesIt() {
		// 1.21.1 at 1280x720 and GUI scale 3 (426x240): its left column runs 30 rows down, past the card
		float[] box = card(CardCorner.BOTTOM_LEFT, 426, 240);
		assertTrue(RecordCard.f3Covers(CardCorner.BOTTOM_LEFT, box, true, f3(426, 30, 150, 12, 120)));
	}

	@Test
	void aBottomRightCardHidesUnderALongRightColumn() {
		// 1.21.1 at 640x360 looking at sand: the block's tags run the right column down past the bottom
		float[] box = card(CardCorner.BOTTOM_RIGHT, 640, 360);
		assertTrue(RecordCard.f3Covers(CardCorner.BOTTOM_RIGHT, box, true, f3(640, 24, 150, 40, 140)));
		assertFalse(RecordCard.f3Covers(CardCorner.BOTTOM_RIGHT, box, true, f3(640, 24, 150, 15, 120)), "a short one ends above it");
	}

	@Test
	void unmeasuredF3KeepsTheOldGuessAndTopCardsAlwaysHide() {
		float[] low = card(CardCorner.BOTTOM_LEFT, 640, 360);
		assertTrue(RecordCard.f3Covers(CardCorner.BOTTOM_LEFT, low, true, null), "as before when a mod draws F3 its own way");
		assertFalse(RecordCard.f3Covers(CardCorner.BOTTOM_RIGHT, card(CardCorner.BOTTOM_RIGHT, 640, 360), true, null));
		assertTrue(RecordCard.f3Covers(CardCorner.TOP_LEFT, card(CardCorner.TOP_LEFT, 640, 360), true, new float[0][]));
		assertFalse(RecordCard.f3Covers(CardCorner.BOTTOM_LEFT, low, false, f3(426, 30, 150, 12, 120)), "F3 closed");
	}
}
