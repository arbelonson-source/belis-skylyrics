package dev.beli.skylyrics.render;

import dev.beli.skylyrics.render.SkyCanvas.SkyFrame;

/**
 * A moonlit night: deep blue overhead, paler towards the horizon, a big full moon with a soft glow,
 * low clouds drifting slowly round the horizon (silvered where they pass the moon) and sparse stars.
 * <p>
 * Like the synthwave sky it covers the vanilla night, so every mesh paints one finished opaque colour
 * per direction; only the cloud band is worked out again as the clouds drift.
 */
final class MoonlitSky {
	private static final float DEG = (float) (Math.PI / 180);

	/** How far each layer sits, as a share of the sky radius. Nearer layers draw over farther ones. */
	static final float DOME = 1f, GLOW_SKY = 0.975f, CLOUDS = 0.95f, STARS = 0.935f, DISC = 0.92f;

	/** The moon hangs left of where the lyrics face, high enough to clear the clouds. */
	static final float MOON_YAW = -32, MOON_ELEV = 25, MOON_RADIUS = 4.5f;
	private static final float TAN_MOON = (float) Math.tan(MOON_RADIUS * DEG);
	private static final SkyFrame MOON_FRAME = new SkyFrame().aim(MOON_YAW, MOON_ELEV);

	// Sky by height: a dusky blue at the horizon to near-black navy overhead
	private static final float[] SKY_AT = {0, 8, 20, 40, 65, 90};
	private static final int[] SKY_RGB = {0x2B4468, 0x1D3153, 0x142341, 0x0E1931, 0x0A1226, 0x070C1D};
	/** Below the horizon everything fades out over this many degrees, like the synthwave sky. */
	static final float HORIZON_FADE = 3;
	private static final float[] DOME_ROWS = {-HORIZON_FADE, 0, 4, 8, 14, 20, 30, 40, 52, 65, 78, 90};
	private static final int DOME_COLUMNS = 36;

	// The moon: a cool cream face with grey seas, a little darker towards its rim
	private static final int MOON_RGB = 0xF3F1E6, SEA_RGB = 0xA9AFBA;
	private static final float SEA_DEPTH = 0.55f, LIMB = 0.18f;
	private static final int DISC_RINGS = 14, DISC_SEGMENTS = 56;

	// Its glow: strong just past the rim, then a wide faint haze out to GLOW_END moon radii
	private static final int GLOW_RGB = 0xAFC3E6;
	private static final float GLOW_NEAR = 0.55f, GLOW_NEAR_REACH = 0.35f, GLOW_FAR = 0.3f, GLOW_FAR_REACH = 1.4f;
	static final float GLOW_END = 4;
	private static final int GLOW_RINGS = 28, GLOW_SEGMENTS = 64;

	// Clouds: a broken band low round the horizon, thickest between CLOUD_LOW and CLOUD_HIGH degrees up
	static final float CLOUD_LOW = 0.5f, CLOUD_HIGH = 9, CLOUD_TOP = 15;
	private static final int CLOUD_FAR_RGB = 0x1B2339, CLOUD_LIT_RGB = 0x9DAACA;
	/** Degrees round the sky per second at normal sky speed: a full turn takes a quarter of an hour. */
	static final float CLOUD_DRIFT = 0.4f;
	/** Clouds this many degrees from the moon catch about a third of its light. */
	private static final float CLOUD_LIT_REACH = 30;
	private static final float[] CLOUD_ROWS = {-HORIZON_FADE, 0, 1.5f, 3, 4.5f, 6, 7.5f, 9, 10.5f, 12, 13.5f, CLOUD_TOP};
	private static final int CLOUD_COLUMNS = 120;

	private static final int STAR_COUNT = 240;
	private static final int STAR_RGB = 0xE6EDFF;
	/** No stars this close to the moon's middle, in moon radii: its light washes them out. */
	private static final float STAR_CLEAR = 1.6f;

	private static final SkyMesh DOME_MESH, GLOW_MESH, DISC_MESH;
	private static final float[] STAR_YAW = new float[STAR_COUNT], STAR_ELEV = new float[STAR_COUNT];
	private static final float[] STAR_SIZE = new float[STAR_COUNT], STAR_ALPHA = new float[STAR_COUNT];
	private static final int[] STAR_SKY = new int[STAR_COUNT];

