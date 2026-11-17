package dev.beli.skylyrics.render;

import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
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

	private static Style of(FontDescription font) {
		return Style.EMPTY.withFont(font);
	}

	private static final Style S_SMOOTH = of(SMOOTH), S_BOLD = of(BOLD), S_SCRIPT = of(SCRIPT), S_ROUND = of(ROUND);
	private static final Style S_WORLD = of(WORLD), S_WORLD_BOLD = of(WORLD_BOLD), S_WORLD_SCRIPT = of(WORLD_SCRIPT),
			S_WORLD_ROUND = of(WORLD_ROUND);

	/** The style every lyric character starts from, before colour is added. */
	public static Style base(SkyLyricsConfig cfg) {
		return cfg.smoothFont ? S_SMOOTH : Style.EMPTY;
	}

	/** Like {@link #base}, but the sky lyrics may use the bold, round or handwritten font; the top bar stays smooth. */
	public static Style sky(SkyLyricsConfig cfg) {
		if (cfg.smoothFont && cfg.scriptFont) {
			return S_SCRIPT;
		}
		if (cfg.smoothFont && cfg.roundFont) {
			return S_ROUND;
		}
		return cfg.smoothFont && cfg.boldFont ? S_BOLD : base(cfg);
	}

	/** Like {@link #sky}, for the World layout's floating words: the same choice of font, from the finer images. */
	public static Style world(SkyLyricsConfig cfg) {
		if (!cfg.smoothFont) {
			return Style.EMPTY;
		}
		return cfg.scriptFont ? S_WORLD_SCRIPT : cfg.roundFont ? S_WORLD_ROUND : cfg.boldFont ? S_WORLD_BOLD : S_WORLD;
	}

	/** Exact width in text units (the plain {@code Font.width(String)} always measures Minecraft's font). */
	public static float width(Font font, Style style, String text) {
		return width(font.getSplitter(), style, text);
	}

	/** {@link #width(Font, Style, String)} on a splitter; invisible direction marks take no room (they are never drawn). */
	static float width(StringSplitter splitter, Style style, String text) {
		return splitter.stringWidth(FormattedText.of(stripMarks(text), style));
	}

	/** An invisible direction mark: LRM, RLM, ALM, the embeddings and overrides, the isolates. Vanilla would draw each as a box. */
	public static boolean isMark(int cp) {
		return cp == 0x200E || cp == 0x200F || cp == 0x061C || cp >= 0x202A && cp <= 0x202E || cp >= 0x2066 && cp <= 0x2069;
	}

	/** The text without its direction marks (the same instance when it has none). */
	public static String stripMarks(String text) {
		int n = text.length();
		int i = 0;
		while (i < n) {
			int cp = text.codePointAt(i);
			if (isMark(cp)) {
				break;
			}
			i += Character.charCount(cp);
		}
		if (i == n) {
			return text;
		}
		StringBuilder out = new StringBuilder(n).append(text, 0, i);
		while (i < n) {
			int cp = text.codePointAt(i);
			if (!isMark(cp)) {
				out.appendCodePoint(cp);
			}
			i += Character.charCount(cp);
		}
		return out.toString();
	}

	/** The same characters with the direction marks left out; apply it AFTER the reorder, so the marks still steered it. */
	public static FormattedCharSequence dropMarks(FormattedCharSequence seq) {
		return sink -> seq.accept((i, st, cp) -> isMark(cp) || sink.accept(i, st, cp));
	}

	/** {@link #ltr} for a value with no letters ("-10°", "0.50×"); a value with words ("60 fps", "-0.30 s") already reads right. */
	public static String ltrNumber(String text) {
		return text.codePoints().anyMatch(Character::isLetter) ? text : ltr(text);
	}

	/**
	 * A left-to-right run inside right-to-left text: the number "-2:35" or "0.50×" keeps its order and its sign and unit
	 * stay on their sides. Both ends are marked, since a trailing sign or unit would otherwise join the right-to-left side.
	 * Drawn through {@link #dropMarks} (the marks are dropped after the order is set).
	 */
	public static String ltr(String text) {
		return "\u200E" + text + "\u200E";
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
	private static final Style[] CANARY_STYLES = {Style.EMPTY, S_SMOOTH, S_BOLD, S_SCRIPT, S_ROUND};
	/** The probe is measured at most this often: callers ask up to three times a frame. */
	private static final long PROBE_GAP_NS = 250_000_000L;
	private static long lastProbe;
	private static boolean probed;
	/** How many probe strings were measured so far, for the test. */
	static int canaryMeasures;

	/**
	 * Notices a resource or font change that happened while nothing here was drawing: measures a probe text in each of
	 * the mod's fonts and the game's own, fresh, and drops the remembered widths when it moved. Call once per frame
	 * before the first {@link #cachedWidth}. This is used instead of a reload listener because it needs no per-version
	 * Fabric API or mixin, and it sees exactly what matters: glyph widths.
	 */
	static void checkFonts(Font font) {
		WIDTHS.mode(dev.beli.skylyrics.util.GlyphMode.current());
		checkFonts(font.getSplitter(), System.nanoTime());
	}

	/** {@link #checkFonts(Font)} at a given time; measures only when the last probe is 250 ms old or a reload was seen. */
	static void checkFonts(StringSplitter splitter, long now) {
		if (probed && now - lastProbe < PROBE_GAP_NS) {
			return;
		}
		probed = true;
		lastProbe = now;
		float sum = 0;
		for (Style style : CANARY_STYLES) {
			canaryMeasures++;
			sum = sum * 1.0009f + width(splitter, style, CANARY);
		}
		WIDTHS.probe(sum);
	}

	/** Forgets when the probe last ran, so the next {@link #checkFonts} measures again (test seam). */
	static void resetProbeGate() {
		probed = false;
	}

	/** Drops every remembered width: a resource reload can change glyph widths. Called while the loading screen is up. */
	static void fontsReloaded() {
		probed = false;
		WIDTHS.invalidate();
	}

	/** How many reloads the width cache has seen, for caches of results built from those widths. */
	static long reloadCount() {
		return WIDTHS.epoch();
	}
}
