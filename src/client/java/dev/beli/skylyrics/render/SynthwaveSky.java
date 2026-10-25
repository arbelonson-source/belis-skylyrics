package dev.beli.skylyrics.render;

import dev.beli.skylyrics.render.SkyCanvas.SkyFrame;

/**
 * The synthwave night: a purple sky fading to magenta at the horizon, a dark swirl with faint rings
 * overhead, sparse stars and a big striped retro sun low in the sky with a hot pink glow.
 * Drawn with ordinary blending, back to front, so it covers the vanilla night sky. Every shape is
 * worked out once; a frame only places the corners.
 */
final class SynthwaveSky {
	private static final float DEG = (float) (Math.PI / 180);

	/** How far each layer sits, as a share of the sky radius. Nearer layers draw over farther ones. */
	static final float DOME = 1f, SWIRL = 0.975f, STARS = 0.95f, HALO = 0.93f, GLOW = 0.91f, DISC = 0.89f;

	/** Sun position: right of where the lyrics face, its middle just above the horizon. */
	static final float SUN_YAW = 26, SUN_ELEV = 6.5f;
	/** Angular radius: about a third of the screen height across at the default field of view. */
	static final float SUN_RADIUS = 11.5f;
	/** Centre of the dark swirl, high above where the lyrics face. */
	private static final float SWIRL_ELEV = 62;

	// Sky gradient by height, measured from the reference: deep indigo-violet overhead to magenta at the horizon
	private static final float[] SKY_AT = {-20, -4, 0, 10, 21, 32, 42, 56, 90};
	private static final int[] SKY_RGB = {0x782A86, 0xB140BF, 0xB140BF, 0x70227E, 0x4B175F, 0x39114E, 0x310F46, 0x290D3F, 0x1E0A34};
	/** Dome rows (degrees of height) and columns, fine where the colour changes fast. */
	private static final float[] DOME_ROWS = {-20, -4, 0, 3, 6, 10, 15, 21, 26, 32, 42, 56, 70, 90};
	private static final int DOME_COLUMNS = 36;

	/** The swirl darkens towards this deep blue-violet. */
	private static final int SWIRL_INK = 0x08031E;
	private static final float SWIRL_DARK = 0.75f, SWIRL_REACH = 38;
	/** Ring spacing in degrees, sampled four times per ring so the vertex colours can show it. */
	private static final float RING_PERIOD = 2.8f, RING_DEPTH = 0.28f;
	private static final int SWIRL_RINGS = 56, SWIRL_SEGMENTS = 40;
	private static final float SWIRL_STEP = RING_PERIOD / 4;

	private static final int STAR_COUNT = 320;
	private static final int STAR_RGB = 0xF4ECFF;

	// Sun face, top to bottom (measured): #FFD677, then orange and coral, to #FF5389
	private static final float[] FACE_AT = {0, 0.25f, 0.5f, 1};
	private static final int[] FACE_RGB = {0xFFD677, 0xFFA866, 0xFE7E69, 0xFF5389};
	/** Gaps start 28% of the way down, one every 8.5%, each a little thicker than the one above. */
	static final float GAP_START = 0.28f, GAP_PITCH = 0.085f, GAP_WIDTH = 0.014f, GAP_GROWTH = 0.005f;
	/** Face rows, closer together near the top and bottom where the outline curves fastest. */
	private static final int FACE_ROWS = 40;

	// The glow hugging the disc (and showing through the gaps), then a broad halo, in sun radii
	private static final int GAP_RGB = 0xF56AC3;
	private static final int GLOW_RGB = 0xF650DC;
	private static final int HALO_RGB = 0xE848D6;
	private static final float[] GLOW_R = {0, 0.95f, 1.08f, 1.2f, 1.32f, 1.45f, 1.6f};
	private static final float[] GLOW_A = {1, 1, 1, 0.75f, 0.48f, 0.22f, 0};
	private static final float[] HALO_R = {0, 1, 1.3f, 1.6f, 2, 2.6f, 3.1f};
	private static final float[] HALO_A = {0.45f, 0.45f, 0.38f, 0.26f, 0.13f, 0.04f, 0};
	private static final int ROUND_SEGMENTS = 48;

	private static final int[] DOME_RGB = new int[DOME_ROWS.length];
	private static final float[] SWIRL_TAN = new float[SWIRL_RINGS + 1];
	private static final float[] SWIRL_ALPHA = new float[SWIRL_RINGS + 1];
	private static final float[] SWIRL_COS = new float[SWIRL_SEGMENTS + 1], SWIRL_SIN = new float[SWIRL_SEGMENTS + 1];
	private static final float[] ROUND_COS = new float[ROUND_SEGMENTS + 1], ROUND_SIN = new float[ROUND_SEGMENTS + 1];
	private static final float[] STAR_YAW = new float[STAR_COUNT], STAR_ELEV = new float[STAR_COUNT];
	private static final float[] STAR_SIZE = new float[STAR_COUNT], STAR_ALPHA = new float[STAR_COUNT];
	/** Face rows from top (0) to bottom (1); a row is drawn when FACE_FILL is set, else it is a gap. */
	static final float[] FACE_T;
	static final boolean[] FACE_FILL;
	private static final int[] FACE_ROW_RGB;

