package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

	/**
	 * Each row sits the plain row pitch below the row above, moved down only by exactly what it takes to clear every
	 * row above it (not only the one directly above), so a row that already clears keeps its spacing to the last unit.
	 * The rows' plain spacing comes from {@link ArcMixedOrderTest#oldPlace}, the placement before rows were moved.
	 */
	@Test
	void aRowMovesDownOnlyAsFarAsItMustToClearEveryRowAbove() {
		int[] counts = new int[3]; // rows kept as they were, moved for the row above, moved for a row higher up
		for (boolean rtl : new boolean[] {false, true}) {
			spacing("short middle", new float[] {40, 38, 36, 3, 2, 40, 38, 36}, new int[] {0, 3, 5}, rtl, counts);
			spacing("short middle two", new float[] {60, 55, 4, 60, 55}, new int[] {0, 2, 3}, rtl, counts);
			spacing("two short middles", new float[] {44, 40, 36, 5, 3, 4, 44, 40, 36}, new int[] {0, 3, 4, 6}, rtl, counts);
		}
		spacing("three rows", new float[] {30, 28, 36, 25, 40, 22, 33, 29, 31}, new int[] {0, 3, 6}, false, counts);
		float[] lng = {22, 12, 8, 36, 12, 12, 42, 14, 38, 28, 32, 36, 30, 22, 24, 12, 10, 6, 20, 12, 12, 22, 36, 24};
		spacing("long", lng, ArcLayout.rows(lng, new boolean[lng.length]), false, counts);
		spacing("long rtl", lng, ArcLayout.rows(lng, new boolean[lng.length]), true, counts);
		for (int n : new int[] {9, 14, 20, 30}) {
			float[] w = new float[n];
			for (int i = 0; i < n; i++) {
				w[i] = 8 + (i * 11) % 31;
			}
			spacing("words " + n, w, ArcLayout.rows(w, new boolean[n]), n % 2 == 0, counts);
		}
		System.out.println("rows kept, moved for the row above, moved for a row higher up: " + java.util.Arrays.toString(counts));
		// The check means something only if all three kinds of row came up
		assertTrue(counts[0] > 1000 && counts[1] > 1000 && counts[2] > 5, java.util.Arrays.toString(counts));
	}

	private static void spacing(String name, float[] widths, int[] rowStarts, boolean rtl, int[] counts) {
		boolean[] tight = new boolean[widths.length];
		for (long seed = 0; seed < 600; seed++) {
			long s = ArcLayout.seed((int) seed, name + seed);
			List<ArcLayout.Group> now = ArcLayout.place(widths, rowStarts, SPACE, s, rtl, tight);
			List<ArcLayout.Group> plain = ArcMixedOrderTest.oldPlace(widths, rowStarts, SPACE, s, rtl, tight);
			int prevFirst = 0;
			for (int first = 0, r = 0; first < now.size(); r++) {
				int end = first;
				while (end < now.size() && rowOf(rowStarts, now.get(end).from()) == r) {
					end++;
				}
				if (r > 0) {
					// How much further down this row is than with the plain pitch alone
					float down = now.get(first).y() - now.get(prevFirst).y() - (plain.get(first).y() - plain.get(prevFirst).y());
					float fromAbove = -Float.MAX_VALUE, fromHigher = -Float.MAX_VALUE;
					for (int a = 0; a < first; a++) {
						for (int b = first; b < end; b++) {
							float need = need(now.get(a), widths, now.get(b), down);
							if (a >= prevFirst) {
								fromAbove = Math.max(fromAbove, need);
							} else {
								fromHigher = Math.max(fromHigher, need);
							}
						}
					}
					float expected = Math.max(0, Math.max(fromAbove, fromHigher));
					assertEquals(expected, down, 1e-3f, name + " seed " + seed + " row " + r);
					counts[expected == 0 ? 0 : fromHigher > Math.max(0, fromAbove) ? 2 : 1]++;
				}
				prevFirst = first;
				first = end;
			}
		}
	}

	/**
	 * How far group l, moved up by {@code down}, must go down to keep {@code ROW_MARGIN} clear of group u above it over
	 * the width the two share (each taken as a strip with straight top and bottom edges, as the layout does); negative
	 * when it clears by more, and {@code -MAX_VALUE} when they share no width.
	 */
	private static float need(ArcLayout.Group u, float[] widths, ArcLayout.Group l, float down) {
		float[] ue = strip(u, widths), le = strip(l, widths);
		float lo = Math.max(ue[0], le[0]), hi = Math.min(ue[1], le[1]);
		if (lo >= hi) {
			return -Float.MAX_VALUE;
		}
		float need = -Float.MAX_VALUE;
		for (float x : new float[] {lo, hi}) {
			float bottom = u.y() + (x - u.x()) * ue[2] + ue[3];
			float top = l.y() - down + (x - l.x()) * le[2] - le[3];
			need = Math.max(need, bottom + ArcLayout.ROW_MARGIN - top);
		}
		return need;
	}

	/** Left and right end on screen, slope of the edges, and how far they are above and below the centre line. */
	private static float[] strip(ArcLayout.Group g, float[] widths) {
		float halfWidth = ArcLayout.width(widths, g.from(), g.to(), SPACE) * g.scale() / 2;
		double a = Math.toRadians(g.angle());
		float half = 4f * g.scale();
		float across = halfWidth * (float) Math.cos(a) + half * (float) Math.abs(Math.sin(a));
		return new float[] {g.x() - across, g.x() + across, (float) Math.tan(a), half / (float) Math.cos(a)};
	}
}
