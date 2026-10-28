package dev.beli.skylyrics;

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
import java.util.HashMap;
import java.util.LinkedHashMap;
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

	private static int placeholders(String s) {
		int n = 0;
		for (int i = s.indexOf("%s"); i >= 0; i = s.indexOf("%s", i + 2)) {
			n++;
		}
		return n;
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
	void hebrewHasSameKeysAndPlaceholders() throws IOException {
		Map<String, String> en = lang("en_us.json");
		Map<String, String> he = lang("he_il.json");
		assertEquals(en.keySet(), he.keySet());
		en.forEach((k, v) -> assertEquals(placeholders(v), placeholders(he.get(k)), k));
	}
}
