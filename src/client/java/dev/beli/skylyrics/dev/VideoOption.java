package dev.beli.skylyrics.dev;

import java.util.Locale;

/**
 * The argument of the test hook's {@code option}: one of the game's own video options and its value, e.g.
 * {@code renderDistance 4}, {@code clouds fancy} or {@code cloudRange 32}.
 */
final class VideoOption {
	private VideoOption() {}

	/** The video options a check can set. */
	enum Kind {
		/** Render Distance, in chunks (2 to 32; the game itself allows at most 16 on a small Java heap). */
		RENDER_DISTANCE("renderDistance", 2, 32),
		/** Clouds: off, fast or fancy. */
		CLOUDS("clouds", 0, 0),
		/** Cloud Distance, in chunks (2 to 128; 1.21.6 and newer only). */
		CLOUD_RANGE("cloudRange", 2, 128);

		final String word;
		final int min, max;

		Kind(String word, int min, int max) {
			this.word = word;
			this.min = min;
			this.max = max;
		}
	}

	/** A parsed option: {@code chunks} for the distances, {@code clouds} (OFF, FAST or FANCY) for the clouds. */
	record Setting(Kind kind, int chunks, String clouds) {}

	static Setting parse(String arg) {
		String[] parts = arg.strip().split("\\s+");
		if (parts.length != 2) {
			throw new IllegalArgumentException("option needs a name and a value, like 'option renderDistance 4': '" + arg + "'");
		}
		for (Kind kind : Kind.values()) {
			if (kind.word.equals(parts[0])) {
				return kind == Kind.CLOUDS ? new Setting(kind, 0, clouds(parts[1])) : new Setting(kind, chunks(kind, parts[1]), null);
			}
		}
		throw new IllegalArgumentException("unknown option '" + parts[0] + "' (renderDistance, clouds or cloudRange)");
	}

	private static String clouds(String value) {
		String upper = value.toUpperCase(Locale.ROOT);
		if (!upper.equals("OFF") && !upper.equals("FAST") && !upper.equals("FANCY")) {
			throw new IllegalArgumentException("clouds are off, fast or fancy: '" + value + "'");
		}
		return upper;
	}

	private static int chunks(Kind kind, String value) {
		int n;
		try {
			n = Integer.parseInt(value);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(kind.word + " needs a whole number of chunks: '" + value + "'", e);
		}
		if (n < kind.min || n > kind.max) {
			throw new IllegalArgumentException(kind.word + " is " + kind.min + " to " + kind.max + " chunks: " + n);
		}
		return n;
	}
}
