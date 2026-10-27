package dev.beli.skylyrics.render;

import net.minecraft.client.gui.Font;
//? if >=1.21.9 {
import net.minecraft.network.chat.FontDescription;
//?}
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

import dev.beli.skylyrics.SkyLyricsClient;
import dev.beli.skylyrics.config.SkyLyricsConfig;

/**
 * The lyrics typeface. The smooth one is a real TrueType font bundled in the mod
 * (assets/skylyrics/font/lyrics.json); letters it lacks fall back to Minecraft's font.
 */
public final class LyricsFont {
	public static final FontDescription SMOOTH = font("lyrics");
	/** Heavier letters, like big sung words on posters (assets/skylyrics/font/bold.json). */
	public static final FontDescription BOLD = font("bold");
	/** Handwritten brush letters for the sky lyrics (assets/skylyrics/font/script.json). */
	public static final FontDescription SCRIPT = font("script");
	/**
	 * The same three fonts drawn from finer letter images (assets/skylyrics/font/world*.json). World layout words
	 * float a few blocks away and show far bigger than any other text, so the usual images look soft up close.
	 */
	static final FontDescription WORLD = font("world");
	static final FontDescription WORLD_BOLD = font("world_bold");
	static final FontDescription WORLD_SCRIPT = font("world_script");

	private LyricsFont() {
	}

	private static FontDescription font(String name) {
		//? if >=1.21.9 {
		return new FontDescription.Resource(SkyLyricsClient.id(name));
		//?} else {
		/*return SkyLyricsClient.id(name);
		*///?}
	}

	/** The style every lyric character starts from, before colour is added. */
	public static Style base(SkyLyricsConfig cfg) {
		return cfg.smoothFont ? Style.EMPTY.withFont(SMOOTH) : Style.EMPTY;
	}

	/** Like {@link #base}, but the sky lyrics may use the bold or handwritten font; the top bar stays smooth. */
	public static Style sky(SkyLyricsConfig cfg) {
		if (cfg.smoothFont && cfg.scriptFont) {
			return Style.EMPTY.withFont(SCRIPT);
		}
		return cfg.smoothFont && cfg.boldFont ? Style.EMPTY.withFont(BOLD) : base(cfg);
	}

	/** Like {@link #sky}, for the World layout's floating words: the same choice of font, from the finer images. */
	public static Style world(SkyLyricsConfig cfg) {
		// Smooth letters are stored as see-through masks, which some shader packs (AstraLex) draw in the world as solid
		// black boxes; Minecraft's own letters are full pictures that every pack draws right
		if (!cfg.smoothFont || ScreenLayer.shadersOn()) {
			return Style.EMPTY;
		}
		return Style.EMPTY.withFont(cfg.scriptFont ? WORLD_SCRIPT : cfg.boldFont ? WORLD_BOLD : WORLD);
	}

	/** Exact width in text units (the plain {@code Font.width(String)} always measures Minecraft's font). */
	public static float width(Font font, Style style, String text) {
		return font.getSplitter().stringWidth(FormattedText.of(text, style));
	}
}