	static {
		for (int i = 0; i < DOME_ROWS.length; i++) {
			DOME_RGB[i] = SkyColors.ramp(SKY_AT, SKY_RGB, DOME_ROWS[i]);
		}
		for (int i = 0; i <= SWIRL_RINGS; i++) {
			float r = i * SWIRL_STEP;
			SWIRL_TAN[i] = (float) Math.tan(r * DEG);
			SWIRL_ALPHA[i] = swirl(r);
		}
		for (int j = 0; j <= SWIRL_SEGMENTS; j++) {
			double a = 2 * Math.PI * j / SWIRL_SEGMENTS;
			SWIRL_COS[j] = (float) Math.cos(a);
			SWIRL_SIN[j] = (float) Math.sin(a);
		}
		for (int j = 0; j <= ROUND_SEGMENTS; j++) {
			double a = 2 * Math.PI * j / ROUND_SEGMENTS;
			ROUND_COS[j] = (float) Math.cos(a);
			ROUND_SIN[j] = (float) Math.sin(a);
		}
		for (int i = 0; i < STAR_COUNT; i++) {
			STAR_YAW[i] = SkyColors.hash(i * 3 + 101) * 360;
			// Even spread over the dome between 10 and 80 degrees up
			float lo = (float) Math.sin(10 * DEG), hi = (float) Math.sin(80 * DEG);
			STAR_ELEV[i] = (float) Math.toDegrees(Math.asin(lo + (hi - lo) * SkyColors.hash(i * 3 + 102)));
			float k = SkyColors.hash(i * 3 + 103);
			STAR_SIZE[i] = 0.9f + 0.7f * k;
			STAR_ALPHA[i] = 0.18f + 0.4f * k * k;
		}
		float[] t = new float[200];
		int n = 0;
		for (int k = 0; k < FACE_ROWS; k++) {
			t[n++] = (float) (1 - Math.cos(Math.PI * k / FACE_ROWS)) / 2;
		}
		for (int g = 0; ; g++) {
			float top = gapTop(g);
			if (top >= 1) {
				break;
			}
			t[n++] = top;
			t[n++] = Math.min(1, top + gapWidth(g));
		}
		t[n++] = 1;
		java.util.Arrays.sort(t, 0, n);
		float[] rows = new float[n];
		int m = 0;
		for (int i = 0; i < n; i++) {
			if (m == 0 || t[i] - rows[m - 1] > 1e-4f) {
				rows[m++] = t[i];
			}
		}
		FACE_T = java.util.Arrays.copyOf(rows, m);
		FACE_FILL = new boolean[m - 1];
		FACE_ROW_RGB = new int[m];
		for (int i = 0; i < m; i++) {
			FACE_ROW_RGB[i] = SkyColors.ramp(FACE_AT, FACE_RGB, FACE_T[i]);
			if (i < m - 1) {
				FACE_FILL[i] = !inGap((FACE_T[i] + FACE_T[i + 1]) / 2);
			}
		}
	}

	private final SkyFrame frame = new SkyFrame();

	/** Top of gap {@code g}, as a share of the sun's height from its top. */
	static float gapTop(int g) {
		return GAP_START + g * GAP_PITCH;
	}

	static float gapWidth(int g) {
		return GAP_WIDTH + g * GAP_GROWTH;
	}

	/** True where the sun face has a stripe gap ({@code t} from 0 at the top to 1 at the bottom). */
	static boolean inGap(float t) {
		if (t < GAP_START) {
			return false;
		}
		int g = (int) ((t - GAP_START) / GAP_PITCH);
		return t - gapTop(g) < gapWidth(g);
	}

	/** How much the swirl darkens the sky {@code r} degrees from its centre, with faint rings. */
	static float swirl(float r) {
		float body = SWIRL_DARK * (1 - SkyColors.smooth(r / SWIRL_REACH));
		// Rings show between the dark middle and the fading rim
		float rings = SkyColors.smooth(r / 6) * (1 - SkyColors.smooth((r - 26) / 12));
		return body * (1 + RING_DEPTH * rings * (float) Math.sin(2 * Math.PI * r / RING_PERIOD));
	}

	/** Sky colour at a height in degrees. */
	static int skyColor(float elevDeg) {
		return SkyColors.ramp(SKY_AT, SKY_RGB, elevDeg);
	}

	/**
	 * @param baseYaw Minecraft yaw the lyrics face; the sun sits to its right
	 * @param level 0..1, how much of the effect shows
	 * @param pixel the angle one screen pixel covers, in radians
	 */
	void draw(SkyCanvas c, float radius, float baseYaw, float level, float time, float pixel) {
		dome(c, radius * DOME, level);
		swirl(c, radius * SWIRL, baseYaw, level);
		stars(c, radius * STARS, level, time, pixel);
		frame.aim(baseYaw + SUN_YAW, SUN_ELEV);
		float tanR = (float) Math.tan(SUN_RADIUS * DEG);
		round(c, radius * HALO, tanR, HALO_R, HALO_A, HALO_RGB, HALO_RGB, level);
		round(c, radius * GLOW, tanR, GLOW_R, GLOW_A, GAP_RGB, GLOW_RGB, level);
		face(c, radius * DISC, tanR, level);
	}

