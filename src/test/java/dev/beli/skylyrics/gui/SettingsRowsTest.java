package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.config.SkyLyricsConfig.Layout;
import dev.beli.skylyrics.config.SkyLyricsConfig.Placement;
import dev.beli.skylyrics.config.SkyLyricsConfig.SkyEffect;
import org.junit.jupiter.api.Test;

class SettingsRowsTest {
	@Test
	void flatOnlyRowsAreHiddenInWorld() {
		assertFalse(SkyLyricsScreen.flatLayout(Layout.WORLD));
		assertTrue(SkyLyricsScreen.flatLayout(Layout.ARC));
		assertTrue(SkyLyricsScreen.flatLayout(Layout.LINES));
	}

	@Test
	void distanceShowsRealBlocks() {
		assertEquals("10.7", SkyLyricsScreen.distanceBlocks(Placement.FLOATING, 64, 12));
		assertEquals("4.0", SkyLyricsScreen.distanceBlocks(Placement.FLOATING, 8, 12));
		assertEquals("16.0", SkyLyricsScreen.distanceBlocks(Placement.FLOATING, 160, 12));
		assertEquals("100", SkyLyricsScreen.distanceBlocks(Placement.SKY, 100, 12));
		// capped to 80% of the render distance (8 chunks = 128 blocks -> 102)
		assertEquals("102", SkyLyricsScreen.distanceBlocks(Placement.SKY, 160, 8));
	}

	@Test
	void constellationsAloneCountAsASkyEffect() {
		SkyLyricsConfig cfg = new SkyLyricsConfig();
		cfg.sky = SkyEffect.OFF;
		cfg.shootingStars = false;
		cfg.twinkle = false;
		cfg.constellations = false;
		assertFalse(SkyLyricsScreen.anySkyEffect(cfg));
		cfg.constellations = true;
		assertTrue(SkyLyricsScreen.anySkyEffect(cfg));
	}

	@Test
	void skySpeedShowsForEveryEffectOnItsClock() {
		SkyLyricsConfig cfg = new SkyLyricsConfig();
		cfg.sky = SkyEffect.OFF;
		cfg.shootingStars = false;
		cfg.twinkle = false;
		cfg.constellations = false;
		assertFalse(SkyLyricsScreen.skySpeedApplies(cfg));
		cfg.constellations = true;
		assertTrue(SkyLyricsScreen.skySpeedApplies(cfg));
		cfg.constellations = false;
		cfg.twinkle = true;
		assertTrue(SkyLyricsScreen.skySpeedApplies(cfg));
		cfg.twinkle = false;
		cfg.sky = SkyEffect.AURORA;
		assertTrue(SkyLyricsScreen.skySpeedApplies(cfg));
		// shooting stars age by real time, not the sky clock
		cfg.sky = SkyEffect.OFF;
		cfg.shootingStars = true;
		assertFalse(SkyLyricsScreen.skySpeedApplies(cfg));
	}
}
