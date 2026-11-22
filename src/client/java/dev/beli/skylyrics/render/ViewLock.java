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
		return cfg.viewLock && pinnable(cfg);
	}

	/** Whether these lyrics can be pinned at all; floating Arc and Lines can't (the settings hide View lock for them). */
	private static boolean pinnable(SkyLyricsConfig cfg) {
		return cfg.layout == Layout.WORLD || cfg.placement == Placement.SKY;
	}

	/**
	 * The pinned spot after a frame. Not locked: nothing. Locked: the spot already held, or, on the frame the lock
	 * turns on, the camera's yaw and pitch right now (not pinned while either is not a finite number). The camera never moves a spot that is already held.
	 */
	@Nullable
	static float[] step(@Nullable float[] held, boolean locked, float camYaw, float camPitch) {
		if (!locked) {
			return null;
		}
		if (held != null) {
			return held;
		}
		// A NaN or infinite camera value must never become the pinned spot: it would stick there for good
		return Float.isFinite(camYaw) && Float.isFinite(camPitch) ? new float[] {camYaw, camPitch} : null;
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

	/** Set on joining a world until the world first shows with its loading screen gone. */
	private static boolean joining;

	/**
	 * You joined a world: a locked view pins wherever you look first there, never a stale spot from the last one. The
	 * world is drawn behind its loading screen before the server says where you look, with the camera still on the
	 * game's default turn (north, level), so the spot is pinned again once that screen is gone (see {@link #tick}).
	 */
	public static void joined() {
		release();
		joining = true;
	}

	/** Call every client tick with whether a screen is open: the first tick with none after {@link #joined} pins afresh. */
	public static void tick(boolean screenOpen) {
		if (joining && !screenOpen) {
			joining = false;
			release();
		}
	}

	/**
	 * The lock key: turns View lock on or off and returns the message to show above the hotbar. Floating Arc and Lines
	 * can't be pinned, so there it changes nothing and says only sky lyrics lock. Unlocked lyrics follow you again only
	 * with Direction Follow; with N, E, S or W they go back to that side of the sky, so they are just unlocked.
	 */
	public static String press(SkyLyricsConfig cfg) {
		if (!pinnable(cfg)) {
			return "skylyrics.lock.sky_only";
		}
		cfg.viewLock = !cfg.viewLock;
		release(); // locking again pins the spot you look at now, even if it was toggled twice between frames
		if (cfg.viewLock) {
			return "skylyrics.lock.on";
		}
		return cfg.direction == Direction.FOLLOW ? "skylyrics.lock.off" : "skylyrics.lock.unlocked";
	}
}
