package dev.beli.skylyrics.render;

/** Colour maths for the night sky: turning the aurora and nebula colours towards a song's colour. */
final class SkyPalette {
	private SkyPalette() {}

	/** Hue of an RGB colour, 0..1 around the colour wheel (0 for greys). */
	static float hue(int rgb) {
		float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float d = max - Math.min(r, Math.min(g, b));
		if (d <= 0) {
			return 0;
		}
		float h = max == r ? (g - b) / d : max == g ? 2 + (b - r) / d : 4 + (r - g) / d;
		h /= 6;
		return h < 0 ? h + 1 : h;
	}

	/** How colourful an RGB colour is, 0 (grey) to 1 (pure). */
	static float saturation(int rgb) {
		int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
		int max = Math.max(r, Math.max(g, b));
		return max == 0 ? 0 : (max - Math.min(r, Math.min(g, b))) / (float) max;
	}

	/** The same colour with its hue turned by {@code turn} (1 = a full circle); brightness stays. */
	static int turnHue(int rgb, float turn) {
		int max = Math.max(rgb >> 16 & 255, Math.max(rgb >> 8 & 255, rgb & 255));
		float h = hue(rgb) + turn;
		return hsv(h - (float) Math.floor(h), saturation(rgb), max / 255f);
	}

	/**
	 * {@code base} turned so that {@code reference} would land on the tint's hue, blended in by {@code amount}.
	 * Turning a whole palette by the same step keeps its look while it takes on the tint's colour.
	 */
	static int recolor(int base, int reference, int tint, float amount) {
		return SkyEffects.mix(base, turnHue(base, hue(tint) - hue(reference)), amount);
	}

	/** How far to follow a tint: greys and near-whites have no hue worth following. */
	static float follow(int tint) {
		return Math.min(1, saturation(tint) * 3);
	}

	private static int hsv(float h, float s, float v) {
		float h6 = h * 6;
		int sector = (int) h6 % 6;
		float f = h6 - (int) h6;
		float p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
		float r, g, b;
		switch (sector) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
	}
}
