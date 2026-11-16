package dev.beli.skylyrics.dev;

import java.util.Locale;

/** Builds the synced test lyrics the test hook swaps in: the given lines in turn, one every {@code stepMs}. */
final class TimedLrc {
	private TimedLrc() {
	}

	/** "a | b | c" as LRC, one line every {@code stepMs} from 0 until {@code lengthMs}, cycling through the lines. */
	static String of(String arg, int stepMs, long lengthMs) {
		if (stepMs <= 0) {
			throw new IllegalArgumentException("step must be above 0 ms: " + stepMs);
		}
		String[] parts = arg.split("\\s*\\|\\s*");
		StringBuilder lrc = new StringBuilder();
		long ms = 0;
		for (int i = 0; ms < lengthMs; i++, ms += stepMs) {
			lrc.append(String.format(Locale.ROOT, "[%02d:%02d.%02d]%s%n", ms / 60000, ms / 1000 % 60, ms / 10 % 100, parts[i % parts.length]));
		}
		return lrc.toString();
	}
}
