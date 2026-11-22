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

	/** How far (radians) each pattern's farthest star lies from its middle, before the width of a star or line is added. */
	private static final float[] REACH = new float[PATTERNS.length];

	static {
		for (int p = 0; p < PATTERNS.length; p++) {
			double far = 0;
			for (int i = 0; i < TAN_X[p].length; i++) {
				far = Math.max(far, Math.atan(Math.hypot(TAN_X[p][i], TAN_Y[p][i])));
			}
			REACH[p] = (float) far * 1.02f;
		}
	}

	private final SkyFrame frame = new SkyFrame();

	/**
	 * @param baseYaw Minecraft yaw the lyrics face
	 * @param level 0..1, how much shows
	 * @param pixel the angle one screen pixel covers, in radians
	 */
	void draw(SkyCanvas c, float radius, float baseYaw, float level, double time, float pixel) {
		draw(c, radius, baseYaw, level, time, pixel, SkyView.ALL);
	}

	/** As above, leaving out the patterns the camera {@code view} can't see. */
	void draw(SkyCanvas c, float radius, float baseYaw, float level, double time, float pixel, SkyView view) {
		draw(c, radius, baseYaw, level, 1, time, pixel, view, null);
	}

	/**
	 * Over a painted sky (synthwave, moonlight), whose finished colour behind each pattern star {@link #behind} gave.
	 * That sky goes out through a pipeline that throws away every pixel fainter than a tenth, so lines sent at their
	 * own 0.4 of the sky's strength vanished whenever the sky showed under a quarter (dusk, dawn, a low Strength, rain)
	 * and the patterns' own fade ended in a jump. Like the painted skies' stars, each line and star is mixed into the
	 * sky behind it instead, by its own strength times {@code show} (0..1, how much the patterns show), and sent exactly
	 * as opaque as the sky itself, {@code level}: it shows whenever the sky does and fades smoothly into it. Over a fully
	 * shown sky it looks the same as the see-through lines and stars did.
	 */
	void drawOver(SkyCanvas c, float radius, float baseYaw, float level, float show, double time, float pixel, SkyView view,
			int[][] behind) {
		draw(c, radius, baseYaw, level, show, time, pixel, view, behind);
	}

	/**
	 * The sky colour (lyrics facing south) right behind every pattern star, pattern by pattern: worked out once per
	 * painted sky, which only turns with the lyrics' direction, as the patterns do.
	 */
	static int[][] behind(SkyMesh.Paint sky) {
		int[][] out = new int[PATTERNS.length][];
		SkyFrame f = new SkyFrame();
		for (int p = 0; p < PATTERNS.length; p++) {
			f.aim(PATTERNS[p].yaw(), PATTERNS[p].elev());
			out[p] = new int[TAN_X[p].length];
			for (int i = 0; i < out[p].length; i++) {
				float a = TAN_X[p][i], b = TAN_Y[p][i];
				float x = f.dx + a * f.ux + b * f.vx, y = f.dy + a * f.uy + b * f.vy, z = f.dz + a * f.uz + b * f.vz;
				float l = (float) Math.sqrt(x * x + y * y + z * z);
				out[p][i] = sky.at(x / l, y / l, z / l);
			}
		}
		return out;
	}

	/** Every star's and line's colour, see-through over the sky ({@code behind} null) or mixed into the painted one. */
	private void draw(SkyCanvas c, float radius, float baseYaw, float level, float show, double time, float pixel, SkyView view,
			int[][] behind) {
		int line = SkyColors.argb(LINE_RGB, LINE_ALPHA * level);
		float half = LINE_PIXELS * pixel / 2;
		float s = STAR_PIXELS * pixel;
		for (int p = 0; p < PATTERNS.length; p++) {
			Pattern pat = PATTERNS[p];
			frame.aim(baseYaw + pat.yaw(), pat.elev());
			if (!view.capVisible(frame.dx, frame.dy, frame.dz, REACH[p] + 3 * pixel)) {
				continue;
			}
			float[] xs = TAN_X[p], ys = TAN_Y[p];
			int[] lines = pat.lines();
			for (int k = 0; k + 1 < lines.length; k += 2) {
				int from = lines[k], to = lines[k + 1];
				float ax = xs[from], ay = ys[from], bx = xs[to], by = ys[to];
				float len = (float) Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay));
				float nx = -(by - ay) / len * half, ny = (bx - ax) / len * half;
				// Over a painted sky each end takes on the sky behind its own star
				int lineA = behind == null ? line : SkyColors.argb(SkyColors.mix(behind[p][from], LINE_RGB, LINE_ALPHA * show), level);
				int lineB = behind == null ? line : SkyColors.argb(SkyColors.mix(behind[p][to], LINE_RGB, LINE_ALPHA * show), level);
				c.at(frame, ax + nx, ay + ny, radius, lineA);
				c.at(frame, bx + nx, by + ny, radius, lineB);
				c.at(frame, bx - nx, by - ny, radius, lineB);
				c.at(frame, ax - nx, ay - ny, radius, lineA);
			}
			for (int i = 0; i < xs.length; i++) {
				float twinkle = 0.85f + 0.15f * (float) Math.sin(SkyClock.phase(time, 1.3, p * 1.7f + i * 2.9f));
				int star = behind == null ? SkyColors.argb(STAR_RGB, STAR_ALPHA * twinkle * level)
						: SkyColors.argb(SkyColors.mix(behind[p][i], STAR_RGB, STAR_ALPHA * twinkle * show), level);
				c.at(frame, xs[i] - s, ys[i] - s, radius, star);
				c.at(frame, xs[i] + s, ys[i] - s, radius, star);
				c.at(frame, xs[i] + s, ys[i] + s, radius, star);
				c.at(frame, xs[i] - s, ys[i] + s, radius, star);
			}
		}
	}
}
