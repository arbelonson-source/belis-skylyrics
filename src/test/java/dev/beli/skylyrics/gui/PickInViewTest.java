package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

/**
 * A card or row picked with the keyboard stays in view when the window is resized: before, the scroll was only kept
 * inside the content, and the pick could sit below the view until the next key (seen in a test game: a card picked far
 * down the library's grid at 1920x1080, then the window made 640x480).
 */
class PickInViewTest {
	/** The demo library's songs, as in the test game. */
	private static final int SONGS = 24;

	/** The grid at a screen this big (in GUI units): columns, a card's height with its text, and the view's height. */
	private record Grid(int cols, int cellH, int viewH) {
		static Grid at(int screenW, int screenH) {
			int viewW = LibraryScreen.viewWidth(LibraryScreen.panelW(screenW));
			return new Grid(LibraryGrid.columns(viewW), LibraryScreen.cellHeight(viewW), LibraryScreen.viewHeight(screenH, 0));
		}

		float maxScroll() {
			return Math.max(0, LibraryGrid.contentHeight(SONGS, cols, cellH, 3, false, false) - viewH);
		}

		/** Whether card {@code i} is wholly in view with the grid scrolled to {@code scroll}. */
		boolean shows(int i, float scroll) {
			int y = (i / cols) * (cellH + LibraryGrid.GAP);
			return y >= scroll && y + cellH <= scroll + viewH;
		}
	}

	/** One frame of the library's grid: the pick followed after a resize, then the scroll kept inside the content. */
	private static float frame(PickInView picks, int screenW, int screenH, float target, int picked) {
		Grid grid = Grid.at(screenW, screenH);
		if (picks.follow(screenW, screenH)) {
			target = LibraryGrid.reveal(target, picked, grid.cols(), grid.cellH(), grid.viewH());
		}
		return Mth.clamp(target, 0, grid.maxScroll());
	}

	@Test
	void aCardPickedFarDownStaysInViewWhenTheWindowShrinks() {
		// 1920x1080 at GUI scale 2, six columns: Down three times and Right twice reach card 14 (the third row)
		Grid big = Grid.at(960, 540);
		assertEquals(6, big.cols());
		PickInView picks = new PickInView();
		int picked = 14;
		float target = frame(picks, 960, 540, 0, picked);
		picks.keyPicked();
		target = LibraryGrid.reveal(target, picked, big.cols(), big.cellH(), big.viewH());
		target = frame(picks, 960, 540, target, picked);
		assertTrue(big.shows(picked, target), "in view before the resize");

		// 640x480 at GUI scale 2: four columns, so the card is now in the fourth row, and a shorter view
		Grid small = Grid.at(320, 240);
		assertEquals(4, small.cols());
		assertFalse(small.shows(picked, Mth.clamp(target, 0, small.maxScroll())), "kept inside the content alone, it is out of view");
		target = frame(picks, 320, 240, target, picked);
		assertTrue(small.shows(picked, target), "brought back into view after the resize, scroll " + target);
	}

	@Test
	void aMouseUsersScrollStaysWhereItIs() {
		PickInView picks = new PickInView();
		picks.keyPicked();
		assertFalse(picks.follow(960, 540), "not on the first frame");
		picks.scrolledAway();
		assertFalse(picks.follow(320, 240), "the mouse picked or scrolled last");
		float target = frame(picks, 960, 540, 120, 14);
		assertEquals(120f, target, "the scroll the mouse left is kept");
	}

	@Test
	void followsOncePerResizeAndOnlyWhenTheSizeChanged() {
		PickInView picks = new PickInView();
		picks.keyPicked();
		assertFalse(picks.follow(960, 540));
		assertFalse(picks.follow(960, 540), "same size");
		assertTrue(picks.follow(320, 240), "narrower and shorter");
		assertFalse(picks.follow(320, 240), "once per resize: after that the keys and the mouse scroll as usual");
		assertTrue(picks.follow(320, 500), "only taller");
		assertTrue(picks.follow(640, 500), "only wider");
	}

	@Test
	void aSettingsRowPickedWithTheKeysStaysInView() {
		// A row the keyboard is on, near the bottom of a 960x540 settings panel's view (200 tall), in view at the top
		PickInView picks = new PickInView();
		picks.follow(960, 540);
		picks.keyPicked();
		int top = 150, bottom = 170;
		float target = Focus.scrollIntoView(0, top, bottom, 200, 14, 400);
		assertEquals(0f, target);
		// 320x240: the panel's view is 152 tall and the narrower rows stack, so the row moves down to 190..210
		int viewH = 152;
		top = 190;
		bottom = 210;
		if (picks.follow(320, 240)) {
			target = Focus.scrollIntoView(target, top, bottom, viewH, 14, 400);
		}
		assertTrue(top >= target && bottom <= target + viewH, "the row shows in the shorter view, scroll " + target);
	}
}
