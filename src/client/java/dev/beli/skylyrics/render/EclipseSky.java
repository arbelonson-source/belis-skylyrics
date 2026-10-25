package dev.beli.skylyrics.render;

import dev.beli.skylyrics.render.SkyCanvas.SkyFrame;

/**
 * The eclipse: a black sun with a thin cream rim, an orange corona fading to red, a long lopsided
 * flare streak and soft rays, under a faint lavender milky way broken up by dark dust.
 * Everything is light added to the night sky (the disc itself is simply left dark), so it needs
 * only the additive pipeline the aurora uses. The colours are worked out once.
 */
final class EclipseSky {
	private static final float DEG = (float) (Math.PI / 180);

	/** How far each layer sits, as a share of the sky radius; farther ones are drawn first. */
	static final float BAND = 1f, CORONA = 0.95f;

	/** Sun position: just right of where the lyrics face and below them. */
	static final float SUN_YAW = 3, SUN_ELEV = 13;
	/** Angular radius of the black disc. */
	static final float SUN_RADIUS = 2.5f;

	// Corona by distance from the middle, in disc radii (measured, minus the night sky):
	// the cream rim just outside the disc, then orange shifting to red as it fades out by 5 radii
	private static final float[] CORONA_AT = {1, 1.05f, 1.1f, 1.3f, 1.6f, 2, 2.5f, 3, 4, 5};
	private static final int[] CORONA_RGB = {0xD8B888, 0xE8CBA0, 0xF0B46E, 0xE9A654, 0xC06634, 0x7A3A1E, 0x46200E, 0x26120A, 0x0A0402, 0};

	/** The flare leans 5 degrees, its left end higher. */
	private static final float STREAK_TILT = -5;
	/** Left arm (measured): a hot core 2-2.5 radii out, gone by 6.2 radii. */
	private static final float[] LEFT_AT = {1, 2.25f, 3, 4, 5, 6.2f};
	private static final int[] LEFT_RGB = {0xF8C27E, 0xF6C886, 0xEEB25C, 0xD86B37, 0x6B250F, 0};
	/** Right arm: dimmer and shorter. */
	private static final float[] RIGHT_AT = {1, 2, 3, 4, 4.8f};
	private static final int[] RIGHT_RGB = {0xF0AA6E, 0xE68C55, 0xB05A30, 0x5A2814, 0};
	/** Half thickness of the streak near the disc, in disc radii; it tapers to soft ends. */
	private static final float STREAK_HALF = 1.0f, STREAK_TIP = 0.15f;

	/** Soft rays: direction (degrees, 0 = right, 90 = up), strength, reach in disc radii. Longest point down. */
	private static final float[][] RAYS = {
		{-90, 0.42f, 4.3f}, {-118, 0.36f, 4.1f}, {-140, 0.3f, 3.6f}, {-68, 0.28f, 3.4f}, {-40, 0.18f, 3f},
		{95, 0.3f, 3.7f}, {128, 0.2f, 3.1f}, {60, 0.2f, 3f}, {-165, 0.15f, 2.8f}, {20, 0.12f, 2.6f}
	};
	private static final int RAY_RGB = 0xF08C46;
	private static final float RAY_WIDTH = 13;

	private static final float[] RINGS = {1, 1.04f, 1.08f, 1.14f, 1.25f, 1.4f, 1.6f, 1.8f, 2.05f, 2.35f, 2.7f, 3.1f,
		3.6f, 4.2f, 4.9f, 5.6f, 6.4f, 7.2f};
	private static final int SEGMENTS = 144;

	/** Milky way: a great circle crossing above the sun, sloping gently down to the right. */
	private static final float BAND_YAW = 3, BAND_ELEV = 33, BAND_SLOPE = -8;
	private static final int BAND_RGB = 0xC8BEE6;
	/** Peak strength (about +30 on a dark sky) and width (sigma, degrees; full width at half height about 14). */
	private static final float BAND_PEAK = 0.16f, BAND_SIGMA = 6;
	private static final int BAND_SEGMENTS = 144, BAND_ROWS = 12;
	private static final float BAND_HALF = 16;

	private static final int[] CORONA_GRID = new int[RINGS.length * (SEGMENTS + 1)];
	private static final float[] RING_COS = new float[SEGMENTS + 1], RING_SIN = new float[SEGMENTS + 1];
	private static final float[] BAND_GRID = new float[(BAND_ROWS + 1) * (BAND_SEGMENTS + 1)];
	private static final float[] ALONG_COS = new float[BAND_SEGMENTS + 1], ALONG_SIN = new float[BAND_SEGMENTS + 1];
	private static final float[] ACROSS_COS = new float[BAND_ROWS + 1], ACROSS_SIN = new float[BAND_ROWS + 1];

