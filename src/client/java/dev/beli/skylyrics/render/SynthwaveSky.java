package dev.beli.skylyrics.render;

import java.util.Arrays;

import dev.beli.skylyrics.render.SkyCanvas.SkyFrame;

/**
 * The synthwave night: a purple sky fading to magenta at the horizon, a dark swirl with faint rings
 * overhead, sparse stars and a big striped retro sun low in the sky with a hot pink glow.
 * <p>
 * It covers the vanilla night sky, so it blends normally, and that pipeline drops nearly transparent
 * pixels: soft see-through overlays would end in a hard edge. So the swirl, the glow and the halo are
 * not overlays; one function gives the finished sky colour in every direction, and each mesh paints it
 * fully opaque, finely where it changes. Meshes and colours are worked out once, for lyrics facing
 * south; a frame only turns them to the lyrics' direction.
 */
final class SynthwaveSky {
	private static final float DEG = (float) (Math.PI / 180);

	/** How far each layer sits, as a share of the sky radius. Nearer layers draw over farther ones. */
	static final float DOME = 1f, SWIRL = 0.975f, SUN_SKY = 0.95f, STARS = 0.93f, DISC = 0.91f;

	/** Sun position: right of where the lyrics face, its middle just above the horizon. */
	static final float SUN_YAW = 26, SUN_ELEV = 6.5f;
	/** Angular radius: about a third of the screen height across at the default field of view. */
	static final float SUN_RADIUS = 11.5f;
	/** Centre of the dark swirl, high above where the lyrics face. */
	private static final float SWIRL_ELEV = 62;

	// Sky gradient by height, measured from the reference: deep indigo-violet overhead to magenta at the horizon
	private static final float[] SKY_AT = {0, 10, 21, 32, 42, 56, 90};
	private static final int[] SKY_RGB = {0xB140BF, 0x70227E, 0x4B175F, 0x39114E, 0x310F46, 0x290D3F, 0x1E0A34};
	/**
	 * Below the horizon everything fades out over this many degrees: there the sky only shows past the
	 * end of the loaded world, and covering that would hide distant land seen from up high.
	 */
	static final float HORIZON_FADE = 3;
	/** Dome rows (degrees of height, including every gradient stop) and columns. */
	private static final float[] DOME_ROWS = {-HORIZON_FADE, 0, 3, 6, 10, 15, 21, 26, 32, 42, 56, 70, 90};
	private static final int DOME_COLUMNS = 36;

	/** The swirl darkens towards this deep blue-violet. */
	private static final int SWIRL_INK = 0x08031E;
	private static final float SWIRL_DARK = 0.75f, SWIRL_REACH = 38;
	/** Ring spacing in degrees, sampled four times per ring so the vertex colours can show it. */
	private static final float RING_PERIOD = 2.8f, RING_DEPTH = 0.28f;
	private static final int SWIRL_RINGS = 57, SWIRL_SEGMENTS = 40;
	private static final float SWIRL_STEP = RING_PERIOD / 4;

	private static final int STAR_COUNT = 320;
	private static final int STAR_RGB = 0xF4ECFF;
	/** No stars this close to the sun's middle, in sun radii (they would show through the stripe gaps). */
	private static final float STAR_CLEAR = 1.3f;

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
	/** The glow is solid to just past the rim, half as strong 0.26 radii further out (measured). */
	private static final float GLOW_SOLID = 1.06f, GLOW_HALF = 0.26f, GLOW_END = 1.8f;
	/** The halo tints the sky out to about three radii. */
	private static final float HALO_PEAK = 0.45f, HALO_END = 3.2f;
	/** Ring spacing round the sun, in sun radii: fine through the glow, coarser through the halo. */
	private static final float GLOW_STEP = 0.05f, HALO_STEP = 0.1f;
	private static final int SUN_SEGMENTS = 48;

	private static final float TAN_SUN = (float) Math.tan(SUN_RADIUS * DEG);
	private static final SkyFrame SUN_FRAME = new SkyFrame().aim(SUN_YAW, SUN_ELEV);
	private static final SkyFrame SWIRL_FRAME = new SkyFrame().aim(0, SWIRL_ELEV);

