package dev.beli.skylyrics;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.LanguageManager;

/**
 * Numbers and times as the game's language writes them, which is the language the player picked in Minecraft and not
 * the computer's: a player in German on an English computer reads "21:07" like the German words around it.
 */
public final class Formats {
	/** The game's language code a test plays in (see the tests' InLanguage), or null to ask the game. */
	static volatile @Nullable String testLanguage;

	private record Picked(String code, Locale locale) {
	}

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
			now = new Picked(code, locale(code));
			last = now;
		}
		return now;
	}

	/** The Java locale of a Minecraft language code: "de_de" is German in Germany, "pt_br" Portuguese in Brazil. */
	static Locale locale(String code) {
		return Locale.forLanguageTag(code.replace('_', '-'));
	}

	/**
	 * Whose clock the top bar's time follows, 12 or 24 hours: the game language's ("21:07" in German, Hebrew or Russian,
	 * "21 h 07" in Canadian French). In American English, which is also the game's default and so says little about where
	 * the player lives, the computer's, as before.
	 */
	public static Locale clockLocale() {
		Picked picked = picked();
		return picked.code().equals("en_us") ? Locale.getDefault(Locale.Category.FORMAT) : picked.locale();
	}
}
