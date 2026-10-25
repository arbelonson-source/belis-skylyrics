package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BossBarTest {
	@Test
	void topOfScreenWithoutBossBars() {
		assertEquals(5, NowPlayingHud.topBelowBossBars(0, 240));
	}

	@Test
	void belowEachBossBar() {
		assertEquals(21, NowPlayingHud.topBelowBossBars(1, 240), "one bar: its name at 3, the bar 12..17");
		assertEquals(40, NowPlayingHud.topBelowBossBars(2, 240));
	}

	@Test
	void onlyAsManyAsMinecraftShows() {
		// Minecraft stops drawing boss bars past the top third of the screen
		assertEquals(NowPlayingHud.topBelowBossBars(4, 240), NowPlayingHud.topBelowBossBars(9, 240));
		assertEquals(59, NowPlayingHud.topBelowBossBars(4, 180));
	}
}
