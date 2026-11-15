package dev.beli.skylyrics.render;

import dev.beli.skylyrics.render.SkyCanvas.SkyFrame;

/**
 * The galaxy: a spiral galaxy seen at a slant, up and to the left of the lyrics, turning very slowly.
 * A warm cream core, two blue arms winding out from it with pink knots of young stars along them,
 * and a faint haze filling the disc. It is all light added to the night sky, so it needs only the
 * additive pipeline the aurora uses. The colours are worked out once in the galaxy's own plane;
 * turning it only moves the corners.
 */
final class GalaxySky {
	private static final float DEG = (float) (Math.PI / 180);

	/** Where the middle sits: left of where the lyrics face and well above them. */
	static final float YAW = -38, ELEV = 46;
	/** Angular radius of the disc, how far it leans away (90 would be edge on) and the way its long axis runs. */
	static final float RADIUS = 17, LEAN = 58, ROLL = -24;
	private static final float LEAN_COS = (float) Math.cos(LEAN * DEG);
	private static final float ROLL_COS = (float) Math.cos(ROLL * DEG), ROLL_SIN = (float) Math.sin(ROLL * DEG);
	/** How far it sits, as a share of the sky radius: behind the aurora and nebula. */
	static final float LAYER = 0.97f;
	/** One turn in about four minutes, slow enough to read as stillness that is somehow alive. */
	static final float SPIN = 0.026f;
	/** Light below the horizon fades out over this many degrees, like the eclipse. */
	private static final float HORIZON_FADE = 3;

	private static final int CORE_RGB = 0xF2DDB0, DISC_RGB = 0x6E78B4, ARM_RGB = 0x6E96FF, KNOT_RGB = 0xFF78C0;
	/** How fast each part falls off with distance from the middle, as a share of the radius. */
	private static final float CORE_SCALE = 0.07f, DISC_SCALE = 0.32f;
	private static final float CORE_PEAK = 0.95f, DISC_PEAK = 0.12f, ARM_PEAK = 0.34f, KNOT_PEAK = 0.3f;
	/** How tightly the arms wind: radians of turn per factor e out. */
	private static final float WIND = 2.2f;
	/** Arms start this far out and are gone at the rim. */
	private static final float ARM_START = 0.08f;

	/** Rings packed tighter near the middle, where the core changes fastest. */
	static final int RINGS = 22, SEGMENTS = 120;
	private static final float[] RING_AT = new float[RINGS + 1];
	private static final float[] COS = new float[SEGMENTS + 1], SIN = new float[SEGMENTS + 1];
	private static final int[] GRID = new int[(RINGS + 1) * (SEGMENTS + 1)];

	static {
		for (int i = 0; i <= RINGS; i++) {
			float t = (float) i / RINGS;
			RING_AT[i] = t * t * 0.6f + t * 0.4f;
		}
		for (int j = 0; j <= SEGMENTS; j++) {
			double a = 2 * Math.PI * j / SEGMENTS;
			COS[j] = (float) Math.cos(a);
			SIN[j] = (float) Math.sin(a);
		}
		for (int i = 0; i <= RINGS; i++) {
			for (int j = 0; j <= SEGMENTS; j++) {
				GRID[i * (SEGMENTS + 1) + j] = light(RING_AT[i], 360f * j / SEGMENTS);
			}
		}
	}

	private final SkyFrame frame = new SkyFrame();
	/** Where each grid point lands on the sky and its colour this frame, reused so a frame allocates nothing. */
	private final float[] pointX = new float[(RINGS + 1) * (SEGMENTS + 1)], pointY = new float[pointX.length],
			pointZ = new float[pointX.length];
	private final int[] pointArgb = new int[pointX.length];
	private final float[] turnedCos = new float[SEGMENTS + 1], turnedSin = new float[SEGMENTS + 1];

	/**
	 * Light in the galaxy's own plane at {@code r} (0 middle, 1 rim) and {@code angle} degrees round it.
	 * Every ring ends at nothing on the rim so the disc has no hard edge.
	 */
	static int light(float r, float angle) {
		float core = CORE_PEAK * (float) Math.exp(-(r / CORE_SCALE) * (r / CORE_SCALE));
		float rim = SkyColors.smooth((1 - r) / 0.35f);
		float disc = DISC_PEAK * (float) Math.exp(-r / DISC_SCALE) * rim;
		int out = SkyColors.add(SkyColors.scale(CORE_RGB, core), SkyColors.scale(DISC_RGB, disc));
		if (r <= ARM_START) {
			return out;
		}
		float arm = arm(r, angle) * rim * SkyColors.smooth((r - ARM_START) / 0.12f);
		// Patchy along the arms, so they look made of clouds of stars rather than drawn with a pen
		float clump = 0.55f + 0.9f * roundNoise(r, angle, 11);
		float knots = SkyColors.smooth((roundNoise(r, angle, 23) - 0.62f) / 0.2f);
		out = SkyColors.add(out, SkyColors.scale(ARM_RGB, ARM_PEAK * arm * clump));
		return SkyColors.add(out, SkyColors.scale(KNOT_RGB, KNOT_PEAK * arm * arm * knots));
	}

