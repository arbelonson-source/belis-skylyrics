package dev.beli.skylyrics;

import java.text.DateFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.LanguageManager;

/**
 * Numbers and times as the game's language writes them, which is the language the player picked in Minecraft and not
 * the computer's: a player in German on an English computer reads "1,50×" and "21:07" like the German words around it.
 */
public final class Formats {
	/** The game's language code a test plays in (see the tests' InLanguage), or null to ask the game. */
	static volatile @Nullable String testLanguage;

	/** The game language, its Java locale and decimal mark, and whether the bar's clock follows the computer in it. */
	private record Picked(String code, Locale locale, char decimalMark, boolean computersClock) {
	}

	/**
	 * Minecraft's English that names no place: American English, the game's default, and the joke languages Pirate Speak
	 * ("en_pt", not the English of Portugal) and Upside Down English ("en_ud").
	 */
	private static final Set<String> PLACELESS_ENGLISH = Set.of("en_us", "en_pt", "en_ud");

	private static volatile @Nullable Picked last;

	private Formats() {
	}

	/** The code of the language picked in Minecraft, like "de_de"; "en_us", the game's default, before there is one. */
	static String gameLanguage() {
		String test = testLanguage;
		if (test != null) {
			return test;
		}
		Minecraft mc = Minecraft.getInstance(); // none in the tests
		LanguageManager languages = mc == null ? null : mc.getLanguageManager();
		String code = languages == null ? null : languages.getSelected();
		return code == null || code.isBlank() ? "en_us" : code;
	}

	private static Picked picked() {
		String code = gameLanguage();
		Picked now = last;
		if (now == null || !now.code().equals(code)) {
			Locale locale = locale(code);
			// Only a comma or a point: another mark (Arabic's ٫) may be missing from the fonts
			char mark = DecimalFormatSymbols.getInstance(locale).getDecimalSeparator() == ',' ? ',' : '.';
			now = new Picked(code, locale, mark, PLACELESS_ENGLISH.contains(code) || !JavaTimes.has(locale));
			last = now;
		}
		return now;
	}

	/**
	 * The Java locale of a Minecraft language code: "de_de" is German in Germany, "pt_br" Portuguese in Brazil. Pirate Speak
	 * ("en_pt") is English: Java 25 has a decimal comma and 24 hours for the English of Portugal.
	 */
	static Locale locale(String code) {
		return code.equals("en_pt") ? Locale.ENGLISH : Locale.forLanguageTag(code.replace('_', '-'));
	}

	/**
	 * The languages Java has times for, found the first time a game language other than a placeless English asks. For any
	 * other (LOLCAT, Shakespearean English, Klingon, Quenya and Minecraft's other made-up languages, and a few real ones
	 * like Classical Chinese) Java falls back to a plain "21:07", which says nothing of how the player reads the time.
	 */
	private static final class JavaTimes {
		private static final Set<String> LANGUAGES = Arrays.stream(DateFormat.getAvailableLocales()).map(Locale::getLanguage)
				.collect(Collectors.toUnmodifiableSet());

		static boolean has(Locale locale) {
			return LANGUAGES.contains(locale.getLanguage());
		}
	}

	/**
	 * {@link String#format} for a number shown to the player: plain digits, with the game language's decimal mark ("1.50×"
	 * in English, "1,50×" in German, French, Russian, Ukrainian, Portuguese and most Spanish). The pattern itself must have
	 * no other dot. The settings screen asks for each slider's number (and its two ends) every frame: the same number in
	 * the same language gives the text made before ({@link #MADE}), so a frame builds nothing.
	 */
	public static String number(String pattern, double value) {
		char mark = picked().decimalMark();
		long bits = Double.doubleToRawLongBits(value);
		int slot = (int) ((pattern.hashCode() * 0x9E3779B97F4A7C15L ^ bits) * 0xBF58476D1CE4E5B9L >>> 58);
		Made[] made = MADE;
		for (int i = 0; i < made.length; i++) {
			Made m = made[(slot + i) & (made.length - 1)];
			if (m == null) {
				break;
			}
			if (m.bits() == bits && m.mark() == mark && m.pattern().equals(pattern)) {
				return m.text();
			}
		}
		String text = String.format(Locale.ROOT, pattern, value);
		if (mark != '.') {
			text = text.replace('.', mark);
		}
		keep(new Made(pattern, bits, mark, text), slot);
		return text;
	}

	/** A text {@link #number} made, with what it was made from. */
	private record Made(String pattern, long bits, char mark, String text) {
	}

	/**
	 * The texts {@link #number} made, each in the first free slot from its own (64: a settings tab shows a few dozen
	 * numbers). Emptied when three quarters full, as a slider dragged through its values fills it. A slot only ever holds a
	 * whole, unchanging entry, so a race between threads costs at most a text made again.
	 */
	private static final Made[] MADE = new Made[64];
	private static int madeCount;

	private static void keep(Made entry, int slot) {
		Made[] made = MADE;
		if (madeCount >= made.length * 3 / 4) {
			java.util.Arrays.fill(made, null);
			madeCount = 0;
		}
		for (int i = 0; i < made.length; i++) {
			int at = (slot + i) & (made.length - 1);
			if (made[at] == null) {
				made[at] = entry;
				madeCount++;
				return;
			}
		}
	}

	/**
	 * Whose clock the top bar's time follows, 12 or 24 hours: the game language's ("21:07" in German, Hebrew or Russian,
	 * "21 h 07" in Canadian French). In American English, which is also the game's default and so says little about where
	 * the player lives, the computer's, as before; so too in the other English that names no place and in a language Java
	 * has no times for (see {@link #PLACELESS_ENGLISH} and {@link JavaTimes}).
	 */
	public static Locale clockLocale() {
		Picked picked = picked();
		return picked.computersClock() ? Locale.getDefault(Locale.Category.FORMAT) : picked.locale();
	}
}
