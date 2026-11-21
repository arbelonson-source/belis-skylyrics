package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.client.gui.Font;
import org.junit.jupiter.api.Test;

/** The settings rows laid out with no game running, measured with a stand-in font. */
class ControlsTest {
	/** Every letter 6 px wide, like most of the game font's. */
	static final class SixPixelFont extends Font {
		SixPixelFont() {
			//? if >=1.21.9 {
			super(null);
			//?} else {
			/*super(id -> null, false);
			*///?}
		}

		@Override
		public int width(String text) {
			return 6 * text.codePointCount(0, text.length());
		}
	}

	private static final Font FONT = new SixPixelFont();

	/** The Look tab's Colour row, laid out {@code rowW} wide; {@code picked[0]} is the choice. */
	private static Controls.Segmented colour(int rowW, int[] picked) {
		Controls.Segmented row = new Controls.Segmented("Colour", "From the album cover, or your own",
				new String[] {"Album", "Pick", "Rainbow", "Time of day", "Each line"}, () -> picked[0], i -> picked[0] = i);
		row.x = 0;
		row.y = 0;
		row.w = rowW;
		row.prepare(FONT);
		return row;
	}

	@Test
	void choicesTooWideForTheSmallestWindowStayInsideTheirRow() {
		// A 320-wide window: 184 px rows, and five names needing 240 px side by side; the first ones ran off the left
		// edge, cut away and out of reach of the mouse
		int[] picked = {0};
		Controls.Segmented row = colour(184, picked);
		assertTrue(row.controlW(FONT) <= row.w - 20, "the control fits its row: " + row.controlW(FONT));

		// it shows the picked name between arrows: the right half goes forward, the left half back
		int right = row.x + row.w - 10 - 4;
		int left = row.x + row.w - 10 - row.controlW(FONT) + 4;
		assertTrue(row.click(right, row.controlCy()));
		assertEquals(1, picked[0]);
		assertTrue(row.click(left, row.controlCy()));
		assertEquals(0, picked[0]);
		assertTrue(row.click(left, row.controlCy()));
		assertEquals(4, picked[0], "back from the first goes round to the last");
		assertFalse(row.click(row.x + 4, row.controlCy()), "left of the control is not part of it");
	}

	@Test
	void choicesWithRoomStaySideBySide() {
		int[] picked = {0};
		Controls.Segmented row = colour(320, picked);
		// 4 + 216 px of names + 5 x 8 around them, every name clickable where it is drawn
		assertEquals(4 + 216 + 5 * 8, row.controlW(FONT));
		int rainbow = row.x + row.w - 10 - row.controlW(FONT) + 2 + (30 + 8) + (24 + 8) + 20;
		assertTrue(row.click(rainbow, row.controlCy()));
		assertEquals(2, picked[0]);
	}
}
