package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Right-to-left and mixed-direction lines, placed by the words' ranks: no two groups of one row overlap, and no group
 * overlaps a group two rows away either (a short middle row must not let the rows around it meet).
 */
class ArcRankRowsTest {
	private static final float SPACE = 3f;
	private static final String HEBREW = "שלום", ENGLISH = "love";

	private static double[][] corners(ArcLayout.Group g, float[] widths) {
		float w = ArcLayout.width(widths, g.from(), g.to(), SPACE) * g.scale() / 2;
		float h = 4f * g.scale();
		double a = Math.toRadians(g.angle()), c = Math.cos(a), s = Math.sin(a);
		double[][] out = new double[4][];
		float[][] local = {{-w, -h}, {w, -h}, {w, h}, {-w, h}};
		for (int i = 0; i < 4; i++) {
			out[i] = new double[] {g.x() + local[i][0] * c - local[i][1] * s, g.y() + local[i][0] * s + local[i][1] * c};
		}
		return out;
	}

	private static boolean overlap(double[][] p, double[][] q) {
		for (double[][] poly : new double[][][] {p, q}) {
			for (int i = 0; i < 2; i++) {
				double nx = -(poly[i + 1][1] - poly[i][1]), ny = poly[i + 1][0] - poly[i][0];
				double[] a = range(p, nx, ny), b = range(q, nx, ny);
				if (a[1] <= b[0] + 1e-3 || b[1] <= a[0] + 1e-3) {
					return false;
				}
			}
		}
		return true;
	}

	private static double[] range(double[][] poly, double nx, double ny) {
		double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
		for (double[] c : poly) {
			double d = c[0] * nx + c[1] * ny;
			lo = Math.min(lo, d);
			hi = Math.max(hi, d);
		}
		return new double[] {lo, hi};
	}

	private static int rowOf(int[] rows, int word) {
		int r = 0;
		while (r + 1 < rows.length && rows[r + 1] <= word) {
			r++;
		}
		return r;
	}

	private static String describe(ArcLayout.Group g) {
		return g.from() + ".." + g.to() + " x " + g.x() + " y " + g.y() + " angle " + g.angle() + " scale " + g.scale();
	}

	/** Word i of n: Hebrew where {@code hebrew(i)}, else English. */
	private static List<TopBarWords.Piece> line(int n, java.util.function.IntPredicate hebrew) {
		List<TopBarWords.Piece> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add(new TopBarWords.Piece(hebrew.test(i) ? HEBREW : ENGLISH, true));
		}
		return out;
	}

	/** Rows {@code apart} rows from each other (0 = the same row). */
	private static void check(String name, float[] widths, int[] rowStarts, int[] rank, int apart) {
		for (long seed = 0; seed < 600; seed++) {
			List<ArcLayout.Group> groups = ArcLayout.place(widths, rowStarts, SPACE, ArcLayout.seed((int) seed, name + seed), null, rank);
			for (int i = 0; i < groups.size(); i++) {
				for (int j = 0; j < groups.size(); j++) {
					ArcLayout.Group a = groups.get(i), b = groups.get(j);
					if (rowOf(rowStarts, a.from()) + apart != rowOf(rowStarts, b.from()) || apart == 0 && j <= i) {
						continue;
					}
					assertFalse(overlap(corners(a, widths), corners(b, widths)),
							name + " seed " + seed + " rows " + apart + " apart: [" + describe(a) + "] over [" + describe(b) + "] rank " + java.util.Arrays.toString(rank));
				}
			}
		}
	}

	private static void checkAll(String name, float[] widths, int[] rowStarts, int[] rank) {
		check(name, widths, rowStarts, rank, 0);
		check(name, widths, rowStarts, rank, 1);
		check(name, widths, rowStarts, rank, 2);
		check(name, widths, rowStarts, rank, 3);
	}

	private static final float[] LONG = {22, 12, 8, 36, 12, 12, 42, 14, 38, 28, 32, 36, 30, 22, 24, 12, 10, 6, 20, 12, 12, 22, 36, 24};
	private static final float[] NINE = {30, 28, 36, 25, 40, 22, 33, 29, 31};

	@Test
	void plainRightToLeftLines() {
		checkAll("rtl long", LONG, ArcLayout.rows(LONG, new boolean[LONG.length]), ArcLayout.straight(LONG.length, true));
		checkAll("rtl nine", NINE, new int[] {0, 3, 6}, ArcLayout.straight(NINE.length, true));
	}

	@Test
	void hebrewLinesWithEnglishPhrases() {
		int[] rank = TopBarWords.visualRanks(line(LONG.length, i -> i % 7 < 4));
		checkAll("hebrew with english long", LONG, ArcLayout.rows(LONG, new boolean[LONG.length]), rank);
		int[] nine = TopBarWords.visualRanks(line(NINE.length, i -> i != 4 && i != 5));
		checkAll("hebrew with english nine", NINE, new int[] {0, 3, 6}, nine);
	}

	@Test
	void englishLinesWithHebrewPhrases() {
		int[] rank = TopBarWords.visualRanks(line(LONG.length, i -> i % 6 >= 3));
		checkAll("english with hebrew long", LONG, ArcLayout.rows(LONG, new boolean[LONG.length]), rank);
		int[] nine = TopBarWords.visualRanks(line(NINE.length, i -> i == 3 || i == 4 || i == 7));
		checkAll("english with hebrew nine", NINE, new int[] {0, 3, 6}, nine);
	}

	/** A narrow middle row between two wide ones, in a right-to-left line: the outer rows are two apart and must still clear. */
	@Test
	void aShortMiddleRowBetweenTwoWideOnes() {
		float[] w = {40, 38, 36, 3, 2, 40, 38, 36};
		checkAll("short middle", w, new int[] {0, 3, 5}, ArcLayout.straight(w.length, true));
		float[] v = {60, 55, 4, 60, 55};
		checkAll("short middle ltr", v, new int[] {0, 2, 3}, ArcLayout.straight(v.length, false));
	}
}
