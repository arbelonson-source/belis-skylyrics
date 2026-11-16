package dev.beli.skylyrics.render;

import net.minecraft.client.gui.Font;
//? if >=1.21.9 {
import net.minecraft.network.chat.FontDescription;
//?}
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.locale.Language;

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
	/** Soft, rounded letters with no sharp corners (assets/skylyrics/font/round.json). */
	public static final FontDescription ROUND = font("round");
	/**
	 * The same four fonts drawn from finer letter images (assets/skylyrics/font/world*.json). World layout words
	 * float a few blocks away and show far bigger than any other text, so the usual images look soft up close.
	 */
	static final FontDescription WORLD = font("world");
	static final FontDescription WORLD_BOLD = font("world_bold");
	static final FontDescription WORLD_SCRIPT = font("world_script");
	static final FontDescription WORLD_ROUND = font("world_round");

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

	/** Like {@link #base}, but the sky lyrics may use the bold, round or handwritten font; the top bar stays smooth. */
	public static Style sky(SkyLyricsConfig cfg) {
		if (cfg.smoothFont && cfg.scriptFont) {
			return Style.EMPTY.withFont(SCRIPT);
		}
		if (cfg.smoothFont && cfg.roundFont) {
			return Style.EMPTY.withFont(ROUND);
		}
		return cfg.smoothFont && cfg.boldFont ? Style.EMPTY.withFont(BOLD) : base(cfg);
	}

	/** Like {@link #sky}, for the World layout's floating words: the same choice of font, from the finer images. */
	public static Style world(SkyLyricsConfig cfg) {
		if (!cfg.smoothFont) {
			return Style.EMPTY;
		}
		return Style.EMPTY.withFont(cfg.scriptFont ? WORLD_SCRIPT : cfg.roundFont ? WORLD_ROUND : cfg.boldFont ? WORLD_BOLD : WORLD);
	}

	/** Exact width in text units (the plain {@code Font.width(String)} always measures Minecraft's font). */
	public static float width(Font font, Style style, String text) {
		return font.getSplitter().stringWidth(FormattedText.of(text, style));
	}

	/** Widths measured for the per-frame text (top bar, Record card, lyric rows); see {@link WidthCache}. */
	private static final WidthCache WIDTHS = new WidthCache(256);

	/**
	 * {@link #width} for text that is measured every frame: the same font, style and text give the remembered width
	 * until a resource reload ({@link #fontsReloaded}) or a language change.
	 */
	public static float cachedWidth(Font font, Style style, String text) {
		return WIDTHS.get(font, style, text, Language.getInstance(), LyricsFont::measure);
	}

	private static float measure(Object font, Object style, String text) {
		return width((Font) font, (Style) style, text);
	}

	private static final String CANARY = "Mw0♪漢א ع";
	private static final Style[] CANARY_STYLES = {Style.EMPTY, Style.EMPTY.withFont(SMOOTH), Style.EMPTY.withFont(BOLD),
			Style.EMPTY.withFont(SCRIPT), Style.EMPTY.withFont(ROUND)};

	/**
	 * Notices a resource or font change that happened while nothing here was drawing: measures a probe text in each of
	 * the mod's fonts and the game's own, fresh, and drops the remembered widths when it moved. Call once per frame
	 * before the first {@link #cachedWidth}. This is used instead of a reload listener because it needs no per-version
	 * Fabric API or mixin, and it sees exactly what matters: glyph widths.
	 */
	static void checkFonts(Font font) {
		float sum = 0;
		for (Style style : CANARY_STYLES) {
			sum = sum * 1.0009f + width(font, style, CANARY);
		}
		WIDTHS.probe(sum);
	}

	/** Drops every remembered width: a resource reload can change glyph widths. Called while the loading screen is up. */
	static void fontsReloaded() {
		WIDTHS.invalidate();
	}

	/** How many reloads the width cache has seen, for caches of results built from those widths. */
	static long reloadCount() {
		return WIDTHS.epoch();
	}
}
