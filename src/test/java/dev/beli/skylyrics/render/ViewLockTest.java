package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.config.SkyLyricsConfig.Direction;
import dev.beli.skylyrics.config.SkyLyricsConfig.Layout;
import dev.beli.skylyrics.config.SkyLyricsConfig.Placement;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

	private static SkyLyricsConfig cfg(Layout layout, Placement placement, Direction direction, boolean locked) {
		SkyLyricsConfig cfg = new SkyLyricsConfig();
		cfg.layout = layout;
		cfg.placement = placement;
		cfg.direction = direction;
		cfg.viewLock = locked;
		return cfg;
	}

	@Test
	void theLockKeyLeavesFloatingLyricsAloneAndSaysOnlySkyLyricsLock() {
		// The settings hide View lock for floating Arc and Lines, and nothing pins them: the key must not claim it did
		for (Layout layout : new Layout[] {Layout.ARC, Layout.LINES}) {
			SkyLyricsConfig cfg = cfg(layout, Placement.FLOATING, Direction.FOLLOW, false);
			assertEquals("skylyrics.lock.sky_only", ViewLock.press(cfg), layout.name());
			assertFalse(cfg.viewLock, layout + ": the hidden setting must not be turned on behind the player's back");
		}
		// World words ignore Placement, so there the key still locks
		SkyLyricsConfig world = cfg(Layout.WORLD, Placement.FLOATING, Direction.FOLLOW, false);
		assertEquals("skylyrics.lock.on", ViewLock.press(world));
		assertTrue(world.viewLock);
	}

	@Test
	void unlockingOnlySaysTheLyricsFollowWhenDirectionIsFollow() {
		SkyLyricsConfig follow = cfg(Layout.LINES, Placement.SKY, Direction.FOLLOW, true);
		assertEquals("skylyrics.lock.off", ViewLock.press(follow));
		assertFalse(follow.viewLock);
		// With N, E, S or W they go back to that side of the sky, not to where you look
		for (Direction d : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
			SkyLyricsConfig fixed = cfg(Layout.ARC, Placement.SKY, d, true);
			assertEquals("skylyrics.lock.unlocked", ViewLock.press(fixed), d.name());
			assertFalse(fixed.viewLock);
			assertEquals("skylyrics.lock.on", ViewLock.press(fixed), d.name());
		}
		assertEquals("skylyrics.lock.unlocked", ViewLock.press(cfg(Layout.WORLD, Placement.SKY, Direction.EAST, true)));
	}

	@Test
	void everyLockKeyMessageIsInTheEnglishFile() throws IOException {
		JsonObject en;
		try (Reader r = Files.newBufferedReader(Path.of("src/main/resources/assets/skylyrics/lang/en_us.json"), StandardCharsets.UTF_8)) {
			en = new Gson().fromJson(r, JsonObject.class);
		}
		for (Layout layout : Layout.values()) {
			for (Placement placement : Placement.values()) {
				for (Direction direction : Direction.values()) {
					for (boolean locked : new boolean[] {false, true}) {
						String key = ViewLock.press(cfg(layout, placement, direction, locked));
						assertTrue(en.has(key), "missing text " + key);
					}
				}
			}
		}
	}
}
