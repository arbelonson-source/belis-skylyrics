package dev.beli.skylyrics.render;

import static dev.beli.skylyrics.render.TopBarLayout.ART;
import static dev.beli.skylyrics.render.TopBarLayout.ART_INSET;
import static dev.beli.skylyrics.render.TopBarLayout.BARS;
import static dev.beli.skylyrics.render.TopBarLayout.BAR_GAP;
import static dev.beli.skylyrics.render.TopBarLayout.BAR_W;
import static dev.beli.skylyrics.render.TopBarLayout.CLIP_GAP;
import static dev.beli.skylyrics.render.TopBarLayout.CLOCK_GAP;
import static dev.beli.skylyrics.render.TopBarLayout.EQ_INSET;
import static dev.beli.skylyrics.render.TopBarLayout.EQ_W;
import static dev.beli.skylyrics.render.TopBarLayout.PILL_H;
import static dev.beli.skylyrics.render.TopBarLayout.SPIN_HOLE;
import static dev.beli.skylyrics.render.TopBarLayout.SPIN_TURNS;
import static dev.beli.skylyrics.render.TopBarLayout.STATS_GAP;
import static dev.beli.skylyrics.render.TopBarLayout.TEXT_GAP;
import static dev.beli.skylyrics.render.TopBarLayout.WORD_EXTRA;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import dev.beli.skylyrics.Tr;
import dev.beli.skylyrics.SkyLyricsClient;
import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.config.SkyLyricsConfig.BarAccent;
import dev.beli.skylyrics.config.SkyLyricsConfig.BarPosition;
import dev.beli.skylyrics.gui.SkyLyricsScreen;
import dev.beli.skylyrics.lyrics.LyricLine;
import dev.beli.skylyrics.lyrics.Lyrics;
import dev.beli.skylyrics.lyrics.LyricsService;
import dev.beli.skylyrics.media.MediaTracker;
import dev.beli.skylyrics.media.Track;
import dev.beli.skylyrics.mixin.BossHealthOverlayAccessor;

/**
 * The bar at the top of the screen: a dark capsule, sized to its text and centred, with the round cover, the line
 * being sung (the last words sung sit on a violet highlight) and a little equaliser. Between lines, and for a
 * moment after the song changes, it shows the title and artist instead. The time can sit on its left, and the
 * connection and frame rate on its right. Sizes are in {@link TopBarLayout}.
 */
public final class NowPlayingHud {
	private static final long SHOW_NS = 6_000_000_000L;
	private static final long TITLE_NS = 3_500_000_000L;
	/** Old and new text crossfade in place this long. */
	private static final long SWAP_NS = 180_000_000L;
	/** The pill's resize when the text changes, eased out. */
	private static final long RESIZE_NS = 290_000_000L;
	/** Highlight edges close about a quarter of the way to their target per frame at 60 fps, quick enough to keep up with fast words. */
	private static final float HL_RATE = 18;
	/** How fast a scrolled long line follows the word being sung, and eases back once the pill has grown. */
	private static final float SCROLL_RATE = 5;
	/** At a line's start the highlight grows out of a sliver this wide at the first word. */
	private static final float HL_START_W = 2.5f;
	private static final float HL_H = 12;
	private static final float HL_RADIUS = 3;

	/** Smooth-font text is drawn smaller than its natural size, like the reference's light line. */
	private static final float TEXT_SCALE = 0.75f;
	private static final float CLOCK_SCALE = 0.85f;
	/** Minecraft's own font is pixel art: any size but whole ones would blur it. */
	private static final float PIXEL_FONT_SCALE = 1;

	private static final int FILL = 0x181523;
	/** Opaque: the reference's fill reads the same over any sky. */
	private static final int FILL_ALPHA = 255;
	private static final int BORDER = 0x4B4665;
	/** The border: two screen pixels at GUI scale 3. */
	private static final float BORDER_W = 2 / 3f;
	private static final int SUNG = 0xFCF8FF;
	private static final int UPCOMING = 0x8C879E;
	private static final int HIGHLIGHT = 0x926CDE;
	private static final int EQ_COLOR = 0xA076F4;
	private static final int CLOCK = 0xF0F0F0;
	private static final int STAT_TEXT = 0x8A8699;
	private static final int SIGNAL_OFF = 0x4E3C62;
	/** The cover's stand-in when the song has none. */
	private static final int NO_ART = 0x3A2E5C;

