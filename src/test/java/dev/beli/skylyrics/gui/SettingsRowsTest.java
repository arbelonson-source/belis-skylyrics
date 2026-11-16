package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.config.SkyLyricsConfig.Layout;
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
}
