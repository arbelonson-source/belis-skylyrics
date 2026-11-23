package dev.beli.skylyrics.render;

/**
 * The sky clock is a double: a float one loses a frame's step after about 64 hours and the sine tables overflow soon
 * after. Every wave takes the phase from here, which is worked out in double and only then narrowed.
 */
public final class SkyClock {
	private SkyClock() {}

	/**
	 * How far through a colour cycle of {@code periodNs} the clock {@code nanos} is, from 0 to just under 1. System.nanoTime()
	 * counts from when the computer started: as a float it loses a frame's step after two days, so the remainder is taken
	 * in whole nanoseconds first.
	 */
	public static float cycle(long nanos, long periodNs) {
		float c = Math.floorMod(nanos, periodNs) / (float) periodNs;
		return c < 1f ? c : 0f; // a nanosecond short of the period rounds up to 1, which is the start again
	}

	private static final double TWO_PI = 2 * Math.PI;

	/** {@code time * rate + offset} as an angle in 0..2π, exact however long the clock has run. */
	static float phase(double time, double rate, double offset) {
		double p = (time * rate + offset) % TWO_PI;
		return (float) (p < 0 ? p + TWO_PI : p);
	}
}