	/** Equaliser heights, from resting to the tallest. */
	private static final float EQ_MIN = 2, EQ_MAX = 20 / 3f;
	/** The song progress line: how far its top sits from the pill's bottom, and how thick it is. */
	private static final float PROGRESS_UP = 7 / 3f, PROGRESS_H = 2 / 3f;
	/** The unplayed part of the progress line, as a share of the accent's brightness. */
	private static final float PROGRESS_TRACK = 0.25f;
	/** The equaliser picks new heights a few times a second, like the reference's, not every frame. */
	private static final float EQ_STEPS_PER_S = 4;
	/** Signal bars: their heights, bottoms level a bit below the pill's middle, and the gap to the fps text. */
	private static final float[] SIGNAL_H = {5 / 3f, 11 / 3f, 17 / 3f, 23 / 3f};
	private static final float SIGNAL_BOTTOM = 3.8f;
	private static final float SIGNAL_TEXT_GAP = 5;
	private static final float SIGNAL_W = BARS * BAR_W + (BARS - 1) * BAR_GAP;
	/** A unit, written the same in every language like the debug screen's. */
	private static final String FPS = " fps";
	/** Room kept for the frame rate, so the bar doesn't twitch as the digits change. */
	private static final String FPS_SAMPLE = "000" + FPS;

	/** How long the last line counts as being sung, for keeping the bar up. */
	private static final long LAST_LINE_MS = 8_000;
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
	/** The pill's width, tweened from {@link #widthFrom} to {@link #widthTo} since {@link #resizedAt}. */
	private float width = -1, widthFrom, widthTo, widthSpeed;
	private long resizedAt;
	/** Where the bar's top edge is, eased: below any boss bars. */
	private float top = -1;
	/** The highlight's edges, from the line's start; negative when it has to start over. */
	private float hlLeft, hlRight = -1;
	/** How far a long line is scrolled, and the old line's while it fades. */
	private float scroll, oldScroll;
	private final float[] eq = new float[BARS];
	/** The spinning cover's angle (radians) and speed (0 to 1, see {@link TopBarLayout#spinSpeed}). */
	private float spin, spinSpeed;
	private int clockMinute = -1;
	private String clockText = "";

	/** What the bar says: a lyric line, or the song's title and artist. */
	/** {@code through} is how far the song is through the line's time, 0 to 1, karaoke or not. */
	private record Content(String key, boolean lyric, String text, String sub, float sung, float through) {}

	/** A word of the line, placed from the line's start in GUI units. */
	private record Word(String text, float x, float w, boolean sung) {}

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
		//? if >=26.2 {
		/*boolean want = cfg.enabled && cfg.nowPlayingHud && !track.equals(Track.NONE) && !mc.gui.hud.isHidden()
		*///?} else {
		boolean want = cfg.enabled && cfg.nowPlayingHud && !track.equals(Track.NONE) && !mc.options.hideGui
		//?}
				&& (info || line != null)
				&& !Visibility.hidden(mc, cfg)
				&& (cfg.barShow == SkyLyricsConfig.BarShow.ALWAYS || !LyricsInView.onScreen())
				//? if >=26.2 {
				/*&& !(mc.gui.screen() instanceof SkyLyricsScreen settings && settings.coversTopBar());
				*///?} else {
				&& !(mc.screen instanceof SkyLyricsScreen settings && settings.coversTopBar());
				//?}
		alpha = want ? Math.min(1, alpha + dt * 4) : Math.max(0, alpha - dt * 3);
		if (alpha <= 0.01f) {
			content = null;
			old = null;
			width = -1;
			widthSpeed = 0;
			top = -1;
			return;
		}

		Content next = line != null && now - shownAt >= TITLE_NS ? line : songInfo(track);
		if (content == null || !content.key().equals(next.key())) {
			old = content;
			oldScroll = scroll;
			swappedAt = now;
			hlRight = -1;
			scroll = 0;
		}
		content = next;

		art.upload();
		Font font = mc.font;
		Style style = LyricsFont.base(cfg);
		float textScale = cfg.smoothFont ? TEXT_SCALE : PIXEL_FONT_SCALE;
		Style clockStyle = cfg.smoothFont ? Style.EMPTY.withFont(LyricsFont.BOLD) : Style.EMPTY.withBold(true);
		float clockScale = cfg.smoothFont ? CLOCK_SCALE : PIXEL_FONT_SCALE;

