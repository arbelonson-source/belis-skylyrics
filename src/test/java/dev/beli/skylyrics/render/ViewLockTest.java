package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.config.SkyLyricsConfig.Direction;
import org.junit.jupiter.api.Test;

class ViewLockTest {
	@Test
	void lockingPinsTheCameraYawAndPitch() {
		assertArrayEquals(new float[] {37, -12}, ViewLock.step(null, true, 37, -12));
	}

	@Test
	void lockedSpotIgnoresTheCamera() {
		float[] held = ViewLock.step(null, true, 37, -12);
		held = ViewLock.step(held, true, 200, 60);
		held = ViewLock.step(held, true, -90, -40);
		assertArrayEquals(new float[] {37, -12}, held);
		// And the lyrics sit there whatever Direction says or the camera does
		assertArrayEquals(new float[] {37, -12}, ViewLock.target(held, Direction.SOUTH, 123, 30));
		assertArrayEquals(new float[] {37, -12}, ViewLock.target(held, Direction.FOLLOW, -5, 30));
	}

	@Test
	void unlockedFollowsDirection() {
		assertArrayEquals(new float[] {123, -30}, ViewLock.target(null, Direction.FOLLOW, 123, 30));
		assertArrayEquals(new float[] {Direction.EAST.yaw, -30}, ViewLock.target(null, Direction.EAST, 123, 30));
		assertNull(ViewLock.step(new float[] {1, 2}, false, 50, 50));
	}

	@Test
	void lockingAgainPinsAFreshSpot() {
		float[] held = ViewLock.step(null, true, 10, 5);
		held = ViewLock.step(held, false, 80, 0);
		held = ViewLock.step(held, true, 80, 20);
		assertArrayEquals(new float[] {80, 20}, held);
	}

	@Test
	void aBrokenCameraValueIsNeverPinned() {
		assertNull(ViewLock.step(null, true, Float.NaN, 5));
		assertNull(ViewLock.step(null, true, 5, Float.NaN));
		assertNull(ViewLock.step(null, true, Float.POSITIVE_INFINITY, 5));
		// and the next good frame pins normally
		float[] held = ViewLock.step(null, true, Float.NaN, 5);
		held = ViewLock.step(held, true, 40, 5);
		assertArrayEquals(new float[] {40, 5}, held);
		assertTrue(Float.isFinite(held[0]));
	}
}