	static {
		// The plain gradient: coarse triangles would smear the glow past the edge of the glow's own rings
		DOME_MESH = SkyMesh.dome(DOME_ROWS, DOME_COLUMNS, (x, y, z) -> skyColor((float) Math.toDegrees(Math.asin(y))));

		// Just under the moon's rim, then closer together near the rim where the glow falls fastest
		float[] glow = new float[GLOW_RINGS + 2];
		glow[0] = 0.96f * TAN_MOON;
		for (int k = 0; k <= GLOW_RINGS; k++) {
			float t = (float) k / GLOW_RINGS;
			glow[k + 1] = (1 + (GLOW_END - 1) * t * t) * TAN_MOON;
		}
		GLOW_MESH = SkyMesh.polar(MOON_FRAME, glow, GLOW_SEGMENTS, MoonlitSky::background);

		// Closer together towards the rim, where the face darkens
		float[] disc = new float[DISC_RINGS + 1];
		for (int k = 0; k <= DISC_RINGS; k++) {
			disc[k] = (float) Math.sin(Math.PI / 2 * k / DISC_RINGS) * TAN_MOON;
		}
		DISC_MESH = SkyMesh.polar(MOON_FRAME, disc, DISC_SEGMENTS, MoonlitSky::face);

		SkyFrame f = new SkyFrame();
		float lo = (float) Math.sin(CLOUD_TOP * DEG), hi = (float) Math.sin(82 * DEG);
		for (int i = 0; i < STAR_COUNT; i++) {
			STAR_YAW[i] = SkyColors.hash(i * 3 + 401) * 360;
			// Even spread over the dome above the clouds
			STAR_ELEV[i] = (float) Math.toDegrees(Math.asin(lo + (hi - lo) * SkyColors.hash(i * 3 + 402)));
			float k = SkyColors.hash(i * 3 + 403);
			STAR_SIZE[i] = 0.8f + 0.7f * k;
			f.aim(STAR_YAW[i], STAR_ELEV[i]);
			float r = moonDistance(f.dx, f.dy, f.dz);
			STAR_ALPHA[i] = r < STAR_CLEAR ? 0 : (0.15f + 0.4f * k * k) * (1 - glow(r));
			STAR_SKY[i] = background(f.dx, f.dy, f.dz);
		}
	}

	private final SkyFrame frame = new SkyFrame();
	private final SkyMesh clouds = SkyMesh.dome(CLOUD_ROWS, CLOUD_COLUMNS, MoonlitSky::background);
	private float drift;
	private final SkyMesh.Paint drifted = (x, y, z) -> background(x, y, z, drift);

	/** The finished sky colour in a direction (unit vector, lyrics facing south), clouds not drifted. */
	static int background(float x, float y, float z) {
		return background(x, y, z, 0);
	}

