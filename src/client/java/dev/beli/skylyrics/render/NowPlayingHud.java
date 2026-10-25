package dev.beli.skylyrics.render;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import dev.beli.skylyrics.Tr;
import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.lyrics.LyricLine;
import dev.beli.skylyrics.lyrics.Lyrics;
import dev.beli.skylyrics.lyrics.LyricsService;
import dev.beli.skylyrics.media.MediaTracker;
import dev.beli.skylyrics.media.Track;

/**
 * The bar at the top of the screen: a dark rounded pill with the round cover, the line being
 * sung (the current word sits on a coloured highlight) and a little equaliser. Between lines,
 * and for a moment after the song changes, it shows the title and artist instead. It can also
 * show the time on its left.
 */
public final class NowPlayingHud {
	private static final long SHOW_NS = 6_000_000_000L;
	private static final long TITLE_NS = 3_500_000_000L;
	private static final long SWAP_NS = 260_000_000L;
	private static final int H = 20;
	private static final int ART = 14;
	private static final int PAD = 3;
	private static final int GAP = 6;
	private static final int EQ_W = 11;
	private static final int BG = 0x0E0C14;
	private static final int GREY = 0x9C96AE;
	private static final int CLOCK = 0xD6D1E3;
	/** From the bar's left edge to the clock, and from the clock to the divider and on to the cover. */
	private static final int CLOCK_INSET = 7;
	private static final int CLOCK_GAP = 5;
	/** How long the last line counts as being sung, for keeping the bar up. */
	private static final long LAST_LINE_MS = 8_000;
	/** Less room than this for the song, and the clock steps aside. */
	private static final float MIN_TEXT = 40;
	private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT);

	private final MediaTracker tracker;
	private final LyricsService service;
	private final AlbumArt art;

	private long shownAt;
	private long lastFrame;
	private float alpha;
	private boolean forced;

	private @Nullable Content content;
	private @Nullable Content old;
	private long swappedAt;
	private float width = -1;
	/** The clock's room, eased like {@link #width} so the text and the bar's edge move together. */
	private float lead = -1;
	private float hlX, hlW = -1;
	private int hlWord = -1;
	private final float[] eq = new float[4];
	private int clockMinute = -1;
	private String clockText = "";

	/** What the bar says: a lyric line, or the song's title and artist. */
	private record Content(String key, boolean lyric, String text, String sub, float sung) {
		Content sung(float s) {
			return new Content(key, lyric, text, sub, s);
		}
	}

	public NowPlayingHud(MediaTracker tracker, LyricsService service, AlbumArt art) {
		this.tracker = tracker;
		this.service = service;
		this.art = art;
	}

	/** Shows the song's name for a few seconds (called on song change and when the key is pressed). */
	public void flash() {
		shownAt = System.nanoTime();
	}

	public void toggleForced() {
		forced = !forced;
		flash();
	}

	public void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		SkyLyricsConfig cfg = SkyLyricsConfig.get();
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;

		Track track = tracker.track();
		Content line = cfg.barLyrics ? currentLine(cfg) : null;
		// Once the singing has started the bar stays, showing the song in the gaps instead of blinking off and on
		boolean info = forced || !cfg.hudOnlyOnChange || now - shownAt < SHOW_NS || cfg.barLyrics && singing(cfg);
		boolean want = cfg.enabled && cfg.nowPlayingHud && !track.equals(Track.NONE) && !mc.options.hideGui
				&& (info || line != null);
		alpha = want ? Math.min(1, alpha + dt * 4) : Math.max(0, alpha - dt * 3);
		if (alpha <= 0.01f) {
			content = null;
			width = -1;
			lead = -1;
			return;
		}

		Content next = line != null && now - shownAt >= TITLE_NS ? line : songInfo(track);
		if (content == null || !content.key().equals(next.key())) {
			old = content == null ? null : content.sung(1);
			swappedAt = now;
			hlWord = -1;
		}
		content = next;

		art.upload();
		Font font = mc.font;
		Style style = LyricsFont.base(cfg);
		float room = Math.min(g.guiWidth() - 40, 360) - (PAD + ART + GAP + 7 + EQ_W + 8);
		String clock = cfg.barClock ? clock() : "";
		float clockW = clock.isEmpty() ? 0 : LyricsFont.width(font, style, clock);
		// Room taken by the clock and its divider, before the cover
		float clockRoom = clock.isEmpty() ? 0 : CLOCK_INSET + clockW + 2 * CLOCK_GAP + 1 - PAD;
		if (room - clockRoom < MIN_TEXT) {
			clock = ""; // a very narrow window: the song matters more
			clockRoom = 0;
		}
		float k = 1 - (float) Math.exp(-dt * 12);
		lead = lead < 0 ? clockRoom : lead + (clockRoom - lead) * k;
		float maxText = Math.max(MIN_TEXT, room - lead);
		float textW = textWidth(font, style, content);
		float textScale = textW > maxText ? Math.max(0.8f, maxText / textW) : 1;
		float shown = Math.min(textW * textScale, maxText);
		float target = lead + PAD + ART + GAP + shown + 7 + EQ_W + 8;
		width = width < 0 ? target : width + (target - width) * k;

		float x = (g.guiWidth() - width) / 2f;
		float y = 5 - (1 - ease(alpha)) * 10;
		draw(g, font, style, mc, x, y, lead, clock, clockW, textScale, maxText, now, dt);
	}

	/** The time as the computer's language writes it, e.g. "21:07" or "9:07 PM". */
	private String clock() {
		LocalTime time = LocalTime.now();
		int minute = time.getHour() * 60 + time.getMinute();
		if (minute != clockMinute) {
			clockMinute = minute;
			// Some languages put a narrow no-break space before AM/PM, which the fonts don't have
			clockText = CLOCK_FORMAT.format(time).replace('\u202F', ' ').replace('\u00A0', ' ');
		}
		return clockText;
	}

	private void draw(GuiGraphics g, Font font, Style style, Minecraft mc, float x, float y, float lead, String clock, float clockW,
			float textScale, float maxText, long now, float dt) {
		float a = alpha;
		int accent = art.color();
		float s = (float) mc.getWindow().getGuiScale();

		// Shapes are drawn in real pixels so the round edges stay smooth at any GUI scale
		g.pose().pushMatrix();
		g.pose().scale(1 / s, 1 / s);
		float px = Math.round(x * s), py = Math.round(y * s), pw = Math.round(width * s), ph = H * s;
		roundRect(g, px, py, pw, ph, ph / 2, ARGB.color(Math.round(a * 46), 0xFFFFFF));
		roundRect(g, px + 1, py + 1, pw - 2, ph - 2, ph / 2 - 1, ARGB.color(Math.round(a * 205), BG));

		if (!clock.isEmpty()) {
			float divX = px + (CLOCK_INSET + clockW + CLOCK_GAP) * s;
			roundRect(g, divX, py + ph * 0.27f, Math.max(1, Math.round(s * 0.5f)), ph * 0.46f, 0, ARGB.color(Math.round(a * 40), 0xFFFFFF));
		}
		int artPx = Math.round(ART * s);
		int artX = Math.round(px + (lead + PAD) * s), artY = Math.round(py + (ph - artPx) / 2);
		if (art.hasTexture()) {
			g.blit(RenderPipelines.GUI_TEXTURED, AlbumArt.TEXTURE, artX, artY, 0, 0, artPx, artPx,
					AlbumArt.SIZE, AlbumArt.SIZE, AlbumArt.SIZE, AlbumArt.SIZE, ARGB.white(a));
		} else {
			roundRect(g, artX, artY, artPx, artPx, artPx / 2f, ARGB.color(Math.round(a * 255), LyricsRenderer.mix(accent, 0x000000, 0.45f)));
		}

		// Equaliser: four bars that bounce while the music plays and rest when it's paused
		float t = now / 1e9f;
		float barW = 2 * s, barGap = 1 * s, maxBar = 10 * s;
		float eqX = px + pw - (8 + EQ_W) * s;
		for (int i = 0; i < eq.length; i++) {
			float target = 0.2f;
			if (tracker.playing()) {
				target = 0.25f + 0.75f * Math.abs(Mth.sin(t * (5.1f + i * 1.7f) + i * 1.3f) * Mth.sin(t * (2.3f + i * 0.9f) + i));
			}
			eq[i] += (target - eq[i]) * Math.min(1, dt * 14);
			float bh = Math.max(barW, maxBar * eq[i]);
			roundRect(g, eqX + i * (barW + barGap), py + (ph - bh) / 2, barW, bh, barW / 2, ARGB.color(Math.round(a * 255), accent));
		}
		g.pose().popMatrix();
		if (!art.hasTexture()) {
			g.drawCenteredString(font, "♪", Math.round(x + lead + PAD + ART / 2f), Math.round(y + H / 2f - 4), ARGB.color(Math.round(a * 230), 0xFFFFFF));
		}
		if (!clock.isEmpty()) {
			g.pose().pushMatrix();
			g.pose().translate(x + CLOCK_INSET, y + H / 2f);
			text(g, font, style, clock, 0, ARGB.color(Math.max(4, Math.round(a * 255)), CLOCK));
			g.pose().popMatrix();
		}

		// Text, sliding in from below while the old text slides out the top
		float tx = x + lead + PAD + ART + GAP;
		float p = Mth.clamp((now - swappedAt) / (float) SWAP_NS, 0, 1);
		p = ease(p);
		g.enableScissor(Math.round(tx) - 4, Math.round(y) + 1, Math.round(tx + maxText) + 4, Math.round(y + H) - 1);
		if (old != null && p < 1) {
			drawContent(g, font, style, old, tx, y + H / 2f - p * 7, textScale, maxText, a * (1 - p), accent, false, dt);
		}
		if (content != null) {
			drawContent(g, font, style, content, tx, y + H / 2f + (1 - p) * 7, textScale, maxText, a * p, accent, true, dt);
		}
		g.disableScissor();
	}

	private void drawContent(GuiGraphics g, Font font, Style style, Content c, float x, float midY, float scale, float maxText,
			float a, int accent, boolean live, float dt) {
		if (a <= 0.02f) {
			return;
		}
		int white = ARGB.color(Math.max(4, Math.round(a * 255)), 0xFFFFFF);
		int grey = ARGB.color(Math.max(4, Math.round(a * 255)), GREY);
		g.pose().pushMatrix();
		g.pose().translate(x, midY);
		g.pose().scale(scale, scale);
		float limit = maxText / scale;
		if (!c.lyric()) {
			String title = clip(font, style, c.text(), limit);
			text(g, font, style, title, 0, white);
			float used = LyricsFont.width(font, style, title);
			if (!c.sub().isEmpty() && used < limit - 12) {
				text(g, font, style, clip(font, style, "  ·  " + c.sub(), limit - used), used, grey);
			}
		} else {
			drawLyric(g, font, style, c, limit, a, accent, white, grey, live, dt);
		}
		g.pose().popMatrix();
	}

	/** The line split into words: sung ones white, the current one on the highlight, the rest grey. */
	private void drawLyric(GuiGraphics g, Font font, Style style, Content c, float limit, float a, int accent,
			int white, int grey, boolean live, float dt) {
		String text = clip(font, style, c.text(), limit);
		float total = LyricsFont.width(font, style, text);
		boolean rtl = isRtl(text);
		int letters = 0;
		for (int i = 0; i < text.length(); i++) {
			letters += text.charAt(i) == ' ' ? 0 : 1;
		}
		float at = c.sung() * letters;

		int count = 0, current = -1;
		float curX = 0, curW = 0;
		int seen = 0;
		for (int start = 0; start < text.length(); ) {
			if (text.charAt(start) == ' ') {
				start++;
				continue;
			}
			int end = text.indexOf(' ', start);
			end = end < 0 ? text.length() : end;
			String word = text.substring(start, end);
			float wx = LyricsFont.width(font, style, text.substring(0, start));
			float ww = LyricsFont.width(font, style, word);
			if (rtl) {
				wx = total - wx - ww;
			}
			// The word being sung; once the line is done the highlight rests on its last word
			boolean now = c.sung() < 1 ? seen + word.length() > at : end == text.length();
			if (now && current < 0) {
				current = count;
				curX = wx;
				curW = ww;
			}
			seen += word.length();
			count++;
			start = end;
		}

		if (live && current >= 0) {
			if (hlWord < 0 || hlW < 0) {
				hlX = curX;
				hlW = curW;
			} else {
				float k = 1 - (float) Math.exp(-dt * 18);
				hlX += (curX - hlX) * k;
				hlW += (curW - hlW) * k;
			}
			hlWord = current;
			int hl = LyricsRenderer.mix(accent, 0x000000, 0.3f);
			roundRectGui(g, hlX - 2.5f, -6.5f, hlW + 5, 12, 3.5f, ARGB.color(Math.round(a * 235), hl));
		}

		// Second pass draws the words over the highlight
		seen = 0;
		for (int start = 0; start < text.length(); ) {
			if (text.charAt(start) == ' ') {
				start++;
				continue;
			}
			int end = text.indexOf(' ', start);
			end = end < 0 ? text.length() : end;
			String word = text.substring(start, end);
			float wx = LyricsFont.width(font, style, text.substring(0, start));
			if (rtl) {
				wx = total - wx - LyricsFont.width(font, style, word);
			}
			boolean upcoming = c.sung() < 1 && seen > at;
			text(g, font, style, word, wx, upcoming ? grey : white);
			seen += word.length();
			start = end;
		}
	}

	private void text(GuiGraphics g, Font font, Style style, String text, float x, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, -4);
		g.drawString(font, Language.getInstance().getVisualOrder(FormattedText.of(text, style)), 0, 0, color, false);
		g.pose().popMatrix();
	}

	private long lyricsTime(SkyLyricsConfig cfg) {
		return tracker.positionMs() + cfg.offsetFor(tracker.track()) + LyricsRenderer.LEAD_MS;
	}

	/**
	 * The song is playing between its first and last line (it may be a gap between lines now). Paused, or in a
	 * long outro after the last line, the bar goes back to hiding.
	 */
	private boolean singing(SkyLyricsConfig cfg) {
		Lyrics lyrics = service.lyrics();
		if (!tracker.playing() || !lyrics.synced() || lyrics.isEmpty()) {
			return false;
		}
		long time = lyricsTime(cfg);
		return lyrics.indexAt(time) >= 0 && time <= lyrics.get(lyrics.lines().size() - 1).timeMs() + LAST_LINE_MS;
	}

	private @Nullable Content currentLine(SkyLyricsConfig cfg) {
		Lyrics lyrics = service.lyrics();
		if (!lyrics.synced() || lyrics.isEmpty()) {
			return null;
		}
		long time = lyricsTime(cfg);
		int i = lyrics.indexAt(time);
		if (i < 0 || i >= lyrics.lines().size()) {
			return null;
		}
		LyricLine line = lyrics.get(i);
		if (line.isBreak() || line.text().isBlank()) {
			return null;
		}
		long end = Math.min(lyrics.endOf(i, line.timeMs() + 5000), line.timeMs() + 12000);
		if (time > end + 1500) {
			return null; // a long gap with no marker: show the song instead of a stale line
		}
		float sung = cfg.karaoke ? Mth.clamp((time - line.timeMs()) / (float) Math.max(1, end - line.timeMs()), 0, 1) : 1;
		String text = cfg.uppercase ? line.text().toUpperCase(Locale.ROOT) : line.text();
		return new Content("L" + i + text, true, text.strip(), "", sung);
	}

	private Content songInfo(Track track) {
		String sub = track.artist().isEmpty() ? tracker.sourceName() : track.artist();
		String status = switch (service.state()) {
			case LOADING -> Tr.t("finding lyrics…");
			case NOT_FOUND -> Tr.t("no lyrics");
			case ERROR -> Tr.t("offline");
			default -> "";
		};
		if (!status.isEmpty()) {
			sub = sub.isEmpty() ? status : sub + "  ·  " + status;
		}
		String title = track.title().isEmpty() ? Tr.t("Unknown song") : track.title();
		return new Content("S" + title + "\n" + sub, false, title, sub, 1);
	}

	private static float textWidth(Font font, Style style, Content c) {
		float w = LyricsFont.width(font, style, c.text());
		if (!c.lyric() && !c.sub().isEmpty()) {
			w += LyricsFont.width(font, style, "  ·  " + c.sub());
		}
		return w;
	}

	private static boolean isRtl(String text) {
		for (int i = 0; i < text.length(); i++) {
			byte d = Character.getDirectionality(text.charAt(i));
			if (d == Character.DIRECTIONALITY_RIGHT_TO_LEFT || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
				return true;
			}
			if (d == Character.DIRECTIONALITY_LEFT_TO_RIGHT) {
				return false;
			}
		}
		return false;
	}

	/** Cuts the text to fit, ending with "…". */
	static String clip(Font font, Style style, String text, float maxW) {
		if (LyricsFont.width(font, style, text) <= maxW) {
			return text;
		}
		float room = maxW - LyricsFont.width(font, style, "…");
		int lo = 0, hi = text.length();
		while (lo < hi) {
			int mid = (lo + hi + 1) / 2;
			if (LyricsFont.width(font, style, text.substring(0, mid)) <= room) {
				lo = mid;
			} else {
				hi = mid - 1;
			}
		}
		return text.substring(0, lo).stripTrailing() + "…";
	}

	/** {@link #roundRect} in GUI units, drawn in real pixels. */
	private static void roundRectGui(GuiGraphics g, float x, float y, float w, float h, float r, int color) {
		float s = (float) Minecraft.getInstance().getWindow().getGuiScale();
		g.pose().pushMatrix();
		g.pose().scale(1 / s, 1 / s);
		roundRect(g, x * s, y * s, w * s, h * s, r * s, color);
		g.pose().popMatrix();
	}

	/** A rounded rectangle in the current pose's units, with smooth curves. */
	static void roundRect(GuiGraphics g, float x, float y, float w, float h, float r, int color) {
		RoundRect.draw(g, x, y, w, h, r, color);
	}

	private static float ease(float t) {
		return 1 - (1 - t) * (1 - t);
	}
}
