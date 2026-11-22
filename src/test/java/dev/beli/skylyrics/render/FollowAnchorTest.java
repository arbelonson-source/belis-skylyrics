package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.config.SkyLyricsConfig.Layout;
import dev.beli.skylyrics.config.SkyLyricsConfig.Placement;
import org.junit.jupiter.api.Test;

class FollowAnchorTest {
	@Test
	void withoutLazyFollowTheAnchorAlwaysChasesAtOnce() {
		assertTrue(FollowAnchor.following(false, false, 0));
		assertTrue(FollowAnchor.following(false, false, 10));
		assertEquals(Float.POSITIVE_INFINITY, FollowAnchor.speed(false));
	}

	@Test
	void lazyFollowStaysPutForSmallTurnsAndCatchesUpAfterBigOnes() {
		assertFalse(FollowAnchor.following(false, true, 20));
		assertTrue(FollowAnchor.following(false, true, 60));
		assertTrue(FollowAnchor.following(false, true, -60));
		// wraps around the 180 line
		assertTrue(FollowAnchor.following(false, true, 350 - 10 + 90));
		// keeps chasing until nearly facing it, then rests
		assertTrue(FollowAnchor.following(true, true, 20));
		assertFalse(FollowAnchor.following(true, true, 2));
		assertEquals(FollowAnchor.LAZY_SPEED, FollowAnchor.speed(true));
	}

	@Test
	void theWorldLayoutIgnoresTheHiddenPlacementSoSparklesStayWithItsWords() {
		assertTrue(FollowAnchor.floating(Placement.FLOATING, Layout.ARC, false));
		assertTrue(FollowAnchor.floating(Placement.FLOATING, Layout.LINES, false));
		assertTrue(FollowAnchor.floating(Placement.FLOATING, Layout.LINES, true));
		assertFalse(FollowAnchor.floating(Placement.SKY, Layout.LINES, false));
		// Placement is hidden for World words: one left on Floating from another layout must not pull the sparkles
		// round with your view while the words stay in the north
		assertFalse(FollowAnchor.floating(Placement.FLOATING, Layout.WORLD, false));
		assertFalse(FollowAnchor.floating(Placement.SKY, Layout.WORLD, false));
		// An instrumental's note line is drawn like Lines in World too, so there Placement still counts
		assertTrue(FollowAnchor.floating(Placement.FLOATING, Layout.WORLD, true));
		assertFalse(FollowAnchor.floating(Placement.SKY, Layout.WORLD, true));
	}

	@Test
	void aBadAnchorStartsOver() {
		assertTrue(FollowAnchor.needsInit(Float.NaN, 0));
		assertTrue(FollowAnchor.needsInit(0, Float.NaN));
		assertTrue(FollowAnchor.needsInit(Float.POSITIVE_INFINITY, 0));
		assertFalse(FollowAnchor.needsInit(10, -30));
	}

	@Test
	void joiningAWorldMakesBothAnchorsStartFresh() throws Exception {
		SkyEffects sky = new SkyEffects(null, null, null, null);
		set(sky, "anchorYaw", 90f);
		assertFalse(sky.anchorNeedsInit());
		sky.onJoinWorld();
		assertTrue(sky.anchorNeedsInit());
		LyricsRenderer r = new LyricsRenderer(null, null, null);
		set(r, "anchorYaw", 90f);
		assertFalse(r.anchorNeedsInit());
		r.onJoinWorld();
		assertTrue(r.anchorNeedsInit());
	}

	private static void set(Object o, String name, Object v) throws Exception {
		java.lang.reflect.Field f = o.getClass().getDeclaredField(name);
		f.setAccessible(true);
		f.set(o, v);
	}
}
