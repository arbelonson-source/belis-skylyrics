package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LibraryViewsTest {
	@Test
	void middleCoverIsBiggestAndBrightest() {
		assertEquals(1f, LibraryViews.scale(0), 1e-6);
		assertEquals(1f, LibraryViews.alpha(0), 1e-6);
		assertEquals(0f, LibraryViews.spread(0, 100), 1e-6);
		assertEquals(1f, LibraryViews.squash(0), 1e-6);
	}

	@Test
	void neighboursShrinkDimAndMirror() {
		for (float o = 0; o < 3; o += 0.25f) {
			assertTrue(LibraryViews.scale(o + 0.25f) <= LibraryViews.scale(o));
			assertTrue(LibraryViews.alpha(o + 0.25f) <= LibraryViews.alpha(o));
			assertTrue(LibraryViews.spread(o + 0.25f, 100) >= LibraryViews.spread(o, 100));
		}
		assertEquals(-LibraryViews.spread(1.5f, 80), LibraryViews.spread(-1.5f, 80), 1e-6);
		assertEquals(LibraryViews.alpha(2f), LibraryViews.alpha(-2f), 1e-6);
		assertEquals(0f, LibraryViews.alpha(3f), 1e-6);
		assertEquals(0.4f, LibraryViews.scale(10), 1e-6);
		assertEquals(0.8f, LibraryViews.squash(5), 1e-6);
	}

	@Test
	void shelfFillsColumnsTopToBottom() {
		assertEquals(3, LibraryViews.shelfRows(300, 100));
		assertEquals(1, LibraryViews.shelfRows(40, 100));
		assertEquals(0, LibraryViews.shelfCol(2, 3));
		assertEquals(1, LibraryViews.shelfCol(3, 3));
		assertEquals(2, LibraryViews.shelfRow(5, 3));
		assertEquals(4, LibraryViews.shelfIndex(1, 1, 3, 10));
		assertEquals(-1, LibraryViews.shelfIndex(3, 1, 3, 10));
		assertEquals(-1, LibraryViews.shelfIndex(0, 3, 3, 10));
		assertEquals(0, LibraryViews.shelfWidth(0, 3, 90, 10));
		assertEquals(2 * 90 - 10, LibraryViews.shelfWidth(4, 3, 90, 10));
	}

	@Test
	void shelfSelectionMovesAndStaysInside() {
		assertEquals(-1, LibraryViews.shelfMove(2, 1, 0, 3, 0));
		assertEquals(0, LibraryViews.shelfMove(-1, 1, 0, 3, 7));
		assertEquals(3, LibraryViews.shelfMove(0, 1, 0, 3, 7));
		assertEquals(0, LibraryViews.shelfMove(0, -1, 0, 3, 7));
		assertEquals(1, LibraryViews.shelfMove(0, 0, 1, 3, 7));
		assertEquals(2, LibraryViews.shelfMove(2, 0, 1, 3, 7));
		assertEquals(6, LibraryViews.shelfMove(5, 1, 0, 3, 7));
		assertEquals(6, LibraryViews.shelfMove(3, 1, 0, 3, 7));
		assertEquals(6, LibraryViews.shelfMove(6, 1, 0, 3, 7));
	}

	@Test
	void revealScrollsTheLeastNeeded() {
		assertEquals(50f, LibraryViews.reveal(50, 60, 20, 100), 1e-6);
		assertEquals(40f, LibraryViews.reveal(50, 40, 20, 100), 1e-6);
		assertEquals(80f, LibraryViews.reveal(50, 160, 20, 100), 1e-6);
	}

	@Test
	void easingGetsThereAtAnyFrameRate() {
		float slow = 0, fast = 0;
		for (int i = 0; i < 30; i++) {
			slow = LibraryViews.ease(slow, 5, 1f / 30, 14);
		}
		for (int i = 0; i < 120; i++) {
			fast = LibraryViews.ease(fast, 5, 1f / 120, 14);
		}
		assertEquals(slow, fast, 0.05f);
		assertEquals(5f, slow, 0.05f);
	}
}
