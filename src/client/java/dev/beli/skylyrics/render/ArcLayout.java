package dev.beli.skylyrics.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Where each word of a line goes in the arc layout. The line is cut into a couple of even rows that bend up at the
 * ends like a smile. Each row is split into little groups of one to three words, like sung phrases, and every
 * group gets its own tilt, size and step up or down, so the line tumbles a little. The choices come from a seed
 * made from the line: a line keeps the same shape every frame and the next line gets a different one.
 * Pure arithmetic in text units (the font's em is about 10.5 units), so it can be tested without a game.
 */
final class ArcLayout {
	/** Space between groups, a bit wider than a normal space so tilted groups don't touch. */
	static final float GAP = 4.7f;
	/** Lines up to this wide stay on one row; longer ones are cut into rows about {@link #ROW_WIDTH} wide. */
	static final float SINGLE_ROW = 110f;
	static final float ROW_WIDTH = 160f;
	/** From one row to the next. */
	static final float ROW_PITCH = 14f;
	/** How fast a row bends up away from its middle: y rises by this × x². */
	static final float CURVE = 0.0024f;
	/** Groups lean along the curve, but only this much of the way (the rest is their own random tilt). */
	static final float FOLLOW = 0.4f;
	static final float ROLL_LIMIT = 16f;
	/** Each group's own tilt, in degrees: at least the first, at most the second, neighbours leaning opposite ways. */
	private static final float ROLL_MIN = 1.5f, ROLL_MAX = 8f;
	/** How far a group steps up or down off the curve. */
	static final float STEP = 3.5f;
	static final float MIN_SCALE = 0.75f;
	static final float MAX_SCALE = 1.5f;
	/** A bigger group pushes its neighbours only this much of its extra width; the rest may overlap a little. */
	private static final float PUSH = 0.5f;
	/** Two rows split after a comma or full stop count as this much more even, so lines break at a natural pause. */
	private static final float PAUSE_BONUS = 0.85f;
	/** Half the height of a lower-case word, for centring the block. */
	private static final float HALF_HEIGHT = 4f;

	private static final int SALT_GROUP = 1, SALT_SIZE = 2, SALT_KIND = 3, SALT_ROLL = 4, SALT_STEP = 5, SALT_FLIP = 6;

	private ArcLayout() {
	}

	/**
	 * Words {@code from..to-1} drawn as one piece: centred on (x, y), turned by {@code angle} degrees (positive turns
	 * clockwise, so the right end goes down) and sized by {@code scale}. {@code wordX} holds each word's centre
	 * relative to the group's centre, before scaling.
	 */
	record Group(int from, int to, float x, float y, float angle, float scale, float[] wordX) {
	}

	/** A seed that stays the same for a line and differs between lines. */
	static long seed(int index, String text) {
		return mix(text.hashCode() * 0x9E3779B97F4A7C15L + index);
	}

	/** A number in [0, 1) picked by the seed, the word and what it's for. */
	static float unit(long seed, int word, int salt) {
		long h = mix(seed + word * 0x632BE59BD9B4E019L + salt * 0x85EBCA77C2B2AE63L);
		return (h >>> 40) / (float) (1 << 24);
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 33)) * 0xFF51AFD7ED558CCDL;
		z = (z ^ (z >>> 33)) * 0xC4CEB9FE1A85EC53L;
		return z ^ (z >>> 33);
	}

	/**
	 * Splits words of the given widths into rows of about even width: one row for a short line, otherwise as few
	 * rows of about {@link #ROW_WIDTH} as it takes (at least two). Returns where each row starts.
	 *
	 * @param pause whether a row may end nicely after each word (it ends with a comma, full stop…)
	 */
	static int[] rows(float[] widths, boolean[] pause) {
		int n = widths.length;
		if (n == 0) {
			return new int[0];
		}
		float total = width(widths, 0, n, GAP);
		int k = total <= SINGLE_ROW ? 1 : Math.max(2, (int) Math.ceil(total / ROW_WIDTH));
		k = Math.min(k, n);
		if (k == 1) {
			return new int[] {0};
		}
		if (k == 2) {
			// The usual case: try every cut, and let one after a comma win when it's nearly as even
			int bestCut = 1;
			float bestScore = Float.MAX_VALUE;
			for (int c = 1; c < n; c++) {
				float score = Math.max(width(widths, 0, c, GAP), width(widths, c, n, GAP)) * (pause[c - 1] ? PAUSE_BONUS : 1);
				if (score < bestScore) {
					bestScore = score;
					bestCut = c;
				}
			}
			return new int[] {0, bestCut};
		}
		// best[r][i]: the smallest widest row when the first i words make r rows
		float[][] best = new float[k + 1][n + 1];
		int[][] cut = new int[k + 1][n + 1];
		for (float[] row : best) {
			Arrays.fill(row, Float.MAX_VALUE);
		}
		best[0][0] = 0;
		for (int r = 1; r <= k; r++) {
			for (int i = r; i <= n; i++) {
				for (int j = r - 1; j < i; j++) {
					if (best[r - 1][j] == Float.MAX_VALUE) {
						continue;
					}
					float w = width(widths, j, i, GAP);
					float worst = Math.max(best[r - 1][j], w);
					if (worst < best[r][i]) {
						best[r][i] = worst;
						cut[r][i] = j;
					}
				}
			}
		}
		int[] starts = new int[k];
		for (int r = k, i = n; r >= 1; r--) {
			starts[r - 1] = cut[r][i];
			i = cut[r][i];
		}
		return starts;
	}

	/** Width of words {@code from..to-1} side by side with {@code gap} between them. */
	static float width(float[] widths, int from, int to, float gap) {
		float w = 0;
		for (int i = from; i < to; i++) {
			w += widths[i];
		}
		return w + gap * Math.max(0, to - from - 1);
	}

	/** How many words go in the group starting at word {@code at}: usually two, sometimes one or three. */
	static int groupSize(long seed, int at) {
		float u = unit(seed, at, SALT_GROUP);
		return u < 0.25f ? 1 : u < 0.8f ? 2 : 3;
	}

	/** A group's size: mostly near 1, now and then a clearly bigger phrase or a quick small aside. */
	static float groupScale(long seed, int at) {
		float kind = unit(seed, at, SALT_KIND), u = unit(seed, at, SALT_SIZE);
		float s;
		if (kind < 0.22f) {
			s = 1.25f + 0.25f * u;
		} else if (kind < 0.4f) {
			s = MIN_SCALE + 0.13f * u;
		} else {
			s = 0.92f + 0.2f * u;
		}
		return Math.clamp(s, MIN_SCALE, MAX_SCALE);
	}

	/**
	 * Places every group. The block is centred on (0, 0).
	 *
	 * @param space width of a normal space, between the words of one group
	 * @param rtl   lay the words out right to left (Hebrew, Arabic)
	 */
	static List<Group> place(float[] widths, int[] rowStarts, float space, long seed, boolean rtl) {
		int n = widths.length;
		List<Group> out = new ArrayList<>();
		int rows = rowStarts.length;
		float flip = unit(seed, 0, SALT_FLIP) < 0.5f ? 1 : -1;
		int groupNo = 0;
		for (int r = 0; r < rows; r++) {
			int from = rowStarts[r], to = r + 1 < rows ? rowStarts[r + 1] : n;
			// Split the row into groups and measure them
			List<int[]> spans = new ArrayList<>();
			for (int i = from; i < to; ) {
				int end = Math.min(to, i + groupSize(seed, i));
				spans.add(new int[] {i, end});
				i = end;
			}
			float[] scale = new float[spans.size()];
			float[] advance = new float[spans.size()];
			float rowWidth = GAP * (spans.size() - 1);
			for (int g = 0; g < spans.size(); g++) {
				int[] s = spans.get(g);
				scale[g] = groupScale(seed, s[0]);
				advance[g] = width(widths, s[0], s[1], space) * (1 + (scale[g] - 1) * PUSH);
				rowWidth += advance[g];
			}
			float rowY = (r - (rows - 1) / 2f) * ROW_PITCH;
			float x = -rowWidth / 2;
			for (int g = 0; g < spans.size(); g++, groupNo++) {
				int[] s = spans.get(g);
				float cx = x + advance[g] / 2;
				x += advance[g] + GAP;
				if (rtl) {
					cx = -cx;
				}
				float y = rowY - CURVE * cx * cx + (unit(seed, s[0], SALT_STEP) * 2 - 1) * STEP;
				float tangent = (float) Math.toDegrees(Math.atan(-2 * CURVE * cx));
				float own = (groupNo % 2 == 0 ? flip : -flip) * (ROLL_MIN + (ROLL_MAX - ROLL_MIN) * unit(seed, s[0], SALT_ROLL));
				float angle = Math.clamp(FOLLOW * tangent + own, -ROLL_LIMIT, ROLL_LIMIT);
				float inner = width(widths, s[0], s[1], space);
				float[] wordX = new float[s[1] - s[0]];
				float wx = -inner / 2;
				for (int i = s[0]; i < s[1]; i++) {
					float c = wx + widths[i] / 2;
					wordX[i - s[0]] = rtl ? -c : c;
					wx += widths[i] + space;
				}
				out.add(new Group(s[0], s[1], cx, y, angle, scale[g], wordX));
			}
		}
		// Centre the block vertically: the raised ends would otherwise push it up
		float top = Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
		for (Group g : out) {
			top = Math.min(top, g.y() - HALF_HEIGHT * g.scale());
			bottom = Math.max(bottom, g.y() + HALF_HEIGHT * g.scale());
		}
		float shift = (top + bottom) / 2;
		out.replaceAll(g -> new Group(g.from(), g.to(), g.x(), g.y() - shift, g.angle(), g.scale(), g.wordX()));
		return out;
	}
}
