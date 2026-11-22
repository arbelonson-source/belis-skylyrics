package dev.beli.skylyrics.dev;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The argument of the test hook's {@code song}: "title | artist", the artist after the last "|" (a title may hold one),
 * no "|" for a title alone. {@code \}{@code uXXXX} stands for that character, so a check script can name an invisible
 * one (an emoji's style selector, a joiner) or a right-to-left letter without an editor turning it around.
 */
final class SongArg {
	private SongArg() {}

	private static final Pattern ESCAPE = Pattern.compile("\\\\u([0-9A-Fa-f]{4})");

	/** The title and the artist, each with its escapes read and its ends trimmed. */
	static String[] parse(String arg) {
		int bar = arg.lastIndexOf('|');
		String title = bar < 0 ? arg : arg.substring(0, bar);
		String artist = bar < 0 ? "" : arg.substring(bar + 1);
		return new String[] {unescape(title).strip(), unescape(artist).strip()};
	}

	static String unescape(String s) {
		Matcher m = ESCAPE.matcher(s);
		StringBuilder out = new StringBuilder(s.length());
		while (m.find()) {
			m.appendReplacement(out, Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(m.group(1), 16))));
		}
		m.appendTail(out);
		return out.toString();
	}
}
