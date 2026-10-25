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

	private LyricsFont() {
	}

	/** The style every lyric character starts from, before colour is added. */
	public static Style base(SkyLyricsConfig cfg) {
		return cfg.smoothFont ? Style.EMPTY.withFont(SMOOTH) : Style.EMPTY;
	}

	/** Exact width in text units (the plain {@code Font.width(String)} always measures Minecraft's font). */
	public static float width(Font font, Style style, String text) {
		return font.getSplitter().stringWidth(FormattedText.of(text, style));
	}
}