		String clock = cfg.barClock ? clock() : "";
		float clockW = clock.isEmpty() ? 0 : LyricsFont.width(font, clockStyle, clock) * clockScale;
		float statsW = statsWidth(cfg, font, style, textScale);
		TopBarLayout.Fit fit = TopBarLayout.fit(g.guiWidth(), clockW, statsW);

		float target = TopBarLayout.pillFor(contentWidth(font, style, textScale, next), fit.pill());
		if (width < 0) {
			width = widthFrom = widthTo = target;
			widthSpeed = 0;
		} else if (Math.abs(target - widthTo) > 0.25f) {
			widthTo = target;
		}
		float spring = 15;
		int steps = Math.max(1, (int) Math.ceil(dt * 240));
		float step = dt / steps;
		for (int i = 0; i < steps; i++) {
			widthSpeed += (spring * spring * (widthTo - width) - 2 * spring * widthSpeed) * step;
			width += widthSpeed * step;
		}

		float x = (g.guiWidth() - width) / 2f;
		float topTarget = cfg.barPosition == BarPosition.BOTTOM
				? g.guiHeight() - 54 - PILL_H
				: topBelowBossBars(mc, g.guiHeight());
		float k = 1 - (float) Math.exp(-dt * 12);
		top = top < 0 ? topTarget : top + (topTarget - top) * k;
		float y = top - (1 - easeOut(alpha)) * 10;

