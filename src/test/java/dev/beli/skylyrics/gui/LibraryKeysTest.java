package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LibraryKeysTest {
	@Test
	void spacePlaysTheSelectedSongWhenNotTyping() {
		assertTrue(LibraryKeys.spacePlays(false, 3, 10));
	}

	@Test
	void spaceStaysATypedSpaceWhileTypingOrWithNothingSelected() {
		assertFalse(LibraryKeys.spacePlays(true, 3, 10));
		assertFalse(LibraryKeys.spacePlays(false, -1, 10));
		assertFalse(LibraryKeys.spacePlays(false, 10, 10));
	}

	@Test
	void buttonFocusStepsAndWraps() {
		assertEquals(0, LibraryKeys.nextButton(-1, 2, 1));
		assertEquals(1, LibraryKeys.nextButton(-1, 2, -1));
		assertEquals(1, LibraryKeys.nextButton(0, 2, 1));
		assertEquals(0, LibraryKeys.nextButton(1, 2, 1));
		assertEquals(1, LibraryKeys.nextButton(0, 2, -1));
	}

	@Test
	void hoveringACoverSelectsItAndLeavingKeepsIt() {
		assertEquals(4, LibraryKeys.afterHover(1, 4));
		assertEquals(1, LibraryKeys.afterHover(1, -1));
	}

	@Test
	void shelfHitFindsTheCoverUnderTheMouse() {
		// 2 rows, covers 40 wide with stride 48, row height 60, view at x=10, shelf top 20, scrolled 0
		assertEquals(0, LibraryViews.shelfHit(15, 25, 10, 20, 0, 48, 60, 40, 2, 7));
		assertEquals(1, LibraryViews.shelfHit(15, 85, 10, 20, 0, 48, 60, 40, 2, 7));
		assertEquals(2, LibraryViews.shelfHit(10 + 48 + 5, 25, 10, 20, 0, 48, 60, 40, 2, 7));
		assertEquals(2, LibraryViews.shelfHit(10 + 48 + 5 - 30, 25, 10, 20, 30, 48, 60, 40, 2, 7), "scrolled sideways");
	}

	@Test
	void shelfHitMissesTheGapsAndTheEnds() {
		assertEquals(-1, LibraryViews.shelfHit(10 + 42, 25, 10, 20, 0, 48, 60, 40, 2, 7), "gap between covers");
		assertEquals(-1, LibraryViews.shelfHit(15, 70, 10, 20, 0, 48, 60, 40, 2, 7), "below the cover, above the next row");
		assertEquals(-1, LibraryViews.shelfHit(10 + 3 * 48 + 5, 85, 10, 20, 0, 48, 60, 40, 2, 7), "past the last cover");
		assertEquals(-1, LibraryViews.shelfHit(5, 25, 10, 20, 0, 48, 60, 40, 2, 7), "left of the shelf");
	}
}
