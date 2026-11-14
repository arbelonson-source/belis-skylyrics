package dev.beli.skylyrics.render;

import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.config.SkyLyricsConfig.Direction;
import dev.beli.skylyrics.config.SkyLyricsConfig.Layout;
import dev.beli.skylyrics.config.SkyLyricsConfig.Placement;
import org.jetbrains.annotations.Nullable;

/**
 * View lock: pins the lyrics at the yaw and pitch you were looking at when you locked them. The pinned spot lives only
 * in memory, so after a restart (or a new world) a locked setting simply pins wherever you look first.
 */
public final class ViewLock {
	private ViewLock() {
	}

	/** The pinned {yaw, pitch}, or null while not locked. */
	@Nullable
	private static float[] held;

	/** True when the lock means something: lyrics in the sky, or World words (which gather round a spot). */
	public static boolean applies(SkyLyricsConfig cfg) {
		return cfg.viewLock && (cfg.layout == Layout.WORLD || cfg.placement == Placement.SKY);
	}

	/**
	 * The pinned spot after a frame. Not locked: nothing. Locked: the spot already held, or, on the frame the lock
	 * turns on, the camera's yaw and pitch right now. The camera never moves a spot that is already held.
	 */
	@Nullable
	static float[] step(@Nullable float[] held, boolean locked, float camYaw, float camPitch) {
		if (!locked) {
			return null;
		}
		return held != null ? held : new float[] {camYaw, camPitch};
	}

	/**
	 * Where the lyrics want to sit: the pinned spot while locked, otherwise per Direction (a compass point at the
	 * Height setting, or where you look for Follow).
	 */
	static float[] target(@Nullable float[] held, Direction direction, float camYaw, float height) {
		if (held != null) {
			return new float[] {held[0], held[1]};
		}
		return new float[] {direction == Direction.FOLLOW ? camYaw : direction.yaw, -height};
	}

	/** Call every frame before using {@link #held()}; returns the pinned {yaw, pitch}, or null when not locked. */
	@Nullable
	public static float[] update(SkyLyricsConfig cfg, float camYaw, float camPitch) {
		held = step(held, applies(cfg), camYaw, camPitch);
		return held;
	}

	/** Forgets the pinned spot, so the next frame pins a new one (when locked). */
	public static void release() {
		held = null;
	}
}
