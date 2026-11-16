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
		String hay = fold(text);
		String us = american(hay);
		String q = fold(query).strip();
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
		clean(clip).codePoints().filter(SettingsSearch::allowed).forEach(out::appendCodePoint);
		return out.toString();
	}

	private static final java.util.regex.Pattern SPACES = java.util.regex.Pattern.compile("[\\s\u00a0\u2007\u202f\u3000]+");
	private static final java.util.regex.Pattern INVISIBLE = java.util.regex.Pattern.compile("\\p{Cf}");
	private static final java.util.regex.Pattern MARKS = java.util.regex.Pattern.compile("\\p{M}");

	/** Odd spaces become plain ones in runs of one, and direction marks and zero-width characters go. */
	static String clean(String s) {
		return SPACES.matcher(INVISIBLE.matcher(s).replaceAll("")).replaceAll(" ");
	}

	/** The text as it is compared: cleaned, lower case, without accents or niqqud, Hebrew final letters as plain ones. */
	static String fold(String s) {
		String d = MARKS.matcher(java.text.Normalizer.normalize(clean(s), java.text.Normalizer.Form.NFD)).replaceAll("");
		d = d.toLowerCase(Locale.ROOT);
		StringBuilder out = new StringBuilder(d.length());
		for (int i = 0; i < d.length(); i++) {
			char c = d.charAt(i);
			int at = FINALS.indexOf(c);
			out.append(at < 0 ? c : PLAIN.charAt(at));
		}
		return out.toString();
	}

	private static final String FINALS = "\u05da\u05dd\u05df\u05e3\u05e5";
	private static final String PLAIN = "\u05db\u05de\u05e0\u05e4\u05e6";

	/** Same rule as Minecraft's chat filter: no section sign, no control characters. */
	static boolean allowed(int cp) {
		return cp != 167 && cp >= 32 && cp != 127 && Character.getType(cp) != Character.FORMAT;
	}

	private static String american(String s) {
		for (String[] pair : SPELLINGS) {
			s = s.replace(pair[0], pair[1]);
		}
		return s;
	}
}
