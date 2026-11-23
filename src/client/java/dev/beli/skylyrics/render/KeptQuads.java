package dev.beli.skylyrics.render;

import dev.beli.skylyrics.render.SkyCanvas.SkyFrame;

/**
 * Sky quads placed once and kept from frame to frame while what places them stays the same (the lyrics' direction, the
 * render distance, the size of a screen pixel), together with what {@link SkyCanvas#vertex} works out from the places
 * alone: which way round each quad faces the camera and how much each corner fades below the horizon. A frame then only
 * brings the colours, and hands on exactly what the same corners sent through {@code vertex} would have given.
 * Free of Minecraft classes, so it can be unit tested. Render thread only.
 */
final class KeptQuads {
	private final int quads;
	/** Each quad's corners in the order they are handed on (already facing the camera), four per quad; made on first use. */
	private float[] x, y, z, keep;
	/** Which of the colours given to {@link #draw} each corner takes. */
	private int[] colour;
	/** Whether a corner fades below the horizon, by {@link #keep} of its alpha. */
	private boolean[] faded;
	/** The corners of the quad being placed, as {@code vertex} collects them. */
	private final float[] cx = new float[4], cy = new float[4], cz = new float[4];
	private final int[] cc = new int[4];
	private int placed;
	/** The fade the quads were placed with, once every one of them is in (see {@link #fade}). */
	private float fade = Float.NaN;
	/** The fade the corners being placed take. */
	private float placing;
	/** How many times the quads have been placed; tests check that a steady frame places none. */
	int layouts;

	KeptQuads(int quads) {
		this.quads = quads;
	}

	/** Whether two keys are the same number to the bit, so that nothing kept for one is used for another. */
	static boolean same(float a, float b) {
		return Float.floatToIntBits(a) == Float.floatToIntBits(b);
	}

	/**
	 * The fade below the horizon (as {@link SkyCanvas#horizonFade}) the corners were placed with; NaN until the last quad of a
	 * placing is in. What is kept is checked against it, so a placing cut short by an error is done again on the next frame
	 * instead of half of it being kept until what places the quads changes.
	 */
	float fade() {
		return fade;
	}

	/** Starts placing the quads again from the first, fading the corners below the horizon as a canvas with {@code horizonFade} would. */
	void begin(float horizonFade) {
		if (x == null) {
			x = new float[quads * 4];
			y = new float[quads * 4];
			z = new float[quads * 4];
			keep = new float[quads * 4];
			colour = new int[quads * 4];
			faded = new boolean[quads * 4];
		}
		fade = Float.NaN;
		placing = horizonFade;
		placed = 0;
		layouts++;
	}

	/** Places the next corner, in the order {@code vertex} would take it; it takes colour {@code colourIndex} of those given to {@link #draw}. */
	void corner(float px, float py, float pz, int colourIndex) {
		int m = placed & 3;
		cx[m] = px;
		cy[m] = py;
		cz[m] = pz;
		cc[m] = colourIndex;
		if (++placed % 4 != 0) {
			return;
		}
		int base = placed - 4;
		boolean front = SkyCanvas.facesOrigin(cx, cy, cz);
		for (int i = 0; i < 4; i++) {
			int from = front ? i : 3 - i, k = base + i;
			x[k] = cx[from];
			y[k] = cy[from];
			z[k] = cz[from];
			colour[k] = cc[from];
			// As vertex fades a corner below the horizon
			faded[k] = placing > 0 && cy[from] < 0;
			if (faded[k]) {
				float below = -cy[from] / (float) Math.sqrt(cx[from] * cx[from] + cy[from] * cy[from] + cz[from] * cz[from]);
				keep[k] = 1 - SkyColors.smooth(below / placing);
			}
		}
		if (placed == quads * 4) {
			fade = placing;
		}
	}

	/** Places the next corner as {@link SkyCanvas#at} places it: {@code a} right and {@code b} up from the middle of {@code frame}, pushed out to {@code radius}. */
	void at(SkyFrame frame, float a, float b, float radius, int colourIndex) {
		// The same operations as SkyCanvas.at, so every corner is exactly what it was
		float px = frame.dx + a * frame.ux + b * frame.vx;
		float py = frame.dy + a * frame.uy + b * frame.vy;
		float pz = frame.dz + a * frame.uz + b * frame.vz;
		float k = radius / (float) Math.sqrt(px * px + py * py + pz * pz);
		corner(px * k, py * k, pz * k, colourIndex);
	}

	/** Hands on quad {@code q} (in the order placed), each corner coloured {@code alpha | rgb} as {@link SkyColors#argb} makes it. */
	void draw(SkyCanvas c, int q, int alpha, int[] rgb) {
		for (int k = q * 4, end = k + 4; k < end; k++) {
			pass(c, k, alpha | rgb[colour[k]] & 0xFFFFFF);
		}
	}

	/** Hands on quad {@code q} (in the order placed), each corner taking its colour from {@code argb} as it is. */
	void draw(SkyCanvas c, int q, int[] argb) {
		for (int k = q * 4, end = k + 4; k < end; k++) {
			pass(c, k, argb[colour[k]]);
		}
	}

	private void pass(SkyCanvas c, int k, int argb) {
		if (faded[k]) {
			argb = Math.round((argb >>> 24) * keep[k]) << 24 | argb & 0xFFFFFF;
		}
		c.passOn(x[k], y[k], z[k], argb);
	}
}
