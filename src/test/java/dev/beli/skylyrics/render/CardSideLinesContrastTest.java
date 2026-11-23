package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.Wcag;
import org.junit.jupiter.api.Test;

/** With Card lines at 3, the Record card's lines before and after the current one, on the card at full Background. */
class CardSideLinesContrastTest {
	/** The card's fill as drawn over {@code world}: the world shows a little through it. */
	static int card(int world) {
		return Wcag.over(RecordCard.FILL_ALPHA << 24 | RecordCard.FILL, world);
	}

	private static double side(int world) {
		int behind = card(world);
		return Wcag.ratio(Wcag.over(RecordCard.SIDE_ALPHA << 24 | RecordCard.SUB, behind), behind);
	}

	@Test
	void theLinesBeforeAndAfterReadInACave() {
		// Before: 1.98:1 (faded to 110 of 255)
		double c = side(Wcag.CAVE);
		assertTrue(c >= Wcag.TEXT, String.format("%.2f:1", c));
	}

	@Test
	void theLinesBeforeAndAfterStillReadWithSnowBehindTheCard() {
		// Before: 1.88:1. The artist line, in the same grey unfaded, is 4.2:1 here: the card lets 8% of the world through
		for (int world : Wcag.WORLDS) {
			double c = side(world);
			assertTrue(c >= Wcag.LARGE, String.format("on %06X: %.2f:1", world, c));
		}
	}

	@Test
	void theLinesBeforeAndAfterStayDimmerThanTheArtistLine() {
		assertTrue(RecordCard.SIDE_ALPHA < 255);
	}
}
