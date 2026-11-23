package dev.beli.skylyrics;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * The game set to one of the mod's languages for a test, so {@link Tr#t} answers from that lang file as it does in
 * game, and {@link Formats} writes numbers and times as that language does. Read the same way the game reads it. Close
 * it to put the game's own language back.
 */
public final class InLanguage implements AutoCloseable {
	private static final Path LANG = Path.of("src/main/resources/assets/skylyrics/lang");

	private final String code;
	private final Map<String, String> texts = new HashMap<>();
	private final Language before = Language.getInstance();
	/** 1.21.x keep a copy of the language in I18n too, which is what {@link Tr} asks there; null where there is none. */
	private final Field i18n;
	private final Object i18nBefore;
	private final String formatsBefore = Formats.testLanguage;

	/** Sets the game to {@code code}, like "de_de". */
	public static InLanguage of(String code) throws IOException {
		return new InLanguage(code);
	}

	private InLanguage(String code) throws IOException {
		this.code = code;
		try (InputStream in = Files.newInputStream(LANG.resolve(code + ".json"))) {
			Language.loadFromJson(in, texts::put);
		}
		Language fake = new Language() {
			@Override
			public String getOrDefault(String key, String fallback) {
				return texts.getOrDefault(key, fallback);
			}

			@Override
			public boolean has(String key) {
				return texts.containsKey(key);
			}

			@Override
			public boolean isDefaultRightToLeft() {
				return false;
			}

			@Override
			public FormattedCharSequence getVisualOrder(FormattedText text) {
				return before.getVisualOrder(text);
			}
		};
		Field field = null;
		Object old = null;
		try {
			field = I18n.class.getDeclaredField("language");
			field.setAccessible(true);
			old = field.get(null);
		} catch (NoSuchFieldException e) {
			// 26.2 and newer: I18n reads Language.getInstance()
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
		i18n = field;
		i18nBefore = old;
		Language.inject(fake);
		set(fake);
		Formats.testLanguage = code;
	}

	/**
	 * How the English UI text {@code english} reads in this language with {@code args} filled in, as its lang file has
	 * it, or a note that the file doesn't have it (which no shown text equals).
	 */
	public String says(String english, Object... args) {
		String text = texts.get(Tr.key(english));
		return text == null ? "(" + code + " has no text for \"" + english + "\")" : String.format(Locale.ROOT, text, args);
	}

	private void set(Object language) {
		if (i18n == null) {
			return;
		}
		try {
			i18n.set(null, language);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public void close() {
		Language.inject(before);
		set(i18nBefore);
		Formats.testLanguage = formatsBefore;
	}
}
