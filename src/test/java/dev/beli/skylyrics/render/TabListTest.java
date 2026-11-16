package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TabListTest {
	@Test
	void notShownWithoutTheKey() {
		assertFalse(NowPlayingHud.tabListShown(false, false, 20, true));
	}

	@Test
	void shownOnAServer() {
		assertTrue(NowPlayingHud.tabListShown(true, false, 1, false));
	}

	@Test
	void aloneInASingleplayerWorldItIsNotShown() {
		assertFalse(NowPlayingHud.tabListShown(true, true, 1, false));
	}

	@Test
	void singleplayerWithGuestsOrAListScoreboardShowsIt() {
		assertTrue(NowPlayingHud.tabListShown(true, true, 2, false));
		assertTrue(NowPlayingHud.tabListShown(true, true, 1, true));
	}

	@Test
	void emptyActionBarMessageIsNotShown() {
		assertFalse(NowPlayingHud.actionBarShowing(40, ""));
		assertFalse(NowPlayingHud.actionBarShowing(0, "Now playing"));
		assertTrue(NowPlayingHud.actionBarShowing(40, "Now playing"));
	}
}
