package dev.beli.skylyrics.dev;

import java.util.Locale;

/**
 * The arguments of the test hook's {@code pack} and {@code unicode}: a resource pack's id and on or off
 * ({@code pack high_contrast on}, {@code pack programmer_art off}), or on or off alone ({@code unicode on}).
 */
final class PackArg {
	private PackArg() {}

	/** A pack to turn on or off, by the id the Resource Packs screen knows it by. */
	record Pack(String id, boolean on) {}

	static Pack parse(String arg) {
		String[] parts = arg.strip().split("\\s+");
		if (parts.length != 2 || parts[0].isEmpty()) {
			throw new IllegalArgumentException("pack needs an id and on or off, like 'pack high_contrast on': '" + arg + "'");
		}
		return new Pack(parts[0], onOff(parts[1]));
	}

	/** "on" or "off" (also "true" or "false"), in any case. */
	static boolean onOff(String word) {
		return switch (word.strip().toLowerCase(Locale.ROOT)) {
			case "on", "true" -> true;
			case "off", "false" -> false;
			default -> throw new IllegalArgumentException("on or off: '" + word + "'");
		};
	}
}
