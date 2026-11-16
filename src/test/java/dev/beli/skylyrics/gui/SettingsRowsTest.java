package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.config.SkyLyricsConfig.Layout;
import org.junit.jupiter.api.Test;

class SettingsRowsTest {
	@Test
	void flatOnlyRowsAreHiddenInWorld() {
		assertFalse(SkyLyricsScreen.flatLayout(Layout.WORLD));
		assertTrue(SkyLyricsScreen.flatLayout(Layout.ARC));
		assertTrue(SkyLyricsScreen.flatLayout(Layout.LINES));
	}

}