	/** 0..1: how much of an arm is at that spot, two arms winding out half a turn apart. */
	static float arm(float r, float angle) {
		float phase = angle * DEG - WIND * (float) Math.log(r / ARM_START);
		float wave = 0.5f + 0.5f * (float) Math.cos(2 * phase);
		// Sharper with distance: the arms are broad near the core and thin out to the rim
		return (float) Math.pow(wave, 2 + 4 * r);
	}

	/** Noise round the disc that joins up after a full turn. */
	private static float roundNoise(float r, float angle, int seed) {
		float w = angle / 360;
		float a = SkyColors.noise(angle / 30, r * 9, seed);
		float b = SkyColors.noise((angle - 360) / 30, r * 9, seed);
		return a * (1 - w) + b * w;
	}

	/**
	 * @param baseYaw Minecraft yaw the lyrics face
	 * @param level 0..1, how much of the effect shows
	 * @param time seconds of sky clock, which sets how far it has turned
	 */
	void draw(SkyCanvas c, float radius, float baseYaw, float level, float time) {
		draw(c, radius, baseYaw, level, time, SkyView.ALL);
	}

	/** As above, drawing nothing when the camera {@code view} can't see the galaxy. */
	void draw(SkyCanvas c, float radius, float baseYaw, float level, float time, SkyView view) {
		frame.aim(baseYaw + YAW, ELEV);
		// The disc is no wider than its radius in any direction (leaning only squashes it)
		if (!view.capVisible(frame.dx, frame.dy, frame.dz, RADIUS * DEG * 1.05f)) {
			return;
		}
		float spin = (time * SPIN) % (2 * (float) Math.PI);
		float cs = (float) Math.cos(spin), sn = (float) Math.sin(spin);
		for (int j = 0; j <= SEGMENTS; j++) {
			turnedCos[j] = COS[j] * cs - SIN[j] * sn;
			turnedSin[j] = SIN[j] * cs + COS[j] * sn;
		}
		float r = radius * LAYER;
		float size = (float) Math.tan(RADIUS * DEG);
		c.fadeBelowHorizon(HORIZON_FADE);
		// Each grid point is a corner of up to four quads: work out its place and colour once, then emit the quads from them
		for (int i = 0; i <= RINGS; i++) {
			float out = RING_AT[i] * size;
			for (int j = 0; j <= SEGMENTS; j++) {
				float[] s = slant(out * turnedCos[j], out * turnedSin[j]);
				// As SkyCanvas.at, kept as the same operations so every vertex is exactly what it was
				float x = frame.dx + s[0] * frame.ux + s[1] * frame.vx;
				float y = frame.dy + s[0] * frame.uy + s[1] * frame.vy;
				float z = frame.dz + s[0] * frame.uz + s[1] * frame.vz;
				float k = r / (float) Math.sqrt(x * x + y * y + z * z);
				int n = i * (SEGMENTS + 1) + j;
				pointX[n] = x * k;
				pointY[n] = y * k;
				pointZ[n] = z * k;
				pointArgb[n] = SkyColors.argb(GRID[n], level);
			}
		}
		for (int i = 0; i < RINGS; i++) {
			int row0 = i * (SEGMENTS + 1), row1 = row0 + SEGMENTS + 1;
			for (int j = 0; j < SEGMENTS; j++) {
				c.vertex(pointX[row0 + j], pointY[row0 + j], pointZ[row0 + j], pointArgb[row0 + j]);
				c.vertex(pointX[row0 + j + 1], pointY[row0 + j + 1], pointZ[row0 + j + 1], pointArgb[row0 + j + 1]);
				c.vertex(pointX[row1 + j + 1], pointY[row1 + j + 1], pointZ[row1 + j + 1], pointArgb[row1 + j + 1]);
				c.vertex(pointX[row1 + j], pointY[row1 + j], pointZ[row1 + j], pointArgb[row1 + j]);
			}
		}
		c.fadeBelowHorizon(0);
	}

	private final float[] slanted = new float[2];

	/** A point in the galaxy's plane as seen leaning away by {@link #LEAN} and rolled by {@link #ROLL}. */
	float[] slant(float x, float y) {
		float squashed = y * LEAN_COS;
		slanted[0] = x * ROLL_COS - squashed * ROLL_SIN;
		slanted[1] = x * ROLL_SIN + squashed * ROLL_COS;
		return slanted;
	}
}
