package dev.beli.skylyrics.render;

/**
 * How far apart two colours look, as the web's accessibility guide (WCAG 2) measures it: from 1:1 (the same) to 21:1
 * (black on white). Words need about 4.5:1 to read easily and 3:1 to make out at all. Colours are packed RGB here;
 * any alpha is ignored.
 */
final class Contrast {
	/**
	 * The least a colour the player or the album picks may leave between words and what is behind them. Below 3:1 words
	 * are hard to make out at all; the colours already at 3:1 or more (the default violet among them) stay as they are.
	 */
	static final double FLOOR = 3;

	/** Each sRGB channel value's linear light, worked out once. */
	private static final double[] LINEAR = new double[256];

	static {
		for (int i = 0; i < 256; i++) {
			double c = i / 255.0;
			LINEAR[i] = c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
		}
	}

	private Contrast() {
	}

	/** How bright a colour looks, from 0 (black) to 1 (white). */
	static double luminance(int rgb) {
		return 0.2126 * LINEAR[rgb >> 16 & 0xFF] + 0.7152 * LINEAR[rgb >> 8 & 0xFF] + 0.0722 * LINEAR[rgb & 0xFF];
	}

	/** The contrast of two colours, 1 to 21. */
	static double ratio(int a, int b) {
		double x = luminance(a), y = luminance(b);
		return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
	}

	/** {@code argb} laid over the opaque {@code below}, channel by channel as the game blends it. */
	static int over(int argb, int below) {
		float a = (argb >>> 24) / 255f;
		int r = Math.round((argb >> 16 & 0xFF) * a + (below >> 16 & 0xFF) * (1 - a));
		int g = Math.round((argb >> 8 & 0xFF) * a + (below >> 8 & 0xFF) * (1 - a));
		int b = Math.round((argb & 0xFF) * a + (below & 0xFF) * (1 - a));
		return r << 16 | g << 8 | b;
	}

	/**
	 * {@code rgb} as a deeper shade of itself (red, green and blue scaled down together, so the hue stays) just dark
	 * enough for words in the light {@code text} colour to reach {@code floor} on it; unchanged when they already do.
	 */
	static int deepen(int rgb, int text, double floor) {
		rgb &= 0xFFFFFF;
		if (ratio(text, rgb) >= floor) {
			return rgb;
		}
		float lo = 0, hi = 1;
		for (int i = 0; i < 16; i++) {
			float k = (lo + hi) / 2;
			if (ratio(text, scaled(rgb, k)) >= floor) {
				lo = k;
			} else {
				hi = k;
			}
		}
		return scaled(rgb, lo);
	}

	/**
	 * {@code rgb} mixed towards white just enough to reach {@code floor} on the dark {@code behind}; unchanged when it
	 * already does.
	 */
	static int lighten(int rgb, int behind, double floor) {
		rgb &= 0xFFFFFF;
		if (ratio(rgb, behind) >= floor) {
			return rgb;
		}
		float lo = 0, hi = 1;
		for (int i = 0; i < 16; i++) {
			float t = (lo + hi) / 2;
			if (ratio(mixWhite(rgb, t), behind) >= floor) {
				hi = t;
			} else {
				lo = t;
			}
		}
		return mixWhite(rgb, hi);
	}

	private static int scaled(int rgb, float k) {
		return (int) ((rgb >> 16 & 0xFF) * k) << 16 | (int) ((rgb >> 8 & 0xFF) * k) << 8 | (int) ((rgb & 0xFF) * k);
	}

	private static int mixWhite(int rgb, float t) {
		int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
		return Math.round(r + (255 - r) * t) << 16 | Math.round(g + (255 - g) * t) << 8 | Math.round(b + (255 - b) * t);
	}
}
