package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.config.SkyLyricsConfig;

/**
 * A settings row that cuts its name, its value or its description short with "…" shows the whole of it in a tooltip
 * while the mouse is on the row. Measured with the game's own letter widths, as {@link TranslationFitTest} reads them.
 */
class CutRowTooltipTest {
	/** Letters as wide as the game draws them; only texts of the game's bitmap fonts are measured here. */
	static final class GameFont extends Font {
		GameFont() {
			//? if >=1.21.9 {
			super(null);
			//?} else {
			/*super(id -> null, false);
			*///?}
		}

		@Override
		public int width(String text) {
			return TranslationFitTest.width(text);
		}
	}

	private static final Font FONT = new GameFont();
	/** The settings rows in the smallest window: 320 wide, like a 640 x 480 window at GUI scale 2. */
	private static final int SMALLEST_ROW = SkyLyricsScreen.rowWidth(320);
	private static Map<String, Map<String, String>> all;

	@BeforeAll
	static void readTheGameFont() throws IOException {
		TranslationFitTest.readTheGameFont();
		all = TranslationFitTest.languages();
	}

	/** A text as the game shows it in {@code lang}, or null when it has a letter outside the bitmap fonts. */
	private static String text(String lang, String key) {
		String t = TranslationFitTest.text(all, lang, "skylyrics.ui." + key);
		assertNotNull(t, key);
		return TranslationFitTest.width(t) < 0 ? null : t;
	}

	private static <R extends Controls.Row> R laidOut(R row, int rowW) {
		row.x = 0;
		row.y = 0;
		row.w = rowW;
		row.prepare(FONT);
		return row;
	}

	@Test
	void aSwitchWhoseNameIsCutShowsItInFull() {
		assertEquals(184, SMALLEST_ROW);
		List<String> cutNames = new ArrayList<>();
		// The Display tab's "Song name only on change" (Top bar card), the Look tab's "Flash with the lyrics" (Crosshair card)
		for (String[] keys : new String[][] {{"song_name_only_on_change", "the_song_name_shows_for_a_few_seconds_then_hides"},
				{"flash_with_the_lyrics", "brightens_as_each_new_line_starts"}}) {
			for (String lang : all.keySet()) {
				String name = text(lang, keys[0]);
				String desc = text(lang, keys[1]);
				if (name == null || desc == null) {
					continue;
				}
				Controls.Toggle row = laidOut(new Controls.Toggle(name, desc, () -> true, v -> { }), SMALLEST_ROW);
				// beside the switch the name and the description get the row less the switch and 28 of margins
				int room = row.stacked() ? row.w - 20 : row.w - 28 - row.controlW(FONT);
				List<String> expected = new ArrayList<>();
				if (FONT.width(name) > room) {
					expected.add(name);
					cutNames.add(lang + " " + keys[0]);
				}
				if (FONT.width(desc) > room) {
					expected.add(desc);
				}
				assertEquals(expected, row.cutTexts(FONT), lang + " " + keys[0] + ", " + room + " px of room");
			}
		}
		// measured: in the smallest window these two names end in "…" (Russian 143 px, German 136 px, in 134 of room)
		assertTrue(cutNames.contains("ru_ru.json song_name_only_on_change"), "cut: " + cutNames);
		assertTrue(cutNames.contains("de_de.json flash_with_the_lyrics"), "cut: " + cutNames);
	}

	@Test
	void aStatusLineCutShortShowsInFull() {
		// The Music tab's Status while the demo song plays: too long for beside its name, it goes on a line of its own
		List<String> cutLines = new ArrayList<>();
		int measured = 0;
		for (String lang : all.keySet()) {
			String name = text(lang, "status");
			String line = text(lang, "playing_the_demo_song");
			if (name == null || line == null) {
				continue;
			}
			measured++;
			Controls.Info row = laidOut(new Controls.Info(name, null, () -> line), SMALLEST_ROW);
			int room = row.stacked() ? row.w - 20 : row.controlW(FONT);
			boolean cut = FONT.width(line) > room;
			if (cut) {
				cutLines.add(lang);
			}
			assertEquals(cut ? List.of(line) : List.of(), row.cutTexts(FONT), lang + ", " + room + " px of room");
		}
		assertTrue(measured >= 10, "only " + measured + " languages measured");
		assertTrue(cutLines.contains("es_es.json"), "measured: \"Reproduciendo la canción de prueba\" is cut; cut: " + cutLines);

		// in a full-size window the whole line fits beside its name: no tooltip
		String line = text("es_es.json", "playing_the_demo_song");
		assertEquals(List.of(), laidOut(new Controls.Info(text("es_es.json", "status"), null, () -> line), SkyLyricsScreen.rowWidth(456)).cutTexts(FONT));
	}

	@Test
	void anInfoRowsNameCutBesideItsValueShowsInFull() {
		// The Look tab's Album colour with no song playing: the value keeps its width beside the name, the name is cut
		List<String> cutNames = new ArrayList<>();
		for (String lang : all.keySet()) {
			String name = text(lang, "album_colour");
			String value = text(lang, "no_song_playing");
			if (name == null || value == null) {
				continue;
			}
			Controls.Info row = laidOut(new Controls.Info(name, null, () -> value), SMALLEST_ROW);
			int nameRoom = row.stacked() ? row.w - 20 : row.w - 28 - row.controlW(FONT);
			int valueRoom = row.stacked() ? row.w - 20 : row.controlW(FONT);
			List<String> expected = new ArrayList<>();
			if (FONT.width(name) > nameRoom) {
				expected.add(name);
				cutNames.add(lang);
			}
			if (FONT.width(value) > valueRoom) {
				expected.add(value);
			}
			assertEquals(expected, row.cutTexts(FONT), lang);
		}
		assertTrue(cutNames.contains("ru_ru.json"), "measured: \"Цвет альбома\" is cut; cut: " + cutNames);
	}

	@Test
	void theLookPickersNameStopsBeforeTheLooksName() {
		ThemePicker picker = laidOut(new ThemePicker(new SkyLyricsConfig()), 80);
		assertEquals(List.of(picker.label), picker.cutTexts(FONT), "a row too narrow for both names");
		assertEquals(List.of(), laidOut(new ThemePicker(new SkyLyricsConfig()), SMALLEST_ROW).cutTexts(FONT));
	}
}
