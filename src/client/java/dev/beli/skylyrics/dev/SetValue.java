package dev.beli.skylyrics.dev;

/** The value part of the test hook's {@code set field value}. */
final class SetValue {
	private SetValue() {}

	/** A String field's value: exactly two double quotes mean the empty string; anything else is taken as it is. */
	static String string(String raw) {
		return "\"\"".equals(raw) ? "" : raw;
	}
}
