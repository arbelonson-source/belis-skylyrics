package dev.beli.skylyrics.gui;

import static dev.beli.skylyrics.gui.TranslationFitTest.languages;
import static dev.beli.skylyrics.gui.TranslationFitTest.text;
import static dev.beli.skylyrics.gui.TranslationFitTest.width;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Texts outside the settings rows, in every language, measured with the game's own letter widths (see
 * {@link TranslationFitTest}) in the smallest window the game gives (320 wide) and at 426 wide.
 */
class SmallWindowFitTest {
	/** The narrowest screen the game gives at any GUI scale. */
	private static final int SMALLEST = 320;
	/** A 1280x720 window at GUI scale 3. */
	private static final int SMALL = 426;

	@BeforeAll
	static void readTheGameFont() throws IOException {
		TranslationFitTest.readTheGameFont();
	}

	/** The lang key {@code Tr} gives an English text. */
	private static String key(String english) {
		String slug = english.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
		if (slug.length() > 48) {
			slug = slug.substring(0, 48).replaceAll("_+$", "");
		}
		return "skylyrics.ui." + slug;
	}

	/** A text of a language by its English, with {@code %s} filled in, as the game shows it. */
	private static String shown(Map<String, Map<String, String>> all, String lang, String english, Object... args) {
		assertNotNull(all.get("en_us.json").get(key(english)), "no such text: " + english);
		return String.format(Locale.ROOT, text(all, lang, key(english)), args);
	}

	/** The game's pop-up (a SystemToast) is 30 wider than its longer line, at least 160, and stands against the screen's right edge. */
	private static int popUpWidth(int titleW, int bodyW) {
		return Math.max(160, 30 + Math.max(titleW, bodyW));
	}

	@Test
	void theCantReadYourMusicPlayerPopUpStaysOnTheSmallestScreen() throws IOException {
		Map<String, Map<String, String>> all = languages();
		List<String> off = new ArrayList<>();
		int measured = 0;
		for (String lang : all.keySet()) {
			String body = shown(all, lang, "Open the settings (%s) → Music to see why", "J");
			for (String title : List.of(shown(all, lang, "SkyLyrics can't read your music player"),
					shown(all, lang, "SkyLyrics can't reach the internet or Spotify"))) {
				int tw = width(title), bw = width(body);
				if (tw < 0 || bw < 0) {
					continue;
				}
				measured++;
				int w = popUpWidth(tw, bw);
				if (w > SMALLEST) {
					off.add(lang + " " + w + " wide, from \"" + (tw >= bw ? title : body) + "\"");
				}
			}
		}
		assertTrue(measured >= 20, "only " + measured + " pop-ups measured");
		assertTrue(off.isEmpty(), "the pop-up is wider than a " + SMALLEST + " wide screen, so its left end (and some of its text) is off the screen: " + off);
	}
}
