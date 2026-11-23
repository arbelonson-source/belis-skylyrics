package dev.beli.skylyrics;

/**
 * WCAG 2 contrast sums for tests, written apart from the mod's own (render.Contrast) so a slip in one shows in the
 * other. Colours are packed RGB; {@link #over} takes ARGB on top.
 */
public final class Wcag {
	/** Normal-size words (Minecraft's letters at GUI scale 2 or 3 count as normal size). */
	public static final double TEXT = 4.5;
	/** Large words, and the least where words can still be made out at all. */
	public static final double LARGE = 3;

	/** The brightest the world behind a panel gets at noon: snow in the sun, clouds. */
	public static final int SNOW = 0xFFFFFF;
	/** The plains sky at noon (its biome sky colour), and the snowy plains one. */
	public static final int NOON_SKY = 0x78A7FF, SNOWY_SKY = 0x7FA1FF;
	/** The darkest: a cave, the night. */
	public static final int CAVE = 0x000000;
	public static final int[] WORLDS = {SNOW, NOON_SKY, SNOWY_SKY, CAVE};

	private Wcag() {
	}

	private static double linear(int channel) {
		double c = channel / 255.0;
		return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
	}

	public static double luminance(int rgb) {
		return 0.2126 * linear(rgb >> 16 & 255) + 0.7152 * linear(rgb >> 8 & 255) + 0.0722 * linear(rgb & 255);
	}

	public static double ratio(int a, int b) {
		double x = luminance(a), y = luminance(b);
		return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
	}

	/** {@code argb} blended over the opaque {@code below}, as the game draws a see-through colour on what is there. */
	public static int over(int argb, int below) {
		double a = (argb >>> 24) / 255.0;
		int out = 0;
		for (int shift = 16; shift >= 0; shift -= 8) {
			double top = argb >> shift & 255, under = below >> shift & 255;
			out |= (int) Math.round(top * a + under * (1 - a)) << shift;
		}
		return out;
	}

	/** The game's drop shadow under text of colour {@code rgb}: each channel a quarter as bright, one pixel down and right. */
	public static int shadow(int rgb) {
		return (rgb >> 16 & 255) / 4 << 16 | (rgb >> 8 & 255) / 4 << 8 | (rgb & 255) / 4;
	}

	/**
	 * How well text of colour {@code rgb} reads on {@code behind}: their contrast. A shadowed letter also has a dark edge
	 * along it (the way the game's own words read on any ground); where that edge stands out from what is behind by at
	 * least {@link #LARGE} (as WCAG asks of an outline or any other mark), the letter counts against its own shadow, as
	 * WCAG counts text against its halo. A drop shadow only edges two sides, so this stays a model: the game check shows it.
	 */
	public static double reads(int rgb, boolean shadowed, int behind) {
		double plain = ratio(rgb, behind);
		if (!shadowed) {
			return plain;
		}
		int s = shadow(rgb);
		return ratio(s, behind) >= LARGE ? Math.max(plain, ratio(rgb, s)) : plain;
	}

	/** A colour of the given hue (0 to 1), saturation and brightness, as java.awt makes it. */
	public static int hsb(float hue, float saturation, float brightness) {
		return java.awt.Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
	}
}
