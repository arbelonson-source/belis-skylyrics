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

	@Test
	void escOnAHeaderButtonInsideAPlaylistTakesTheKeyboardBackToThePlaylist() {
		// Up from the playlist's songs onto the Grid button (stop 0) or Carousel (2), then Esc: the playlist closes and the
		// keyboard is on its card again, not left on the button with nothing said
		assertEquals(-1, LibraryKeys.stopAfterClose(0));
		assertEquals(-1, LibraryKeys.stopAfterClose(2));
		assertEquals(-1, LibraryKeys.stopAfterClose(-1));
	}

	@Test
	void theArrowPastTheLastCardGoesOntoTheLineThatSaysWhyTheNextPageFailed() {
		// After the automatic tries, Down from the grid's last row (Right from the shelf's last column) reaches the line
		// that a click tries again, instead of staying on the card with no key to try again
		assertTrue(LibraryKeys.ontoFooter(true, true, true));
		// The arrow still moves to a card, it is the other way, or no page failed: it does what it always did
		assertFalse(LibraryKeys.ontoFooter(true, false, true));
		assertFalse(LibraryKeys.ontoFooter(false, true, true));
		assertFalse(LibraryKeys.ontoFooter(true, true, false));
	}

	@Test
	void enterOnTheLineUnderTheSongsTriesAgain() {
		assertTrue(LibraryKeys.triesAgain(true, false), "Enter on the failed page's line plays the card above it or nothing");
		assertTrue(LibraryKeys.triesAgain(false, true), "the empty list's Try again button");
		assertFalse(LibraryKeys.triesAgain(false, false), "on a card Enter plays or opens it");
	}

	@Test
	void aDoubleClickOnPlaylistsBackStaysOnThePlaylists() {
		// "‹ Playlists" sits where the section tabs are (Search, then Liked songs, then Playlists): the double-click's
		// second click, after the first closed the playlist, landed on Search or Liked songs instead
		int[] tabs = {47, 75, 66};
		int left = 10;
		long now = 0;
		assertEquals(-1, LibraryKeys.sectionAt(30, left, tabs, 4, now + 120_000_000L), "the second click of the double-click");
		assertEquals(-1, LibraryKeys.sectionAt(70, left, tabs, 4, now + 120_000_000L), "on Liked songs too");
		// a click of its own, later, picks the tab
		assertEquals(0, LibraryKeys.sectionAt(30, left, tabs, 4, LibraryKeys.DOUBLE_CLICK_NS));
		assertEquals(1, LibraryKeys.sectionAt(70, left, tabs, 4, LibraryKeys.DOUBLE_CLICK_NS));
	}

	@Test
	void aSectionTabIsPickedWhereItIsDrawn() {
		int[] tabs = {47, 75, 66};
		long never = Long.MAX_VALUE / 2;
		assertEquals(0, LibraryKeys.sectionAt(10, 10, tabs, 4, never));
		assertEquals(0, LibraryKeys.sectionAt(56.9, 10, tabs, 4, never));
		assertEquals(-1, LibraryKeys.sectionAt(58, 10, tabs, 4, never), "the gap between two tabs");
		assertEquals(1, LibraryKeys.sectionAt(61, 10, tabs, 4, never));
		assertEquals(2, LibraryKeys.sectionAt(140, 10, tabs, 4, never));
		assertEquals(-1, LibraryKeys.sectionAt(206, 10, tabs, 4, never), "after the last tab");
		assertEquals(-1, LibraryKeys.sectionAt(9.5, 10, tabs, 4, never), "before the first");
	}

	@Test
	void theWheelSlidingTheShelfUnderAStillMouseChoosesTheCoverUnderIt() {
		// Hovering cover 2 chose it and showed its name; the wheel then slid cover 5 under the mouse, which stayed put:
		// cover 5 lifted for the mouse while cover 2 kept the name, and a click played cover 5, not the named one
		assertEquals(5, LibraryKeys.afterShelfFrame(2, 5, true));
		assertEquals(2, LibraryKeys.afterShelfFrame(2, -1, true), "the gap between two covers keeps the choice, as hovering does");
	}

	@Test
	void aStillMouseLeavesTheKeyboardsChoiceAlone() {
		// Without the wheel (the library just opened under the mouse, or the keys moved on) the cover under a mouse that
		// hasn't moved doesn't take the choice
		assertEquals(2, LibraryKeys.afterShelfFrame(2, 5, false));
		assertEquals(-1, LibraryKeys.afterShelfFrame(-1, 5, false));
	}
}
