package dev.beli.skylyrics.gui;

import java.util.Locale;

/** Matching for the search box on the settings screen. */
final class SettingsSearch {
	private SettingsSearch() {
	}

	/** True when every word of {@code query} is somewhere in {@code text}, ignoring case and word order. */
	static boolean matches(String text, String query) {
		String hay = text.toLowerCase(Locale.ROOT);
		String q = query.toLowerCase(Locale.ROOT).strip();
		if (q.isEmpty()) {
			return false;
		}
		for (String word : q.split("\\s+")) {
			if (!hay.contains(word)) {
				return false;
			}
		}
		return true;
	}
}
