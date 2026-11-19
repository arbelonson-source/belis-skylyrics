package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

/** In one row, no group is drawn over its neighbour in the same row, however big or tilted the two are. */
class ArcSameRowOverlapTest {
	private static final float SPACE = 3f;

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

	private static void check(String name, float[] widths, int[] rowStarts) {
		for (long seed = 0; seed < 3000; seed++) {
			List<ArcLayout.Group> groups = ArcLayout.place(widths, rowStarts, SPACE, ArcLayout.seed((int) seed, name + seed), false);
			for (int i = 0; i < groups.size(); i++) {
				for (int j = i + 1; j < groups.size(); j++) {
					ArcLayout.Group a = groups.get(i), b = groups.get(j);
					if (rowOf(rowStarts, a.from()) != rowOf(rowStarts, b.from())) {
						continue;
					}
					assertFalse(overlap(corners(a, widths), corners(b, widths)),
							name + " seed " + seed + ": words " + a.from() + ".." + a.to() + " and " + b.from() + ".." + b.to());
				}
			}
		}
	}

	@Test
	void longLineInFiveRows() {
		// This is a very long line of lyrics that keeps going across the whole sky so it has to wrap onto several rows
		float[] w = {22, 12, 8, 36, 12, 12, 42, 14, 38, 28, 32, 36, 30, 22, 24, 12, 10, 6, 20, 12, 12, 22, 36, 24};
		check("five rows", w, ArcLayout.rows(w, new boolean[w.length]));
	}

	@Test
	void shortAndMixedLines() {
		float[] a = {40, 38, 25, 30};
		check("one row", a, new int[] {0});
		float[] b = {10, 60, 12, 55, 8, 44, 30, 9};
		check("mixed", b, new int[] {0});
		check("mixed rows", b, new int[] {0, 4});
	}
}
