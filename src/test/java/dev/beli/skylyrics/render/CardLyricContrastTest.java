package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.Wcag;
import org.junit.jupiter.api.Test;

/** The Record card's current line, in the lyrics colour, on the card at full Background. */
class CardLyricContrastTest {
	private static void reads(int rgb) {
		int shown = RecordCard.lyricColor(rgb);
		for (int world : Wcag.WORLDS) {
			int behind = CardSideLinesContrastTest.card(world);
			double c = Wcag.ratio(shown, behind);
			assertTrue(c >= Wcag.LARGE, String.format("lyrics colour %06X drawn %06X on %06X: %.2f:1", rgb, shown, behind, c));
		}
	}

	@Test
	void theCurrentLineReadsInEveryAlbumColour() {
		// Album colours are a hue at full brightness and 55 to 85% saturation (AlbumArt.pickColor): a deep blue one
		// (2626FF) left the line at 1.95:1 with snow behind the card
		for (int h = 0; h < 360; h++) {
			for (float s = 0.55f; s <= 0.851f; s += 0.05f) {
				reads(Wcag.hsb(h / 360f, s, 1));
			}
		}
	}

	@Test
	void theCurrentLineReadsInAnyColourAtAll() {
		for (int r = 0; r < 256; r += 15) {
			for (int g = 0; g < 256; g += 15) {
				for (int b = 0; b < 256; b += 15) {
					reads(r << 16 | g << 8 | b);
				}
			}
		}
	}

	@Test
	void aLyricsColourThatAlreadyReadsIsLeftAlone() {
		// The default, every colour to pick, the looks' colours, Rainbow's darkest (blue) and the other modes' colours
		int[] kept = {0xC7D0F4, 0xEEBAE8, 0xAE90F6, 0x8CDAFF, 0xC8B29C, 0xFF6EC7, 0xFF5A5A, 0xFFA24C, 0xFFE066, 0x6BFF8E, 0xFFFFFF,
				0xF4A6FF, 0xDCE6FF, 0xD9B6F2, 0xFFD27A, 0x7373FF,
				LyricsRenderer.NIGHT, LyricsRenderer.TWILIGHT, LyricsRenderer.DAY, AlbumArt.FALLBACK_COLOR};
		for (int c : kept) {
			assertEquals(c, RecordCard.lyricColor(c), String.format("%06X", c));
		}
		for (int c : LyricsRenderer.LINE_COLORS) {
			assertEquals(c, RecordCard.lyricColor(c), String.format("%06X", c));
		}
	}

	@Test
	void aLiftedColourKeepsItsHue() {
		int deep = 0x2626FF;
		int shown = RecordCard.lyricColor(deep);
		float[] a = java.awt.Color.RGBtoHSB(0x26, 0x26, 0xFF, null);
		float[] b = java.awt.Color.RGBtoHSB(shown >> 16 & 255, shown >> 8 & 255, shown & 255, null);
		assertEquals(a[0], b[0], 0.01f, String.format("%06X", shown));
	}
}
