package dev.beli.skylyrics.render;

/**
 * The sky clock is a double: a float one loses a frame's step after about 64 hours and the sine tables overflow soon
 * after. Every wave takes the phase from here, which is worked out in double and only then narrowed.
 */
final class SkyClock {
	private SkyClock() {}

	private static final double TWO_PI = 2 * Math.PI;

	/** {@code time * rate + offset} as an angle in 0..2π, exact however long the clock has run. */
	static float phase(double time, double rate, double offset) {
		double p = (time * rate + offset) % TWO_PI;
		return (float) (p < 0 ? p + TWO_PI : p);
	}
}
