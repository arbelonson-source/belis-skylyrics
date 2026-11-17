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

	@Test
	void resultsAreOnlyStaleInTheSearchViewWhileTyping() {
		assertTrue(LibraryKeys.staleResults(true, true));
		assertFalse(LibraryKeys.staleResults(true, false));
		assertFalse(LibraryKeys.staleResults(false, true), "liked songs and playlists don't change as you type");
	}

	@Test
	void aClickRightAfterOpeningAPlaylistIsTheDoubleClicksSecondHalf() {
		assertTrue(LibraryKeys.tooSoonAfterOpen(0));
		assertTrue(LibraryKeys.tooSoonAfterOpen(LibraryKeys.DOUBLE_CLICK_NS - 1));
		assertFalse(LibraryKeys.tooSoonAfterOpen(LibraryKeys.DOUBLE_CLICK_NS));
		assertFalse(LibraryKeys.tooSoonAfterOpen(Long.MAX_VALUE / 2), "a playlist that was never opened");
	}

	@Test
	void theHeaderStopsAreWalkedAndLeftAtTheEnd() {
		assertEquals(1, LibraryKeys.stopMove(0, 5, 1));
		assertEquals(0, LibraryKeys.stopMove(0, 5, -1));
		assertEquals(3, LibraryKeys.stopMove(4, 5, -1));
		assertEquals(-1, LibraryKeys.stopMove(4, 5, 1));
		assertEquals(-1, LibraryKeys.stopMove(0, 0, 1));
	}

	@Test
	void theKeyboardEntersAtTheViewButtonOrTheFirstRecentSearch() {
		assertEquals(2, LibraryKeys.enterStop(true, 2, 4));
		assertEquals(2, LibraryKeys.enterStop(true, 2, -1));
		assertEquals(2, LibraryKeys.enterStop(false, 2, -1));
		assertEquals(4, LibraryKeys.enterStop(false, 2, 4));
	}

	@Test
	void upLeavesTheSongsOnlyFromTheTopRow() {
		var grid = dev.beli.skylyrics.config.SkyLyricsConfig.LibraryView.GRID;
		var shelf = dev.beli.skylyrics.config.SkyLyricsConfig.LibraryView.SHELF;
		var carousel = dev.beli.skylyrics.config.SkyLyricsConfig.LibraryView.CAROUSEL;
		assertTrue(LibraryKeys.atTop(grid, -1, 4, 2));
		assertTrue(LibraryKeys.atTop(grid, 3, 4, 2));
		assertFalse(LibraryKeys.atTop(grid, 4, 4, 2));
		assertTrue(LibraryKeys.atTop(shelf, 4, 4, 2));
		assertFalse(LibraryKeys.atTop(shelf, 5, 4, 2));
		assertTrue(LibraryKeys.atTop(carousel, 7, 4, 2));
		// nothing chosen is at the top in every view
		assertTrue(LibraryKeys.atTop(shelf, -1, 4, 2));
		assertTrue(LibraryKeys.atTop(carousel, -1, 4, 2));
	}

	@Test
	void closingAPlaylistPutsTheKeyboardBackOnIt() {
		assertEquals(3, LibraryKeys.afterClose(3, 10));
		assertEquals(4, LibraryKeys.afterClose(7, 5));
		assertEquals(-1, LibraryKeys.afterClose(-1, 10));
		assertEquals(-1, LibraryKeys.afterClose(2, 0));
	}
}
