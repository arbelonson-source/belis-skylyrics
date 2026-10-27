package dev.beli.skylyrics.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.config.SkyLyricsConfig.Animation;
import dev.beli.skylyrics.config.SkyLyricsConfig.ColorMode;
import dev.beli.skylyrics.config.SkyLyricsConfig.Placement;
import dev.beli.skylyrics.config.SkyLyricsConfig.SkyEffect;
import dev.beli.skylyrics.config.Themes.Theme;

class ThemesTest {
	private static Theme theme(String id) {
		return Themes.ALL.stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
	}

	private static SkyLyricsConfig unusual() {
		SkyLyricsConfig cfg = new SkyLyricsConfig();
		cfg.enabled = false;
		cfg.placement = Placement.FLOATING;
		cfg.height = 40;
		cfg.distance = 30;
		cfg.showPrevious = false;
		cfg.syncOffsetMs = 250;
		cfg.songOffsets.put("song", 120);
		cfg.seeThroughBlocks = false;
		cfg.hideInCaves = true;
		cfg.hideWithF1 = false;
		cfg.nowPlayingHud = false;
		cfg.preferredPlayer = "spotify";
		cfg.demoMode = true;
		return cfg;
	}

	private static Map<String, Object> otherSettings(SkyLyricsConfig cfg) throws IllegalAccessException {
		Map<String, Object> out = new HashMap<>();
		for (Field f : SkyLyricsConfig.class.getDeclaredFields()) {
			if (!Modifier.isStatic(f.getModifiers()) && !Themes.LOOK.contains(f.getName())) {
				Object v = f.get(cfg);
				out.put(f.getName(), v instanceof Map<?, ?> m ? new LinkedHashMap<>(m) : v);
			}
		}
		return out;
	}

	@Test
	void applyingSetsTheLookAndNothingElse() throws IllegalAccessException {
		for (Theme theme : Themes.ALL) {
			SkyLyricsConfig cfg = unusual();
			cfg.glow = 0.35f;
			cfg.uppercase = true;
			cfg.animation = Animation.TYPEWRITER;
			Map<String, Object> before = otherSettings(cfg);
			Themes.apply(theme, cfg);
			assertTrue(Themes.sameLook(theme.preview(), cfg), theme.id());
			assertSame(theme, Themes.active(cfg), theme.id());
			assertEquals(before, otherSettings(cfg), theme.id());
		}
	}

	@Test
	void themesSetTheExpectedLooks() {
		SkyLyricsConfig neon = theme("neon").preview();
		assertTrue(Themes.sameLook(new SkyLyricsConfig(), neon));

		SkyLyricsConfig minimal = theme("minimal").preview();
		assertEquals(0, minimal.glow);
		assertFalse(minimal.pulse);
		assertTrue(minimal.size < 1);
		assertEquals(Animation.FADE, minimal.animation);
		assertEquals(SkyEffect.OFF, minimal.sky);

		SkyLyricsConfig karaoke = theme("karaoke").preview();
		assertTrue(karaoke.size > 1);
		assertTrue(karaoke.karaoke);
		assertEquals(Animation.RISE, karaoke.animation);

		SkyLyricsConfig retro = theme("retro").preview();
		assertFalse(retro.smoothFont);
		assertEquals(0, retro.glow);
		assertTrue(retro.shadow);

		SkyLyricsConfig dreamy = theme("dreamy").preview();
		assertTrue(dreamy.smoothFont && dreamy.scriptFont);
		assertEquals(SkyEffect.NEBULA, dreamy.sky);
		assertTrue(dreamy.glow > 0 && dreamy.glow <= 0.5f); // a soft glow; the default look has none

		assertEquals(ColorMode.RAINBOW, theme("rainbow").preview().colorMode);

		SkyLyricsConfig moonlit = theme("moonlit").preview();
		assertEquals(SkyLyricsConfig.Layout.WORLD, moonlit.layout);
		assertEquals(SkyEffect.MOONLIGHT, moonlit.sky);
		assertTrue(moonlit.uppercase);

		SkyLyricsConfig drift = theme("drift").preview();
		assertEquals(SkyLyricsConfig.Layout.WORLD, drift.layout);
		assertTrue(drift.worldFlow);
		assertEquals(ColorMode.ALBUM, drift.colorMode);

		SkyLyricsConfig lilac = theme("lilac").preview();
		assertTrue(lilac.worldFlow);
		assertFalse(lilac.uppercase);
		assertEquals(SkyEffect.OFF, lilac.sky);

		SkyLyricsConfig corona = theme("corona").preview();
		assertEquals(SkyEffect.ECLIPSE, corona.sky);
		assertTrue(corona.size > 1 && corona.glow > 0);
	}

	@Test
	void flowingWordsOnlyComeWithTheThemeThatAsksForThem() {
		SkyLyricsConfig cfg = new SkyLyricsConfig();
		Themes.apply(theme("drift"), cfg);
		assertTrue(cfg.worldFlow);
		Themes.apply(theme("moonlit"), cfg);
		assertFalse(cfg.worldFlow);
	}

	@Test
	void changingAnyLookSettingShowsCustom() {
		SkyLyricsConfig cfg = new SkyLyricsConfig();
		assertSame(theme("neon"), Themes.active(cfg));
		cfg.glow = 0.5f;
		assertNull(Themes.active(cfg));

		Themes.apply(theme("retro"), cfg);
		assertSame(theme("retro"), Themes.active(cfg));
		cfg.shadow = false;
		assertNull(Themes.active(cfg));
		cfg.shadow = true;
		assertSame(theme("retro"), Themes.active(cfg));

		cfg.distance = 20;
		cfg.hideInNether = true;
		assertSame(theme("retro"), Themes.active(cfg));

		cfg.color = 0x123456;
		assertNull(Themes.active(cfg));
	}

	@Test
	void pickedColourIsIgnoredWhileTheSongPicksTheColour() {
		SkyLyricsConfig cfg = new SkyLyricsConfig();
		cfg.color = 0x7BD8FF;
		assertNull(Themes.active(cfg)); // Neon has its own fixed colour, so another one is Custom
		Themes.apply(theme("rainbow"), cfg);
		cfg.color = 0xFF7BAC;
		assertSame(theme("rainbow"), Themes.active(cfg));
	}

	@Test
	void everyThemeLooksDifferentAndFitsTheSliders() {
		for (Theme a : Themes.ALL) {
			for (Theme b : Themes.ALL) {
				assertEquals(a == b, Themes.sameLook(a.preview(), b.preview()), a.id() + " vs " + b.id());
			}
			SkyLyricsConfig c = a.preview();
			assertTrue(c.glow >= 0 && c.glow <= 1, a.id());
			assertTrue(c.opacity >= 0.2f && c.opacity <= 1, a.id());
			assertTrue(c.size >= 0.3f && c.size <= 3, a.id());
			assertTrue(c.skyStrength >= 0.1f && c.skyStrength <= 1, a.id());
		}
	}

	@Test
	void activeThemeSurvivesSavingAndLoading() {
		Gson gson = new Gson();
		for (Theme theme : Themes.ALL) {
			SkyLyricsConfig loaded = gson.fromJson(gson.toJson(theme.preview()), SkyLyricsConfig.class);
			assertSame(theme, Themes.active(loaded), theme.id());
		}
	}
}