	/** The gradient, lit by the moon's glow, under clouds moved {@code drift} degrees round. */
	static int background(float x, float y, float z, float drift) {
		float elev = (float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, y))));
		int rgb = SkyColors.mix(skyColor(elev), GLOW_RGB, glow(moonDistance(x, y, z)));
		float d = cloud((float) Math.toDegrees(Math.atan2(-x, z)), elev, drift);
		if (d <= 0) {
			return rgb;
		}
		float toMoon = x * MOON_FRAME.dx + y * MOON_FRAME.dy + z * MOON_FRAME.dz;
		float lit = (float) Math.exp(-Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, toMoon)))) / CLOUD_LIT_REACH);
		// Thin cloud edges near the moon shine brighter than the thick middles
		float edge = 4 * d * (1 - d);
		int cloud = SkyColors.mix(CLOUD_FAR_RGB, CLOUD_LIT_RGB, lit * (0.7f + 0.6f * edge));
		return SkyColors.mix(rgb, cloud, 0.92f * d);
	}

	/** The moon's face in a direction (anywhere on or just past the disc). */
	private static int face(float x, float y, float z) {
		float cos = x * MOON_FRAME.dx + y * MOON_FRAME.dy + z * MOON_FRAME.dz;
		float a = (x * MOON_FRAME.ux + y * MOON_FRAME.uy + z * MOON_FRAME.uz) / cos / TAN_MOON;
		float b = (x * MOON_FRAME.vx + y * MOON_FRAME.vy + z * MOON_FRAME.vz) / cos / TAN_MOON;
		return moonColor(a, b);
	}

	/** Colour of the moon's face at {@code a} right and {@code b} up from its middle, in moon radii. */
	static int moonColor(float a, float b) {
		float r2 = Math.min(1, a * a + b * b);
		float n = 0.6f * SkyColors.noise(a * 1.8f + 4.3f, b * 1.8f + 2.1f, 31)
				+ 0.4f * SkyColors.noise(a * 4.2f + 1.7f, b * 4.2f + 7.9f, 37);
		int rgb = SkyColors.mix(MOON_RGB, SEA_RGB, SEA_DEPTH * SkyColors.smooth((n - 0.48f) / 0.2f));
		return SkyColors.scale(rgb, 1 - LIMB + LIMB * (float) Math.sqrt(1 - r2));
	}

	/** Distance from the moon's middle in moon radii, measured on the flat sky round it like the face. */
	static float moonDistance(float x, float y, float z) {
		float cos = x * MOON_FRAME.dx + y * MOON_FRAME.dy + z * MOON_FRAME.dz;
		if (cos <= 0) {
			return Float.MAX_VALUE;
		}
		return (float) Math.sqrt(Math.max(0, 1 - cos * cos)) / cos / TAN_MOON;
	}

	/** Strength of the glow {@code r} moon radii from the middle: full on the disc, gone by GLOW_END. */
	static float glow(float r) {
		if (r <= 1) {
			return 1;
		}
		if (r >= GLOW_END) {
			return 0;
		}
		float k = r - 1;
		float g = GLOW_NEAR * (float) Math.exp(-k / GLOW_NEAR_REACH) + GLOW_FAR * (float) Math.exp(-k / GLOW_FAR_REACH);
		return Math.min(1, g) * (1 - SkyColors.smooth(k / (GLOW_END - 1)));
	}

	/** How thick the clouds are (0..1) at a yaw and height in degrees, moved {@code drift} degrees round. */
	static float cloud(float yawDeg, float elevDeg, float drift) {
		float band = SkyColors.smooth((elevDeg - CLOUD_LOW) / 2.5f)
				* (1 - SkyColors.smooth((elevDeg - CLOUD_HIGH) / (CLOUD_TOP - 1 - CLOUD_HIGH)));
		if (band <= 0) {
			return 0;
		}
		float u = yawDeg + drift;
		// Both layers repeat after exactly one turn, so the band joins up all the way round
		float n = 0.62f * SkyColors.noise(u / 15, elevDeg / 2.8f, 51, 24)
				+ 0.38f * SkyColors.noise(u / 9, elevDeg / 1.8f, 57, 40);
		return SkyColors.smooth((n - 0.5f) / 0.22f) * band;
	}

	/** Sky colour at a height in degrees (the horizon colour below it). */
	static int skyColor(float elevDeg) {
		return SkyColors.ramp(SKY_AT, SKY_RGB, elevDeg);
	}

	/**
	 * @param baseYaw Minecraft yaw the lyrics face; the moon sits to its left
	 * @param level 0..1, how much of the effect shows
	 * @param time seconds, sped up or slowed down by the sky speed; moves the clouds and twinkles the stars
	 * @param pixel the angle one screen pixel covers, in radians
	 */
	void draw(SkyCanvas c, float radius, float baseYaw, float level, float time, float pixel) {
		float d = (time * CLOUD_DRIFT) % 360;
		// Repainting the band costs a little, so only when the clouds have visibly moved
		if (Math.abs(d - drift) > 0.05f) {
			drift = d;
			clouds.repaint(drifted);
		}
		float cos = (float) Math.cos(baseYaw * DEG), sin = (float) Math.sin(baseYaw * DEG);
		c.fadeBelowHorizon(HORIZON_FADE);
		DOME_MESH.draw(c, cos, sin, radius * DOME, level);
		GLOW_MESH.draw(c, cos, sin, radius * GLOW_SKY, level);
		clouds.draw(c, cos, sin, radius * CLOUDS, level);
		stars(c, radius * STARS, baseYaw, level, time, pixel);
		DISC_MESH.draw(c, cos, sin, radius * DISC, level);
		c.fadeBelowHorizon(0);
	}

	/** Small square stars, mixed into the sky colour behind them (so they stay opaque), twinkling. */
	private void stars(SkyCanvas c, float r, float baseYaw, float level, float time, float pixel) {
		for (int i = 0; i < STAR_COUNT; i++) {
			if (STAR_ALPHA[i] == 0) {
				continue;
			}
			float twinkle = 0.7f + 0.3f * (float) Math.sin(time * (0.6f + 0.11f * (i % 9)) + i * 1.7f);
			int col = SkyColors.argb(SkyColors.mix(STAR_SKY[i], STAR_RGB, STAR_ALPHA[i] * twinkle), level);
			float s = STAR_SIZE[i] * pixel;
			frame.aim(baseYaw + STAR_YAW[i], STAR_ELEV[i]);
			c.at(frame, -s, -s, r, col);
			c.at(frame, s, -s, r, col);
			c.at(frame, s, s, r, col);
			c.at(frame, -s, s, r, col);
		}
	}
}