	static {
		for (int j = 0; j <= SEGMENTS; j++) {
			double a = 2 * Math.PI * j / SEGMENTS;
			RING_COS[j] = (float) Math.cos(a);
			RING_SIN[j] = (float) Math.sin(a);
		}
		for (int i = 0; i < RINGS.length; i++) {
			for (int j = 0; j <= SEGMENTS; j++) {
				CORONA_GRID[i * (SEGMENTS + 1) + j] = light(RINGS[i] * RING_COS[j], RINGS[i] * RING_SIN[j]);
			}
		}
		for (int j = 0; j <= BAND_SEGMENTS; j++) {
			double a = 2 * Math.PI * j / BAND_SEGMENTS;
			ALONG_COS[j] = (float) Math.cos(a);
			ALONG_SIN[j] = (float) Math.sin(a);
		}
		for (int i = 0; i <= BAND_ROWS; i++) {
			float across = -BAND_HALF + 2 * BAND_HALF * i / BAND_ROWS;
			ACROSS_COS[i] = (float) Math.cos(across * DEG);
			ACROSS_SIN[i] = (float) Math.sin(across * DEG);
			for (int j = 0; j <= BAND_SEGMENTS; j++) {
				float along = 360f * j / BAND_SEGMENTS;
				BAND_GRID[i * (BAND_SEGMENTS + 1) + j] = band(along, across);
			}
		}
	}

	private final SkyFrame frame = new SkyFrame();
	private final float[] axisA = new float[3], axisB = new float[3], pole = new float[3];

	/** Light around the sun at {@code (x, y)} disc radii from its middle, x to the right and y up. */
	static int light(float x, float y) {
		float r = (float) Math.sqrt(x * x + y * y);
		if (r < 1) {
			return 0;
		}
		int corona = SkyColors.ramp(CORONA_AT, CORONA_RGB, r);
		// Along and across the tilted streak
		float tilt = STREAK_TILT * DEG;
		float s = x * (float) Math.cos(tilt) + y * (float) Math.sin(tilt);
		float q = -x * (float) Math.sin(tilt) + y * (float) Math.cos(tilt);
		float[] at = s < 0 ? LEFT_AT : RIGHT_AT;
		int[] rgb = s < 0 ? LEFT_RGB : RIGHT_RGB;
		float len = at[at.length - 1], d = Math.abs(s);
		float taper = (float) Math.pow(Math.max(0, 1 - (d / len) * (d / len)), 0.3);
		float half = STREAK_HALF * taper + STREAK_TIP;
		float across = (float) Math.exp(-(q / half) * (q / half));
		// The measured streak already includes the corona under it, so only add what it lacks
		int streak = SkyColors.sub(SkyColors.ramp(at, rgb, d), SkyColors.ramp(CORONA_AT, CORONA_RGB, d));
		int out = SkyColors.add(corona, SkyColors.scale(streak, across));
		float angle = (float) Math.toDegrees(Math.atan2(y, x));
		float rays = 0;
		for (float[] ray : RAYS) {
			float diff = Math.abs(angle - ray[0]) % 360;
			diff = Math.min(diff, 360 - diff);
			float reach = SkyColors.clamp01(1 - (r - 1) / (ray[2] - 1));
			rays += ray[1] * (float) Math.exp(-(diff / RAY_WIDTH) * (diff / RAY_WIDTH)) * reach * reach;
		}
		return SkyColors.add(out, SkyColors.scale(RAY_RGB, rays));
	}

	/** Milky way strength at {@code along} degrees round the band and {@code across} degrees off its middle. */
	static float band(float along, float across) {
		float body = (float) Math.exp(-0.5f * (across / BAND_SIGMA) * (across / BAND_SIGMA));
		// Wispy brightness along the band, and dark dust lanes eating into its middle
		float wisp = 0.7f + 0.6f * roundNoise(along, across, 9, 6, 17);
		float dust = SkyColors.smooth((roundNoise(along, across, 7, 4, 29) - 0.45f) / 0.25f);
		float lane = (float) Math.exp(-0.5f * (across / (BAND_SIGMA * 0.7f)) * (across / (BAND_SIGMA * 0.7f)));
		return BAND_PEAK * body * wisp * (1 - 0.85f * dust * lane);
	}

