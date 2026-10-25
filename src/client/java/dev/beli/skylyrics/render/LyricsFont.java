package dev.beli.skylyrics.render;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

import dev.beli.skylyrics.SkyLyricsClient;
import dev.beli.skylyrics.config.SkyLyricsConfig;

/**
 * The lyrics typeface. The smooth one is a real TrueType font bundled in the mod
 * (assets/skylyrics/font/lyrics.json); letters it lacks fall back to Minecraft's font.
 */
public final class LyricsFont {
	public static final FontDescription SMOOTH = new FontDescription.Resource(SkyLyricsClient.id("lyrics"));
	/** Heavier letters, like big sung words on posters (assets/skylyrics/font/bold.json). */
	public static final FontDescription BOLD = new FontDescription.Resource(SkyLyricsClient.id("bold"));
	/** Handwritten brush letters for the sky lyrics (assets/skylyrics/font/script.json). */
	public static final FontDescription SCRIPT = new FontDescription.Resource(SkyLyricsClient.id("script"));

	private LyricsFont() {
	}

	/** The style every lyric character starts from, before colour is added. */
	public static Style base(SkyLyricsConfig cfg) {
		return cfg.smoothFont ? Style.EMPTY.withFont(SMOOTH) : Style.EMPTY;
	}

	/** Like {@link #base}, but the sky lyrics may use the handwritten font; the top bar stays smooth. */
	public static Style sky(SkyLyricsConfig cfg) {
		return cfg.smoothFont && cfg.scriptFont ? Style.EMPTY.withFont(SCRIPT) : base(cfg);
	}

	/** Exact width in text units (the plain {@code Font.width(String)} always measures Minecraft's font). */
	public static float width(Font font, Style style, String text) {
		return font.getSplitter().stringWidth(FormattedText.of(text, style));
	}
}
