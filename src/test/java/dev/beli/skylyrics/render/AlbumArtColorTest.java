package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

class AlbumArtColorTest {
	@Test
	void aCoverWeCantReadFallsBackInsteadOfKeepingTheLastColour() {
		assertEquals(AlbumArt.FALLBACK_COLOR, AlbumArt.colorFor(null));
	}

	@Test
	void aColourfulCoverGivesItsOwnColour() {
		BufferedImage red = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				red.setRGB(x, y, Color.RED.getRGB());
			}
		}
		assertNotEquals(AlbumArt.FALLBACK_COLOR, AlbumArt.colorFor(red));
	}

	private static BufferedImage solid(int rgb) {
		BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				image.setRGB(x, y, rgb);
			}
		}
		return image;
	}

	private static int red(int rgb) {
		return (rgb >> 16) & 0xFF;
	}

	private static int green(int rgb) {
		return (rgb >> 8) & 0xFF;
	}

	private static int blue(int rgb) {
		return rgb & 0xFF;
	}

	@Test
	void aRedCoverGivesAReddishGlowWithoutAlpha() {
		int c = AlbumArt.colorFor(solid(Color.RED.getRGB()));
		assertEquals(0, c >>> 24);
		assertEquals(255, red(c));
		assertTrue(green(c) < 160 && blue(c) < 160, "should stay red: " + Integer.toHexString(c));
	}

	@Test
	void aBlueCoverGivesABluishGlow() {
		int c = AlbumArt.colorFor(solid(Color.BLUE.getRGB()));
		assertEquals(255, blue(c));
		assertTrue(red(c) < 160 && green(c) < 200, "should stay blue: " + Integer.toHexString(c));
	}

	@Test
	void greyBlackAndWhiteCoversGiveTheFallback() {
		assertEquals(AlbumArt.FALLBACK_COLOR, AlbumArt.colorFor(solid(0x808080)));
		assertEquals(AlbumArt.FALLBACK_COLOR, AlbumArt.colorFor(solid(0x000000)));
		assertEquals(AlbumArt.FALLBACK_COLOR, AlbumArt.colorFor(solid(0xFFFFFF)));
	}

	@Test
	void aVeryDarkOrWashedOutCoverGivesTheFallback() {
		// Dark red: brightness below 0.2. Pale pink: saturation below 0.25.
		assertEquals(AlbumArt.FALLBACK_COLOR, AlbumArt.colorFor(solid(0x300000)));
		assertEquals(AlbumArt.FALLBACK_COLOR, AlbumArt.colorFor(solid(0xFFD8D8)));
	}

	@Test
	void aSingleColourfulSpeckOnGreyIsNotEnough() {
		BufferedImage grey = solid(0x808080);
		grey.setRGB(0, 0, Color.RED.getRGB());
		assertEquals(AlbumArt.FALLBACK_COLOR, AlbumArt.colorFor(grey));
	}

	@Test
	void theMostCommonHueWins() {
		BufferedImage image = solid(Color.GREEN.getRGB());
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 20; x++) {
				image.setRGB(x, y, Color.RED.getRGB());
			}
		}
		int c = AlbumArt.colorFor(image);
		assertTrue(green(c) > red(c), "green covers more of the image: " + Integer.toHexString(c));
	}

	@Test
	void greyBordersDontHideTheColourInside() {
		BufferedImage image = solid(0x808080);
		for (int y = 16; y < 48; y++) {
			for (int x = 16; x < 48; x++) {
				image.setRGB(x, y, Color.RED.getRGB());
			}
		}
		int c = AlbumArt.colorFor(image);
		assertEquals(255, red(c));
		assertTrue(green(c) < 160 && blue(c) < 160);
	}
}
