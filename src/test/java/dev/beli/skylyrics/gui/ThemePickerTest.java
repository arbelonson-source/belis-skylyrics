package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.config.Themes;
import dev.beli.skylyrics.config.Themes.Theme;

class ThemePickerTest {
	private static final List<Theme> ALL = Themes.ALL;

	private static SkyLyricsConfig custom() {
		SkyLyricsConfig cfg = new SkyLyricsConfig();
		Themes.apply(ALL.get(0), cfg);
		cfg.size = 3.21f; // no theme uses this
		assertNull(Themes.active(cfg));
		return cfg;
	}

	private static SkyLyricsConfig on(int i) {
		SkyLyricsConfig cfg = new SkyLyricsConfig();
		Themes.apply(ALL.get(i), cfg);
		assertSame(ALL.get(i), Themes.active(cfg));
		return cfg;
	}

	@Test
	void customRightGoesToFirst() {
		assertSame(ALL.get(0), assertDoesNotThrow(() -> ThemePicker.step(custom(), true)));
	}

	@Test
	void customLeftGoesToLast() {
		assertSame(ALL.get(ALL.size() - 1), assertDoesNotThrow(() -> ThemePicker.step(custom(), false)));
	}

	@Test
	void realThemeMovesByOneAndWraps() {
		assertSame(ALL.get(2), ThemePicker.step(on(1), true));
		assertSame(ALL.get(0), ThemePicker.step(on(1), false));
		assertSame(ALL.get(0), ThemePicker.step(on(ALL.size() - 1), true));
		assertSame(ALL.get(ALL.size() - 1), ThemePicker.step(on(0), false));
	}
}
