package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.client.StringSplitter;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

/**
 * Every resource reload (F3+T, a pack turned on or off, another language) makes a new language object, which is how a
 * reload is noticed when nothing of the mod's drew while its loading screen was up: packs changed from the title screen's
 * Options, where the loading screen covers the Options screen and not the title screen's lyrics.
 */
class FontReloadTest {
	/** The language object a reload makes: the same texts as the one before it, a new object. */
	private static Language reloaded(Language before) {
		return new Language() {
			@Override
			public String getOrDefault(String key, String fallback) {
				return before.getOrDefault(key, fallback);
			}

			@Override
			public boolean has(String key) {
				return before.has(key);
			}

			@Override
			public boolean isDefaultRightToLeft() {
				return before.isDefaultRightToLeft();
			}

			@Override
			public FormattedCharSequence getVisualOrder(FormattedText text) {
				return before.getVisualOrder(text);
			}
		};
	}

	@Test
	void aReloadNothingHereSawStillMovesTheReloadCount() {
		// The probe text measures the same after the reload: the pack changed only letters it doesn't have (Cyrillic ones,
		// say). The lyric rows, the letters' places and the cut titles are kept by this count, so it has to move anyway.
		StringSplitter splitter = new StringSplitter((cp, style) -> 5);
		Language before = Language.getInstance();
		LyricsFont.resetProbeGate();
		LyricsFont.checkFonts(splitter, 0);
		long seen = LyricsFont.reloadCount();
		Language.inject(reloaded(before));
		try {
			LyricsFont.checkFonts(splitter, 10_000_000_000L);
			assertEquals(seen + 1, LyricsFont.reloadCount(), "a new language object is a reload");
			LyricsFont.checkFonts(splitter, 20_000_000_000L);
			assertEquals(seen + 1, LyricsFont.reloadCount(), "the same language object again is no new reload");
		} finally {
			Language.inject(before);
		}
	}
}