	private static final Mesh DOME_MESH, SWIRL_MESH, SUN_MESH;
	private static final float[] STAR_YAW = new float[STAR_COUNT], STAR_ELEV = new float[STAR_COUNT];
	private static final float[] STAR_SIZE = new float[STAR_COUNT], STAR_ALPHA = new float[STAR_COUNT];
	private static final int[] STAR_SKY = new int[STAR_COUNT];
	/** Face rows from top (0) to bottom (1); a row is drawn when FACE_FILL is set, else it is a gap. */
	static final float[] FACE_T;
	static final boolean[] FACE_FILL;
	private static final int[] FACE_ROW_RGB;

	static {
		DOME_MESH = new Mesh(DOME_ROWS.length, DOME_COLUMNS + 1);
		for (int i = 0; i < DOME_ROWS.length; i++) {
			double e = DOME_ROWS[i] * DEG;
			// Plain gradient: where the dome meets the swirl and the sun, their own meshes paint over it
			int rgb = skyColor(DOME_ROWS[i]);
			for (int j = 0; j <= DOME_COLUMNS; j++) {
				double yaw = 2 * Math.PI * j / DOME_COLUMNS;
				DOME_MESH.set(i, j, (float) (-Math.cos(e) * Math.sin(yaw)), (float) Math.sin(e),
						(float) (Math.cos(e) * Math.cos(yaw)), rgb);
			}
		}
		float[] swirlRings = new float[SWIRL_RINGS + 1];
		for (int i = 0; i <= SWIRL_RINGS; i++) {
			swirlRings[i] = (float) Math.tan(i * SWIRL_STEP * DEG);
		}
		SWIRL_MESH = polar(SWIRL_FRAME, swirlRings, SWIRL_SEGMENTS);
		SUN_MESH = polar(SUN_FRAME, sunRings(), SUN_SEGMENTS);

		SkyFrame f = new SkyFrame();
		float lo = (float) Math.sin(10 * DEG), hi = (float) Math.sin(80 * DEG);
		for (int i = 0; i < STAR_COUNT; i++) {
			STAR_YAW[i] = SkyColors.hash(i * 3 + 101) * 360;
			// Even spread over the dome between 10 and 80 degrees up
			STAR_ELEV[i] = (float) Math.toDegrees(Math.asin(lo + (hi - lo) * SkyColors.hash(i * 3 + 102)));
			float k = SkyColors.hash(i * 3 + 103);
			STAR_SIZE[i] = 0.9f + 0.7f * k;
			f.aim(STAR_YAW[i], STAR_ELEV[i]);
			STAR_ALPHA[i] = sunDistance(f.dx, f.dy, f.dz) < STAR_CLEAR ? 0 : 0.18f + 0.4f * k * k;
			STAR_SKY[i] = background(f.dx, f.dy, f.dz);
		}

		float[] t = new float[FACE_ROWS * 2 + 64];
		int n = 0;
		for (int k = 0; k < FACE_ROWS; k++) {
			t[n++] = (float) (1 - Math.cos(Math.PI * k / FACE_ROWS)) / 2;
		}
		for (int g = 0; gapTop(g) < 1; g++) {
			t[n++] = gapTop(g);
			t[n++] = Math.min(1, gapTop(g) + gapWidth(g));
		}
		t[n++] = 1;
		Arrays.sort(t, 0, n);
		float[] rows = new float[n];
		int m = 0;
		for (int i = 0; i < n; i++) {
			if (m == 0 || t[i] - rows[m - 1] > 1e-4f) {
				rows[m++] = t[i];
			}
		}
		FACE_T = Arrays.copyOf(rows, m);
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

	/** Points on the sky in rows and columns, with their colours; directions are for lyrics facing south. */
	private static final class Mesh {
		final int rows, cols;
		final float[] x, y, z;
		final int[] rgb;

		Mesh(int rows, int cols) {
			this.rows = rows;
			this.cols = cols;
			x = new float[rows * cols];
			y = new float[rows * cols];
			z = new float[rows * cols];
			rgb = new int[rows * cols];
		}

		void set(int i, int j, float px, float py, float pz, int color) {
			int k = i * cols + j;
			x[k] = px;
			y[k] = py;
			z[k] = pz;
			rgb[k] = color;
		}

		/** Turned by the yaw (given as cosine and sine) and pushed out to {@code r}. */
		void draw(SkyCanvas c, float cos, float sin, float r, float level) {
			for (int i = 0; i + 1 < rows; i++) {
				for (int j = 0; j + 1 < cols; j++) {
					corner(c, i * cols + j, cos, sin, r, level);
					corner(c, i * cols + j + 1, cos, sin, r, level);
					corner(c, (i + 1) * cols + j + 1, cos, sin, r, level);
					corner(c, (i + 1) * cols + j, cos, sin, r, level);
				}
			}
		}

		private void corner(SkyCanvas c, int k, float cos, float sin, float r, float level) {
			c.vertex((x[k] * cos - z[k] * sin) * r, y[k] * r, (z[k] * cos + x[k] * sin) * r, SkyColors.argb(rgb[k], level));
		}
	}

	/** Rings (tangents of their angular radius) round the centre of {@code f}, coloured by {@link #background}. */
	private static Mesh polar(SkyFrame f, float[] rings, int segments) {
		Mesh mesh = new Mesh(rings.length, segments + 1);
		for (int i = 0; i < rings.length; i++) {
			for (int j = 0; j <= segments; j++) {
				double a = 2 * Math.PI * j / segments;
				float ta = rings[i] * (float) Math.cos(a), tb = rings[i] * (float) Math.sin(a);
				float px = f.dx + ta * f.ux + tb * f.vx, py = f.dy + ta * f.uy + tb * f.vy, pz = f.dz + ta * f.uz + tb * f.vz;
				float l = (float) Math.sqrt(px * px + py * py + pz * pz);
				mesh.set(i, j, px / l, py / l, pz / l, background(px / l, py / l, pz / l));
			}
		}
		return mesh;
	}

	/** Rings round the sun as tangents: its middle, then finely through the glow and coarser through the halo. */
	private static float[] sunRings() {
		int glow = Math.round((GLOW_END - 0.9f) / GLOW_STEP), halo = Math.round((HALO_END - GLOW_END) / HALO_STEP);
		float[] r = new float[1 + glow + 1 + halo];
		for (int i = 0; i <= glow; i++) {
			r[1 + i] = (0.9f + (GLOW_END - 0.9f) * i / glow) * TAN_SUN;
		}
		for (int i = 1; i <= halo; i++) {
			r[1 + glow + i] = (GLOW_END + (HALO_END - GLOW_END) * i / halo) * TAN_SUN;
		}
		return r;
	}

	/**
	 * The finished sky colour in a direction (unit vector, lyrics facing south): the gradient, darkened
	 * by the swirl, tinted by the halo and the glow round the sun.
	 */
	static int background(float x, float y, float z) {
		float elev = (float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, y))));
		int rgb = skyColor(elev);
		float toSwirl = x * SWIRL_FRAME.dx + y * SWIRL_FRAME.dy + z * SWIRL_FRAME.dz;
		rgb = SkyColors.mix(rgb, SWIRL_INK, swirl((float) Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, toSwirl))))));
		float r = sunDistance(x, y, z);
		rgb = SkyColors.mix(rgb, HALO_RGB, halo(r));
		// Behind the face the glow takes the slightly paler gap colour
		int glow = SkyColors.mix(GAP_RGB, GLOW_RGB, SkyColors.smooth((r - 0.9f) / 0.15f));
		return SkyColors.mix(rgb, glow, glow(r));
	}

	/** Distance from the sun's middle in sun radii, measured on the flat sky round it like the face. */
	static float sunDistance(float x, float y, float z) {
		float cos = x * SUN_FRAME.dx + y * SUN_FRAME.dy + z * SUN_FRAME.dz;
		if (cos <= 0) {
			return Float.MAX_VALUE;
		}
		return (float) Math.sqrt(Math.max(0, 1 - cos * cos)) / cos / TAN_SUN;
	}

	/** Strength of the hot glow {@code r} sun radii from the middle. */
	static float glow(float r) {
		if (r <= GLOW_SOLID) {
			return 1;
		}
		float k = (r - GLOW_SOLID) / GLOW_HALF;
		return r >= GLOW_END ? 0 : (float) Math.exp(-Math.log(2) * k * k);
	}

	/** Strength of the broad halo {@code r} sun radii from the middle. */
	static float halo(float r) {
		return HALO_PEAK * (1 - SkyColors.smooth((r - 1) / (HALO_END - 1)));
	}

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

	/** Sky colour at a height in degrees (the horizon colour below it). */
	static int skyColor(float elevDeg) {
		return SkyColors.ramp(SKY_AT, SKY_RGB, elevDeg);
	}

	/**
	 * @param baseYaw Minecraft yaw the lyrics face; the sun sits to its right
	 * @param level 0..1, how much of the effect shows
	 * @param pixel the angle one screen pixel covers, in radians
	 */
	void draw(SkyCanvas c, float radius, float baseYaw, float level, float time, float pixel) {
		float cos = (float) Math.cos(baseYaw * DEG), sin = (float) Math.sin(baseYaw * DEG);
		c.fadeBelowHorizon(HORIZON_FADE);
		DOME_MESH.draw(c, cos, sin, radius * DOME, level);
		SWIRL_MESH.draw(c, cos, sin, radius * SWIRL, level);
		SUN_MESH.draw(c, cos, sin, radius * SUN_SKY, level);
		stars(c, radius * STARS, baseYaw, level, time, pixel);
		frame.aim(baseYaw + SUN_YAW, SUN_ELEV);
		face(c, radius * DISC, level);
		c.fadeBelowHorizon(0);
	}

	/** Small square stars, mixed into the sky colour behind them (so they stay opaque), twinkling. */
	private void stars(SkyCanvas c, float r, float baseYaw, float level, float time, float pixel) {
		for (int i = 0; i < STAR_COUNT; i++) {
			if (STAR_ALPHA[i] == 0) {
				continue;
			}
			float twinkle = 0.75f + 0.25f * (float) Math.sin(time * (0.7f + 0.13f * (i % 7)) + i * 2.3f);
			int col = SkyColors.argb(SkyColors.mix(STAR_SKY[i], STAR_RGB, STAR_ALPHA[i] * twinkle), level);
			float s = STAR_SIZE[i] * pixel;
			frame.aim(baseYaw + STAR_YAW[i], STAR_ELEV[i]);
			c.at(frame, -s, -s, r, col);
			c.at(frame, s, -s, r, col);
			c.at(frame, s, s, r, col);
			c.at(frame, -s, s, r, col);
		}
	}

	/** The striped face: one trapezoid per row, gaps left open so the glow behind shows through. */
	private void face(SkyCanvas c, float r, float level) {
		for (int i = 0; i < FACE_FILL.length; i++) {
			if (!FACE_FILL[i]) {
				continue;
			}
			float b0 = 1 - 2 * FACE_T[i], b1 = 1 - 2 * FACE_T[i + 1];
			float w0 = (float) Math.sqrt(Math.max(0, 1 - b0 * b0)), w1 = (float) Math.sqrt(Math.max(0, 1 - b1 * b1));
			int c0 = SkyColors.argb(FACE_ROW_RGB[i], level), c1 = SkyColors.argb(FACE_ROW_RGB[i + 1], level);
			c.at(frame, -w0 * TAN_SUN, b0 * TAN_SUN, r, c0);
			c.at(frame, w0 * TAN_SUN, b0 * TAN_SUN, r, c0);
			c.at(frame, w1 * TAN_SUN, b1 * TAN_SUN, r, c1);
			c.at(frame, -w1 * TAN_SUN, b1 * TAN_SUN, r, c1);
		}
	}
}
