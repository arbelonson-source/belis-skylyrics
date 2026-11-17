package dev.beli.skylyrics.render;

/**
 * The last star-brightness answer and everything it was worked out from. The time of day is part of the key: with
 * /tick freeze the game tick stands still while /time set moves the sun, and the old answer must not outlive that.
 * Free of Minecraft classes (the level and camera are only compared by identity), so it can be unit tested.
 */
final class StarMemo {
	private Object level, camera;
	/** Two client ticks and a bit. */
	static final long SETTLE_MS = 120;
	private long tick, dayTime, storedAt, settledAt;
	private float partial, value;
	private double x, y, z;

	/** Whether the kept answer was worked out from exactly these. */
	boolean hit(Object level, Object camera, long tick, long dayTime, float partial, double x, double y, double z) {
		return this.level == level && this.camera == camera && this.tick == tick && this.dayTime == dayTime
				&& this.partial == partial && this.x == x && this.y == y && this.z == z;
	}

	/**
	 * The kept answer if it still holds, else {@code read}'s, which is then kept. When the time of day has just moved,
	 * the game's attribute probe may still hold the old sky until its next client tick, which can be several frames
	 * away, so an answer read within {@link #SETTLE_MS} of the move is not kept: it is read again until one comes from
	 * after that.
	 */
	float get(Object level, Object camera, long tick, long dayTime, float partial, double x, double y, double z, long nowMs,
			java.util.function.DoubleSupplier read) {
		if (hit(level, camera, tick, dayTime, partial, x, y, z) && storedAt >= settledAt) {
			return value;
		}
		if (this.level == level && this.dayTime != dayTime) {
			settledAt = nowMs + SETTLE_MS;
		}
		float fresh = (float) read.getAsDouble();
		store(level, camera, tick, dayTime, partial, x, y, z, fresh);
		storedAt = nowMs;
		return fresh;
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
		settledAt = 0;
		level = null;
		camera = null;
	}
}
