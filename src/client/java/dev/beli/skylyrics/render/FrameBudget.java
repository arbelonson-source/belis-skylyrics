package dev.beli.skylyrics.render;

/**
 * Watches the frame rate and says when the lyrics should draw lighter effects to give frames back. It goes light
 * after a stretch of slow frames and comes back once the game has been smooth for a while; if full effects make it
 * slow again soon after, it waits longer the next time, so it doesn't flip back and forth.
 */
final class FrameBudget {
	/** Frames a second below which effects get lighter, and above which they may come back (both capped by the limit). */
	static final float SLOW_FPS = 45, SMOOTH_FPS = 58;
	/** Seconds the frame rate must stay low before going light. */
	static final float SLOW_FOR = 1.5f;
	/** Seconds of smooth frames before full effects come back; doubles after each relapse, up to the most. */
	static final float FIRST_WAIT = 5, MOST_WAIT = 120;
	/** Going slow again within this many seconds of coming back counts as the full effects' fault. */
	static final float RELAPSE = 30;

	private float frame; // smoothed seconds per frame
	private float timer;
	private float wait = FIRST_WAIT;
	private float sinceFull = Float.MAX_VALUE;
	private boolean light;

	/**
	 * @param dt    seconds since the last frame
	 * @param limit the game's current frame rate limit, so a capped or throttled game isn't mistaken for a slow one
	 */
	void update(float dt, int limit) {
		if (dt <= 0) {
			return;
		}
		frame = frame == 0 ? dt : frame + (dt - frame) * Math.min(1, dt * 4);
		float fps = 1 / frame;
		if (!light) {
			sinceFull = Math.min(Float.MAX_VALUE, sinceFull + dt);
			timer = fps < Math.min(SLOW_FPS, limit * 0.75f) ? timer + dt : 0;
			if (timer >= SLOW_FOR) {
				light = true;
				timer = 0;
				wait = sinceFull < RELAPSE ? Math.min(MOST_WAIT, wait * 2) : FIRST_WAIT;
			}
		} else {
			timer = fps > Math.min(SMOOTH_FPS, limit * 0.9f) ? timer + dt : 0;
			if (timer >= wait) {
				light = false;
				timer = 0;
				sinceFull = 0;
			}
		}
	}

	boolean light() {
		return light;
	}

	/** Smoothed frames a second, 0 before the first frame. */
	float fps() {
		return frame == 0 ? 0 : 1 / frame;
	}
}
