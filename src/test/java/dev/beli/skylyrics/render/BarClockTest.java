package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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

	/** The bar keeps its clock's text through the minute, and makes it again when the minute or the language changes. */
	@Test
	void theClockTurnsWithTheMinute() throws IOException {
		Locale was = Locale.getDefault();
		Locale wasFormat = Locale.getDefault(Locale.Category.FORMAT);
		Locale wasDisplay = Locale.getDefault(Locale.Category.DISPLAY);
		Locale.setDefault(Locale.US);
		try {
			NowPlayingHud hud = new NowPlayingHud(null, null, null);
			// 21:07 on a winter day on this computer's clock, in milliseconds since 1970
			long seven = LocalDate.of(2026, 1, 15).atTime(21, 7).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
			String before = hud.clock(seven - 1);
			assertEquals("9:06 PM", before);
			assertSame(before, hud.clock(seven - 60_000));
			String now = hud.clock(seven);
			assertEquals("9:07 PM", now);
			assertSame(now, hud.clock(seven + 59_999));
			assertEquals("9:08 PM", hud.clock(seven + 60_000));
			// The computer's clock set back a minute
			assertEquals("9:07 PM", hud.clock(seven + 59_999));
			// A day later at the same time, and the game switched to German in the same minute
			assertEquals("9:07 PM", hud.clock(seven + 86_400_000L));
			try (InLanguage game = InLanguage.of("de_de")) {
				assertEquals("21:07", hud.clock(seven + 86_400_000L + 1));
			}
			assertEquals("9:07 PM", hud.clock(seven + 86_400_000L + 2));
		} finally {
			Locale.setDefault(was);
			Locale.setDefault(Locale.Category.FORMAT, wasFormat);
			Locale.setDefault(Locale.Category.DISPLAY, wasDisplay);
		}
	}
}
