package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Texts that sit in a spot of a fixed size, in every language, measured with Minecraft's own letter widths: the
 * bitmap fonts of the game's default font, read from the game jar and measured the way the game measures them. A text
 * with a letter those fonts don't have (Arabic or Chinese, drawn with the Unicode font) is left out, as its width
 * can't be told here.
 */
class TranslationFitTest {
	private static final Path LANG = Path.of("src/main/resources/assets/skylyrics/lang");
	private static final Map<Integer, Integer> ADVANCE = new HashMap<>();

	private static JsonObject json(String path) throws IOException {
		try (Reader r = new InputStreamReader(open(path), StandardCharsets.UTF_8)) {
			return new Gson().fromJson(r, JsonObject.class);
		}
	}

	private static InputStream open(String path) {
		InputStream in = TranslationFitTest.class.getResourceAsStream(path);
		if (in == null) {
			throw new IllegalStateException("not on the test classpath: " + path);
		}
		return in;
	}

	@BeforeAll
	static void readTheGameFont() throws IOException {
		System.setProperty("java.awt.headless", "true");
		for (JsonElement e : json("/assets/minecraft/font/include/space.json").getAsJsonArray("providers")) {
			JsonObject p = e.getAsJsonObject();
			if ("space".equals(p.get("type").getAsString())) {
				p.getAsJsonObject("advances").entrySet().forEach(a -> ADVANCE.putIfAbsent(a.getKey().codePointAt(0), a.getValue().getAsInt()));
			}
		}
		// Like the game: the first font that has a letter wins, a cell's width is its rightmost column with any pixel
		for (JsonElement e : json("/assets/minecraft/font/include/default.json").getAsJsonArray("providers")) {
			JsonObject p = e.getAsJsonObject();
			if (!"bitmap".equals(p.get("type").getAsString())) {
				continue;
			}
			String file = p.get("file").getAsString();
			BufferedImage image;
			try (InputStream in = open("/assets/minecraft/textures/" + file.substring(file.indexOf(':') + 1))) {
				image = ImageIO.read(in);
			}
			List<int[]> rows = new ArrayList<>();
			p.getAsJsonArray("chars").forEach(row -> rows.add(row.getAsString().codePoints().toArray()));
			int cellW = image.getWidth() / rows.get(0).length;
			int cellH = image.getHeight() / rows.size();
			float scale = (float) (p.has("height") ? p.get("height").getAsInt() : 8) / cellH;
			for (int r = 0; r < rows.size(); r++) {
				for (int c = 0; c < rows.get(r).length; c++) {
					int cp = rows.get(r)[c];
					if (cp == 0 || ADVANCE.containsKey(cp)) {
						continue;
					}
					int used = 0;
					columns:
					for (int x = cellW - 1; x >= 0; x--) {
						for (int y = 0; y < cellH; y++) {
							if ((image.getRGB(c * cellW + x, r * cellH + y) >>> 24) != 0) {
								used = x + 1;
								break columns;
							}
						}
					}
					ADVANCE.put(cp, (int) (0.5 + used * scale) + 1);
				}
			}
		}
	}

	/** The width the game gives {@code text}, or -1 when it has a letter outside the bitmap fonts. */
	static int width(String text) {
		int w = 0;
		for (int cp : text.codePoints().toArray()) {
			Integer a = ADVANCE.get(cp);
			if (a == null) {
				return -1;
			}
			w += a;
		}
		return w;
	}

