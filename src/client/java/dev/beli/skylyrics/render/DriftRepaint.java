package dev.beli.skylyrics.render;

/**
 * When to repaint something that drifts (degrees, round a full turn) and which bite of it: it waits until the drift
 * has moved a step, then hands out its parts one per call, all with the same drift, so the cost is spread over frames.
 * A big jump is repainted at once. Out of view it does nothing; coming back in it asks for a whole repaint at once, so stale clouds are never seen.
 */
final class DriftRepaint {
	/** Nothing to repaint this frame. */
	static final int NONE = -1;
	/** Repaint everything now. */
	static final int ALL = -2;

	/** A change of this many steps or more is repainted whole. */
	private static final float JUMP = 20;

	private final float step;
	private final int parts;
	private float painted, target;
	private int next = -1;
	private boolean stale;

	DriftRepaint(float step, int parts) {
		this.step = step;
		this.parts = parts;
	}

	/** The drift the part handed out last (and the rest of its repaint) is painted with. */
	float target() {
		return target;
	}

	/**
	 * @param drift the drift now, in degrees
	 * @param visible whether any of it can be on screen
	 * @return {@link #NONE}, {@link #ALL}, or the part (0 up to parts - 1) to repaint with {@link #target()}
	 */
	int advance(float drift, boolean visible) {
		if (!visible) {
			stale = true;
			next = -1;
			return NONE;
		}
		if (stale) {
			stale = false;
			painted = target = drift;
			return ALL;
		}
		if (next >= 0) {
			int part = next;
			next = part + 1 < parts ? part + 1 : -1;
			return part;
		}
		float apart = Math.abs(drift - painted);
		apart = Math.min(apart, 360 - apart);
		if (apart > JUMP * step) {
			// Far from the last picture (just started, or the clock jumped): a bitten repaint would show a patchwork
			painted = target = drift;
			next = -1;
			return ALL;
		}
		if (apart > step) {
			painted = target = drift;
			next = parts > 1 ? 1 : -1;
			return 0;
		}
		return NONE;
	}
}
