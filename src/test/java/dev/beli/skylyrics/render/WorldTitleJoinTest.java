package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The World layout's song title after joining a world: it floats up once the world shows, not behind its loading screen. */
class WorldTitleJoinTest {
	@Test
	void theSongTitleWaitsUntilTheLoadingScreenIsGone() {
		try {
			ViewLock.joined();
			// The game already draws the world behind its loading screen, lyrics and all: a title put up now spends its few
			// seconds there, and joining a server that takes longer than that to load never shows it
			assertFalse(LyricsRenderer.titleCanShow(true, 1f), "the title was put up behind the loading screen");
			ViewLock.tick(true); // still loading
			assertFalse(LyricsRenderer.titleCanShow(true, 1f));
			ViewLock.tick(false); // the world shows
			assertTrue(LyricsRenderer.titleCanShow(true, 1f));
			// Screens after that (the pause menu, the chat) are no join: the title shows as before
			ViewLock.tick(true);
			assertTrue(LyricsRenderer.titleCanShow(true, 1f));
		} finally {
			ViewLock.tick(false);
			ViewLock.release();
		}
	}

	@Test
	void stillNotBeforeTheLyricsAreInOrWhileTheWordsAreHidden() {
		ViewLock.tick(false);
		assertTrue(LyricsRenderer.titleCanShow(true, 1f));
		assertFalse(LyricsRenderer.titleCanShow(false, 1f));
		assertFalse(LyricsRenderer.titleCanShow(true, 0f));
	}
}
