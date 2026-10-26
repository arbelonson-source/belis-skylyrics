package dev.beli.skylyrics.render;

import org.joml.Vector4f;

/**
 * The colour of the sky behind the lyrics this frame, so World words can take on a little of it.
 * Vanilla's sky comes from the fog colour the world is drawn with; a painted sky reports its own.
 */
public final class SkyTint {
	/** How far a fully coloured sky pulls the words' colour towards its own. */
	static final float AMOUNT = 0.6f;
	/** Where the words float, in degrees above the horizon, for reading a painted sky's colour. */
	static final float WORDS_ELEVATION = 20;
	/** At or below this brightest channel the sky counts as black; the tint then grows over the next {@link #DARK_FADE}. */
	private static final int DARKEST = 8, DARK_FADE = 24;

	private static int fog = 0x7FA6FF;
	private static int painted;
	private static float paintedAmount;

	private SkyTint() {
	}

	/** Called as the world starts drawing, with the colour its fog and sky fade to. */
	public static void fog(Vector4f rgba) {
		fog = Math.round(SkyColors.clamp01(rgba.x) * 255) << 16 | Math.round(SkyColors.clamp01(rgba.y) * 255) << 8
				| Math.round(SkyColors.clamp01(rgba.z) * 255);
	}

	/** Set by the sky each frame: a painted sky's colour where the words float, and how strongly it shows. */
	static void painted(int rgb, float amount) {
		painted = rgb;
		paintedAmount = amount;
	}

	/** The sky colour behind the words right now. */
	static int sky() {
		return SkyColors.mix(fog, painted, paintedAmount);
	}

	/**
	 * {@code rgb} moved towards the sky's hue at full brightness, so a dark night sky tints the words without
	 * dimming them; grey and white skies have no hue to give and leave them as they are.
	 */
	static int tint(int rgb, int sky, float amount) {
		int r = sky >> 16 & 255, g = sky >> 8 & 255, b = sky & 255;
		int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
		if (max <= DARKEST) {
			return rgb;
		}
		// Hardly any light means hardly any trustworthy hue, so a nearly black sky fades the tint out;
		// the square root lets the muted colours of a night sky still show
		float saturation = (float) Math.sqrt((max - min) / (float) max) * SkyColors.clamp01((max - DARKEST) / (float) DARK_FADE);
		int bright = SkyColors.scale(sky, 255f / max);
		int mixed = SkyColors.mix(rgb, bright, amount * saturation);
		// A coloured light looks darker than white, so lift the words half way back to their own brightness
		float lost = Math.max(1, SkyColors.luma(rgb) / Math.max(1, SkyColors.luma(mixed)));
		return SkyColors.scale(mixed, (float) Math.sqrt(lost));
	}
}
