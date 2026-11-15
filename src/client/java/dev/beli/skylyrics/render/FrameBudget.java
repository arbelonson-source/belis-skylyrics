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

	/** A single frame this many times the smoothed one, and at least this many seconds, is a stall and is left out. */
	static final float STALL_FACTOR = 4, STALL_MIN = 0.05f;
	/** Stalls are left out only this many seconds of counted frames apart: ones that keep coming are a slow game. */
	static final float STALL_APART = 0.5f;

	private float frame; // smoothed seconds per frame
	private float timer;
	private float wait = FIRST_WAIT;
	private float sinceFull = Float.MAX_VALUE;
	private boolean light;
	private float sinceStall = Float.MAX_VALUE; // seconds of counted frames since the last one left out as a stall

	/**
	 * @param dt    seconds since the last frame
	 * @param limit the game's current frame rate limit, so a capped or throttled game isn't mistaken for a slow one
	 */
	void update(float dt, int limit) {
		if (dt <= 0) {
			return;
		}
		// One frame far slower than the rest is a stall (a reload, a shader switch, a chunk burst), not a slow game: leave
		// it out. Two in a row, or stalls that keep coming (every other frame), are the new normal and count.
		if (frame != 0 && dt > STALL_FACTOR * frame && dt > STALL_MIN && sinceStall >= STALL_APART) {
			sinceStall = 0;
			return;
		}
		sinceStall = Math.min(Float.MAX_VALUE, sinceStall + dt);
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

	/**
	 * Forgets everything a stall taught it: back to full effects, no slow time, no relapse doubling, a fresh average.
	 * Called when resources finish reloading or the shaders switch, where the long frames say nothing about the game
	 * (if it is really slow it goes light again after {@link #SLOW_FOR}).
	 */
	void forgive() {
		frame = 0;
		timer = 0;
		wait = FIRST_WAIT;
		sinceFull = Float.MAX_VALUE;
		light = false;
		sinceStall = Float.MAX_VALUE;
	}

	boolean light() {
		return light;
	}

	/** Smoothed frames a second, 0 before the first frame. */
	float fps() {
		return frame == 0 ? 0 : 1 / frame;
	}
}
