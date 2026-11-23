package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.InLanguage;

/** The top bar's clock: 12 or 24 hours as the game's language has them, whatever language the computer is set to. */
class BarClockTest {
	private static final LocalTime EVENING = LocalTime.of(21, 7);

	/** The clock at {@code time} with Minecraft in {@code language} on a computer set to {@code computer}. */
	private static String clock(String language, Locale computer, LocalTime time) throws IOException {
		Locale was = Locale.getDefault();
		Locale wasFormat = Locale.getDefault(Locale.Category.FORMAT);
		Locale wasDisplay = Locale.getDefault(Locale.Category.DISPLAY);
		Locale.setDefault(computer);
		try (InLanguage game = InLanguage.of(language)) {
			return NowPlayingHud.clockText(time);
		} finally {
			Locale.setDefault(was);
			Locale.setDefault(Locale.Category.FORMAT, wasFormat);
			Locale.setDefault(Locale.Category.DISPLAY, wasDisplay);
		}
	}

	@Test
	void aGameInAnotherLanguageShowsItsOwnHoursOnAnAmericanComputer() throws IOException {
		// Minecraft in German, French, Russian or Hebrew on a computer set to American English: the bar said "9:07 PM"
		// next to the German, French, Russian or Hebrew words
		for (String code : List.of("de_de", "de_at", "de_ch", "fr_fr", "ru_ru", "uk_ua", "he_il", "pt_br", "pt_pt", "es_es")) {
			assertEquals("21:07", clock(code, Locale.US, EVENING), code);
		}
		assertEquals("09:07", clock("de_de", Locale.US, LocalTime.of(9, 7)));
		assertEquals("9:07", clock("he_il", Locale.US, LocalTime.of(9, 7)));
	}

	@Test
	void noLanguageButAmericanEnglishSaysPmOnAnAmericanComputer() throws IOException {
		List<String> codes;
		try (Stream<Path> files = Files.list(Path.of("src/main/resources/assets/skylyrics/lang"))) {
			codes = files.map(f -> f.getFileName().toString().replace(".json", "")).sorted().toList();
		}
		assertEquals(18, codes.size(), codes.toString());
		for (String code : codes) {
			String shown = clock(code, Locale.US, EVENING);
			assertTrue(shown.startsWith("21:07") || shown.startsWith("9:07") || shown.startsWith("21 h 07"), code + ": " + shown);
			// The Spanish of the Americas has its own "p. m.", and none has a space the fonts lack
			assertEquals(code.equals("en_us"), shown.contains("PM"), code + ": " + shown);
			assertFalse(shown.contains(" ") || shown.contains(" "), code + ": " + shown);
		}
	}

	@Test
	void inAmericanEnglishTheClockStaysTheComputers() throws IOException {
		// American English is the game's default, so it says little about where the player is: the computer decides, as
		// before, and a German computer keeps its 24 hours
		assertEquals("21:07", clock("en_us", Locale.GERMANY, EVENING));
		assertEquals("9:07 PM", clock("en_us", Locale.US, EVENING));
	}
}
