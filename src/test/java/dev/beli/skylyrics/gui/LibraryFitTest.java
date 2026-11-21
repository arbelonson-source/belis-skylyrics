package dev.beli.skylyrics.gui;

import static dev.beli.skylyrics.gui.TranslationFitTest.languages;
import static dev.beli.skylyrics.gui.TranslationFitTest.text;
import static dev.beli.skylyrics.gui.TranslationFitTest.width;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The song library's texts in every language, measured with the game's own letter widths (see {@link TranslationFitTest})
 * in the smallest window the game gives (320 wide) and at 426 wide (a 1280x720 window at GUI scale 3).
 */
class LibraryFitTest {
	private static final int SMALLEST = 320;
	private static final int SMALL = 426;

	@BeforeAll
	static void readTheGameFont() throws IOException {
		TranslationFitTest.readTheGameFont();
	}

	/** A text of a language by its English, with {@code %s} filled in, as the game shows it. */
	private static String shown(Map<String, Map<String, String>> all, String lang, String english, Object... args) {
		String key = all.get("en_us.json").entrySet().stream().filter(e -> e.getValue().equals(english)).map(Map.Entry::getKey)
				.findFirst().orElseThrow(() -> new AssertionError("no such text: " + english));
		return String.format(Locale.ROOT, text(all, lang, key), args);
	}

	@Test
	void aPlaylistCardShowsItsWholeSongCount() throws IOException {
		Map<String, Map<String, String>> all = languages();
		List<String> cut = new ArrayList<>();
		int measured = 0;
		for (int screenW : new int[] {SMALLEST, SMALL}) {
			int cell = LibraryGrid.cellWidth(LibraryScreen.viewWidth(LibraryScreen.panelW(screenW)));
			for (String lang : all.keySet()) {
				// The demo playlists have 7 and 13 songs; Spotify ones can have thousands
				for (int songs : new int[] {1, 2, 7, 13, 100, 1000, 10000}) {
					String words = songs == 1 ? shown(all, lang, "1 song") : shown(all, lang, "%s songs", songs);
					if (width(words) < 0) {
						continue;
					}
					measured++;
					String card = LibraryScreen.songCount(words, songs, cell, TranslationFitTest::width);
					// The whole words (Hebrew says "one song" in letters), or something short with the number in it
					if (width(card) > cell || !card.equals(words) && !card.contains(String.valueOf(songs))) {
						cut.add(screenW + " wide, " + lang + " \"" + words + "\" " + width(words) + " for " + cell);
					}
				}
			}
		}
		assertTrue(measured >= 200, "only " + measured + " counts measured");
		assertTrue(cut.isEmpty(), cut.size() + " playlist cards cut their song count short with \"…\", like: " + cut.subList(0, Math.min(10, cut.size())));
	}
}
