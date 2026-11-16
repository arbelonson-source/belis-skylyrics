package dev.beli.skylyrics.render;

import java.util.function.UnaryOperator;

import net.minecraft.locale.Language;

import dev.beli.skylyrics.Tr;

/**
 * One translated text kept between frames. Translating formats a string each time, so a text drawn every frame is
 * translated once and again only when the game's {@code Language} object is replaced (the language changed or the
 * resources reloaded). Render thread only.
 */
final class LangText {
	private final String english;
	private Object language;
	private String shown;

	LangText(String english) {
		this.english = english;
	}

	String get() {
		return get(Language.getInstance(), Tr::t);
	}

	String get(Object currentLanguage, UnaryOperator<String> translate) {
		if (shown == null || currentLanguage != language) {
			shown = translate.apply(english);
			language = currentLanguage;
		}
		return shown;
	}
}
