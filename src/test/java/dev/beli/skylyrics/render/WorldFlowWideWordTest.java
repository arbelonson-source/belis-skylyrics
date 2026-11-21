package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A word that starts a new row of a flowing line is never indented so far that it runs past the row's far end, and a
 * word wider than the whole row (the line's big word, low in the sky) sits on the row's middle instead of all of it
 * sticking out at the far end.
 */
class WorldFlowWideWordTest {
	@Test
	void aWordThatJustFitsARowStartsItWithoutRunningPastItsEnd() {
		for (boolean rtl : new boolean[] {false, true}) {
			WorldWords.Sentence s = new WorldWords.Sentence(-20, 5, 10, 0, 0, rtl);
			s.place(2, 1, 0, 0);
			s.newRow = true;
			// Measured, as the layout does, at the new row's foot: under the first row's lowest word (pitch 5), a row gap and its height
			float half = WorldWords.Sentence.yawHalf(16, 5 + WorldWords.Sentence.ROW_GAP + 2);
			float yaw = s.place(16, 1, 2.5f, 0)[0];
			// Measured from the edge the row starts at
			float start = rtl ? s.left + s.width - (yaw + half) : yaw - half - s.left;
			assertTrue(2 * half < s.width && 2 * half > s.width - 2.5f, "the word should fit the row only without the indent: " + 2 * half);
			assertTrue(start >= 0 && start + 2 * half <= s.width + 0.1f, (rtl ? "rtl" : "ltr") + " word from " + start + " to " + (start + 2 * half) + " of " + s.width);
		}
	}

	@Test
	void aWordWiderThanTheRowSitsOnItsMiddle() {
		for (boolean rtl : new boolean[] {false, true}) {
			WorldWords.Sentence s = new WorldWords.Sentence(-20, 5, 10, 0, 0, rtl);
			s.place(2, 1, 0, 0);
			s.newRow = true;
			float yaw = s.place(18, 1, 2.5f, 0)[0];
			assertEquals(s.left + s.width / 2, yaw, 0.1f, (rtl ? "rtl" : "ltr") + " a word wider than the row");
		}
	}
}
