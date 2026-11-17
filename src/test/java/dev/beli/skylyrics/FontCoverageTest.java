package dev.beli.skylyrics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Every smooth lyrics font must draw Cyrillic, Greek and Vietnamese with a bundled font, not the pixel fallback. */
class FontCoverageTest {
	private static final String[] FONTS = {
		"lyrics", "bold", "round", "script", "world", "world_bold", "world_round", "world_script"};
	private static final String[] SAMPLES = {
		"Привет, как дела? Ёлка йод",
		"Γειά σου κόσμε",
		"Tiếng Việt có dấu: ơ ư ầ ẫ ặ"};

	private static InputStream open(String path) {
		InputStream in = FontCoverageTest.class.getResourceAsStream(path);
		if (in == null) throw new IllegalStateException("missing resource " + path);
		return in;
	}

	@Test
	void sampleLinesNeverFallBackToThePixelFont() throws Exception {
		System.setProperty("java.awt.headless", "true");
		List<String> missing = new ArrayList<>();
		for (String name : FONTS) {
			JsonObject json;
			try (var r = new InputStreamReader(open("/assets/skylyrics/font/" + name + ".json"), StandardCharsets.UTF_8)) {
				json = JsonParser.parseReader(r).getAsJsonObject();
			}
			List<Font> fonts = new ArrayList<>();
			for (JsonElement e : json.getAsJsonArray("providers")) {
				JsonObject p = e.getAsJsonObject();
				if (!"ttf".equals(p.get("type").getAsString())) continue;
				String file = p.get("file").getAsString().replace("skylyrics:", "");
				try (InputStream in = open("/assets/skylyrics/font/" + file)) {
					fonts.add(Font.createFont(Font.TRUETYPE_FONT, in));
				}
			}
			for (String line : SAMPLES) {
				line.codePoints().forEach(cp -> {
					if (Character.isWhitespace(cp)) return;
					if (fonts.stream().noneMatch(f -> f.canDisplay(cp)))
						missing.add(name + ": U+" + Integer.toHexString(cp).toUpperCase() + " " + new String(Character.toChars(cp)));
				});
			}
		}
		assertTrue(missing.isEmpty(), "letters with no smooth font: " + missing);
	}
}
