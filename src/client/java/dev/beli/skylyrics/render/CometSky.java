package dev.beli.skylyrics.render;

import dev.beli.skylyrics.render.SkyCanvas.SkyFrame;

/**
 * The comet: a bright head low on the right of the lyrics with two tails sweeping up and away from it.
 * A broad warm dust tail that bends as it goes, and a narrow blue gas tail that runs straight, with
 * streamers drifting slowly out along it. Like the galaxy it is all light added to the night sky, and
 * everything but the streamers is worked out once in the comet's own flat picture.
 */
final class CometSky {
	private static final float DEG = (float) (Math.PI / 180);

	/** Where the head sits: right of where the lyrics face and low, so the tails climb up beside them. */
	static final float YAW = 30, ELEV = 18;
	/** Radius of the glow round the head and length of the tails, in degrees. */
	static final float HEAD = 5, LENGTH = 36;
	/** The way the dust tail leaves the head (0 right, 90 straight up) and how far it bends round as it goes. */
	static final float TILT = 52, BEND = 0.2f;
	/** The gas tail points this much higher than the dust tail and doesn't bend. */
	static final float ION_TILT = 7;
	/** How far it sits, as a share of the sky radius: behind the aurora and nebula. */
	static final float LAYER = 0.96f;
	/** How fast the streamers run out along the gas tail, in tail lengths per second. */
	static final float DRIFT = 0.03f;
	/** Light below the horizon fades out over this many degrees, like the galaxy. */
	private static final float HORIZON_FADE = 3;

	private static final int CORE_RGB = 0xF4FBFF, COMA_RGB = 0x6FE8C0, DUST_RGB = 0xFFDDB0, DUST_TIP_RGB = 0xFFB48C,
			ION_RGB = 0x6AA4FF;
	private static final float CORE_PEAK = 1, COMA_PEAK = 0.4f, DUST_PEAK = 0.42f, ION_PEAK = 0.36f;
	/** Half the width of each tail at the head and how much it adds by the tip, as shares of the length. */
	private static final float DUST_WIDTH = 0.02f, DUST_SPREAD = 0.17f, ION_WIDTH = 0.008f, ION_SPREAD = 0.04f;

	private static final int RINGS = 10, SEGMENTS = 36, STEPS = 40, DUST_ACROSS = 12, ION_ACROSS = 16;
	/** Steps along the tails, closer together near the head where they change fastest. */
	private static final float[] STEP_AT = new float[STEPS + 1];
	private static final float[] HEAD_A = new float[(RINGS + 1) * (SEGMENTS + 1)], HEAD_B = new float[HEAD_A.length];
	private static final float[] DUST_A = new float[(STEPS + 1) * (DUST_ACROSS + 1)], DUST_B = new float[DUST_A.length];
	private static final float[] ION_A = new float[(STEPS + 1) * (ION_ACROSS + 1)], ION_B = new float[ION_A.length];
	private static final int[] HEAD_GRID = new int[HEAD_A.length], DUST_GRID = new int[DUST_A.length];

	static {
		for (int i = 0; i <= STEPS; i++) {
			float t = (float) i / STEPS;
			STEP_AT[i] = t * t * 0.5f + t * 0.5f;
		}
		float head = (float) Math.tan(HEAD * DEG);
		for (int i = 0; i <= RINGS; i++) {
			float r = (float) i / RINGS;
			int light = head(r);
			for (int j = 0; j <= SEGMENTS; j++) {
				double a = 2 * Math.PI * j / SEGMENTS;
				int k = i * (SEGMENTS + 1) + j;
				HEAD_A[k] = r * head * (float) Math.cos(a);
				HEAD_B[k] = r * head * (float) Math.sin(a);
				HEAD_GRID[k] = light;
			}
		}
		lay(TILT, BEND, DUST_WIDTH, DUST_SPREAD, DUST_ACROSS, DUST_A, DUST_B);
		lay(TILT + ION_TILT, 0, ION_WIDTH, ION_SPREAD, ION_ACROSS, ION_A, ION_B);
		for (int i = 0; i <= STEPS; i++) {
			float s = STEP_AT[i];
			int rgb = SkyColors.mix(DUST_RGB, DUST_TIP_RGB, s);
			for (int j = 0; j <= DUST_ACROSS; j++) {
				DUST_GRID[i * (DUST_ACROSS + 1) + j] = SkyColors.scale(rgb, DUST_PEAK * tail(s, across(j, DUST_ACROSS)));
			}
		}
	}

