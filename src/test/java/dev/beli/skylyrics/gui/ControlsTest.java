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

	@Test
	void aSliderUnderItsNameOnlyMovesFromItsOwnLine() {
		// Glow in a 184 px row: the slider goes on a line under its name and description
		double[] glow = {0.5};
		Controls.Slider row = new Controls.Slider("Glow", "Soft light around the letters", 0, 1, 0.05,
				() -> glow[0], v -> glow[0] = v, v -> Math.round(v * 100) + "%");
		row.x = 0;
		row.y = 0;
		row.w = 184;
		row.prepare(FONT);
		assertTrue(row.stacked());

		// a click on the description, above the slider's right end, leaves the setting alone
		assertFalse(row.click(170, row.y + 20));
		assertFalse(row.dragging);
		assertEquals(0.5, glow[0]);
		assertFalse(row.click(100, row.y + 8), "nor on the name's line");
		assertEquals(0.5, glow[0]);

		// on the slider's own line it still takes hold
		assertTrue(row.click(170, row.controlCy()));
		assertTrue(row.dragging);
		assertEquals(0.95, glow[0], 1e-9);
		row.release();
	}

	@Test
	void aSliderBesideItsNameTakesTheWholeRowHeight() {
		double[] size = {1};
		Controls.Slider row = new Controls.Slider("Size", null, 0.3, 3, 0.05,
				() -> size[0], v -> size[0] = v, v -> String.format(java.util.Locale.ROOT, "%.2f×", v));
		row.x = 0;
		row.y = 0;
		row.w = 320;
		row.prepare(FONT);
		assertFalse(row.stacked());
		// the slider runs from x 214 to 310; a click near the row's top edge still grabs it
		assertTrue(row.click(310 - 1, row.y + 1));
		assertEquals(3, size[0], 0.051);
		row.release();
	}
}
