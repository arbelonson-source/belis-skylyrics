package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.config.SkyLyricsConfig.WorldLeave;
import org.junit.jupiter.api.Test;

/** Distortion Effects at 0 leaves every World motion still (words may still fade); at 1 nothing changes. */
class WorldCalmTest {
	@Test
	void leavingMovesAreStillAtZeroAndUnchangedAtOne() {
		for (WorldLeave leave : WorldLeave.values()) {
			float up = WorldWords.leaveUp(leave, 0.8f, 1.2f);
			float aside = WorldWords.blownBack(leave, 0.8f, 1.2f, false);
			assertEquals(up, WorldWords.leaveUp(leave, 0.8f, 1.2f, 1f), 0f, leave + " up at 1");
			assertEquals(aside, WorldWords.blownBack(leave, 0.8f, 1.2f, false, 1f), 0f, leave + " aside at 1");
			assertEquals(0f, WorldWords.leaveUp(leave, 0.8f, 1.2f, 0f), 0f, leave + " up at 0");
			assertEquals(0f, WorldWords.blownBack(leave, 0.8f, 1.2f, true, 0f), 0f, leave + " aside at 0");
			assertEquals(up / 2, WorldWords.leaveUp(leave, 0.8f, 1.2f, 0.5f), 1e-6f, leave + " up at half");
		}
		assertEquals(1f, WorldWords.leaveSize(0.8f, 1.2f, 0f), 0f, "no shrinking at 0");
		assertEquals(WorldWords.leaveSize(0.8f, 1.2f), WorldWords.leaveSize(0.8f, 1.2f, 1f), 0f);
	}

	@Test
	void fallAndFlowSlideAreStillAtZeroAndUnchangedAtOne() {
		assertTrue(WorldWords.fallDrop(1f) > 0);
		assertEquals(0f, WorldWords.fallDrop(1f, 0f), 0f);
		assertEquals(WorldWords.fallDrop(1f), WorldWords.fallDrop(1f, 1f), 0f);
		assertTrue(WorldWords.slideIn(0f) > 0);
		assertEquals(0f, WorldWords.slideIn(0f, 0f), 0f);
		assertEquals(WorldWords.slideIn(0.1f), WorldWords.slideIn(0.1f, 1f), 0f);
	}

	@Test
	void theSeparateGlideSnapsAtZeroAndIsUnchangedAtOne() {
		float usual = 1 - (float) Math.exp(-WorldWords.SLIDE_SPEED * 0.016f);
		assertTrue(usual > 0 && usual < 1);
		assertEquals(usual, WorldWords.glideEase(0.016f, 1f), 1e-7f);
		assertEquals(1f, WorldWords.glideEase(0.016f, 0f), 0f, "no gliding: it is simply there");
	}
}
