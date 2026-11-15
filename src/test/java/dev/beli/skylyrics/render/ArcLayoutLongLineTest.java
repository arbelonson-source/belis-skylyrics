package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

class ArcLayoutLongLineTest {
	/** The row cutting as it was before the prefix sums: re-sums every candidate row (cost about k*n^3). */
	private static int[] oldRows(float[] widths, boolean[] pause, boolean[] tight) {
		int n = widths.length;
		if (n == 0) {
			return new int[0];
		}
		float total = oldWidth(widths, tight, 0, n, ArcLayout.GAP);
		int k = total <= ArcLayout.SINGLE_ROW ? 1 : Math.max(2, (int) Math.ceil(total / ArcLayout.ROW_WIDTH));
		k = Math.min(k, n);
		if (k == 1) {
			return new int[] {0};
		}
		if (k == 2) {
			int bestCut = 1;
			float bestScore = Float.MAX_VALUE;
			for (int c = 1; c < n; c++) {
				float score = Math.max(oldWidth(widths, tight, 0, c, ArcLayout.GAP), oldWidth(widths, tight, c, n, ArcLayout.GAP))
						* (pause[c - 1] ? 0.85f : 1);
				if (score < bestScore) {
					bestScore = score;
					bestCut = c;
				}
			}
			return new int[] {0, bestCut};
		}
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
					float w = oldWidth(widths, tight, j, i, ArcLayout.GAP);
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

	private static float oldWidth(float[] widths, boolean[] tight, int from, int to, float gap) {
		float w = 0;
		for (int i = from; i < to; i++) {
			w += widths[i];
			if (i + 1 < to && (tight == null || !tight[i])) {
				w += gap;
			}
		}
		return w;
	}

	/** What the cutting minimises: the widest row (for two rows, the same with the comma bonus). */
	private static float cost(float[] w, boolean[] pause, boolean[] tight, int[] rows) {
		float worst = 0;
		for (int r = 0; r < rows.length; r++) {
			int to = r + 1 < rows.length ? rows[r + 1] : w.length;
			worst = Math.max(worst, oldWidth(w, tight, rows[r], to, ArcLayout.GAP));
		}
		return rows.length == 2 && pause[rows[1] - 1] ? worst * 0.85f : worst;
	}

	/**
	 * Prefix sums add the widths in another order than the old row-by-row sum, so two cuts that tie in exact
	 * arithmetic can differ by a float rounding step and the old code picked either one. Cuts must match except in
	 * such ties (the cost is equal within 0.001), and ties must be rare.
	 */
	@Test
	void sameCutsAsTheOldAlgorithmOnNormalLines() {
		Random rnd = new Random(20261115);
		int lines = 6000, differ = 0;
		for (int t = 0; t < lines; t++) {
			int n = 1 + rnd.nextInt(60);
			boolean grid = t % 2 == 0;
			float[] w = new float[n];
			boolean[] pause = new boolean[n], tight = new boolean[n];
			boolean cjk = rnd.nextInt(4) == 0;
			for (int i = 0; i < n; i++) {
				w[i] = grid ? 1 + rnd.nextInt(40) : 2 + rnd.nextFloat() * 40;
				pause[i] = rnd.nextInt(6) == 0;
				tight[i] = cjk ? rnd.nextInt(8) != 0 : rnd.nextInt(10) == 0;
			}
			boolean[] tightArg = t % 5 == 0 ? null : tight;
			int[] want = oldRows(w, pause, tightArg), got = ArcLayout.rows(w, pause, tightArg);
			if (!Arrays.equals(want, got)) {
				differ++;
				assertEquals(cost(w, pause, tightArg, want), cost(w, pause, tightArg, got), 1e-3f,
						"line " + t + " widths " + Arrays.toString(w) + " want " + Arrays.toString(want) + " got " + Arrays.toString(got));
			}
		}
		System.out.println("cuts differing from old: " + differ + " of " + lines);
		assertTrue(differ * 100 <= lines, differ + " of " + lines + " lines cut differently");
	}

	@Test
	void fiveThousandWordsAreCutQuickly() {
		Random rnd = new Random(1);
		int n = 5000;
		float[] w = new float[n];
		boolean[] pause = new boolean[n];
		for (int i = 0; i < n; i++) {
			w[i] = 10 + rnd.nextInt(30);
			pause[i] = i % 9 == 8;
		}
		int[][] rows = new int[1][];
		assertTimeoutPreemptively(Duration.ofSeconds(10), () -> rows[0] = ArcLayout.rows(w, pause, null));
		assertSane(rows[0], n, w, null);
	}

	@Test
	void aLongCjkLineIsCutQuickly() {
		int n = 20000;
		float[] w = new float[n];
		boolean[] pause = new boolean[n], tight = new boolean[n];
		Arrays.fill(w, 10.5f);
		Arrays.fill(tight, true);
		int[][] rows = new int[1][];
		assertTimeoutPreemptively(Duration.ofSeconds(10), () -> rows[0] = ArcLayout.rows(w, pause, tight));
		assertSane(rows[0], n, w, tight);
	}

	/** Rows start at 0, strictly rising, and none is much wider than a row should be. */
	private static void assertSane(int[] rows, int n, float[] w, boolean[] tight) {
		assertEquals(0, rows[0]);
		float widest = 0;
		for (int r = 0; r < rows.length; r++) {
			int to = r + 1 < rows.length ? rows[r + 1] : n;
			assertTrue(to > rows[r] && to <= n, "row " + r);
			if (to - rows[r] > 1) {
				widest = Math.max(widest, ArcLayout.width(w, tight, rows[r], to, ArcLayout.GAP));
			}
		}
		float limit = ArcLayout.ROW_WIDTH;
		assertTrue(widest <= limit, "widest row " + widest);
		assertTrue(rows.length >= Math.ceil(ArcLayout.width(w, tight, 0, n, ArcLayout.GAP) / ArcLayout.ROW_WIDTH));
	}
}
