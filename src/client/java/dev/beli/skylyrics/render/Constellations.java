package dev.beli.skylyrics.render;

import dev.beli.skylyrics.render.SkyCanvas.SkyFrame;

/**
 * A few made-up star patterns spread round the night sky: small bright stars joined by thin faint
 * lines. They stay put in the sky (like the lyrics' compass direction, not the turning stars), so
 * they read as landmarks. Line width and star size follow the screen, about one and three pixels.
 */
final class Constellations {
	private static final float DEG = (float) (Math.PI / 180);

	private static final int STAR_RGB = 0xFAE1FF;
	private static final int LINE_RGB = 0xCDB9F0;
	private static final float STAR_ALPHA = 0.95f, LINE_ALPHA = 0.4f;
	/** Star half-size and line width, in screen pixels. */
	private static final float STAR_PIXELS = 1.5f, LINE_PIXELS = 1.3f;

	/**
	 * Each pattern: where it sits (yaw from where the lyrics face, height, both degrees), its roll and
	 * size, the stars (x right, y up, degrees before scaling) and which stars the lines join.
	 */
	record Pattern(float yaw, float elev, float roll, float scale, float[] stars, int[] lines) {}

	static final Pattern[] PATTERNS = {
		// A "W", up and to the right of the lyrics
		new Pattern(16, 27, 0, 1.3f, new float[] {0, 0, 1.6f, 2.6f, 3.7f, 1.1f, 5.3f, 4, 7.7f, 2.3f},
				new int[] {0, 1, 1, 2, 2, 3, 3, 4}),
		// A branching six-star figure
		new Pattern(78, 38, 15, 1.4f, new float[] {0, 0, 2.5f, 3, 5.5f, 4.5f, 2, -3, 5, -4, -3.5f, 0.5f},
				new int[] {0, 1, 1, 2, 0, 3, 3, 4, 0, 5}),
		// A slanted parallelogram
		new Pattern(150, 30, -10, 1.5f, new float[] {0, 0, 4, 1, 5, 4, 1, 3},
				new int[] {0, 1, 1, 2, 2, 3, 3, 0}),
		// A dipper: a bowl with a bent handle
		new Pattern(-75, 40, 20, 1.2f, new float[] {0, 0, 3, 0.5f, 5.5f, 0, 7.5f, -1.5f, 8, -4.5f, 11.5f, -4.8f, 11, -1.6f},
				new int[] {0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 3}),
		// A kite with a tail
		new Pattern(-140, 26, 0, 1.4f, new float[] {0, 0, 3, 2, 6, 0, 3, -2.5f, 3, -6},
				new int[] {0, 1, 1, 2, 2, 3, 3, 0, 3, 4}),
	};

	/** Tangent-plane positions of every star, pattern by pattern, rolled and scaled once. */
	private static final float[][] TAN_X = new float[PATTERNS.length][], TAN_Y = new float[PATTERNS.length][];

	static {
		for (int p = 0; p < PATTERNS.length; p++) {
			Pattern pat = PATTERNS[p];
			int n = pat.stars().length / 2;
			float cx = 0, cy = 0;
			for (int i = 0; i < n; i++) {
				cx += pat.stars()[2 * i] / n;
				cy += pat.stars()[2 * i + 1] / n;
			}
			float cos = (float) Math.cos(pat.roll() * DEG), sin = (float) Math.sin(pat.roll() * DEG);
			TAN_X[p] = new float[n];
			TAN_Y[p] = new float[n];
			for (int i = 0; i < n; i++) {
				// Centred on the pattern's middle so it sits where its yaw and height say
				float x = (pat.stars()[2 * i] - cx) * pat.scale(), y = (pat.stars()[2 * i + 1] - cy) * pat.scale();
				TAN_X[p][i] = (float) Math.tan((x * cos - y * sin) * DEG);
				TAN_Y[p][i] = (float) Math.tan((x * sin + y * cos) * DEG);
			}
		}
	}

	private final SkyFrame frame = new SkyFrame();

	/**
	 * @param baseYaw Minecraft yaw the lyrics face
	 * @param level 0..1, how much shows
	 * @param pixel the angle one screen pixel covers, in radians
	 */
	void draw(SkyCanvas c, float radius, float baseYaw, float level, float time, float pixel) {
		int line = SkyColors.argb(LINE_RGB, LINE_ALPHA * level);
		float half = LINE_PIXELS * pixel / 2;
		float s = STAR_PIXELS * pixel;
		for (int p = 0; p < PATTERNS.length; p++) {
			Pattern pat = PATTERNS[p];
			frame.aim(baseYaw + pat.yaw(), pat.elev());
			float[] xs = TAN_X[p], ys = TAN_Y[p];
			int[] lines = pat.lines();
			for (int k = 0; k + 1 < lines.length; k += 2) {
				float ax = xs[lines[k]], ay = ys[lines[k]], bx = xs[lines[k + 1]], by = ys[lines[k + 1]];
				float len = (float) Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay));
				float nx = -(by - ay) / len * half, ny = (bx - ax) / len * half;
				c.at(frame, ax + nx, ay + ny, radius, line);
				c.at(frame, bx + nx, by + ny, radius, line);
				c.at(frame, bx - nx, by - ny, radius, line);
				c.at(frame, ax - nx, ay - ny, radius, line);
			}
			for (int i = 0; i < xs.length; i++) {
				float twinkle = 0.85f + 0.15f * (float) Math.sin(time * 1.3f + p * 1.7f + i * 2.9f);
				int star = SkyColors.argb(STAR_RGB, STAR_ALPHA * twinkle * level);
				c.at(frame, xs[i] - s, ys[i] - s, radius, star);
				c.at(frame, xs[i] + s, ys[i] - s, radius, star);
				c.at(frame, xs[i] + s, ys[i] + s, radius, star);
				c.at(frame, xs[i] - s, ys[i] + s, radius, star);
			}
		}
	}
}
