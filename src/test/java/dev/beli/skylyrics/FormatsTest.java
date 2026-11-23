package dev.beli.skylyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Minecraft's language codes as Java locales. */
class FormatsTest {
	@Test
	void minecraftsCodesBecomeJavasLocales() {
		assertEquals(Locale.forLanguageTag("pt-BR"), Formats.locale("pt_br"));
		assertEquals(Locale.forLanguageTag("de-CH"), Formats.locale("de_ch"));
		assertEquals("he", Formats.locale("he_il").getLanguage());
	}
}
