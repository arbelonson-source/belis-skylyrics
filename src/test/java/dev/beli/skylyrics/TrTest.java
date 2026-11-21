package dev.beli.skylyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class TrTest {
	private static final Pattern CALL = Pattern.compile("\\bt\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
	private static final Path LANG = Path.of("src/main/resources/assets/skylyrics/lang");

	private static Map<String, String> lang(String file) throws IOException {
		try (Reader r = Files.newBufferedReader(LANG.resolve(file), StandardCharsets.UTF_8)) {
			JsonObject o = new Gson().fromJson(r, JsonObject.class);
			Map<String, String> m = new LinkedHashMap<>();
			o.entrySet().forEach(e -> m.put(e.getKey(), e.getValue().getAsString()));
			return m;
		}
	}

	private static String unescape(String s) {
		return new Gson().fromJson("\"" + s + "\"", String.class);
	}

	private static Map<String, String> literals() throws IOException {
		Map<String, String> byKey = new HashMap<>();
		try (Stream<Path> files = Files.walk(Path.of("src/client/java"))) {
			for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
				if (p.endsWith("Tr.java")) {
					continue;
				}
				Matcher m = CALL.matcher(Files.readString(p, StandardCharsets.UTF_8));
				while (m.find()) {
					String english = unescape(m.group(1));
					String key = Tr.key(english);
					String old = byKey.put(key, english);
					assertTrue(old == null || old.equals(english), "key clash: " + old + " / " + english);
				}
			}
		}
		return byKey;
	}

	private static final Pattern FORMAT = Pattern.compile("%(?:\\d+\\$)?[sd%]");

	private static List<String> placeholders(String s) {
		List<String> found = new ArrayList<>();
		Matcher m = FORMAT.matcher(s);
		while (m.find()) {
			found.add(m.group());
		}
		Collections.sort(found);
		return found;
	}

	@Test
	void keysAreSlugs() {
		assertEquals("skylyrics.ui.pulse", Tr.key("Pulse"));
		assertEquals("skylyrics.ui.late_move_right_early_left", Tr.key("Late? Move right. Early? Left"));
		String key = Tr.key("Keeps lyrics bright through shaders, clouds and rain, always");
		String slug = key.substring("skylyrics.ui.".length());
		assertTrue(slug.length() <= 48);
		assertFalse(slug.endsWith("_"));
		assertEquals("keeps_lyrics_bright_through_shaders_clouds_and_r", slug);
	}

	@Test
	void everyTextIsInEnglishFile() throws IOException {
		Map<String, String> en = lang("en_us.json");
		Map<String, String> found = literals();
		assertFalse(found.isEmpty());
		found.forEach((k, english) -> assertEquals(english, en.get(k), k));
	}

	@Test
	void noLeftoverTexts() throws IOException {
		Map<String, String> found = literals();
		lang("en_us.json").keySet().stream().filter(k -> k.startsWith("skylyrics.ui."))
				.forEach(k -> assertTrue(found.containsKey(k), "no longer used: " + k));
	}

	@Test
	void everyLanguageHasSameKeysAndPlaceholders() throws IOException {
		Map<String, String> en = lang("en_us.json");
		List<String> files = new ArrayList<>();
		try (Stream<Path> list = Files.list(LANG)) {
			list.map(f -> f.getFileName().toString()).filter(f -> f.endsWith(".json") && !f.equals("en_us.json"))
					.sorted().forEach(files::add);
		}
		assertTrue(files.contains("he_il.json"), "he_il.json missing");
		assertTrue(files.contains("es_es.json"), "es_es.json missing");
		assertTrue(files.contains("de_de.json"), "de_de.json missing");
		assertTrue(files.contains("fr_fr.json"), "fr_fr.json missing");
		for (String file : files) {
			Map<String, String> tr = lang(file);
			for (String k : en.keySet()) {
				assertTrue(tr.containsKey(k), file + ": missing key " + k);
			}
			for (String k : tr.keySet()) {
				assertTrue(en.containsKey(k), file + ": extra key " + k);
			}
			en.forEach((k, v) -> {
				String t = tr.get(k);
				assertFalse(t.isBlank(), file + ": empty value for " + k);
				assertEquals(placeholders(v), placeholders(t), file + ": placeholders differ for " + k);
			});
		}
	}

	@Test
	void regionalCopiesMatchTheirBaseLanguage() throws IOException {
		// Minecraft falls back to en_us (not es_es) for es_mx, de_at, fr_ca etc., so the copies must be byte-identical.
		List<String> files = new ArrayList<>();
		try (Stream<Path> list = Files.list(LANG)) {
			list.map(f -> f.getFileName().toString()).filter(f -> f.endsWith(".json")).sorted().forEach(files::add);
		}
		for (String file : files) {
			String base = file.substring(0, 2);
			// pt_pt is its own European Portuguese translation since 2026-11-17, so pt_br is no copy of it.
			if (file.equals("pt_br.json")) {
				continue;
			}
			if (file.length() == "es_es.json".length() && !file.equals(base + "_" + base + ".json")
					&& files.contains(base + "_" + base + ".json")) {
				assertEquals(Files.readString(LANG.resolve(base + "_" + base + ".json")),
						Files.readString(LANG.resolve(file)), file + " differs from " + base + "_" + base + ".json");
			}
		}
	}

	@Test
	void europeanPortugueseIsNotTheBrazilianCopy() throws IOException {
		Map<String, String> br = lang("pt_br.json"), pt = lang("pt_pt.json");
		long differing = br.keySet().stream().filter(k -> !br.get(k).equals(pt.get(k))).count();
		assertTrue(differing >= 100, "pt_pt.json must stay its own European Portuguese translation; only " + differing + " texts differ from pt_br.json");
		assertEquals("Ecr\u00e3", pt.get("skylyrics.ui.display"), "the Display tab is 'Ecra' in Portugal");
		assertNotEquals(br.get("skylyrics.ui.display"), pt.get("skylyrics.ui.display"));
	}

	@Test
	void russianAndUkrainianAreEachTheirOwnLanguage() throws IOException {
		Map<String, String> ru = lang("ru_ru.json"), uk = lang("uk_ua.json");
		long differing = ru.keySet().stream().filter(k -> !ru.get(k).equals(uk.get(k))).count();
		assertTrue(differing >= 400, "uk_ua.json must be its own Ukrainian translation; only " + differing + " texts differ from ru_ru.json");
		// Letters only one of the two alphabets has: Russian ы э ъ ё, Ukrainian і ї є ґ
		Pattern russianOnly = Pattern.compile("[ыэъёЫЭЪЁ]");
		Pattern ukrainianOnly = Pattern.compile("[іїєґІЇЄҐ]");
		uk.forEach((k, v) -> assertFalse(russianOnly.matcher(v).find(), "uk_ua.json has a Russian letter in " + k + ": " + v));
		ru.forEach((k, v) -> assertFalse(ukrainianOnly.matcher(v).find(), "ru_ru.json has a Ukrainian letter in " + k + ": " + v));
		// The button that opens Minecraft's Controls screen, and Done, use Minecraft's own words
		assertEquals("Управление", ru.get("skylyrics.ui.controls"));
		assertEquals("Керування", uk.get("skylyrics.ui.controls"));
		assertEquals("Готово", ru.get("skylyrics.ui.done"));
		assertEquals("Готово", uk.get("skylyrics.ui.done"));
	}

	@Test
	void keysStayTheSameAsBeforeTheRegexesWereCompiledOnce() {
		String[][] golden = {
				{"Hello World", "skylyrics.ui.hello_world"},
				{"  Leading and trailing  ", "skylyrics.ui.leading_and_trailing"},
				{"Don't stop!", "skylyrics.ui.don_t_stop"},
				{"???", "skylyrics.ui."},
				{"", "skylyrics.ui."},
				{"Already_snake", "skylyrics.ui.already_snake"},
				{"Caf\u00e9 \u00dcBER 42", "skylyrics.ui.caf_ber_42"},
				{"A-b/c", "skylyrics.ui.a_b_c"},
				{"__x__", "skylyrics.ui.x"},
				{"Very long text that exceeds forty eight characters in length for sure", "skylyrics.ui.very_long_text_that_exceeds_forty_eight_characte"},
				{"Seven chars then a break: aaaaaaaaaaaaaaaaa bbbbbbbbbbbbbb", "skylyrics.ui.seven_chars_then_a_break_aaaaaaaaaaaaaaaaa_bbbbb"},
				{"Sync offset (ms)", "skylyrics.ui.sync_offset_ms"},
				{"100% done", "skylyrics.ui.100_done"},
		};
		for (String[] g : golden) {
			assertEquals(g[1], Tr.key(g[0]), g[0]);
		}
	}

	@Test
	void everyEnglishBehindOneShownTextIsKept() {
		Tr.remember("\u05de\u05d9\u05dc\u05d9\u05dd test", "Words test");
		Tr.remember("\u05de\u05d9\u05dc\u05d9\u05dd test", "Lyrics test");
		Tr.remember("\u05de\u05d9\u05dc\u05d9\u05dd test", "Lyrics test");
		assertEquals("Lyrics test Words test", Tr.english("\u05de\u05d9\u05dc\u05d9\u05dd test"));
		assertEquals("plain", Tr.english("plain"));
	}
}
