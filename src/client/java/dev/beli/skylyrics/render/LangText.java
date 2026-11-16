package dev.beli.skylyrics.render;

import java.util.function.Supplier;

import net.minecraft.locale.Language;

/**
 * One translated text kept between frames. Translating formats a string each time, so a text drawn every frame is
 * translated once and again only when the game's {@code Language} object is replaced (the language changed or the
 * resources reloaded). Built from the translating call itself, a lambda that calls Tr.t, so the
 * text still shows up for the translation checks. Render thread only.
 */
final class LangText {
	private final Supplier<String> translate;
	private Object language;
	private String shown;

	LangText(Supplier<String> translate) {
		this.translate = translate;
	}

	String get() {
		return get(Language.getInstance());
	}

	String get(Object currentLanguage) {
		if (shown == null || currentLanguage != language) {
			shown = translate.get();
			language = currentLanguage;
		}
		return shown;
	}
}
