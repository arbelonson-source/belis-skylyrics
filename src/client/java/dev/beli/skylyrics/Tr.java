package dev.beli.skylyrics;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import net.minecraft.client.resources.language.I18n;

public final class Tr {
	private static final Map<String, String> KEYS = new ConcurrentHashMap<>();

	/** What each translated text was written as in English, so a search can match the English words too. */
	private static final Map<String, Set<String>> SOURCE = new ConcurrentHashMap<>();

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
				remember(shown, english);
			}
			return shown;
		}
		return args.length == 0 ? english : String.format(Locale.ROOT, english, args);
	}

	/** Notes that {@code shown} is how {@code english} reads in this language; several English texts may share one. */
	static void remember(String shown, String english) {
		SOURCE.computeIfAbsent(shown, k -> new ConcurrentSkipListSet<>()).add(english);
	}

	/**
	 * Every English a shown text was translated from, joined by a space in a stable order, or the text itself when it
	 * is English or not from {@link #t}.
	 */
	public static String english(String shown) {
		if (shown == null) {
			return "";
		}
		Set<String> all = SOURCE.get(shown);
		return all == null || all.isEmpty() ? shown : String.join(" ", all);
	}

	private static final java.util.regex.Pattern NON_ALNUM = java.util.regex.Pattern.compile("[^a-z0-9]+");
	private static final java.util.regex.Pattern EDGES = java.util.regex.Pattern.compile("^_+|_+$");
	private static final java.util.regex.Pattern TAIL = java.util.regex.Pattern.compile("_+$");

	/** The lang key for an English text: "skylyrics.ui." + slug. */
	static String key(String english) {
		String slug = EDGES.matcher(NON_ALNUM.matcher(english.toLowerCase(Locale.ROOT)).replaceAll("_")).replaceAll("");
		if (slug.length() > 48) {
			slug = TAIL.matcher(slug.substring(0, 48)).replaceAll("");
		}
		return "skylyrics.ui." + slug;
	}
}
