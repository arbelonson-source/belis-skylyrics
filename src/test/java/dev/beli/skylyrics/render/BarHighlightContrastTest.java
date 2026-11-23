package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.Wcag;
import org.junit.jupiter.api.Test;

/** The top bar's last two sung words are white on a highlight in the bar's accent colour. */
class BarHighlightContrastTest {
	/** The Pick a colour swatches (SkyLyricsScreen.SWATCHES): every one is lighter than the default violet. */
	private static final int[] SWATCHES = {
			0xC7D0F4, 0xEEBAE8, 0xAE90F6, 0x8CDAFF, 0xC8B29C, 0xFF6EC7, 0xFF5A5A, 0xFFA24C, 0xFFE066, 0x6BFF8E, 0xFFFFFF,
	};
	private static final int DEFAULT_ACCENT = 0x926CDE;

	private static String hex(int rgb) {
		return String.format("%06X", rgb & 0xFFFFFF);
	}

	private static void reads(int accent) {
		int hl = NowPlayingHud.highlight(accent);
		double c = Wcag.ratio(NowPlayingHud.SUNG, hl);
		assertTrue(c >= Wcag.LARGE, String.format("words on the %s highlight (accent %s) are %.2f:1", hex(hl), hex(accent), c));
	}

	@Test
	void wordsOnTheHighlightReadInEveryColourToPick() {
		// Before: 1.05:1 (White) to 2.92:1 (Red); Yellow 1.24:1, Periwinkle 1.45:1
		for (int accent : SWATCHES) {
			reads(accent);
		}
	}

	@Test
	void wordsOnTheHighlightReadInEveryAlbumAndLyricsColour() {
		// Album colours are a hue at full brightness and 55 to 85% saturation (AlbumArt.pickColor), and so is Rainbow;
		// a yellow or green album left the words at 1.0:1. Lyrics colours of the Each line and Time of day modes too.
		for (int h = 0; h < 360; h++) {
			for (float s = 0.55f; s <= 0.851f; s += 0.05f) {
				reads(Wcag.hsb(h / 360f, s, 1));
			}
		}
		for (int c : LyricsRenderer.LINE_COLORS) {
			reads(c);
		}
		reads(LyricsRenderer.DAY);
		reads(LyricsRenderer.TWILIGHT);
		reads(LyricsRenderer.NIGHT);
		reads(AlbumArt.FALLBACK_COLOR);
	}

	@Test
	void wordsOnTheHighlightReadOnAnyColourAtAll() {
		for (int r = 0; r < 256; r += 15) {
			for (int g = 0; g < 256; g += 15) {
				for (int b = 0; b < 256; b += 15) {
					reads(r << 16 | g << 8 | b);
				}
			}
		}
	}

	@Test
	void anAccentTheWordsAlreadyReadOnIsLeftAlone() {
		// The default violet (3.7:1) is a matter of taste, not unreadable: it stays as it is
		assertEquals(DEFAULT_ACCENT, NowPlayingHud.highlight(DEFAULT_ACCENT));
		for (int c : new int[] {0x000000, 0x181523, 0x4E3C62, 0x2626FF, 0xC84646, 0x8260C6}) {
			assertEquals(c, NowPlayingHud.highlight(c), hex(c));
		}
	}

	@Test
	void aDeepenedHighlightIsTheSameHueAndOnlyAsDarkAsItNeeds() {
		for (int accent : SWATCHES) {
			int hl = NowPlayingHud.highlight(accent);
			float[] a = java.awt.Color.RGBtoHSB(accent >> 16 & 255, accent >> 8 & 255, accent & 255, null);
			float[] d = java.awt.Color.RGBtoHSB(hl >> 16 & 255, hl >> 8 & 255, hl & 255, null);
			if (a[1] > 0.15f) {
				float turn = Math.abs(a[0] - d[0]);
				assertTrue(Math.min(turn, 1 - turn) < 0.02f, hex(accent) + " turned into " + hex(hl));
			}
			assertTrue(Wcag.ratio(NowPlayingHud.SUNG, hl) < Wcag.LARGE + 0.15, hex(accent) + " went to " + hex(hl) + ", darker than it needs");
		}
	}
}
