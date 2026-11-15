package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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
}
