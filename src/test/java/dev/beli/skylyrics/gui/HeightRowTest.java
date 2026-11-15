package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.config.SkyLyricsConfig.Direction;
import dev.beli.skylyrics.config.SkyLyricsConfig.Layout;
import dev.beli.skylyrics.config.SkyLyricsConfig.Placement;
import org.junit.jupiter.api.Test;

class HeightRowTest {
	@Test
	void arcShowsHeightOnlyForACompassDirectionInTheSky() {
		for (Direction d : Direction.values()) {
			assertFalse(SkyLyricsScreen.heightApplies(Layout.ARC, Placement.FLOATING, d), "floating " + d);
		}
		assertFalse(SkyLyricsScreen.heightApplies(Layout.ARC, Placement.SKY, Direction.FOLLOW));
		assertTrue(SkyLyricsScreen.heightApplies(Layout.ARC, Placement.SKY, Direction.NORTH));
		assertTrue(SkyLyricsScreen.heightApplies(Layout.ARC, Placement.SKY, Direction.WEST));
	}

	@Test
	void linesKeepsHeightAndWorldHasNone() {
		assertTrue(SkyLyricsScreen.heightApplies(Layout.LINES, Placement.FLOATING, Direction.FOLLOW));
		assertTrue(SkyLyricsScreen.heightApplies(Layout.LINES, Placement.SKY, Direction.FOLLOW));
		assertFalse(SkyLyricsScreen.heightApplies(Layout.WORLD, Placement.SKY, Direction.NORTH));
	}
}
