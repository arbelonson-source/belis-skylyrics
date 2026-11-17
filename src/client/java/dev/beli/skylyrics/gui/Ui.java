package dev.beli.skylyrics.gui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import dev.beli.skylyrics.render.LyricsFont;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/** Colours and small drawing helpers shared by the settings screen. */
final class Ui {
	static final int PANEL = 0xFF141019;
	static final int SIDEBAR = 0xFF0F0C15;
	static final int CARD = 0xFF1C1725;
	static final int CARD_HOVER = 0xFF231D2E;
	static final int DIVIDER = 0xFF2A2433;
	static final int TEXT = 0xFFF2EEF8;
	static final int MUTED = 0xFF9C94AD;
	static final int DIM = 0xFF8A82A3;
	static final int ACCENT = 0xFFA678FF;
	static final int ACCENT_SOFT = 0x40A678FF;
	static final int TRACK = 0xFF3A3346;
	static final int CONTROL = 0xFF2B2536;
	static final int CONTROL_HOVER = 0xFF372F45;

	private Ui() {
	}

	/**
	 * Text in the order it is drawn: right-to-left words (Hebrew) reversed into place. Drawing a plain String
	 * skips this, which wrote the Hebrew settings backwards.
	 */
	static FormattedCharSequence visual(String text) {
		Language language = Language.getInstance();
		return visual(text, baseRtl(language.isDefaultRightToLeft(), text));
	}

	/** One line of a wrapped paragraph, laid out in the direction decided for the whole paragraph ({@link #paragraphRtl}). */
	static FormattedCharSequence visual(String line, boolean rtl) {
		Language language = Language.getInstance();
		return VISUAL.get(language, line, rtl, () ->
				// Direction marks (LRM and the like) steer the order, then are dropped: vanilla would draw each as a box
				LyricsFont.dropMarks(language.getVisualOrder(FormattedText.of(marked(line, rtl)))));
	}

	/** Drawn text remembered between frames: the same string comes back about 120 times a frame. Render thread only. */
	private static final VisualCache<FormattedCharSequence> VISUAL = new VisualCache<>(512);

	/** The base direction for a whole paragraph in the current game language. */
	static boolean paragraphRtl(String paragraph) {
		return baseRtl(Language.getInstance().isDefaultRightToLeft(), paragraph);
	}

