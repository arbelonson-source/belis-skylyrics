package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.InLanguage;
import dev.beli.skylyrics.config.SkyLyricsConfig.Placement;
import dev.beli.skylyrics.render.LyricsRenderer;

/** The settings' numbers (Size, Timing, Linger, Floating distance) write their decimals as the game's language does. */
class DecimalMarkTest {
	/** The mod's languages that write a decimal comma; the other four (American English, Swiss German, Mexican Spanish, Hebrew) a point. */
	private static final Set<String> COMMA = Set.of("de_de", "de_at", "es_ar", "es_cl", "es_ec", "es_es", "es_uy", "es_ve",
			"fr_ca", "fr_fr", "pt_br", "pt_pt", "ru_ru", "uk_ua");

	private static final float LINES = LyricsRenderer.skyReach(false, 1, 70, 1920, 1080);

	private static List<String> languages() throws IOException {
		try (Stream<Path> files = Files.list(Path.of("src/main/resources/assets/skylyrics/lang"))) {
			return files.map(f -> f.getFileName().toString().replace(".json", "")).sorted().toList();
		}
	}

	@Test
	void everyLanguageWritesItsOwnDecimalMark() throws IOException {
		List<String> codes = languages();
		assertEquals(18, codes.size(), codes.toString());
		assertTrue(codes.containsAll(COMMA), codes.toString());
		for (String code : codes) {
			// German, French, Russian and the rest read "1.25×" and "+0.25 s", with an English point, among their own words
			String m = COMMA.contains(code) ? "," : ".";
			try (InLanguage game = InLanguage.of(code)) {
				assertEquals("1" + m + "25×", SkyLyricsScreen.times(1.25), code);
				assertEquals("0" + m + "50×", SkyLyricsScreen.times(0.5), code);
				assertEquals("+0" + m + "25 s", SkyLyricsScreen.seconds(250), code);
				assertEquals("-1" + m + "50 s", SkyLyricsScreen.seconds(-1500), code);
				assertEquals("0 s", SkyLyricsScreen.seconds(0), code);
				assertEquals("10" + m + "7", SkyLyricsScreen.distanceBlocks(Placement.FLOATING, 64, 12, LINES), code);
				assertEquals("16" + m + "00", SkyLyricsScreen.distanceBlocks(Placement.FLOATING, 160, 12, LINES), code);
				// Whole numbers have no mark to change
				assertEquals("64", SkyLyricsScreen.distanceBlocks(Placement.SKY, 64, 12, LINES), code);
			}
		}
	}

	@Test
	void theNarratorStillSaysTheUnitAfterADecimalComma() throws IOException {
		try (InLanguage de = InLanguage.of("de_de")) {
			assertEquals("+0,25 Sekunden", Narration.speakable(SkyLyricsScreen.seconds(250), "Prozent", "Grad", "mal", "Sekunden"));
			assertEquals("1,25 mal", Narration.speakable(SkyLyricsScreen.times(1.25), "Prozent", "Grad", "mal", "Sekunden"));
		}
	}
}
