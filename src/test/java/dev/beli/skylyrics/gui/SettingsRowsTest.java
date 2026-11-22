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
		assertEquals("10.7", SkyLyricsScreen.distanceBlocks(Placement.FLOATING, 64, 12, LINES));
		assertEquals("4.0", SkyLyricsScreen.distanceBlocks(Placement.FLOATING, 8, 12, LINES));
		assertEquals("16.00", SkyLyricsScreen.distanceBlocks(Placement.FLOATING, 160, 12, LINES));
		assertEquals("64", SkyLyricsScreen.distanceBlocks(Placement.SKY, 64, 12, LINES));
		// never closer than 8 blocks, even when the stored value would say less
		assertEquals("8", SkyLyricsScreen.distanceBlocks(Placement.SKY, 2, 12, LINES));
	}

	/** The default lines (Size 1, the stacked lines) in the default view: FOV 70 in a 16:9 window. */
	private static final float LINES = dev.beli.skylyrics.render.LyricsRenderer.skyReach(false, 1, 70, 1920, 1080);

	@Test
	void skyDistanceReadsWhereTheLyricsHangInFrontOfThePaintedSky() {
		// The Sky lyrics come in to stay in front of the painted skies (88 blocks out at render distance 8, 21 at 2), the
		// ends of a row lying farther than its middle; the slider said 80% of the render distance, 102 and 26 blocks
		assertEquals("66", SkyLyricsScreen.distanceBlocks(Placement.SKY, 160, 8, LINES));
		assertEquals("16", SkyLyricsScreen.distanceBlocks(Placement.SKY, 100, 2, LINES));
		// An Arc line as wide as the view allows reaches farther out, so it comes in at render distance 8 already
		float arc = dev.beli.skylyrics.render.LyricsRenderer.skyReach(true, 1, 70, 1920, 1080);
		assertEquals("60", SkyLyricsScreen.distanceBlocks(Placement.SKY, 64, 8, arc));
		// And the Distance setting itself where the lines fit: 100 at render distance 16
		assertEquals("100", SkyLyricsScreen.distanceBlocks(Placement.SKY, 100, 16, LINES));
	}

	@Test
	void floatingDistanceReadoutChangesOnEveryStep() {
		String previous = null;
		for (int v = 8; v <= 160; v++) {
			String text = SkyLyricsScreen.distanceBlocks(Placement.FLOATING, v, 12, LINES);
			assertFalse(text.equals(previous), "step " + v + " reads the same as the one before: " + text);
			previous = text;
		}
	}

	@Test
	void timingUnderFiveMillisecondsReadsZero() {
		assertEquals("0 s", SkyLyricsScreen.seconds(0));
		assertEquals("0 s", SkyLyricsScreen.seconds(1));
		assertEquals("0 s", SkyLyricsScreen.seconds(-4));
		assertEquals("+0.01 s", SkyLyricsScreen.seconds(5));
		assertEquals("+0.05 s", SkyLyricsScreen.seconds(50));
		assertEquals("-1.20 s", SkyLyricsScreen.seconds(-1200));
	}

	@Test
	void aSavedPlayerThisSystemDoesNotListStillShowsItsName() {
		String[] keys = {"", "spotify", "firefox"};
		java.util.List<String> names = java.util.List.of("Spotify", "Firefox");
		assertEquals(java.util.List.of("Auto", "Spotify", "Firefox"), SkyLyricsScreen.playerChoices(keys, names, "firefox", "Auto"));
		assertEquals(2, SkyLyricsScreen.playerIndex(keys, "firefox"));
		assertEquals(0, SkyLyricsScreen.playerIndex(keys, ""));
		assertEquals(java.util.List.of("Auto", "Spotify", "Firefox", "edge"), SkyLyricsScreen.playerChoices(keys, names, "edge", "Auto"));
		assertEquals(3, SkyLyricsScreen.playerIndex(keys, "edge"));
	}

	@Test
	void aVeryLongUnlistedPlayerKeyShowsShortenedButIsNotChanged() {
		String[] keys = {"", "spotify"};
		java.util.List<String> names = java.util.List.of("Spotify");
		String key = "x".repeat(200);
		java.util.List<String> choices = SkyLyricsScreen.playerChoices(keys, names, key, "Auto");
		assertEquals("x".repeat(23) + "\u2026", choices.get(2));
		assertEquals(3, choices.size());
		// Exactly 24 code points stay whole
		assertEquals("y".repeat(24), SkyLyricsScreen.playerChoices(keys, names, "y".repeat(24), "Auto").get(2));
		// Emoji (two chars each) are counted as one and never split
		String emoji = "\uD83C\uDFB5".repeat(30);
		String shown = SkyLyricsScreen.playerChoices(keys, names, emoji, "Auto").get(2);
		assertEquals("\uD83C\uDFB5".repeat(23) + "\u2026", shown);
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

	@Test
	void karaokeStaysForTheTopBarInWorld() {
		SkyLyricsConfig cfg = new SkyLyricsConfig();
		cfg.layout = Layout.WORLD;
		cfg.nowPlayingHud = true;
		cfg.barLyrics = true;
		assertTrue(SkyLyricsScreen.karaokeApplies(cfg));
		cfg.barLyrics = false;
		assertFalse(SkyLyricsScreen.karaokeApplies(cfg));
		cfg.barLyrics = true;
		cfg.nowPlayingHud = false;
		assertFalse(SkyLyricsScreen.karaokeApplies(cfg));
		cfg.layout = Layout.LINES;
		assertTrue(SkyLyricsScreen.karaokeApplies(cfg));
	}

	@Test
	void shadowIsHiddenWhereDepthReplacesIt() {
		SkyLyricsConfig cfg = new SkyLyricsConfig();
		cfg.layout = Layout.ARC;
		cfg.depth = true;
		assertFalse(SkyLyricsScreen.shadowApplies(cfg));
		cfg.depth = false;
		assertTrue(SkyLyricsScreen.shadowApplies(cfg));
		// Lines draws its shadow whatever Depth says
		cfg.layout = Layout.LINES;
		cfg.depth = true;
		assertTrue(SkyLyricsScreen.shadowApplies(cfg));
		cfg.layout = Layout.WORLD;
		assertFalse(SkyLyricsScreen.shadowApplies(cfg));
	}

	@Test
	void showUndergroundOnlyShowsWhenItMatters() {
		SkyLyricsConfig cfg = new SkyLyricsConfig();
		cfg.layout = Layout.ARC;
		cfg.layer = SkyLyricsConfig.Layer.AUTO;
		cfg.hideInCaves = false;
		cfg.seeThroughBlocks = false;
		assertTrue(SkyLyricsScreen.showUndergroundApplies(cfg));
		cfg.seeThroughBlocks = true;
		assertFalse(SkyLyricsScreen.showUndergroundApplies(cfg));
		cfg.seeThroughBlocks = false;
		cfg.hideInCaves = true;
		assertFalse(SkyLyricsScreen.showUndergroundApplies(cfg));
		cfg.hideInCaves = false;
		cfg.layer = SkyLyricsConfig.Layer.SCREEN;
		assertFalse(SkyLyricsScreen.showUndergroundApplies(cfg));
		cfg.layer = SkyLyricsConfig.Layer.AUTO;
		cfg.layout = Layout.WORLD;
		assertFalse(SkyLyricsScreen.showUndergroundApplies(cfg));
	}

	@Test
	void depthOnlyShowsForArc() {
		SkyLyricsConfig cfg = new SkyLyricsConfig();
		cfg.layout = Layout.ARC;
		assertTrue(SkyLyricsScreen.depthApplies(cfg));
		cfg.layout = Layout.LINES;
		assertFalse(SkyLyricsScreen.depthApplies(cfg));
		cfg.layout = Layout.WORLD;
		assertFalse(SkyLyricsScreen.depthApplies(cfg));
	}
}
