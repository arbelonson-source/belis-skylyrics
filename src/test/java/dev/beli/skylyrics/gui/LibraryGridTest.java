package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LibraryGridTest {
	@Test
	void coversFillTheRowWithoutSpillingOver() {
		for (int width : new int[] {20, 56, 120, 436, 600}) {
			int cols = LibraryGrid.columns(width);
			int cell = LibraryGrid.cellWidth(width);
			assertTrue(cols >= 1);
			assertTrue(cols * cell + (cols - 1) * LibraryGrid.GAP <= Math.max(width, cell), "width " + width);
			assertTrue(cols == 1 || cell >= LibraryGrid.COVER, "width " + width);
		}
		assertEquals(6, LibraryGrid.columns(436));
	}

	@Test
	void selectionStaysOnTheGrid() {
		assertEquals(-1, LibraryGrid.move(3, 1, 0));
		assertEquals(0, LibraryGrid.move(-1, 7, 10));
		assertEquals(8, LibraryGrid.move(1, 7, 10));
		assertEquals(5, LibraryGrid.move(5, 7, 10));
		assertEquals(0, LibraryGrid.move(0, -1, 10));
	}

	@Test
	void coversPopInWithASmallOvershoot() {
		assertEquals(0, LibraryGrid.popIn(-1));
		assertEquals(1, LibraryGrid.popIn(2));
		float peak = 0;
		for (float t = 0; t <= 1; t += 0.01f) {
			peak = Math.max(peak, LibraryGrid.popIn(t));
		}
		assertTrue(peak > 1 && peak < 1.15f, "peak " + peak);
	}

	@Test
	void placeholderColourIsSteadyPerName() {
		assertEquals(LibraryGrid.hue("Rainy cabin"), LibraryGrid.hue("Rainy cabin"));
		float h = LibraryGrid.hue("anything");
		assertTrue(h >= 0 && h < 1);
	}
}
