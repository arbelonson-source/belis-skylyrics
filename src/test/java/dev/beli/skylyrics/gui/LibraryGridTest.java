package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

	private static dev.beli.skylyrics.media.LibraryItem song(String title, String artists) {
		return new dev.beli.skylyrics.media.LibraryItem("id", "spotify:track:id", title, artists, "", "", 0, "");
	}

	private static dev.beli.skylyrics.media.Track playing(String title, String artist) {
		return new dev.beli.skylyrics.media.Track(title, artist, "", 0, "", "spotify");
	}

	@Test
	void theSongPlayingNowIsFoundEvenWhenThePlayerListsArtistsDifferently() {
		assertTrue(LibraryGrid.isPlaying(song("Neon Skyline", "SkyLyrics Demo"), playing("Neon Skyline", "SkyLyrics Demo")));
		assertTrue(LibraryGrid.isPlaying(song("Glass Over", "Ana, The Quiet Birds"), playing("glass over ", "The Quiet Birds")));
		assertTrue(LibraryGrid.isPlaying(song("Glass Over", "Ana, Bo"), playing("Glass Over", "Bo & Ana")));
		assertTrue(LibraryGrid.isPlaying(song("Glass Over", "Ana"), playing("Glass Over", "")));
	}

	@Test
	void otherSongsAreNotMarkedAsPlaying() {
		assertFalse(LibraryGrid.isPlaying(song("Glass Over", "Ana"), playing("Glass Over", "Someone Else")));
		assertFalse(LibraryGrid.isPlaying(song("Glass Over", "Ana"), playing("Lantern Home", "Ana")));
		assertFalse(LibraryGrid.isPlaying(song("Glass Over", "Ana"), dev.beli.skylyrics.media.Track.NONE));
	}

	@Test
	void thePlayingBarsStayInsideTheirBadge() {
		for (int i = 0; i < 3; i++) {
			for (float clock = 0; clock < 5; clock += 0.05f) {
				float h = LibraryGrid.bar(i, clock);
				assertTrue(h >= 0.3f - 1e-6f && h <= 1 + 1e-6f);
			}
		}
	}

	@Test
	void aLongPageDoesNotKeepItsTailHidden() {
		assertEquals(10f, LibraryGrid.revealAt(10f, 0, 0.03f), 1e-4);
		assertEquals(10.09f, LibraryGrid.revealAt(10f, 3, 0.03f), 1e-4);
		assertEquals(10f + LibraryGrid.STAGGER_COVERS * 0.03f, LibraryGrid.revealAt(10f, 500, 0.03f), 1e-4);
	}

	@Test
	void aFailedPageSaysSoWhereTheLoadingLineWas() {
		// 50 liked songs in rows of 6 cards 88 tall, 3 px padding: the view scrolls to the bottom of this height
		int rows = LibraryGrid.contentHeight(50, 6, 88, 3, false, false);
		assertEquals(9 * (88 + LibraryGrid.GAP) - LibraryGrid.GAP + 6, rows);
		assertEquals(rows + LibraryGrid.FOOTER, LibraryGrid.contentHeight(50, 6, 88, 3, true, false));
		// the next page failed: its message (a click on it tries again) takes the Loading line's place, inside what can be scrolled to
		assertEquals(rows + LibraryGrid.FOOTER, LibraryGrid.contentHeight(50, 6, 88, 3, false, true),
				"a failed page's message needs the room under the last row, or it sits below the bottom of the scroll");
		assertEquals(6, LibraryGrid.contentHeight(0, 6, 88, 3, false, false));
	}

	@Test
	void downWithNothingBelowLoadsTheNextPage() {
		// 50 songs in rows of 6: the last row holds 2, so from song 46 (row 7, column 3) Down has nowhere to go
		int cellH = 88, viewH = 236, content = LibraryGrid.contentHeight(50, 6, cellH, 3, false, false);
		assertEquals(45, LibraryGrid.move(45, 6, 50));
		float scroll = 7 * (cellH + LibraryGrid.GAP) + cellH - viewH; // row 7 scrolled into view at the bottom
		assertTrue(LibraryGrid.nearEnd(scroll, viewH, content, cellH, 45, 50, 6),
				"Down is stuck on song 46 of 50, so the next page must load or the list seems to end there");
		// the middle of the list loads nothing yet
		assertFalse(LibraryGrid.nearEnd(0, viewH, content, cellH, 10, 50, 6));
		// scrolled to the bottom by the mouse still loads, with nothing chosen too
		assertTrue(LibraryGrid.nearEnd(content - viewH, viewH, content, cellH, -1, 50, 6));
	}
}
