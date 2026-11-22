package dev.beli.skylyrics.render;

import dev.beli.skylyrics.config.SkyLyricsConfig;
import net.minecraft.util.Mth;

/**
 * The Follow rule shared by the lyrics and the sky effects that gather round them, so the two can't drift apart:
 * with Lazy follow off the anchor snaps to where you look; with it on, it stays put until you turn far away and then
 * catches up until you are nearly facing it again.
 */
final class FollowAnchor {
	static final float LAZY_START = 38f;
	static final float LAZY_STOP = 4f;
	static final float LAZY_SPEED = 2.2f;

	private FollowAnchor() {
	}

	/** Whether Lazy follow counts: the World layout ignores it (its setting is hidden there), so the sparkles must too. */
	static boolean lazy(boolean lazyFollow, SkyLyricsConfig.Layout layout) {
		return lazyFollow && layout != SkyLyricsConfig.Layout.WORLD;
	}

	/**
	 * Whether the lyrics float close by and follow your view. World words ignore Placement (its setting is hidden there,
	 * and they gather by Direction), so one left on Floating must not pull the sparkles round with you; only the note
	 * line of an instrumental song, drawn like Lines even in World, still goes by it.
	 */
	static boolean floating(SkyLyricsConfig.Placement placement, SkyLyricsConfig.Layout layout, boolean instrumental) {
		return placement == SkyLyricsConfig.Placement.FLOATING && (layout != SkyLyricsConfig.Layout.WORLD || instrumental);
	}

	/** Whether the anchor is chasing the camera this frame, given whether it was and the yaw gap in degrees. */
	static boolean following(boolean was, boolean lazy, float yawGap) {
		if (!lazy) {
			return true;
		}
		float gap = Math.abs(Mth.wrapDegrees(yawGap));
		if (gap > LAZY_START) {
			return true;
		}
		if (gap < LAZY_STOP) {
			return false;
		}
		return was;
	}

	/** How fast the anchor moves per second; infinite means snap. */
	static float speed(boolean lazy) {
		return lazy ? LAZY_SPEED : Float.POSITIVE_INFINITY;
	}

	/** True when the anchor has no usable value yet (never set, or a bad camera value got in) and must start over. */
	static boolean needsInit(float anchorYaw, float anchorPitch) {
		return !Float.isFinite(anchorYaw) || !Float.isFinite(anchorPitch);
	}
}
