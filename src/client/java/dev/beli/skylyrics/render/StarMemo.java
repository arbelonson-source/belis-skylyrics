package dev.beli.skylyrics.render;

/**
 * The last star-brightness answer and everything it was worked out from. The time of day is part of the key: with
 * /tick freeze the game tick stands still while /time set moves the sun, and the old answer must not outlive that.
 * Free of Minecraft classes (the level and camera are only compared by identity), so it can be unit tested.
 */
final class StarMemo {
	private Object level, camera;
	private long tick, dayTime;
	private float partial, value;
	private double x, y, z;

	/** Whether the kept answer was worked out from exactly these. */
	boolean hit(Object level, Object camera, long tick, long dayTime, float partial, double x, double y, double z) {
		return this.level == level && this.camera == camera && this.tick == tick && this.dayTime == dayTime
				&& this.partial == partial && this.x == x && this.y == y && this.z == z;
	}

	float value() {
		return value;
	}

	void store(Object level, Object camera, long tick, long dayTime, float partial, double x, double y, double z, float value) {
		this.level = level;
		this.camera = camera;
		this.tick = tick;
		this.dayTime = dayTime;
		this.partial = partial;
		this.x = x;
		this.y = y;
		this.z = z;
		this.value = value;
	}

	/** Lets go of the kept level and camera, so leaving a world doesn't keep it alive. */
	void forget() {
		level = null;
		camera = null;
	}
}