	private static void dome(SkyCanvas c, float r, float level) {
		float step = 360f / DOME_COLUMNS;
		for (int i = 0; i + 1 < DOME_ROWS.length; i++) {
			int lo = SkyColors.argb(DOME_RGB[i], level), hi = SkyColors.argb(DOME_RGB[i + 1], level);
			float e0 = DOME_ROWS[i], e1 = DOME_ROWS[i + 1];
			for (int j = 0; j < DOME_COLUMNS; j++) {
				float y0 = j * step, y1 = y0 + step;
				c.sky(y0, e0, r, lo);
				c.sky(y1, e0, r, lo);
				c.sky(y1, e1, r, hi);
				c.sky(y0, e1, r, hi);
			}
		}
	}

	private void swirl(SkyCanvas c, float r, float baseYaw, float level) {
		frame.aim(baseYaw, SWIRL_ELEV);
		for (int i = 0; i < SWIRL_RINGS; i++) {
			float t0 = SWIRL_TAN[i], t1 = SWIRL_TAN[i + 1];
			int c0 = SkyColors.argb(SWIRL_INK, SWIRL_ALPHA[i] * level), c1 = SkyColors.argb(SWIRL_INK, SWIRL_ALPHA[i + 1] * level);
			for (int j = 0; j < SWIRL_SEGMENTS; j++) {
				c.at(frame, t0 * SWIRL_COS[j], t0 * SWIRL_SIN[j], r, c0);
				c.at(frame, t0 * SWIRL_COS[j + 1], t0 * SWIRL_SIN[j + 1], r, c0);
				c.at(frame, t1 * SWIRL_COS[j + 1], t1 * SWIRL_SIN[j + 1], r, c1);
				c.at(frame, t1 * SWIRL_COS[j], t1 * SWIRL_SIN[j], r, c1);
			}
		}
	}

	private void stars(SkyCanvas c, float r, float level, float time, float pixel) {
		for (int i = 0; i < STAR_COUNT; i++) {
			float twinkle = 0.75f + 0.25f * (float) Math.sin(time * (0.7f + 0.13f * (i % 7)) + i * 2.3f);
			int col = SkyColors.argb(STAR_RGB, STAR_ALPHA[i] * twinkle * level);
			float s = STAR_SIZE[i] * pixel;
			frame.aim(STAR_YAW[i], STAR_ELEV[i]);
			c.at(frame, -s, -s, r, col);
			c.at(frame, s, -s, r, col);
			c.at(frame, s, s, r, col);
			c.at(frame, -s, s, r, col);
		}
	}

	/** Rings around the sun's middle ({@code at} in sun radii) fading by {@code alpha}; the first ring uses {@code inner}. */
	private void round(SkyCanvas c, float r, float tanR, float[] at, float[] alpha, int inner, int outer, float level) {
		for (int i = 0; i + 1 < at.length; i++) {
			float a0 = at[i] * tanR, a1 = at[i + 1] * tanR;
			int c0 = SkyColors.argb(i == 0 || at[i] < 1 ? inner : outer, alpha[i] * level);
			int c1 = SkyColors.argb(at[i + 1] < 1 ? inner : outer, alpha[i + 1] * level);
			for (int j = 0; j < ROUND_SEGMENTS; j++) {
				c.at(frame, a0 * ROUND_COS[j], a0 * ROUND_SIN[j], r, c0);
				c.at(frame, a0 * ROUND_COS[j + 1], a0 * ROUND_SIN[j + 1], r, c0);
				c.at(frame, a1 * ROUND_COS[j + 1], a1 * ROUND_SIN[j + 1], r, c1);
				c.at(frame, a1 * ROUND_COS[j], a1 * ROUND_SIN[j], r, c1);
			}
		}
	}

	/** The striped face: one trapezoid per row, gaps left open so the glow behind shows through. */
	private void face(SkyCanvas c, float r, float tanR, float level) {
		for (int i = 0; i < FACE_FILL.length; i++) {
			if (!FACE_FILL[i]) {
				continue;
			}
			float b0 = 1 - 2 * FACE_T[i], b1 = 1 - 2 * FACE_T[i + 1];
			float w0 = (float) Math.sqrt(Math.max(0, 1 - b0 * b0)), w1 = (float) Math.sqrt(Math.max(0, 1 - b1 * b1));
			int c0 = SkyColors.argb(FACE_ROW_RGB[i], level), c1 = SkyColors.argb(FACE_ROW_RGB[i + 1], level);
			c.at(frame, -w0 * tanR, b0 * tanR, r, c0);
			c.at(frame, w0 * tanR, b0 * tanR, r, c0);
			c.at(frame, w1 * tanR, b1 * tanR, r, c1);
			c.at(frame, -w1 * tanR, b1 * tanR, r, c1);
		}
	}
}
