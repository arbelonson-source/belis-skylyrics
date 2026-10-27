package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.config.SkyLyricsConfig.Crosshair;
import java.util.List;
import org.junit.jupiter.api.Test;

class CrosshairShapeTest {
	private static final int MIDDLE = CrosshairShape.REACH + 1;

	@Test
	void everyShapeHasAMiddleAndIsSymmetric() {
		for (Crosshair style : Crosshair.values()) {
			if (style == Crosshair.NORMAL) continue;
			boolean[][] on = CrosshairShape.pixels(style);
			assertTrue(on[MIDDLE][MIDDLE], style + " has no middle");
			int side = on.length;
			for (int r = 0; r < side; r++) {
				for (int c = 0; c < side; c++) {
					assertEquals(on[r][c], on[side - 1 - r][c], style + " not symmetric top to bottom");
					assertEquals(on[r][c], on[r][side - 1 - c], style + " not symmetric left to right");
					assertEquals(on[r][c], on[c][r], style + " not symmetric across the diagonal");
				}
			}
		}
	}

	@Test
	void edgeSurroundsTheShapeWithoutCoveringIt() {
		boolean[][] dot = CrosshairShape.pixels(Crosshair.DOT);
		boolean[][] edge = CrosshairShape.edge(dot);
		int lit = 0;
		for (int r = 0; r < dot.length; r++) {
			for (int c = 0; c < dot.length; c++) {
				assertFalse(dot[r][c] && edge[r][c]);
				if (edge[r][c]) lit++;
			}
		}
		// A 3x3 dot gets a 5x5 ring round it
		assertEquals(25 - 9, lit);
	}

	@Test
	void runsCoverEveryLitPixelOnce() {
		boolean[][] cross = CrosshairShape.pixels(Crosshair.CROSS);
		List<int[]> runs = CrosshairShape.runs(cross);
		int lit = 0, covered = 0;
		for (boolean[] row : cross) {
			for (boolean p : row) if (p) lit++;
		}
		for (int[] run : runs) covered += run[2];
		assertEquals(lit, covered);
		// The across arm and the middle are one run each side of the gap, so far fewer runs than pixels
		assertTrue(runs.size() < lit);
	}
}
