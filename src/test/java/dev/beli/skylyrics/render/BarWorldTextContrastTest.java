package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.Wcag;
import org.junit.jupiter.api.Test;

/** The top bar's clock and frame rate sit beside the pill, straight on the world, with no panel behind them. */
class BarWorldTextContrastTest {
	private static void reads(String what, int rgb) {
		for (int world : Wcag.WORLDS) {
			double c = Wcag.reads(rgb, NowPlayingHud.ON_WORLD_SHADOW, world);
			assertTrue(c >= Wcag.TEXT, String.format("%s on %06X reads at %.2f:1", what, world, c));
		}
	}

	@Test
	void theClockReadsOnSnowTheNoonSkyAndInACave() {
		// Before: 1.14:1 on snow and clouds, 2.1:1 on the noon sky
		reads("the clock", NowPlayingHud.CLOCK);
	}

	@Test
	void theFrameRateReadsOnSnowTheNoonSkyAndInACave() {
		// Before: 1.47:1 on the noon sky, 3.5:1 on snow
		reads("the frame rate", NowPlayingHud.STAT_TEXT);
	}
}
