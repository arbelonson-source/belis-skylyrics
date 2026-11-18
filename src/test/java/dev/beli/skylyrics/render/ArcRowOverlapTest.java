package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

/** On a line of two or more rows, no group of one row is drawn over a group of the row above. */
class ArcRowOverlapTest {
	private static final float SPACE = 3f;

	/** The group's tilted box: four corners, in screen coordinates (y down), 8 tall at scale 1 like a line of text. */
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

	/** Separating axis test: true when the two boxes overlap by more than a hair. */
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

	private static void check(String name, float[] widths, int[] rowStarts) {
		for (long seed = 0; seed < 3000; seed++) {
			List<ArcLayout.Group> groups = ArcLayout.place(widths, rowStarts, SPACE, ArcLayout.seed((int) seed, name + seed), false);
			for (ArcLayout.Group a : groups) {
				for (ArcLayout.Group b : groups) {
					if (rowOf(rowStarts, a.from()) + 1 != rowOf(rowStarts, b.from())) {
						continue;
					}
					assertFalse(overlap(corners(a, widths), corners(b, widths)),
							name + " seed " + seed + ": words " + a.from() + ".." + a.to() + " and " + b.from() + ".." + b.to());
				}
			}
		}
	}

	private static int rowOf(int[] rows, int word) {
		int r = 0;
		while (r + 1 < rows.length && rows[r + 1] <= word) {
			r++;
		}
		return r;
	}

	@Test
	void shortTwoRowLine() {
		check("torches", new float[] {40, 38, 25, 30}, new int[] {0, 2}); // Torches flicker / down below
	}

	@Test
	void longUpperRowShortLowerRow() {
		check("long over short", new float[] {40, 38, 30, 34, 36, 28, 22}, new int[] {0, 6});
		check("long over one", new float[] {40, 38, 30, 34, 36, 28, 22, 20}, new int[] {0, 7});
	}

	@Test
	void shortUpperRowLongLowerRow() {
		check("short over long", new float[] {22, 40, 38, 30, 34, 36, 28}, new int[] {0, 1});
		check("two over long", new float[] {22, 26, 40, 38, 30, 34, 36, 28}, new int[] {0, 2});
	}

	@Test
	void threeRows() {
		check("three rows", new float[] {30, 28, 36, 25, 40, 22, 33, 29, 31}, new int[] {0, 3, 6});
	}
}
