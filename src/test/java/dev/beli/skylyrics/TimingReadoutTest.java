package dev.beli.skylyrics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Bidi;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.gui.SkyLyricsScreen;

/** The message above the hotbar when a timing key is pressed ("Timing for this song: +0.10 s"), in every language. */
class TimingReadoutTest {
	private static final Path LANG = Path.of("src/main/resources/assets/skylyrics/lang");

	/**
	 * What the game shows left to right. Vanilla lays out every message through ICU's Bidi; in a right-to-left game
	 * language (Hebrew) with the paragraph level "default right-to-left" (127): the first strong letter decides the
	 * direction, right-to-left when there is none. For a left-to-right language the first letter is a Latin or Cyrillic
	 * one, so the same call gives what the game shows there too.
	 */
	private static String shown(String logical) {
		Bidi bidi = new Bidi(logical, Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT);
		int n = bidi.getRunCount();
		byte[] levels = new byte[n];
		Integer[] runs = new Integer[n];
		for (int i = 0; i < n; i++) {
			levels[i] = (byte) bidi.getRunLevel(i);
			runs[i] = i;
		}
		Bidi.reorderVisually(levels, 0, runs, 0, n);
		StringBuilder out = new StringBuilder();
		for (int r : runs) {
			String run = logical.substring(bidi.getRunStart(r), bidi.getRunLimit(r));
			out.append((bidi.getRunLevel(r) & 1) == 1 ? new StringBuilder(run).reverse() : run);
		}
		return out.toString();
	}

	private static String message(String file) throws IOException {
		try (Reader r = Files.newBufferedReader(LANG.resolve(file), StandardCharsets.UTF_8)) {
			return new Gson().fromJson(r, JsonObject.class).get("skylyrics.timing.song").getAsString();
		}
	}

	@Test
	void theSignAndUnitStayWithTheNumberInEveryLanguage() throws IOException {
		List<String> files;
		try (Stream<Path> list = Files.list(LANG)) {
			files = list.map(f -> f.getFileName().toString()).filter(f -> f.endsWith(".json")).sorted().toList();
		}
		assertTrue(files.contains("he_il.json"), "he_il.json missing");
		for (String file : files) {
			String template = message(file);
			for (int ms : new int[] {100, -300, 0, 5000, -5000}) {
				String readout = SkyLyricsScreen.seconds(ms);
				String line = template.replace("%1$s", readout).replace("%s", readout);
				// Hebrew without care: "s 0.10+" left of the words, the plus and the unit on the wrong sides of the number
				assertTrue(shown(line).contains(readout), file + " shows \"" + shown(line) + "\" for " + readout);
			}
		}
	}
}
