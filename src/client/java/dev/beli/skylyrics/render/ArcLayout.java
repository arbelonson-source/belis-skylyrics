package dev.beli.skylyrics.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

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
	/**
	 * How far a row's ends rise above its middle: this share of the row's width, kept between the limits so short
	 * rows still bend and long ones don't climb too far.
	 */
	static final float RISE = 0.1f, MIN_RISE = 6f, MAX_RISE = 20f;
	/** Groups lean along the curve, but only this much of the way (the rest is their own random tilt). */
	static final float FOLLOW = 0.4f;
	static final float ROLL_LIMIT = 16f;
	/** Each group's own tilt, in degrees: at least the first, at most the second, neighbours leaning opposite ways. */
	private static final float ROLL_MIN = 3f, ROLL_MAX = 10f;
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

	/** Above this many steps (rows x words squared) the exact search is skipped for a plain fill; about 15 ms of work. */
	private static final long MAX_DP_STEPS = 5_000_000L;

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

	/**
	 * How far off the line's middle (as a share of the distance to the line) a group may be and still be drawn. Past about
	 * 68 degrees from the line's centre a row is under half a degree tall, so it can't be read from any view.
	 */
	static final float REACH_TAN = 2.5f;

	/**
	 * How far from the block's middle, in the layout's own units, a group is still drawn. A giant line has hundreds of
	 * rows, and only the ones near the middle can be seen. {@code unitTan} is the tangent of the angle one layout unit
	 * covers seen from the camera (the line's size at its distance, divided by its row height, times the arc size).
	 */
	static float reach(float unitTan) {
		return REACH_TAN / Math.max(1e-6f, unitTan);
	}

	/** Whether a group is close enough to the view's middle (the block's middle moved by {@code focus}) to be worth drawing. */
	static boolean worthDrawing(Group g, float reach, float focus) {
		return Math.abs(g.y() - focus) - HALF_HEIGHT * MAX_SCALE * 2 <= reach;
	}

	/** A block taller than this (units, about nine rows) can't sit in view whole, so it scrolls to follow the words being sung. */
	static final float TALL = 9 * ROW_PITCH;
	/** The scroll averages this many groups either side of the current one, so it glides instead of hopping with each group's own step. */
	private static final int FOCUS_SPAN = 5;

	/**
	 * How far down the block's middle should sit so the words being sung stay in view: 0 for a normal line, and for a tall
	 * one the average height of the groups around the one holding the word at position {@code word}. The position is
	 * fractional (the sung word plus how far through it the singing is), and the result slides between one group's
	 * average and the next one's, so the scroll never hops. Words come out from the top, so without this a line of
	 * hundreds of words would be sung above the view, with only its middle in front of you.
	 */
	static float focus(List<Group> groups, float word) {
		if (groups.size() < 2) {
			return 0;
		}
		float top = Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
		for (Group g : groups) {
			top = Math.min(top, g.y());
			bottom = Math.max(bottom, g.y());
		}
		if (bottom - top <= TALL) {
			return 0;
		}
		int whole = (int) Math.floor(word);
		int lo = 0, hi = groups.size() - 1;
		while (lo < hi) { // the last group starting at or before the word
			int mid = (lo + hi + 1) >>> 1;
			if (groups.get(mid).from() <= whole) {
				lo = mid;
			} else {
				hi = mid - 1;
			}
		}
		Group g = groups.get(lo);
		float t = Math.max(0, Math.min(1, (word - g.from()) / Math.max(1, g.to() - g.from())));
		float here = focusAround(groups, lo);
		return t == 0 || lo + 1 >= groups.size() ? here : here + (focusAround(groups, lo + 1) - here) * t;
	}

	/** The average height of the groups around group {@code index}. */
	private static float focusAround(List<Group> groups, int index) {
		int from = Math.max(0, index - FOCUS_SPAN), to = Math.min(groups.size() - 1, index + FOCUS_SPAN);
		float sum = 0;
		for (int i = from; i <= to; i++) {
			sum += groups.get(i).y();
		}
		return sum / (to - from + 1);
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
		return rows(widths, pause, null);
	}

	/**
	 * Like {@link #rows(float[], boolean[])}, with {@code tight[i]} saying word i runs straight into word i+1 with no
	 * gap (neighbouring Chinese or Japanese characters). May be null.
	 */
	static int[] rows(float[] widths, boolean[] pause, boolean @Nullable [] tight) {
		int n = widths.length;
		if (n == 0) {
			return new int[0];
		}
		float total = width(widths, tight, 0, n, GAP);
		int k = total <= SINGLE_ROW ? 1 : Math.max(2, (int) Math.ceil(total / ROW_WIDTH));
		k = Math.min(k, n);
		if (k == 1) {
			return new int[] {0};
		}
		// Row widths from prefix sums, so one candidate row costs O(1) instead of re-adding its words
		double[] sum = new double[n + 1];
		int[] gaps = new int[n + 1];
		for (int i = 0; i < n; i++) {
			sum[i + 1] = sum[i] + widths[i];
			gaps[i + 1] = gaps[i] + (tight == null || !tight[i] ? 1 : 0);
		}
		if ((long) k * n * n > MAX_DP_STEPS) {
			return fill(sum, gaps, n);
		}
		if (k == 2) {
			// The usual case: try every cut, and let one after a comma win when it's nearly as even
			int bestCut = 1;
			float bestScore = Float.MAX_VALUE;
			for (int c = 1; c < n; c++) {
				float score = Math.max(span(sum, gaps, 0, c), span(sum, gaps, c, n)) * (pause[c - 1] ? PAUSE_BONUS : 1);
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
					float w = span(sum, gaps, j, i);
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

	/** {@link #width(float[], boolean[], int, int, float)} with {@link #GAP} from prefix sums of the widths and the gaps. */
	private static float span(double[] sum, int[] gaps, int from, int to) {
		return (float) (sum[to] - sum[from] + GAP * (gaps[to - 1] - gaps[from]));
	}

	/**
	 * Cheap cut for a line too long for the exact search: each row takes words until the next one would pass
	 * {@link #ROW_WIDTH} (a row has at least one word). Linear in the number of words.
	 */
	private static int[] fill(double[] sum, int[] gaps, int n) {
		int[] starts = new int[Math.min(n, (int) (Math.ceil(span(sum, gaps, 0, n) / ROW_WIDTH) * 2) + 2)];
		int rows = 0;
		for (int from = 0; from < n; ) {
			if (rows == starts.length) {
				starts = Arrays.copyOf(starts, rows * 2);
			}
			starts[rows++] = from;
			int to = from + 1;
			while (to < n && span(sum, gaps, from, to + 1) <= ROW_WIDTH) {
				to++;
			}
			from = to;
		}
		return Arrays.copyOf(starts, rows);
	}

	/** Width of words {@code from..to-1} side by side with {@code gap} between them. */
	static float width(float[] widths, int from, int to, float gap) {
		float w = 0;
		for (int i = from; i < to; i++) {
			w += widths[i];
		}
		return w + gap * Math.max(0, to - from - 1);
	}

	/** Width of words {@code from..to-1} with {@code gap} between them, except after a word flagged in {@code tight}. */
	static float width(float[] widths, boolean @Nullable [] tight, int from, int to, float gap) {
		float w = 0;
		for (int i = from; i < to; i++) {
			w += widths[i];
			if (i + 1 < to && (tight == null || !tight[i])) {
				w += gap;
			}
		}
		return w;
	}

	/** How far the ends of a row this wide rise above its middle. */
	static float rise(float rowWidth) {
		return Math.clamp(RISE * rowWidth, MIN_RISE, MAX_RISE);
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
		return place(widths, rowStarts, space, seed, rtl, null);
	}

	/** Like {@link #place(float[], int[], float, long, boolean)}; {@code tight[i]}: no space between word i and i+1. */
	static List<Group> place(float[] widths, int[] rowStarts, float space, long seed, boolean rtl,
			boolean @Nullable [] tight) {
		int n = widths.length;
		List<Group> out = new ArrayList<>();
		int rows = rowStarts.length;
		float flip = unit(seed, 0, SALT_FLIP) < 0.5f ? 1 : -1;
		int groupNo = 0;
		float rowY = 0, above = 0;
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
			float biggest = 0;
			for (int g = 0; g < spans.size(); g++) {
				int[] s = spans.get(g);
				scale[g] = groupScale(seed, s[0]);
				biggest = Math.max(biggest, scale[g]);
				advance[g] = width(widths, tight, s[0], s[1], space) * (1 + (scale[g] - 1) * PUSH);
				rowWidth += advance[g];
			}
			// Big words need more room between the rows, or the row below runs into them
			if (r > 0) {
				rowY += ROW_PITCH * Math.max(1, (above + biggest) / 2);
			}
			above = biggest;
			float x = -rowWidth / 2;
			// y = rowY - curve * x², rising by the row's rise at its ends
			float half = Math.max(1, rowWidth / 2);
			float curve = rise(rowWidth) / (half * half);
			for (int g = 0; g < spans.size(); g++, groupNo++) {
				int[] s = spans.get(g);
				float cx = x + advance[g] / 2;
				x += advance[g] + GAP;
				if (rtl) {
					cx = -cx;
				}
				float y = rowY - curve * cx * cx + (unit(seed, s[0], SALT_STEP) * 2 - 1) * STEP;
				float tangent = (float) Math.toDegrees(Math.atan(-2 * curve * cx));
				float own = (groupNo % 2 == 0 ? flip : -flip) * (ROLL_MIN + (ROLL_MAX - ROLL_MIN) * unit(seed, s[0], SALT_ROLL));
				float angle = Math.clamp(FOLLOW * tangent + own, -ROLL_LIMIT, ROLL_LIMIT);
				float inner = width(widths, tight, s[0], s[1], space);
				float[] wordX = new float[s[1] - s[0]];
				float wx = -inner / 2;
				for (int i = s[0]; i < s[1]; i++) {
					float c = wx + widths[i] / 2;
					wordX[i - s[0]] = rtl ? -c : c;
					wx += widths[i] + (tight != null && tight[i] ? 0 : space);
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
