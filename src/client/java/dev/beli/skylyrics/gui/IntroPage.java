package dev.beli.skylyrics.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * What one page of the first-run tour says: the heading, its paragraphs, the keys it lists and the choice made on it
 * ("Neon", "Arc"). Kept free of the screen so the same words are drawn and spoken, and can be tested.
 */
record IntroPage(String heading, List<String> paragraphs, List<String> keys, String chosen) {
	/** Everything the page says that stays while the page does: heading, paragraphs and keys. The Narrator reads it once, when the page shows. */
	String spoken() {
		List<String> parts = new ArrayList<>();
		parts.add(heading);
		parts.addAll(paragraphs);
		parts.addAll(keys);
		return Narration.join(parts.toArray(new String[0]));
	}
}
