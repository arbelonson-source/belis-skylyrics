package dev.beli.skylyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Game languages that say nothing about how the player writes numbers or reads a clock: Minecraft's joke and made-up
 * languages, and those Java has no times for. Their numbers and clock must not come from a stranger's locale.
 */
class PlacelessLanguageTest {
	/** Minecraft's joke and made-up languages, and real ones Java has no times for (Classical Chinese, Interslavic). */
	private static final List<String> SAYS_NOTHING = List.of("en_pt", "en_ud", "lol_us", "enws", "enp", "tlh_aa", "qya_aa",
			"isv", "lzh");

	private static String number(String code, String pattern, double value) {
		String was = Formats.testLanguage;
		Formats.testLanguage = code;
		try {
			return Formats.number(pattern, value);
		} finally {
			Formats.testLanguage = was;
		}
	}

	/** Whose clock the bar follows with Minecraft in {@code code} on a computer set to {@code computer}. */
	private static Locale clockLocale(String code, Locale computer) {
		Locale was = Locale.getDefault();
		Locale wasFormat = Locale.getDefault(Locale.Category.FORMAT);
		Locale wasDisplay = Locale.getDefault(Locale.Category.DISPLAY);
		String wasLanguage = Formats.testLanguage;
		Locale.setDefault(computer);
		Formats.testLanguage = code;
		try {
			return Formats.clockLocale();
		} finally {
			Formats.testLanguage = wasLanguage;
			Locale.setDefault(was);
			Locale.setDefault(Locale.Category.FORMAT, wasFormat);
			Locale.setDefault(Locale.Category.DISPLAY, wasDisplay);
		}
	}

	/** 21:07 as the bar writes it in {@code locale}. */
	private static String evening(Locale locale) {
		return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(LocalTime.of(21, 7))
				.replace(' ', ' ');
	}

	@Test
	void pirateSpeakKeepsTheEnglishPoint() {
		// Minecraft's "en_pt" is Pirate Speak, not the English of Portugal, which has a decimal comma in Java 25: the
		// settings read "1,25×" and "+0,25 s" between English words
		assertEquals("1.25×", number("en_pt", "%.2f×", 1.25));
		assertEquals("+0.25 s", number("en_pt", "%+.2f s", 0.25));
		assertEquals("10.7", number("en_pt", "%.1f", 10.7));
	}

	@Test
	void aLanguageThatSaysNothingOfTheTimeKeepsTheComputersClock() {
		// Like American English, these say nothing of the player's clock: before, Java's "21:07" for no language at all (or
		// Portugal's for Pirate Speak) replaced an American computer's "9:07 PM"
		for (String code : SAYS_NOTHING) {
			assertEquals(Locale.US, clockLocale(code, Locale.US), code);
			assertEquals("9:07 PM", evening(clockLocale(code, Locale.US)), code);
			assertEquals(Locale.GERMANY, clockLocale(code, Locale.GERMANY), code);
			assertEquals("21:07", evening(clockLocale(code, Locale.GERMANY)), code);
		}
	}

	@Test
	void aLanguageJavaKnowsStillHasItsOwnClock() {
		assertEquals(Locale.forLanguageTag("de-DE"), clockLocale("de_de", Locale.US));
		assertEquals("21:07", evening(clockLocale("de_de", Locale.US)));
		assertEquals(Locale.forLanguageTag("fr-CA"), clockLocale("fr_ca", Locale.US));
		assertEquals(Locale.forLanguageTag("ja-JP"), clockLocale("ja_jp", Locale.US));
		assertEquals("he", clockLocale("he_il", Locale.US).getLanguage());
		assertEquals(Locale.GERMANY, clockLocale("en_us", Locale.GERMANY));
		assertEquals("1,25×", number("de_de", "%.2f×", 1.25));
	}
}
