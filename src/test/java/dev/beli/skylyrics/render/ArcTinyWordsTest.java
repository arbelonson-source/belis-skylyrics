package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

/** A line of very narrow words (a couple of letters each) on tilted, bowed rows: no two groups of one row are drawn over each other. */
class ArcTinyWordsTest {
	private static final float SPACE = 3f;
	private static final float[] WIDTHS = {2, 3, 2, 2, 4, 2, 3, 2};

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

	private static void check(String name, float[] widths, int[] rowStarts, boolean rtl) {
		for (long seed = 0; seed < 500; seed++) {
			List<ArcLayout.Group> groups = ArcLayout.place(widths, rowStarts, SPACE, ArcLayout.seed((int) seed, name + seed), rtl);
			for (int i = 0; i < groups.size(); i++) {
				for (int j = i + 1; j < groups.size(); j++) {
					ArcLayout.Group a = groups.get(i), b = groups.get(j);
					if (rowOf(rowStarts, a.from()) != rowOf(rowStarts, b.from())) {
						continue;
					}
					assertFalse(overlap(corners(a, widths), corners(b, widths)),
							name + " rtl " + rtl + " seed " + seed + ": [" + describe(a) + "] over [" + describe(b) + "]");
				}
			}
		}
	}

	@Test
	void tinyWordsInOneRow() {
		for (boolean rtl : new boolean[] {false, true}) {
			check("tiny one row", WIDTHS, new int[] {0}, rtl);
		}
	}

	@Test
	void tinyWordsInTheRowsTheLayoutPicks() {
		for (boolean rtl : new boolean[] {false, true}) {
			check("tiny auto rows", WIDTHS, ArcLayout.rows(WIDTHS, new boolean[WIDTHS.length]), rtl);
		}
	}

	@Test
	void tinyWordsInTwoShortRows() {
		for (boolean rtl : new boolean[] {false, true}) {
			check("tiny two rows", WIDTHS, new int[] {0, 4}, rtl);
			check("tiny three rows", WIDTHS, new int[] {0, 3, 6}, rtl);
		}
	}

	/** Mostly tiny words with one big one among them: the big one's tilt and size must not run into the little ones beside it. */
	@Test
	void aBigWordAmongTinyOnes() {
		float[] w = {2, 3, 2, 40, 2, 2, 3, 2, 30, 2};
		for (boolean rtl : new boolean[] {false, true}) {
			check("tiny with big", w, new int[] {0}, rtl);
		}
	}
}
