package dev.beli.skylyrics.gui;

import java.util.Locale;

/** Matching for the search box on the settings screen. */
final class SettingsSearch {
	private SettingsSearch() {
	}

	/** British spellings in our labels and the American ones people may type instead. */
	private static final String[][] SPELLINGS = {{"colour", "color"}, {"centred", "centered"}, {"centre", "center"},
			{"grey", "gray"}};

	/**
	 * True when every word of {@code query} is somewhere in {@code text}, ignoring case and word order. "Color" finds
	 * "Colour" and the like, so either spelling works.
	 */
	static boolean matches(String text, String query) {
		String hay = text.toLowerCase(Locale.ROOT);
		String us = american(hay);
		String q = query.toLowerCase(Locale.ROOT).strip();
		if (q.isEmpty()) {
			return false;
		}
		for (String word : q.split("\\s+")) {
			if (!hay.contains(word) && !us.contains(american(word))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The search box after pasting {@code clip}: runs of white space become one space, and the characters typing
	 * would refuse (the section sign, control characters) are dropped.
	 */
	static String pasted(String query, String clip) {
		StringBuilder out = new StringBuilder(query);
		clip.replaceAll("\\s+", " ").codePoints().filter(SettingsSearch::allowed).forEach(out::appendCodePoint);
		return out.toString();
	}

	/** Same rule as Minecraft's chat filter: no section sign, no control characters. */
	static boolean allowed(int cp) {
		return cp != 167 && cp >= 32 && cp != 127;
	}

	private static String american(String s) {
		for (String[] pair : SPELLINGS) {
			s = s.replace(pair[0], pair[1]);
		}
		return s;
	}
}
