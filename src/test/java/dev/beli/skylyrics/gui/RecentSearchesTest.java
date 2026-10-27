package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class RecentSearchesTest {
	@Test
	void newestFirstWithoutRepeats() {
		List<String> recent = new ArrayList<>(List.of("neon", "skyline"));
		assertEquals(List.of("Skyline", "neon"), LibraryScreen.remember(recent, "Skyline"));
	}

	@Test
	void keepsOnlyTheLatestFew() {
		List<String> recent = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			recent = LibraryScreen.remember(recent, "song " + i);
		}
		assertEquals(LibraryScreen.RECENT, recent.size());
		assertEquals("song 9", recent.getFirst());
	}
}