	/** Noise that joins up after a full turn round the band (cross-faded with itself one turn back). */
	private static float roundNoise(float along, float across, float sizeAlong, float sizeAcross, int seed) {
		float w = along / 360;
		float a = SkyColors.noise(along / sizeAlong, across / sizeAcross, seed);
		float b = SkyColors.noise((along - 360) / sizeAlong, across / sizeAcross, seed);
		return a * (1 - w) + b * w;
	}

	/**
	 * @param baseYaw Minecraft yaw the lyrics face; the sun sits just below them
	 * @param level 0..1, how much of the effect shows
	 */
	void draw(SkyCanvas c, float radius, float baseYaw, float level, float time) {
		band(c, radius * BAND, baseYaw, level);
		corona(c, radius * CORONA, baseYaw, level, time);
	}

	private void corona(SkyCanvas c, float r, float baseYaw, float level, float time) {
		frame.aim(baseYaw + SUN_YAW, SUN_ELEV);
		float tanR = (float) Math.tan(SUN_RADIUS * DEG);
		// A slow, slight breathing so the corona feels alive
		float alpha = level * (0.94f + 0.06f * (float) Math.sin(time * 0.8f));
		for (int i = 0; i + 1 < RINGS.length; i++) {
			float a0 = RINGS[i] * tanR, a1 = RINGS[i + 1] * tanR;
			int row0 = i * (SEGMENTS + 1), row1 = row0 + SEGMENTS + 1;
			for (int j = 0; j < SEGMENTS; j++) {
				c.at(frame, a0 * RING_COS[j], a0 * RING_SIN[j], r, SkyColors.argb(CORONA_GRID[row0 + j], alpha));
				c.at(frame, a0 * RING_COS[j + 1], a0 * RING_SIN[j + 1], r, SkyColors.argb(CORONA_GRID[row0 + j + 1], alpha));
				c.at(frame, a1 * RING_COS[j + 1], a1 * RING_SIN[j + 1], r, SkyColors.argb(CORONA_GRID[row1 + j + 1], alpha));
				c.at(frame, a1 * RING_COS[j], a1 * RING_SIN[j], r, SkyColors.argb(CORONA_GRID[row1 + j], alpha));
			}
		}
	}

	private void band(SkyCanvas c, float r, float baseYaw, float level) {
		// A = a point on the band, B = the way the band runs there, pole = A x B
		frame.aim(baseYaw + BAND_YAW, BAND_ELEV);
		float cs = (float) Math.cos(BAND_SLOPE * DEG), sn = (float) Math.sin(BAND_SLOPE * DEG);
		axisA[0] = frame.dx;
		axisA[1] = frame.dy;
		axisA[2] = frame.dz;
		axisB[0] = cs * frame.ux + sn * frame.vx;
		axisB[1] = cs * frame.uy + sn * frame.vy;
		axisB[2] = cs * frame.uz + sn * frame.vz;
		cross(axisA, axisB, pole);
		for (int i = 0; i < BAND_ROWS; i++) {
			for (int j = 0; j < BAND_SEGMENTS; j++) {
				int g0 = i * (BAND_SEGMENTS + 1) + j, g1 = g0 + BAND_SEGMENTS + 1;
				bandPoint(c, j, i, r, SkyColors.argb(BAND_RGB, BAND_GRID[g0] * level));
				bandPoint(c, j + 1, i, r, SkyColors.argb(BAND_RGB, BAND_GRID[g0 + 1] * level));
				bandPoint(c, j + 1, i + 1, r, SkyColors.argb(BAND_RGB, BAND_GRID[g1 + 1] * level));
				bandPoint(c, j, i + 1, r, SkyColors.argb(BAND_RGB, BAND_GRID[g1] * level));
			}
		}
	}

	/** Corner {@code j} along the band and row {@code i} across it. */
	private void bandPoint(SkyCanvas c, int j, int i, float r, int argb) {
		float ca = ALONG_COS[j], sa = ALONG_SIN[j];
		float cp = ACROSS_COS[i] * r, sp = ACROSS_SIN[i] * r;
		c.vertex((ca * axisA[0] + sa * axisB[0]) * cp + pole[0] * sp,
				(ca * axisA[1] + sa * axisB[1]) * cp + pole[1] * sp,
				(ca * axisA[2] + sa * axisB[2]) * cp + pole[2] * sp, argb);
	}

	private static void cross(float[] a, float[] b, float[] out) {
		float x = a[1] * b[2] - a[2] * b[1], y = a[2] * b[0] - a[0] * b[2], z = a[0] * b[1] - a[1] * b[0];
		out[0] = x;
		out[1] = y;
		out[2] = z;
	}
}
