package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

	@Test
	void signInStripWrapsOnlyWhenTheTextDoesNotFit() {
		assertEquals(16, LibraryViews.bannerStripH(200, 200));
		assertEquals(16, LibraryViews.bannerStripH(0, 50));
		assertEquals(26, LibraryViews.bannerStripH(201, 200));
		assertEquals(26, LibraryViews.bannerStripH(400, 60));
		assertEquals(26, LibraryViews.bannerStripH(400, 30));
		// too little room to wrap into: stay one clipped line, never a splitter at a tiny width
		assertEquals(16, LibraryViews.bannerStripH(400, 29));
		assertEquals(16, LibraryViews.bannerStripH(5, -4));
		assertEquals(16, LibraryViews.bannerStripH(400, 0));
	}

	@Test
	void aShelfChoicePastTheEndOfAShorterListComesBack() {
		// card 10 chosen on two rows, and the list now holds 3: the arrows bring the choice back instead of doing nothing
		assertEquals(2, LibraryViews.shelfMove(9, 1, 0, 2, 3), "Right");
		assertEquals(2, LibraryViews.shelfMove(9, -1, 0, 2, 3), "Left");
		assertEquals(2, LibraryViews.shelfMove(9, 0, -1, 2, 3), "Up");
	}

	@Test
	void theLoadingLineFitsAtTheEndOfTheShelf() {
		// 50 covers on 2 rows, 92 apart, in a shelf 436 wide; "Loading..." is 50 wide
		int width = LibraryViews.shelfWidth(50, 2, 92, 8);
		int left = LibraryViews.shelfMaxScroll(50, 2, 92, 8, 50, 436);
		assertEquals(436, LibraryViews.tailX(0, left, width) + 50,
				"scrolled to the end, the Loading line must end at the shelf's right edge, not run past it");
		// no line: the scroll ends at the last cover; a short shelf doesn't scroll at all
		assertEquals(width - 436, LibraryViews.shelfMaxScroll(50, 2, 92, 8, 0, 436));
		assertEquals(0, LibraryViews.shelfMaxScroll(3, 2, 92, 8, 50, 436));
	}

	@Test
	void aFailedShelfPageSaysSoAfterTheLastColumn() {
		assertEquals("Spotify didn't answer, try again", LibraryViews.tailText(false, "Spotify didn't answer, try again", "Loading..."),
				"a page that failed to load must say so after the last column, not show nothing");
		assertEquals("Loading...", LibraryViews.tailText(true, "", "Loading..."));
		assertEquals("", LibraryViews.tailText(false, "", "Loading..."));
	}

	@Test
	void aClickOnTheFailedShelfPageLineTriesAgain() {
		// covers 176 wide from x 10, not scrolled: the line starts at 194, is 60 wide and drawn at y 50
		assertTrue(LibraryViews.tailHit(200, 53, 10, 0, 176, 60, 50), "a click on the message after the last column tries the page again");
		assertTrue(LibraryViews.tailHit(200 - 100, 53, 10, 100, 176, 60, 50), "scrolled sideways");
		assertFalse(LibraryViews.tailHit(194 + 60 + 10, 53, 10, 0, 176, 60, 50), "past its end");
		assertFalse(LibraryViews.tailHit(200, 80, 10, 0, 176, 60, 50), "below it");
		assertFalse(LibraryViews.tailHit(200, 53, 10, 0, 176, 0, 50), "no line");
	}

	@Test
	void aClickOnAnyLineOfALongFailedShelfPageMessageTriesAgain() {
		// The same message on three lines from y 50: the last is drawn at y 70
		assertTrue(LibraryViews.tailHit(200, 73, 10, 0, 176, 60, 50, 3), "a click on its third line");
		assertFalse(LibraryViews.tailHit(200, 73, 10, 0, 176, 60, 50, 1), "where a one-line message has nothing");
		assertFalse(LibraryViews.tailHit(200, 90, 10, 0, 176, 60, 50, 3), "below its last line");
	}
}
