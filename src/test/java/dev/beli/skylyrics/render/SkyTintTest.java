package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkyTintTest {
	@Test
	void greyAndBlackSkiesLeaveTheColourAlone() {
		assertEquals(0xFF66AA, SkyTint.tint(0xFF66AA, 0x808080, SkyTint.AMOUNT));
		assertEquals(0xFF66AA, SkyTint.tint(0xFF66AA, 0x000000, SkyTint.AMOUNT));
		assertEquals(0xFF66AA, SkyTint.tint(0xFF66AA, 0x050208, SkyTint.AMOUNT));
	}

	@Test
	void aDarkNightSkyTintsWithoutDimming() {
		int tinted = SkyTint.tint(0xFFFFFF, 0x0A1226, SkyTint.AMOUNT);
		int r = tinted >> 16 & 255, g = tinted >> 8 & 255, b = tinted & 255;
		// White turns a little blue, but stays bright
		assertEquals(255, b);
		assertTrue(r < 255 && r > 150, "red " + r);
		assertTrue(g < 255 && g > r, "green " + g);
	}

	@Test
	void aPurpleSkyPullsWhiteTowardsPurple() {
		int tinted = SkyTint.tint(0xFFFFFF, SynthwaveSky.skyColor(SkyTint.WORDS_ELEVATION), SkyTint.AMOUNT);
		int r = tinted >> 16 & 255, g = tinted >> 8 & 255, b = tinted & 255;
		assertTrue(g < r && g < b, "green should drop the most: " + Integer.toHexString(tinted));
		assertTrue(Math.min(r, b) > 180, "stays light: " + Integer.toHexString(tinted));
	}

	@Test
	void tintedWordsKeepMostOfTheirBrightness() {
		for (int sky : new int[] {MoonlitSky.skyColor(SkyTint.WORDS_ELEVATION), SynthwaveSky.skyColor(SkyTint.WORDS_ELEVATION)}) {
			int tinted = SkyTint.tint(0xFFFFFF, sky, SkyTint.AMOUNT);
			assertTrue(tinted != 0xFFFFFF, "still tinted on " + Integer.toHexString(sky));
			assertTrue(SkyColors.luma(tinted) > 0.8f * 255, "too dark on " + Integer.toHexString(sky) + ": " + Integer.toHexString(tinted));
		}
	}

	@Test
	void aNearlyBlackSkyTintsLessThanTheSameColourInDaylight() {
		// The same hue at 12 and at 200 on its brightest channel: the dim one has hardly any trustworthy colour
		int dim = SkyTint.tint(0xFFFFFF, 0x0C0408, SkyTint.AMOUNT);
		int bright = SkyTint.tint(0xFFFFFF, 0xC84064, SkyTint.AMOUNT);
		int dimLoss = 3 * 255 - ((dim >> 16 & 255) + (dim >> 8 & 255) + (dim & 255));
		int brightLoss = 3 * 255 - ((bright >> 16 & 255) + (bright >> 8 & 255) + (bright & 255));
		assertTrue(dimLoss < brightLoss / 2, "dim " + Integer.toHexString(dim) + " bright " + Integer.toHexString(bright));
		// and right at the edge of black it is only just beginning
		int edge = SkyTint.tint(0xFFFFFF, 0x090308, SkyTint.AMOUNT);
		assertTrue(3 * 255 - ((edge >> 16 & 255) + (edge >> 8 & 255) + (edge & 255)) < dimLoss, Integer.toHexString(edge));
	}

	@Test
	void aMutedSkyStillTintsAtAllNotInProportionToItsPaleness() {
		// A pale blue-grey sky: only 13% saturated, but the square root keeps its tint visible
		int tinted = SkyTint.tint(0xFFFFFF, 0xC8D2E6, SkyTint.AMOUNT);
		int r = tinted >> 16 & 255;
		assertTrue(r <= 251, "red " + r + " in " + Integer.toHexString(tinted));
	}

	@Test
	void noAmountNoTint() {
		assertEquals(0x33CC99, SkyTint.tint(0x33CC99, 0x4B175F, 0));
	}
}
