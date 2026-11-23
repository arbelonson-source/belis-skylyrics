package dev.beli.skylyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Languages of Minecraft's that the mod has no texts for still get their own numbers. */
class FormatsTest {
	private static String number(String code, String pattern, Object... args) {
		String was = Formats.testLanguage;
		Formats.testLanguage = code;
		try {
			return Formats.number(pattern, args);
		} finally {
			Formats.testLanguage = was;
		}
	}

	@Test
	void aLanguageWithoutTheModsTextsKeepsItsDecimalMark() {
		assertEquals("1,25×", number("it_it", "%.2f×", 1.25));
		assertEquals("+0,25 s", number("pl_pl", "%+.2f s", 0.25));
		assertEquals("1.25×", number("en_gb", "%.2f×", 1.25));
		assertEquals("1.25×", number("ja_jp", "%.2f×", 1.25));
		// Arabic and Persian write ٫ and their own digits, which the fonts may lack: plain digits and a point
		assertEquals("1.25×", number("ar_sa", "%.2f×", 1.25));
		assertEquals("1.25×", number("fa_ir", "%.2f×", 1.25));
		// Made-up and odd codes don't throw
		assertEquals("1.25×", number("lol_us", "%.2f×", 1.25));
		assertEquals("1.25×", number("zlm_arab", "%.2f×", 1.25));
		assertEquals("1.25×", number("", "%.2f×", 1.25));
	}

	@Test
	void minecraftsCodesBecomeJavasLocales() {
		assertEquals(Locale.forLanguageTag("pt-BR"), Formats.locale("pt_br"));
		assertEquals(Locale.forLanguageTag("de-CH"), Formats.locale("de_ch"));
		assertEquals("he", Formats.locale("he_il").getLanguage());
	}
}