	private final SkyFrame frame = new SkyFrame();
	private final int[] ionGrid = new int[ION_A.length];

	/** Light round the head at {@code r} (0 middle, 1 edge of the glow), ending at nothing on the edge. */
	static int head(float r) {
		float core = CORE_PEAK * (float) Math.exp(-(r / 0.12f) * (r / 0.12f));
		float coma = COMA_PEAK * (float) Math.exp(-r / 0.3f) * SkyColors.smooth((1 - r) / 0.4f);
		return SkyColors.add(SkyColors.scale(CORE_RGB, core), SkyColors.scale(COMA_RGB, coma));
	}

	/**
	 * 0..1: how strong a tail is {@code s} of the way along it (0 head, 1 tip) and {@code w} across it
	 * (0 middle, ±1 edges). It grows out of the head, thins towards the tip and has no hard edge anywhere.
	 */
	static float tail(float s, float w) {
		float across = 1 - w * w;
		float along = SkyColors.smooth(s / 0.06f) * (1 - s) * (1 - 0.5f * s);
		return Math.max(0, along) * across * across;
	}

	/** 0..1.55: the gas tail's streamers, long along the tail and thin across it, running outward with time. */
	static float streak(float s, float w, float time) {
		return 0.45f + 1.1f * SkyColors.noise((s - time * DRIFT) * 2.5f, (w + 1) * 3, 31);
	}

	private static float across(int j, int columns) {
		return 2f * j / columns - 1;
	}

	/** Lays a tail's grid out in the comet's flat picture, from the head along {@code tilt}, bending by {@code bend}. */
	private static void lay(float tilt, float bend, float width, float spread, int columns, float[] a, float[] b) {
		float length = (float) Math.tan(LENGTH * DEG);
		float cos = (float) Math.cos(tilt * DEG), sin = (float) Math.sin(tilt * DEG);
		for (int i = 0; i <= STEPS; i++) {
			float s = STEP_AT[i];
			float cx = length * (s * cos + bend * s * s * sin), cy = length * (s * sin - bend * s * s * cos);
			float dx = cos + 2 * bend * s * sin, dy = sin - 2 * bend * s * cos;
			float half = length * (width + spread * s) / (float) Math.sqrt(dx * dx + dy * dy);
			for (int j = 0; j <= columns; j++) {
				float w = across(j, columns);
				int k = i * (columns + 1) + j;
				a[k] = cx - dy * half * w;
				b[k] = cy + dx * half * w;
			}
		}
	}

	/**
	 * @param baseYaw Minecraft yaw the lyrics face
	 * @param level 0..1, how much of the effect shows
	 * @param time seconds of sky clock, which moves the streamers
	 */
	void draw(SkyCanvas c, float radius, float baseYaw, float level, float time) {
		frame.aim(baseYaw + YAW, ELEV);
		for (int i = 0; i <= STEPS; i++) {
			float s = STEP_AT[i];
			for (int j = 0; j <= ION_ACROSS; j++) {
				float w = across(j, ION_ACROSS);
				ionGrid[i * (ION_ACROSS + 1) + j] = SkyColors.scale(ION_RGB, ION_PEAK * tail(s, w) * streak(s, w, time));
			}
		}
		float r = radius * LAYER;
		c.fadeBelowHorizon(HORIZON_FADE);
		mesh(c, r, DUST_A, DUST_B, DUST_GRID, STEPS, DUST_ACROSS, level);
		mesh(c, r, ION_A, ION_B, ionGrid, STEPS, ION_ACROSS, level);
		mesh(c, r, HEAD_A, HEAD_B, HEAD_GRID, RINGS, SEGMENTS, level);
		c.fadeBelowHorizon(0);
	}

	/** A grid of quads, {@code rows} by {@code columns}, from points and colours laid out row by row. */
	private void mesh(SkyCanvas c, float r, float[] a, float[] b, int[] rgb, int rows, int columns, float level) {
		for (int i = 0; i < rows; i++) {
			int row0 = i * (columns + 1), row1 = row0 + columns + 1;
			for (int j = 0; j < columns; j++) {
				corner(c, r, a, b, rgb, row0 + j, level);
				corner(c, r, a, b, rgb, row0 + j + 1, level);
				corner(c, r, a, b, rgb, row1 + j + 1, level);
				corner(c, r, a, b, rgb, row1 + j, level);
			}
		}
	}

	private void corner(SkyCanvas c, float r, float[] a, float[] b, int[] rgb, int k, float level) {
		c.at(frame, a[k], b[k], r, SkyColors.argb(rgb[k], level));
	}
}
