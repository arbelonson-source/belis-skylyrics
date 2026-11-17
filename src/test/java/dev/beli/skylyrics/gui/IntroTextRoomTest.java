package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class IntroTextRoomTest {
	private static final Path LANG = Path.of("src/main/resources/assets/skylyrics/lang");
	private static final String[] KEYS = {
		"skylyrics.ui.play_a_song_in_the_music_or_spotify_app_browsers", // the Mac text, the longest
		"skylyrics.ui.play_a_song_in_spotify_or_your_music_player_and",
		"skylyrics.ui.optional_spotify_web_needs_premium_and_a_free_de",
		"skylyrics.ui.no_music_right_now_try_the_demo_song"};

	/** A little wider than the game's font really is (about 5.5 a character), so a pass here is a pass there. */
	private static int width(String s) {
		return s.length() * 6;
	}

	private static List<String> page(String file, boolean mac) throws IOException {
		try (Reader r = Files.newBufferedReader(LANG.resolve(file), StandardCharsets.UTF_8)) {
			JsonObject o = new Gson().fromJson(r, JsonObject.class);
			return List.of(o.get(KEYS[mac ? 0 : 1]).getAsString(), o.get(KEYS[2]).getAsString(), o.get(KEYS[3]).getAsString());
		}
	}

	@Test
	void theDemoPageTextStopsAboveTheDemoButtonInEveryLanguageAtTheSmallestPanel() throws IOException {
		List<String> files;
		try (Stream<Path> all = Files.list(LANG)) {
			files = all.map(p -> p.getFileName().toString()).filter(f -> f.endsWith(".json")).sorted().toList();
		}
		for (int ph : new int[] {190, IntroScreen.fit(120, 190)}) {
			int py = 4;
			for (String file : files) {
				for (boolean mac : new boolean[] {false, true}) {
					List<IntroScreen.Row> rows = IntroScreen.rows(page(file, mac), 300 - 40, py + IntroScreen.vy(38, ph),
							IntroScreen.textLimit(3, py, ph), IntroTextRoomTest::width);
					assertFalse(rows.isEmpty(), file);
					IntroScreen.Row last = rows.get(rows.size() - 1);
					assertTrue(last.y() + 8 <= IntroScreen.demoY(py, ph), file + " mac=" + mac + " ph=" + ph + ": text ends at "
							+ (last.y() + 8) + ", button starts at " + IntroScreen.demoY(py, ph));
				}
			}
		}
	}

	@Test
	void textThatDoesNotFitEndsInAnEllipsisAndTextThatFitsDoesNot() {
		List<IntroScreen.Row> cut = IntroScreen.rows(List.of("aaa bbb ccc ddd eee fff"), 20, 0, 30, IntroTextRoomTest::width);
		assertEquals(3, cut.size());
		assertTrue(cut.get(2).text().endsWith("…"));
		List<IntroScreen.Row> whole = IntroScreen.rows(List.of("aaa bbb"), 100, 0, 30, IntroTextRoomTest::width);
		assertEquals(1, whole.size());
		assertEquals("aaa bbb", whole.get(0).text());
	}

	@Test
	void focusMovesToTheNearestButtonWhenTheOldOneIsGone() {
		Set<String> first = Set.of("skip", "next");
		Set<String> last = Set.of("back", "settings", "done");
		Set<String> middle = Set.of("back", "next", "look-", "look+");
		assertEquals("next", IntroScreen.focusAfter("back", first));
		assertEquals("next", IntroScreen.focusAfter("skip", middle));
		assertEquals("done", IntroScreen.focusAfter("next", last));
		assertEquals("back", IntroScreen.focusAfter("back", middle));
		assertEquals("next", IntroScreen.focusAfter("next", middle));
		assertEquals(null, IntroScreen.focusAfter(null, middle));
	}
}
