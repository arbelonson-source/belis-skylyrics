package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The Record card's small text (the times, the dimmed lines) in Minecraft's letters keeps whole screen pixels per letter pixel. */
class CardSmallTextTest {
	@Test
	void minecraftLettersGetWholeScreenPixels() {
		for (int gui = 1; gui <= 6; gui++) {
			for (float wanted : new float[] {RecordCard.TIME_SCALE, RecordCard.SIDE_SCALE}) {
				float size = RecordCard.smallSize(false, wanted, gui);
				float pixels = size * gui;
				assertEquals(Math.round(pixels), pixels, 0.001f,
						"at GUI scale " + gui + " a letter pixel of text wanted at " + wanted + " is " + pixels + " screen pixels");
				assertTrue(pixels >= 1, "never smaller than one screen pixel per letter pixel");
				// as near the wanted size as whole pixels allow
				assertTrue(Math.abs(pixels - wanted * gui) <= 0.5f + 0.001f, "at GUI scale " + gui + ": " + pixels + " for " + wanted * gui);
			}
		}
		// GUI scale 3, as in the pictures: the times at whole size (3 pixels), the dimmed lines at 2 pixels
		assertEquals(1f, RecordCard.smallSize(false, RecordCard.TIME_SCALE, 3), 0.001f);
		assertEquals(2 / 3f, RecordCard.smallSize(false, RecordCard.SIDE_SCALE, 3), 0.001f);
	}

	@Test
	void smoothLettersKeepTheWantedSize() {
		// The smooth font is drawn smoothed, so any size looks even
		assertEquals(0.75f * RecordCard.TIME_SCALE, RecordCard.smallSize(true, 0.75f * RecordCard.TIME_SCALE, 3), 0.0001f);
		assertEquals(0.75f * RecordCard.SIDE_SCALE, RecordCard.smallSize(true, 0.75f * RecordCard.SIDE_SCALE, 2), 0.0001f);
	}
}
