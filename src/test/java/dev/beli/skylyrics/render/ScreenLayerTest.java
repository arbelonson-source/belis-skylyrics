package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.config.SkyLyricsConfig.Layer;
import org.junit.jupiter.api.Test;

class ScreenLayerTest {
	private static boolean wanted(Layer layer, boolean seeThrough, boolean shaders, boolean rain, boolean underground,
			boolean showUnderground) {
		return ScreenLayer.wanted(layer, seeThrough, shaders, rain, underground, showUnderground);
	}

	@Test
	void screenLayerIsAlwaysOnScreen() {
		for (int bits = 0; bits < 32; bits++) {
			assertTrue(wanted(Layer.SCREEN, (bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0, (bits & 8) != 0, (bits & 16) != 0));
		}
	}

	@Test
	void autoStaysInTheWorldWhenNothingCallsForTheScreen() {
		assertFalse(wanted(Layer.AUTO, false, false, false, false, true));
		assertFalse(wanted(Layer.AUTO, false, false, false, false, false));
	}

	@Test
	void autoGoesOnScreenForSeeThroughShadersOrRain() {
		assertTrue(wanted(Layer.AUTO, true, false, false, false, false));
		assertTrue(wanted(Layer.AUTO, false, true, false, false, false));
		assertTrue(wanted(Layer.AUTO, false, false, true, false, false));
	}

	@Test
	void undergroundMovesAutoOnScreenOnlyWhenAsked() {
		assertTrue(wanted(Layer.AUTO, false, false, false, true, true));
		assertFalse(wanted(Layer.AUTO, false, false, false, true, false));
	}

	@Test
	void worldLayerIsNeverOverriddenByShowUnderground() {
		assertFalse(wanted(Layer.WORLD, true, true, true, false, true));
		assertFalse(wanted(Layer.WORLD, false, false, false, true, false));
		assertFalse(wanted(Layer.WORLD, false, false, false, true, true));
	}
}
