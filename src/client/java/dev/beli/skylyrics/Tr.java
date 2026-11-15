package dev.beli.skylyrics;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.resources.language.I18n;

public final class Tr {
	private static final Map<String, String> KEYS = new ConcurrentHashMap<>();

	/** What each translated text was written as in English, so a search can match the English words too. */
	private static final Map<String, String> SOURCE = new ConcurrentHashMap<>();

	private Tr() {
	}

	/** The translation of an English UI text, or the English itself when the language has none. */
	public static String t(String english, Object... args) {
		String key = KEYS.computeIfAbsent(english, Tr::key);
		//? if >=26.1 {
		/*if (net.minecraft.locale.Language.getInstance().has(key)) {
		*///?} else {
		if (I18n.exists(key)) {
		//?}
			String shown = I18n.get(key, args);
			if (args.length == 0 && !shown.equals(english)) {
				SOURCE.put(shown, english);
			}
			return shown;
		}
		return args.length == 0 ? english : String.format(Locale.ROOT, english, args);
	}

	/** The English a shown text was translated from, or the text itself when it is English or not from {@link #t}. */
	public static String english(String shown) {
		return shown == null ? "" : SOURCE.getOrDefault(shown, shown);
	}

	/** The lang key for an English text: "skylyrics.ui." + slug. */
	static String key(String english) {
		String slug = english.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
		if (slug.length() > 48) {
			slug = slug.substring(0, 48).replaceAll("_+$", "");
		}
		return "skylyrics.ui." + slug;
	}
}