	/** Every language file by name, each as key to text. */
	static Map<String, Map<String, String>> languages() throws IOException {
		Map<String, Map<String, String>> all = new TreeMap<>();
		try (Stream<Path> list = Files.list(LANG)) {
			for (Path f : (Iterable<Path>) list.filter(f -> f.toString().endsWith(".json"))::iterator) {
				try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
					Map<String, String> texts = new HashMap<>();
					new Gson().fromJson(r, JsonObject.class).entrySet().forEach(e -> texts.put(e.getKey(), e.getValue().getAsString()));
					all.put(f.getFileName().toString(), texts);
				}
			}
		}
		return all;
	}

	@Test
	void theGameFontIsMeasuredLikeTheGameDoes() {
		assertEquals(32, width("Lyrics"));
		assertEquals(24, width("Done"));
		assertEquals(4, width(" "));
		assertEquals(9, width("ilt"), "the narrow letters");
		assertEquals(6, width("é"), "accented letters come from their own font");
		assertEquals(6, width("מ"), "Hebrew has bitmap letters too");
		assertEquals(-1, width("歌"), "Chinese is drawn with the Unicode font");
	}

	@Test
	void theLyricsSwitchUnderTheTabsKeepsItsNameBesideIt() throws IOException {
		Controls.Toggle toggle = new Controls.Toggle("Lyrics", null, () -> true, v -> { });
		List<String> stacked = new ArrayList<>();
		int measured = 0;
		for (Map.Entry<String, Map<String, String>> lang : languages().entrySet()) {
			String name = lang.getValue().get("skylyrics.ui.lyrics");
			int w = width(name);
			if (w < 0) {
				continue;
			}
			measured++;
			if (Controls.Row.stacks(SkyLyricsScreen.MASTER_TOGGLE_W, toggle.controlWidth(null), w, 0)) {
				stacked.add(lang.getKey() + " \"" + name + "\" " + w + "px");
			}
		}
		assertTrue(measured >= 5, "only " + measured + " languages measured");
		assertTrue(stacked.isEmpty(), "the switch would drop under its name, below the window: " + stacked);
	}

	@Test
	void theSongSearchHintFitsItsBoxUncut() throws IOException {
		List<String> cut = new ArrayList<>();
		int measured = 0;
		for (Map.Entry<String, Map<String, String>> lang : languages().entrySet()) {
			String hint = lang.getValue().get("skylyrics.ui.search_songs");
			int w = width(hint);
			if (w < 0) {
				continue;
			}
			measured++;
			// LibraryScreen shows the end of a text wider than this, as it does while typing
			if (w > LibraryScreen.SEARCH_W - 20) {
				cut.add(lang.getKey() + " \"" + hint + "\" " + w + "px");
			}
		}
		assertTrue(measured >= 5, "only " + measured + " languages measured");
		assertTrue(cut.isEmpty(), "the search box would show only the end of: " + cut);
	}

	/** The narrowest screen the game gives at any GUI scale (it picks a smaller scale before going under 320). */
	private static final int SMALLEST_SCREEN = 320;

	/** A text of a language, or the English one when that language doesn't have it (as the game shows it). */
	static String text(Map<String, Map<String, String>> all, String lang, String key) {
		String t = all.get(lang).get(key);
		return t != null ? t : all.get("en_us.json").get(key);
	}

	@Test
	void resetAllNeverRunsUnderDoneInTheSmallestWindow() throws IOException {
		Map<String, Map<String, String>> all = languages();
		int pw = SMALLEST_SCREEN - 16; // the settings panel leaves 8 on each side
		List<String> under = new ArrayList<>();
		int measured = 0;
		for (String lang : all.keySet()) {
			int done = width(text(all, lang, "skylyrics.ui.done"));
			for (String key : List.of("skylyrics.ui.reset_all", "skylyrics.ui.click_again_to_reset")) {
				String reset = text(all, lang, key);
				int w = width(reset);
				if (w < 0 || done < 0) {
					continue;
				}
				measured++;
				int[] b = SkyLyricsScreen.footerButtons(pw, w, Controls.buttonW(done, 56));
				if (b[0] + b[1] > b[2]) {
					under.add(lang + " \"" + reset + "\" reaches " + (b[0] + b[1]) + ", Done starts at " + b[2]);
				}
			}
		}
		assertTrue(measured >= 10, "only " + measured + " texts measured");
		assertTrue(under.isEmpty(), "Reset all runs under Done, so a click on Done resets every setting: " + under);
	}

	@Test
	void thePreviewHintFitsTheSmallestWindow() throws IOException {
		Map<String, Map<String, String>> all = languages();
		net.minecraft.client.StringSplitter splitter = new net.minecraft.client.StringSplitter((cp, style) -> ADVANCE.getOrDefault(cp, 0).floatValue());
		List<String> off = new ArrayList<>();
		int measured = 0;
		for (String lang : all.keySet()) {
			for (String key : List.of("skylyrics.ui.nothing_is_playing_turn_on_the_demo_song_on_the",
					"skylyrics.ui.previewing_click_or_press_any_key_to_go_back")) {
				String hint = text(all, lang, key);
				if (width(hint) < 0) {
					continue;
				}
				measured++;
				List<String> lines = SkyLyricsScreen.previewLines(splitter, hint, SMALLEST_SCREEN);
				assertEquals(hint.replaceAll("\\s", ""), String.join("", lines).replaceAll("\\s", ""), lang + ": every word is shown");
				for (String line : lines) {
					// the pill is 24 wider than its text and keeps 8 from each edge of the screen
					if (width(line) + 24 > SMALLEST_SCREEN - 16) {
						off.add(lang + " \"" + line + "\" " + (width(line) + 24) + "px");
					}
				}
			}
		}
		assertTrue(measured >= 10, "only " + measured + " texts measured");
		assertTrue(off.isEmpty(), "the preview hint runs off the sides of a " + SMALLEST_SCREEN + " wide screen: " + off);
	}

	/** The lang key Tr gives a one- or two-word English text, like "skylyrics.ui.dreamy". */
	private static String key(String english) {
		return "skylyrics.ui." + english.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
	}

	@Test
	void everyLookIsFoundByItsNameInEveryLanguage() throws IOException {
		Map<String, Map<String, String>> all = languages();
		List<String> missed = new ArrayList<>();
		for (String lang : all.keySet()) {
			// the look picker's search words in that language: each look's name as the game shows it there
			String shown = ThemePicker.names(t -> text(all, lang, key(t.name().get())));
			for (dev.beli.skylyrics.config.Themes.Theme theme : dev.beli.skylyrics.config.Themes.ALL) {
				String name = all.get(lang).get(key(theme.name().get()));
				if (name == null || !SettingsSearch.matches(shown, name)) {
					missed.add(lang + " " + theme.id() + " \"" + name + "\"");
				}
			}
		}
		assertTrue(missed.isEmpty(), "a look's name isn't found: " + missed);
		// typed the way people do: without ё, without accents
		Map<String, String> ru = all.get("ru_ru.json");
		assertTrue(SettingsSearch.matches(ThemePicker.names(t -> ru.get(key(t.name().get()))), "грезы"), "Грёзы");
		Map<String, String> de = all.get("de_de.json");
		assertTrue(SettingsSearch.matches(ThemePicker.names(t -> de.get(key(t.name().get()))), "vertraumt"), "Verträumt");
	}

	@Test
	void aHoveredDescriptionStaysOnScreenAtBothEdges() throws IOException {
		Map<String, Map<String, String>> all = languages();
		net.minecraft.client.StringSplitter splitter = new net.minecraft.client.StringSplitter((cp, style) -> ADVANCE.getOrDefault(cp, 0).floatValue());
		List<String> off = new ArrayList<>();
		int measured = 0;
		for (int screenW : new int[] {SMALLEST_SCREEN, 427, 480}) {
			for (String lang : all.keySet()) {
				for (String text : all.get(lang).values()) {
					if (width(text) < 0) {
						continue;
					}
					measured++;
					List<String> lines = SkyLyricsScreen.tooltipLines(splitter, text, screenW);
					assertEquals(text.replaceAll("\\s", ""), String.join("", lines).replaceAll("\\s", ""), lang + ": every word is shown");
					int w = lines.stream().mapToInt(TranslationFitTest::width).max().orElse(0);
					for (int mx : new int[] {0, screenW / 3, screenW / 2, screenW - 1}) {
						int[] at = SkyLyricsScreen.tooltipPos(screenW, 240, mx, 120, w, 10 * lines.size());
						// the tooltip's box reaches 4 past its text on each side
						if (at[0] - 4 < 0 || at[0] + w + 4 > screenW) {
							off.add(screenW + " wide, mouse at " + mx + ": " + lang + " \"" + Ui.clipBy(text, 120, TranslationFitTest::width) + "\" "
									+ (at[0] - 4) + " to " + (at[0] + w + 4));
						}
					}
				}
			}
		}
		assertTrue(measured >= 1000, "only " + measured + " texts measured");
		assertTrue(off.isEmpty(), off.size() + " tooltips run off the screen, like: " + off.subList(0, Math.min(6, off.size())));
	}
}
