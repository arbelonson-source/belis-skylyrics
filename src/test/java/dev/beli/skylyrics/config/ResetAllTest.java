package dev.beli.skylyrics.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.media.Track;

class ResetAllTest {
	@Test
	void resetKeepsPersonalDataAndResetsSettings() {
		SkyLyricsConfig cfg = new SkyLyricsConfig();
		Track t = new Track("Paper Moon", "Nova Lane", "", 180_000, "", "spotify");
		cfg.introSeen = true;
		cfg.updates = SkyLyricsConfig.Updates.OFF;
		cfg.spotifyClientId = "abc123";
		cfg.setSongOffset(t, 300);
		cfg.recentSearches.add("moon");
		cfg.layout = SkyLyricsConfig.Layout.ARC;
		cfg.glow = 0.5f;

		cfg.resetToDefaults();

		assertTrue(cfg.introSeen);
		assertEquals(SkyLyricsConfig.Updates.OFF, cfg.updates);
		assertEquals("abc123", cfg.spotifyClientId);
		assertEquals(300, cfg.songOffset(t));
		assertEquals(java.util.List.of("moon"), cfg.recentSearches);
		assertEquals(SkyLyricsConfig.Layout.WORLD, cfg.layout);
		assertEquals(0f, cfg.glow);
	}
}
