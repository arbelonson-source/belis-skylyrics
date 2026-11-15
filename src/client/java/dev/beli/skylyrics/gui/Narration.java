package dev.beli.skylyrics.gui;

/** The words the Narrator says for the settings screen, built from texts that are already translated. */
final class Narration {
	private Narration() {
	}

	/** The parts as one phrase: blanks left out and a part that only repeats the one before it said once. */
	static String join(String... parts) {
		StringBuilder out = new StringBuilder();
		String last = null;
		for (String part : parts) {
			if (part == null || part.isBlank() || part.strip().equalsIgnoreCase(last)) {
				continue;
			}
			if (!out.isEmpty()) {
				out.append(", ");
			}
			out.append(part.strip());
			last = part.strip();
		}
		return out.toString();
	}

	/** "Layout, choice, Arc, 1 of 3": a picker's name, what it is, what is picked and where it sits (already said, like "1 of 3"). */
	static String choice(String name, String kind, String value, String place) {
		return join(name, kind, value, place);
	}

	/** A value as it is said aloud: "40%" is "40 percent", "-10°" "-10 degrees", "1.5×" "1.5 times". */
	static String speakable(String value, String percent, String degrees, String times) {
		return value.replace("%", " " + percent).replace("°", " " + degrees).replace("×", " " + times);
	}
}