	/**
	 * Right-to-left base direction for a piece of UI text: when the game language is right-to-left and the text has any
	 * right-to-left letter, otherwise when its first strong letter is right-to-left (what vanilla does by itself).
	 */
	static boolean baseRtl(boolean languageRtl, String text) {
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			i += Character.charCount(cp);
			byte d = Character.getDirectionality(cp);
			boolean r = d == Character.DIRECTIONALITY_RIGHT_TO_LEFT || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC;
			if (r && languageRtl) {
				return true;
			}
			if (!languageRtl && (r || d == Character.DIRECTIONALITY_LEFT_TO_RIGHT)) {
				return r; // the first strong letter decides
			}
		}
		return false;
	}

	/** The text with the mark that sets its base direction in front (RLM for right-to-left, LRM otherwise). */
	static String marked(String text, boolean rtl) {
		return (rtl ? "\u200F" : "\u200E") + text;
	}

	/** Filled rectangle with rounded corners, drawn without overlapping fills so alpha stays even. */
	static void rect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
		if (w <= 0 || h <= 0 || ARGB.alpha(color) == 0) {
			return;
		}
		r = Math.min(r, Math.min(w, h) / 2);
		if (r <= 0) {
			g.fill(x, y, x + w, y + h, color);
			return;
		}
		g.fill(x, y + r, x + w, y + h - r, color);
		for (int i = 0; i < r; i++) {
			float dy = r - i - 0.5f;
			int inset = Math.round(r - Mth.sqrt(r * r - dy * dy));
			g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
			g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
		}
	}

	/** One pixel outline with rounded corners. */
	static void outline(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
		r = Math.min(r, Math.min(w, h) / 2);
		int[] inset = new int[Math.max(r, 1)];
		for (int i = 0; i < r; i++) {
			float dy = r - i - 0.5f;
			inset[i] = Math.round(r - Mth.sqrt(r * r - dy * dy));
		}
		g.fill(x + inset[0], y, x + w - inset[0], y + 1, color);
		g.fill(x + inset[0], y + h - 1, x + w - inset[0], y + h, color);
		for (int i = 1; i < r; i++) {
			int from = inset[i];
			int to = Math.max(from + 1, inset[i - 1]);
			g.fill(x + from, y + i, x + to, y + i + 1, color);
			g.fill(x + w - to, y + i, x + w - from, y + i + 1, color);
			g.fill(x + from, y + h - 1 - i, x + to, y + h - i, color);
			g.fill(x + w - to, y + h - 1 - i, x + w - from, y + h - i, color);
		}
		g.fill(x, y + Math.max(r, 1), x + 1, y + h - Math.max(r, 1), color);
		g.fill(x + w - 1, y + Math.max(r, 1), x + w, y + h - Math.max(r, 1), color);
	}

	static int alpha(int argb, float a) {
		return ARGB.color(Math.round(ARGB.alpha(argb) * Mth.clamp(a, 0, 1)), argb & 0xFFFFFF);
	}

	static int lerp(float t, int a, int b) {
		return ARGB.srgbLerp(Mth.clamp(t, 0, 1), a, b);
	}

	static String clip(Font font, String text, int maxW) {
		if (maxW <= 0) {
			return "";
		}
		return CLIPS.get(Language.getInstance(), font, text, maxW, () -> clip(font, text, maxW, font.width(text)));
	}

	/** Cuts remembered between frames, per (font, text, room), dropped when the language object changes (a reload). Render thread only. */
	private static final ClipCache CLIPS = new ClipCache(512);

	/** {@link #clip(Font, String, int)} when the width of {@code text} is already known. */
	static String clip(Font font, String text, int maxW, int textW) {
		if (maxW <= 0) {
			return "";
		}
		if (textW <= maxW) {
			return text;
		}
		return CLIPS.get(Language.getInstance(), font, text, maxW, () -> clipBy(text, maxW, font::width));
	}

	/** The text cut to fit {@code maxW} and ending in "…", cut between whole characters (never inside a flag or an accent). */
	static String clipBy(String text, int maxW, java.util.function.ToIntFunction<String> width) {
		if (maxW <= 0) {
			return "";
		}
		if (width.applyAsInt(text) <= maxW) {
			return text;
		}
		String ellipsis = "…";
		int room = Math.max(0, maxW - width.applyAsInt(ellipsis));
		return dev.beli.skylyrics.util.TextCut.prefix(text, t -> width.applyAsInt(t) <= room).stripTrailing() + ellipsis;
	}

	/** Room for header text that starts at {@code textX} and must stop before something at {@code rightX}. */
	static int headerRoom(int textX, int rightX) {
		return Math.max(0, rightX - 6 - textX);
	}

	static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	/** Moves {@code value} towards {@code target} with a frame-rate independent ease. */
	static float approach(float value, float target, float dt, float speed) {
		float k = 1 - (float) Math.exp(-dt * speed);
		float next = value + (target - value) * k;
		return Math.abs(target - next) < 0.002f ? target : next;
	}

	/**
	 * A small least-recently-used memory of cut texts per (font, text, room), valid for one language object: a resource
	 * reload or language switch makes a new one and everything is dropped, as glyph widths may have changed. Render thread only.
	 */
	static final class ClipCache {
		private record Key(Object font, String text, int room) {}

		private final int limit;
		private final LinkedHashMap<Key, String> map;
		private Object language;

		ClipCache(int limit) {
			this.limit = limit;
			this.map = new LinkedHashMap<>(16, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<Key, String> eldest) {
					return size() > ClipCache.this.limit;
				}
			};
		}

		String get(Object language, Object font, String text, int room, Supplier<String> compute) {
			if (language != this.language) {
				map.clear();
				this.language = language;
			}
			Key key = new Key(font, text, room);
			String known = map.get(key);
			if (known == null) {
				known = compute.get();
				map.put(key, known);
			}
			return known;
		}

		int size() {
			return map.size();
		}
	}

	/**
	 * A small least-recently-used memory of values worked out per (text, direction), valid for one language object: when
	 * the game's {@code Language} instance changes (a language switch or resource reload) everything is dropped. The values
	 * must be immutable. Render thread only.
	 */
	static final class VisualCache<V> {
		private record Key(String text, boolean rtl) {}

		private final int limit;
		private final LinkedHashMap<Key, V> map;
		private Object language;

		VisualCache(int limit) {
			this.limit = limit;
			this.map = new LinkedHashMap<>(16, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<Key, V> eldest) {
					return size() > VisualCache.this.limit;
				}
			};
		}

		V get(Object language, String text, boolean rtl, Supplier<V> compute) {
			if (language != this.language) {
				map.clear();
				this.language = language;
			}
			Key key = new Key(text, rtl);
			V known = map.get(key);
			if (known == null) {
				known = compute.get();
				map.put(key, known);
			}
			return known;
		}

		int size() {
			return map.size();
		}
	}
}
