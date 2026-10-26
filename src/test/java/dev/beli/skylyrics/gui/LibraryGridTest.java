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
}
