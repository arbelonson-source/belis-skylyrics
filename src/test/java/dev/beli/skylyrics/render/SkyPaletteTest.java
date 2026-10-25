package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkyPaletteTest {
	@Test
	void hueGoesRoundTheWheel() {
		assertEquals(0, SkyPalette.hue(0xFF0000), 1e-6f);
		assertEquals(1 / 3f, SkyPalette.hue(0x00FF00), 1e-6f);
		assertEquals(2 / 3f, SkyPalette.hue(0x0000FF), 1e-6f);
		assertEquals(5 / 6f, SkyPalette.hue(0xFF00FF), 1e-6f);
		assertEquals(0, SkyPalette.hue(0x808080), 1e-6f);
	}

	@Test
	void saturationIsZeroForGreys() {
		assertEquals(0, SkyPalette.saturation(0xFFFFFF), 1e-6f);
		assertEquals(0, SkyPalette.saturation(0x000000), 1e-6f);
		assertEquals(1, SkyPalette.saturation(0xFF0000), 1e-6f);
		assertEquals(0.5f, SkyPalette.saturation(0xFF8080), 0.01f);
	}

	@Test
	void turningHueKeepsBrightnessAndColourfulness() {
		assertEquals(0x00FF00, SkyPalette.turnHue(0xFF0000, 1 / 3f));
		assertEquals(0x0000FF, SkyPalette.turnHue(0xFF0000, -1 / 3f + 1));
		assertEquals(0xFF0000, SkyPalette.turnHue(0xFF0000, 2));
		assertEquals(0x808080, SkyPalette.turnHue(0x808080, 0.3f));
		int teal = 0x3CFFB4;
		int turned = SkyPalette.turnHue(teal, 0.25f);
		assertEquals(SkyPalette.saturation(teal), SkyPalette.saturation(turned), 0.01f);
		assertEquals(255, Math.max(turned >> 16 & 255, Math.max(turned >> 8 & 255, turned & 255)));
	}

	@Test
	void turningRoundTripsWithoutDrift() {
		for (int rgb : new int[] {0x3CFFB4, 0x7A4CFF, 0x2A58E8, 0xD24CFF, 0xB57BFF}) {
			int back = SkyPalette.turnHue(SkyPalette.turnHue(rgb, 0.37f), -0.37f);
			for (int shift = 0; shift <= 16; shift += 8) {
				assertTrue(Math.abs((rgb >> shift & 255) - (back >> shift & 255)) <= 2,
						Integer.toHexString(rgb) + " came back as " + Integer.toHexString(back));
			}
		}
	}

	@Test
	void recolorMovesTheReferenceOntoTheTint() {
		int red = 0xFF2020;
		int teal = 0x3CFFB4;
		int recoloured = SkyPalette.recolor(teal, teal, red, 1);
		assertEquals(SkyPalette.hue(red), SkyPalette.hue(recoloured), 0.01f);
		assertEquals(teal, SkyPalette.recolor(teal, teal, red, 0));
	}

	@Test
	void recolorKeepsThePaletteSpacing() {
		int teal = 0x3CFFB4, violet = 0x7A4CFF;
		float gap = SkyPalette.hue(violet) - SkyPalette.hue(teal);
		int tint = 0xFF9020;
		float newGap = SkyPalette.hue(SkyPalette.recolor(violet, teal, tint, 1))
				- SkyPalette.hue(SkyPalette.recolor(teal, teal, tint, 1));
		float diff = Math.abs(gap - newGap);
		assertTrue(Math.min(diff, 1 - diff) < 0.01f, "gap " + gap + " became " + newGap);
	}

	@Test
	void greysAreNotFollowed() {
		assertEquals(0, SkyPalette.follow(0xFFFFFF), 1e-6f);
		assertEquals(0, SkyPalette.follow(0x404040), 1e-6f);
		assertEquals(1, SkyPalette.follow(0xB57BFF), 1e-6f);
		assertTrue(SkyPalette.follow(0xF0E8FF) < 0.5f, "pale lavender barely counts");
	}
}
