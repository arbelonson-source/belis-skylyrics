package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
	void aBadAnchorStartsOver() {
		assertTrue(FollowAnchor.needsInit(Float.NaN, 0));
		assertTrue(FollowAnchor.needsInit(0, Float.NaN));
		assertTrue(FollowAnchor.needsInit(Float.POSITIVE_INFINITY, 0));
		assertFalse(FollowAnchor.needsInit(10, -30));
	}
}
