package dev.beli.skylyrics.util;

import java.nio.file.Path;

/** Text for the game log: players paste it into bug reports, so their home folder (and so their name) is cut to "~". */
public final class LogText {
	private LogText() {
	}

	/** Replaces every use of {@code home} (either slash style) with "~"; a folder that only starts with the same letters is left alone. */
	public static String redact(String text, String home) {
		if (text == null || home == null) {
			return text;
		}
		String base = home;
		while (!base.isEmpty() && (base.endsWith("/") || base.endsWith("\\"))) {
			base = base.substring(0, base.length() - 1);
		}
		if (base.isEmpty()) {
			return text;
		}
		String result = cut(text, base);
		String slashed = base.replace('\\', '/');
		if (!slashed.equals(base)) {
			result = cut(result, slashed);
		}
		return result;
	}

	private static String cut(String text, String base) {
		StringBuilder out = new StringBuilder(text.length());
		int from = 0;
		int at;
		while ((at = text.indexOf(base, from)) >= 0) {
			int end = at + base.length();
			boolean whole = end == text.length() || text.charAt(end) == '/' || text.charAt(end) == '\\';
			out.append(text, from, at).append(whole ? "~" : base);
			from = end;
		}
		return out.append(text, from, text.length()).toString();
	}

	public static String of(Path path) {
		return path == null ? "null" : redact(path.toString(), System.getProperty("user.home"));
	}

	public static String of(Throwable e) {
		return redact(String.valueOf(e), System.getProperty("user.home"));
	}

	/**
	 * The exception and what caused it, each as its class and message: the technical words (Java's, a server's) a
	 * player is shown a plain sentence for instead.
	 */
	public static String chain(Throwable e) {
		StringBuilder out = new StringBuilder();
		for (int depth = 0; e != null && depth < 6; depth++) {
			if (depth > 0) {
				out.append(", caused by ");
			}
			out.append(e);
			Throwable cause = e.getCause();
			e = cause == e ? null : cause;
		}
		return redact(out.toString(), System.getProperty("user.home"));
	}
}