		float barSize = cfg.barSize;
		g.pose().pushMatrix();
		g.pose().translate((1 - barSize) * g.guiWidth() / 2f, (1 - barSize) * y);
		g.pose().scale(barSize, barSize);
		drawPill(g, mc, x, y, now, dt);
		float midY = y + PILL_H / 2f;
		if (fit.clock()) {
			text(g, font, clockStyle, clock, x - CLOCK_GAP - clockW, midY, clockScale, fade(alpha, CLOCK));
		}
		if (fit.stats()) {
			drawStats(g, mc, font, style, textScale, x + width + STATS_GAP, midY);
		}
		drawText(g, font, style, textScale, x, midY, now, dt);
		g.pose().popMatrix();
	}

	/**
	 * Boss bars (a dragon, a wither, server events) are drawn from the top of the screen, 19 units apart, as many
	 * as fit in the top third. The bar goes just below the last one instead of covering their names.
	 */
	static float topBelowBossBars(Minecraft mc, int guiHeight) {
		//? if >=26.2 {
		/*int count = ((BossHealthOverlayAccessor) mc.gui.hud.getBossOverlay()).skylyrics$getEvents().size();
		*///?} else {
		int count = ((BossHealthOverlayAccessor) mc.gui.getBossOverlay()).skylyrics$getEvents().size();
		//?}
		return topBelowBossBars(count, guiHeight);
	}

	static float topBelowBossBars(int count, int guiHeight) {
		int shown = 0;
		for (int y = 12; shown < count && y < guiHeight / 3; y += 19) {
			shown++;
		}
		return shown == 0 ? 5 : 12 + 19 * (shown - 1) + 5 + 4;
	}

	/** The time as the computer's language writes it, e.g. "21:07" or "9:07 PM". */
	private String clock() {
		LocalTime time = LocalTime.now();
		int minute = time.getHour() * 60 + time.getMinute();
		if (minute != clockMinute) {
			clockMinute = minute;
			// Some languages put a narrow no-break space before AM/PM, which the fonts don't have
			clockText = CLOCK_FORMAT.format(time).replace(' ', ' ').replace(' ', ' ');
		}
		return clockText;
	}

	/** The capsule, its border, the cover and the equaliser, drawn in real pixels so round edges stay smooth. */
	private void drawPill(GuiGraphics g, Minecraft mc, float x, float y, long now, float dt) {
		float a = alpha;
		float s = (float) mc.getWindow().getGuiScale();
		// The cover and equaliser dim for a moment while the text crossfades
		float swap = Mth.clamp((now - swappedAt) / (float) SWAP_NS, 0, 1);
		float dim = old == null ? 1 : 1 - 0.35f * Mth.sin(swap * Mth.PI);

		g.pose().pushMatrix();
		g.pose().scale(1 / s, 1 / s);
		float px = Math.round(x * s), py = Math.round(y * s), pw = Math.round(width * s), ph = Math.round(PILL_H * s);
		float b = Math.max(1, Math.round(BORDER_W * s));
		roundRect(g, px, py, pw, ph, ph / 2, ARGB.color(Math.round(a * 255), BORDER));
		SkyLyricsConfig cfg = SkyLyricsConfig.get();
		roundRect(g, px + b, py + b, pw - 2 * b, ph - 2 * b, ph / 2 - b,
				ARGB.color(Math.round(a * FILL_ALPHA * cfg.barBackground), FILL));

		int artPx = Math.round(ART * s);
		int artX = Math.round(px + ART_INSET * s), artY = Math.round(py + (ph - artPx) / 2);
		spinSpeed = cfg.barSpin ? TopBarLayout.spinSpeed(spinSpeed, tracker.playing(), dt) : 0;
		spin = cfg.barSpin ? (spin + spinSpeed * SPIN_TURNS * Mth.TWO_PI * dt) % Mth.TWO_PI : 0;
		if (cfg.barArt && art.hasTexture()) {
			float cx = artX + artPx / 2f, cy = artY + artPx / 2f;
			g.pose().pushMatrix();
			g.pose().translate(cx, cy);
			g.pose().rotate(spin);
			g.pose().translate(-cx, -cy);
			// The texture is already cut round (AlbumArt)
			g.blit(RenderPipelines.GUI_TEXTURED, AlbumArt.TEXTURE, artX, artY, 0, 0, artPx, artPx,
					AlbumArt.SIZE, AlbumArt.SIZE, AlbumArt.SIZE, AlbumArt.SIZE, ARGB.white(a * dim));
			g.pose().popMatrix();
			if (cfg.barSpin) {
				// A record's middle: a dark hole with a thin bright rim
				float hole = Math.max(3, Math.round(artPx * SPIN_HOLE)), rim = Math.max(1, Math.round(s / 2));
				roundRect(g, cx - hole / 2 - rim, cy - hole / 2 - rim, hole + 2 * rim, hole + 2 * rim, hole / 2 + rim,
						ARGB.color(Math.round(a * dim * 150), 0xFFFFFF));
				roundRect(g, cx - hole / 2, cy - hole / 2, hole, hole, hole / 2, ARGB.color(Math.round(a * 255), FILL));
			}
		} else if (cfg.barArt) {
			roundRect(g, artX, artY, artPx, artPx, artPx / 2f, ARGB.color(Math.round(a * dim * 255), NO_ART));
		}

		// Equaliser: four bars that hop to new heights while the music plays and rest when it's paused
		float barW = BAR_W * s, barGap = BAR_GAP * s;
		float eqX = px + pw - (EQ_INSET + EQ_W) * s;
		for (int i = 0; i < eq.length; i++) {
			float target = 0;
			if (tracker.playing()) {
				// Each bar changes on its own beat, a quarter step after the one before
				long n = (long) (now / 1e9 * EQ_STEPS_PER_S + i * 0.25f);
				target = 0.15f + 0.85f * hash01(n * 31 + i * 7919);
			}
			eq[i] += (target - eq[i]) * Math.min(1, dt * 16);
			float bh = Math.round(Math.max(barW, (EQ_MIN + (EQ_MAX - EQ_MIN) * eq[i]) * s));
			if (cfg.barEqualiser) {
				roundRect(g, Math.round(eqX + i * (barW + barGap)), Math.round(py + (ph - bh) / 2), barW, bh, barW / 2,
						ARGB.color(Math.round(a * dim * 255), accent(cfg)));
			}
		}

		// Song progress: a thin line under the words that fills as the song plays
		Track track = tracker.track();
		float done = track == null ? -1 : TopBarLayout.progress(tracker.positionMs(), track.lengthMs());
		if (cfg.barProgress && done >= 0) {
			float lx = Math.round((x + ART_INSET + ART + TEXT_GAP) * s);
			float lw = Math.round(TopBarLayout.textRoom(width) * s);
			float lh = Math.max(1, Math.round(PROGRESS_H * s)), ly = Math.round(py + ph - PROGRESS_UP * s);
			roundRect(g, lx, ly, lw, lh, lh / 2, ARGB.color(Math.round(a * PROGRESS_TRACK * 255), accent(cfg)));
			roundRect(g, lx, ly, Math.max(lh, Math.round(lw * done)), lh, lh / 2,
					ARGB.color(Math.round(a * 255), accent(cfg)));
		}
		g.pose().popMatrix();
		if (cfg.barArt && !art.hasTexture()) {
			Font font = mc.font;
			g.pose().pushMatrix();
			g.pose().translate(x + ART_INSET + ART / 2f, y + PILL_H / 2f);
			g.pose().scale(0.85f, 0.85f);
			g.drawCenteredString(font, "♪", 0, -4, ARGB.color(Math.round(a * dim * 230), 0xFFFFFF));
			g.pose().popMatrix();
		}
	}

	/** A number from 0 to 1 that looks random but is the same for the same seed. */
	private static float hash01(long seed) {
		long z = seed * 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 31)) * 0xBF58476D1CE4E5B9L;
		return ((z ^ (z >>> 29)) >>> 40) / (float) (1L << 24);
	}

	/** Room for the signal bars and the frame rate, whichever are on; 0 when neither is. */
	private float statsWidth(SkyLyricsConfig cfg, Font font, Style style, float textScale) {
		float w = cfg.barPing ? SIGNAL_W : 0;
		if (cfg.barStats) {
			w += (cfg.barPing ? SIGNAL_TEXT_GAP : 0) + LyricsFont.width(font, style, FPS_SAMPLE) * textScale;
		}
		return w;
	}

	/** Signal bars (lit by ping on a server, dim in single player), then the frame rate; each can be off. */
	private void drawStats(GuiGraphics g, Minecraft mc, Font font, Style style, float textScale, float x, float midY) {
		SkyLyricsConfig cfg = SkyLyricsConfig.get();
		float a = alpha;
		if (cfg.barStats) {
			float tx = cfg.barPing ? x + SIGNAL_W + SIGNAL_TEXT_GAP : x;
			text(g, font, style, mc.getFps() + FPS, tx, midY, textScale, fade(a, STAT_TEXT));
		}
		if (!cfg.barPing) {
			return;
		}
		int lit = 0;
		ClientPacketListener connection = mc.getConnection();
		if (!mc.isLocalServer() && connection != null && mc.player != null) {
			PlayerInfo me = connection.getPlayerInfo(mc.player.getUUID());
			lit = TopBarLayout.signalBars(me == null ? -1 : me.getLatency());
		}
		float s = (float) mc.getWindow().getGuiScale();
		g.pose().pushMatrix();
		g.pose().scale(1 / s, 1 / s);
		float barW = Math.round(BAR_W * s), pitch = (BAR_W + BAR_GAP) * s;
		float bottom = Math.round((midY + SIGNAL_BOTTOM) * s);
		for (int i = 0; i < SIGNAL_H.length; i++) {
			float h = Math.max(barW, Math.round(SIGNAL_H[i] * s));
			int color = i < lit ? accent(cfg) : SIGNAL_OFF;
			roundRect(g, Math.round(x * s + i * pitch), bottom - h, barW, h, barW / 2, ARGB.color(Math.round(a * 255), color));
		}
		g.pose().popMatrix();
	}

	/** The line or the song's name; the old one fades out where the new one fades in. */
	private void drawText(GuiGraphics g, Font font, Style style, float scale, float x, float midY, long now, float dt) {
		float tx = x + ART_INSET + ART + TEXT_GAP;
		float room = TopBarLayout.textRoom(width);
		float p = Mth.clamp((now - swappedAt) / (float) SWAP_NS, 0, 1);
		int clipLeft = Math.round(x + ART_INSET + ART + CLIP_GAP);
		// Words past the room stop where the highlight would, a little before the equaliser
		int clipRight = Math.round(tx + room);
		g.enableScissor(clipLeft, Math.round(midY - PILL_H / 2f) + 1, clipRight, Math.round(midY + PILL_H / 2f) - 1);
		if (old != null && p < 1) {
			drawContent(g, font, style, scale, old, tx, midY, room, alpha * (1 - p), false, dt);
		} else {
			old = null;
		}
		if (content != null) {
			drawContent(g, font, style, scale, content, tx, midY, room, alpha * (old == null ? 1 : p), true, dt);
		}
		g.disableScissor();
	}

	private void drawContent(GuiGraphics g, Font font, Style style, float scale, Content c, float x, float midY, float room,
			float a, boolean live, float dt) {
		if (a <= 0.02f) {
			return;
		}
		if (!c.lyric()) {
			// The song's name doesn't scroll: while the pill is narrow it ends in "…"
			float limit = room / scale;
			//? if >=26.1 {
			/*String title = clip(font, style, c.drawString(), limit);
			*///?} else {
			String title = clip(font, style, c.text(), limit);
			//?}
			text(g, font, style, title, x, midY, scale, fade(a, SUNG));
			float used = LyricsFont.width(font, style, title);
			if (!c.sub().isEmpty() && used < limit - 12) {
				text(g, font, style, clip(font, style, "  ·  " + c.sub(), limit - used), x + used * scale, midY, scale, fade(a, UPCOMING));
			}
			return;
		}
		drawLyric(g, font, style, scale, c, x, midY, room, a, live, dt);
	}

	/** The line word by word: sung words white, the rest grey, and the last two sung on the highlight. */
	private void drawLyric(GuiGraphics g, Font font, Style style, float scale, Content c, float x, float midY, float room,
			float a, boolean live, float dt) {
		//? if >=26.1 {
		/*Word[] words = words(font, style, scale, c.drawString(), c.sung());
		*///?} else {
		Word[] words = words(font, style, scale, c.text(), c.sung());
		//?}
		if (words.length == 0) {
			return;
		}
		//? if >=26.1 {
		/*boolean rtl = isRtl(c.drawString());
		*///?} else {
		boolean rtl = isRtl(c.text());
		//?}
		float total = words[words.length - 1].x() + words[words.length - 1].w();
		int current = 0;
		for (int i = 0; i < words.length; i++) {
			if (words[i].sung()) {
				current = i;
			}
		}
		Word cur = words[current], prev = words[Math.max(0, current - 1)];
		float pad = TopBarLayout.highlightPad(wordGap(font, style, scale));

		// Long lines scroll, in reading order, to keep the word being sung in view
		float target = TopBarLayout.lineShift(total, cur.x() + cur.w(), room, pad);
		float k = 1 - (float) Math.exp(-dt * SCROLL_RATE);
		float shift;
		if (live) {
			scroll += (target - scroll) * k;
			shift = scroll;
		} else {
			shift = oldScroll;
		}
		// Positions are kept in reading order; a right-to-left line is mirrored, its start at the right
		float start;
		if (rtl) {
			start = (total + pad > room ? x + room - pad : x + total) + shift;
		} else {
			start = x - shift;
		}

		if (live) {
			float left = prev.x(), right = cur.x() + cur.w();
			if (hlRight < 0) {
				hlLeft = words[0].x();
				hlRight = hlLeft + HL_START_W;
			}
			float hk = 1 - (float) Math.exp(-dt * HL_RATE);
			hlLeft += (left - hlLeft) * hk;
			hlRight += (right - hlRight) * hk;
			float x0 = rtl ? start - hlRight : start + hlLeft;
			float hw = hlRight - hlLeft;
			roundRectGui(g, x0 - pad, midY - HL_H / 2f, hw + 2 * pad, HL_H, HL_RADIUS,
					ARGB.color(Math.round(a * 255), accent(SkyLyricsConfig.get())));
		}

		int sung = fade(a, SUNG), upcoming = fade(a, UPCOMING);
		for (Word w : words) {
			// The old line greys out the moment it is replaced
			int color = live && w.sung() ? sung : upcoming;
			float wx = rtl ? start - w.x() - w.w() : start + w.x();
			//? if >=26.1 {
			/*text(g, font, style, w.drawString(), wx, midY, scale, color);
			*///?} else {
			text(g, font, style, w.text(), wx, midY, scale, color);
			//?}
		}
	}

	/** The gap between the line's words, in GUI units. */
	private static float wordGap(Font font, Style style, float scale) {
		return LyricsFont.width(font, style, " ") * scale + WORD_EXTRA;
	}

	/**
	 * The line's words in reading order, a space (and a little) apart. A word turns white as a whole once its
	 * singing starts; the share of the line sung is counted in letters.
	 */
	private static Word[] words(Font font, Style style, float scale, String text, float sungShare) {
		int letters = 0;
		for (int i = 0; i < text.length(); i++) {
			letters += text.charAt(i) == ' ' ? 0 : 1;
		}
		float at = sungShare * letters;
		String[] parts = text.trim().split(" +");
		if (parts.length == 1 && parts[0].isEmpty()) {
			return new Word[0];
		}
		Word[] out = new Word[parts.length];
		float gap = wordGap(font, style, scale);
		float x = 0;
		int seen = 0;
		for (int i = 0; i < parts.length; i++) {
			float w = LyricsFont.width(font, style, parts[i]) * scale;
			boolean sung = seen <= at || sungShare >= 1;
			out[i] = new Word(parts[i], x, w, sung);
			x += w + gap;
			seen += parts[i].length();
		}
		return out;
	}

	/** Draws text with its middle at {@code midY}, scaled about its left end. */
	private static void text(GuiGraphics g, Font font, Style style, String text, float x, float midY, float scale, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, midY);
		g.pose().scale(scale, scale);
		g.drawString(font, Language.getInstance().getVisualOrder(FormattedText.of(text, style)), 0, -4, color, false);
		g.pose().popMatrix();
	}

	private static int fade(float a, int rgb) {
		return ARGB.color(Math.max(4, Math.round(a * 255)), rgb);
	}

	private static int accent(SkyLyricsConfig cfg) {
		return switch (cfg.barAccent) {
			case ALBUM -> SkyLyricsClient.get().art().color();
			case LYRICS -> SkyLyricsClient.get().renderer().color();
			case FIXED -> cfg.barAccentColor;
		};
	}

	/** The line being sung right now, as the bar would show it, or null between lines. */
	@Nullable String singingNow(SkyLyricsConfig cfg) {
		Content line = currentLine(cfg);
		return line == null ? null : line.text();
	}

	/** How far the song is through the line being sung, 0 to 1 (0 between lines). */
	float singingThrough(SkyLyricsConfig cfg) {
		Content line = currentLine(cfg);
		return line == null ? 0 : line.through();
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
		//? if >=26.1 {
		/*if (line.isBreak() || line.drawString().isBlank()) {
		*///?} else {
		if (line.isBreak() || line.text().isBlank()) {
		//?}
			return null;
		}
		if (time > lyrics.lineEnd(i) + 1500) {
			return null; // a long gap with no marker: show the song instead of a stale line
		}
		long end = lyrics.sungEnd(i);
		float through = Mth.clamp((time - line.timeMs()) / (float) Math.max(1, end - line.timeMs()), 0, 1);
		float sung = cfg.karaoke ? through : 1;
		//? if >=26.1 {
		/*String text = cfg.uppercase ? WorldWords.capitals(line.drawString()) : line.drawString();
		*///?} else {
		String text = cfg.uppercase ? WorldWords.capitals(line.text()) : line.text();
		//?}
		return new Content("L" + i + text, true, text.strip(), "", sung, through);
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
		return new Content("S" + title + "\n" + sub, false, title, sub, 1, 1);
	}

	/** The text's width on screen, in GUI units. */
	private static float contentWidth(Font font, Style style, float scale, Content c) {
		if (c.lyric()) {
			//? if >=26.1 {
			/*Word[] words = words(font, style, scale, c.drawString(), 1);
			*///?} else {
			Word[] words = words(font, style, scale, c.text(), 1);
			//?}
			return words.length == 0 ? 0 : words[words.length - 1].x() + words[words.length - 1].w();
		}
		//? if >=26.1 {
		/*float w = LyricsFont.width(font, style, c.drawString());
		*///?} else {
		float w = LyricsFont.width(font, style, c.text());
		//?}
		if (!c.sub().isEmpty()) {
			w += LyricsFont.width(font, style, "  ·  " + c.sub());
		}
		return w * scale;
	}

	static boolean isRtl(String text) {
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
	static void roundRectGui(GuiGraphics g, float x, float y, float w, float h, float r, int color) {
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

	private static float easeOut(float t) {
		return 1 - (1 - t) * (1 - t);
	}

	private static float easeOutCubic(float t) {
		float u = 1 - t;
		return 1 - u * u * u;
	}
}
