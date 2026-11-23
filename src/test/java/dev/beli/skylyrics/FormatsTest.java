package dev.beli.skylyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.util.Locale;

import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;

/** Languages of Minecraft's that the mod has no texts for still get their own numbers. */
class FormatsTest {
	private static String number(String code, String pattern, double value) {
		String was = Formats.testLanguage;
		Formats.testLanguage = code;
		try {
			return Formats.number(pattern, value);
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

	private static String sink;

	/**
	 * The settings screen writes each of its sliders' numbers every frame, and the slider's two ends for the room it needs:
	 * the same number in the same language is the text made the time before, built once and not every frame.
	 */
	@Test
	void aNumberAskedForAgainIsTheTextMadeBefore() {
		String was = Formats.testLanguage;
		Formats.testLanguage = "de_de";
		try {
			String first = Formats.number("%.2f×", 1.25);
			assertEquals("1,25×", first);
			assertSame(first, Formats.number("%.2f×", 1.25));
			ThreadMXBean mx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
			for (int i = 0; i < 200_000; i++) {
				sink = Formats.number("%.2f×", 1.25);
				sink = Formats.number("%+.2f s", -0.5);
			}
			long id = Thread.currentThread().getId();
			long before = mx.getThreadAllocatedBytes(id);
			for (int i = 0; i < 10_000; i++) {
				sink = Formats.number("%.2f×", 1.25);
				sink = Formats.number("%+.2f s", -0.5);
			}
			long used = mx.getThreadAllocatedBytes(id) - before;
			System.out.println("Formats.number bytes per 10000 frames of two numbers: " + used);
			assertTrue(used < 100_000, "allocated " + used + " bytes for 10000 frames of two numbers");
			// Everything the text is made from still counts: the number, the pattern and the language's mark
			assertEquals("1,30×", Formats.number("%.2f×", 1.3));
			assertEquals(String.format(Locale.ROOT, "%.1f s", 1.25).replace('.', ','), Formats.number("%.1f s", 1.25));
			assertEquals("-0,50 s", Formats.number("%+.2f s", -0.5));
			Formats.testLanguage = "en_us";
			assertEquals("1.25×", Formats.number("%.2f×", 1.25));
			assertEquals("-0.50 s", Formats.number("%+.2f s", -0.5));
			// A slider dragged through many values: each is still its own text, in either language
			for (String code : new String[] {"fr_fr", "en_us", "fr_fr"}) {
				Formats.testLanguage = code;
				for (int i = -300; i <= 300; i++) {
					double v = i / 97.0;
					String want = String.format(Locale.ROOT, "%.2f×", v);
					assertEquals(code.equals("en_us") ? want : want.replace('.', ','), Formats.number("%.2f×", v), code + " " + v);
				}
			}
		} finally {
			Formats.testLanguage = was;
		}
	}
}
