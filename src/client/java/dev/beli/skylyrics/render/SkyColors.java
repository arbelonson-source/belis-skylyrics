package dev.beli.skylyrics.render;

/** Colour helpers for the sky shapes; plain 0xRRGGBB ints, no Minecraft classes. */
final class SkyColors {
	private SkyColors() {}

	static float clamp01(float v) {
		return v < 0 ? 0 : Math.min(v, 1);
	}

	static float smooth(float x) {
		float t = clamp01(x);
		return t * t * (3 - 2 * t);
	}

	static int argb(int rgb, float alpha) {
		return Math.round(clamp01(alpha) * 255) << 24 | rgb & 0xFFFFFF;
	}

	static int mix(int a, int b, float t) {
		float k = clamp01(t);
		int r = Math.round((a >> 16 & 255) * (1 - k) + (b >> 16 & 255) * k);
		int g = Math.round((a >> 8 & 255) * (1 - k) + (b >> 8 & 255) * k);
		int bl = Math.round((a & 255) * (1 - k) + (b & 255) * k);
		return r << 16 | g << 8 | bl;
	}

	/** Each channel times {@code k}, capped at 255. */
	static int scale(int rgb, float k) {
		int r = Math.min(255, Math.round((rgb >> 16 & 255) * Math.max(0, k)));
		int g = Math.min(255, Math.round((rgb >> 8 & 255) * Math.max(0, k)));
		int b = Math.min(255, Math.round((rgb & 255) * Math.max(0, k)));
		return r << 16 | g << 8 | b;
	}

	/** Channel-wise sum, capped at 255: light added on light. */
	static int add(int a, int b) {
		int r = Math.min(255, (a >> 16 & 255) + (b >> 16 & 255));
		int g = Math.min(255, (a >> 8 & 255) + (b >> 8 & 255));
		int bl = Math.min(255, (a & 255) + (b & 255));
		return r << 16 | g << 8 | bl;
	}

	/** Channel-wise {@code a - b}, not below 0. */
	static int sub(int a, int b) {
		int r = Math.max(0, (a >> 16 & 255) - (b >> 16 & 255));
		int g = Math.max(0, (a >> 8 & 255) - (b >> 8 & 255));
		int bl = Math.max(0, (a & 255) - (b & 255));
		return r << 16 | g << 8 | bl;
	}

	/** The colour at {@code t} along stops at increasing {@code at} positions; flat beyond the ends. */
	static int ramp(float[] at, int[] colors, float t) {
		if (t <= at[0]) {
			return colors[0];
		}
		for (int i = 1; i < at.length; i++) {
			if (t <= at[i]) {
				return mix(colors[i - 1], colors[i], (t - at[i - 1]) / (at[i] - at[i - 1]));
			}
		}
		return colors[colors.length - 1];
	}

	/** Like {@link #ramp} for plain numbers. */
	static float ramp(float[] at, float[] values, float t) {
		if (t <= at[0]) {
			return values[0];
		}
		for (int i = 1; i < at.length; i++) {
			if (t <= at[i]) {
				float k = (t - at[i - 1]) / (at[i] - at[i - 1]);
				return values[i - 1] + (values[i] - values[i - 1]) * k;
			}
		}
		return values[values.length - 1];
	}

	/** A repeatable pseudo-random number in [0, 1) for an integer seed. */
	static float hash(int n) {
		int h = n * 0x27D4EB2D;
		h ^= h >>> 15;
		h *= 0x85EBCA6B;
		h ^= h >>> 13;
		return (h >>> 8) / (float) (1 << 24);
	}

	/** Smooth value noise on a grid, repeatable, in [0, 1). */
	static float noise(float x, float y, int seed) {
		int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
		float fx = smooth(x - ix), fy = smooth(y - iy);
		float a = hash(ix * 73856093 ^ iy * 19349663 ^ seed);
		float b = hash((ix + 1) * 73856093 ^ iy * 19349663 ^ seed);
		float c = hash(ix * 73856093 ^ (iy + 1) * 19349663 ^ seed);
		float d = hash((ix + 1) * 73856093 ^ (iy + 1) * 19349663 ^ seed);
		return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
	}

	/** Like {@link #noise} but repeating every {@code period} grid steps along x, so it can go round the sky. */
	static float noise(float x, float y, int seed, int period) {
		int fl = (int) Math.floor(x), iy = (int) Math.floor(y);
		int ix = Math.floorMod(fl, period), jx = (ix + 1) % period;
		float fx = smooth(x - fl), fy = smooth(y - iy);
		float a = hash(ix * 73856093 ^ iy * 19349663 ^ seed);
		float b = hash(jx * 73856093 ^ iy * 19349663 ^ seed);
		float c = hash(ix * 73856093 ^ (iy + 1) * 19349663 ^ seed);
		float d = hash(jx * 73856093 ^ (iy + 1) * 19349663 ^ seed);
		return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
	}
}
