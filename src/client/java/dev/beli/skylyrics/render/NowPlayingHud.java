package dev.beli.skylyrics.render;

import static dev.beli.skylyrics.render.TopBarLayout.ART;
import static dev.beli.skylyrics.render.TopBarLayout.ART_INSET;
import static dev.beli.skylyrics.render.TopBarLayout.BARS;
import static dev.beli.skylyrics.render.TopBarLayout.BAR_GAP;
import static dev.beli.skylyrics.render.TopBarLayout.BAR_W;
import static dev.beli.skylyrics.render.TopBarLayout.CLOCK_GAP;
import static dev.beli.skylyrics.render.TopBarLayout.EQ_INSET;
import static dev.beli.skylyrics.render.TopBarLayout.EQ_W;
import static dev.beli.skylyrics.render.TopBarLayout.PILL_H;
import static dev.beli.skylyrics.render.TopBarLayout.SPIN_HOLE;
import static dev.beli.skylyrics.render.TopBarLayout.SPIN_TURNS;
import static dev.beli.skylyrics.render.TopBarLayout.STATS_GAP;
import static dev.beli.skylyrics.render.TopBarLayout.TEXT_GAP;
import static dev.beli.skylyrics.render.TopBarLayout.WORD_EXTRA;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.client.gui.components.SubtitleOverlay;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
//? if >=26.2
/*import net.minecraft.world.scores.TeamColor;*/

import dev.beli.skylyrics.Formats;
import dev.beli.skylyrics.Tr;
import dev.beli.skylyrics.SkyLyricsClient;
import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.config.SkyLyricsConfig.BarAccent;
import dev.beli.skylyrics.config.SkyLyricsConfig.BarPosition;
import dev.beli.skylyrics.gui.ClipBox;
import dev.beli.skylyrics.gui.SkyLyricsScreen;
import dev.beli.skylyrics.gui.ToastTime;
import dev.beli.skylyrics.lyrics.LyricLine;
import dev.beli.skylyrics.lyrics.Lyrics;
import dev.beli.skylyrics.lyrics.LyricsService;
import dev.beli.skylyrics.media.MediaTracker;
import dev.beli.skylyrics.media.Track;
import dev.beli.skylyrics.mixin.BossHealthOverlayAccessor;
import dev.beli.skylyrics.mixin.GuiOverlayAccessor;
import dev.beli.skylyrics.mixin.SubtitleOverlayAccessor;
import dev.beli.skylyrics.mixin.SubtitleTextAccessor;
import dev.beli.skylyrics.util.Guard;

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
	static final int SUNG = 0xFCF8FF;
	private static final int UPCOMING = 0x8C879E;
	private static final int HIGHLIGHT = 0x926CDE;
	private static final int EQ_COLOR = 0xA076F4;
	static final int CLOCK = 0xF0F0F0;
	static final int STAT_TEXT = 0x8A8699;
	/**
	 * The clock and the frame rate stand on the world itself, beside the pill: like the game's own words over the world
	 * (the action bar, titles), they get its drop shadow, or they vanish on a bright sky or snow.
	 */
	static final boolean ON_WORLD_SHADOW = true;
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

	private final MediaTracker tracker;
	private final LyricsService service;
	private final AlbumArt art;

	private long shownAt;
	private long lastFrame;
	private float alpha;
	private boolean forced;

	private @Nullable Content content;
	private @Nullable Content old;
	/** The music player's reading the progress line drew last (null while the bar isn't showing), kept while it fades out. */
	private dev.beli.skylyrics.media.@Nullable Snapshot shownReading;
	private long swappedAt;
	/** The pill's width, tweened from {@link #widthFrom} to {@link #widthTo} since {@link #resizedAt}. */
	private float width = -1, widthFrom, widthTo, widthSpeed;
	/** The bar's scale and pivot this frame, for the clip box on versions whose scissor ignores the pose. */
	private float clipSize = 1, clipPivotY, clipGuiWidth;
	/** The text's clip box edges this frame, in the bar's own units, for the dev probe. */
	private int textClipLeft, textClipRight;
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
	/** The minute since 1970 the clock's text was made for. */
	private long clockMinute = Long.MIN_VALUE;
	private Locale clockLocale = Locale.ROOT;
	private String clockText = "";
	/** The last few lines laid out (the one sung, the one fading out, one spare); see {@link LineKey}. */
	private final SmallCache<LineKey, Laid> laid = new SmallCache<>(3);
	private double guiScale;
	private boolean wasLoading;
	private int fpsShown = -1;
	private String fpsText = "";
	/** The last line text worked out from a lyric line, and what it was worked out from. */
	private @Nullable LineText lineText;
	private @Nullable SongSource songSource;
	private @Nullable Content songContent;

	/** What the bar says: a lyric line, or the song's title and artist. */
	/** {@code through} is how far the song is through the line's time, 0 to 1, karaoke or not. */
	private record Content(String key, boolean lyric, String text, String sub, float sung, float through) {}

	/** A laid-out line with what each word is drawn from, built once per {@link LineKey}. */
	private record Laid(LineLayout layout, FormattedCharSequence[] seqs) {}

	/** What a lyric line's bar text is worked out from, and the content key and text it gives. */
	private record LineSource(int index, LyricLine line, boolean uppercase) {}

	private record LineText(LineSource source, String key, String text) {}

	/** What the song info is worked out from. */
	private record SongSource(String title, String artist, String source, LyricsService.State state, Object language, boolean demo) {}

	/** What a piece of text's visual order is worked out from. */
	private static final class VisualKey {
		String text;
		Style style;
		Object language;

		VisualKey(String text, Style style, Object language) {
			this.text = text;
			this.style = style;
			this.language = language;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof VisualKey k && text.equals(k.text) && style.equals(k.style) && java.util.Objects.equals(language, k.language);
		}

		@Override
		public int hashCode() {
			return text.hashCode();
		}
	}

	/** Refilled for every lookup, so a hit builds no key; only a miss stores a copy. Render thread only. */
	private static final VisualKey VISUAL_PROBE = new VisualKey("", Style.EMPTY, null);

	/** The game's visual order of short texts (the clock, the frame rate, a title), kept while they stay the same. */
	private static final SmallCache<VisualKey, FormattedCharSequence> VISUAL = new SmallCache<>(12);

	/** The note in the round space for a missing cover. */
	private static final String NOTE = "♪";
	/** The size the note is asked for, in Minecraft's letters (see {@link #noteSize}). */
	static final float NOTE_SCALE = 0.85f;

	/**
	 * The size the note is drawn at in a bar {@code barSize} big at this GUI scale. It is a letter of Minecraft's pixel
	 * font: at 0.85 each of its pixels came out 2 or 3 screen pixels by turns at GUI scale 3, so it looked uneven. It gets
	 * the nearest size with a whole number of screen pixels per letter pixel, as the Record card's small text does
	 * ({@link RecordCard#smallSize}).
	 */
	static float noteSize(double guiScale, float barSize) {
		// The bar's size scales the note too: the screen pixels per unit are the GUI scale times it
		return RecordCard.smallSize(false, NOTE_SCALE, (float) guiScale * barSize);
	}

	private static @Nullable Language noteLanguage;
	private static @Nullable FormattedCharSequence noteOrder;

	/**
	 * The note in {@code language}'s visual order, as the game lays out a string it is asked to draw. Laid out once per
	 * language: the game would do it afresh every frame, with its right-to-left reordering and Arabic shaping.
	 */
	static FormattedCharSequence note(Language language) {
		FormattedCharSequence order = noteOrder;
		if (order == null || language != noteLanguage) {
			noteOrder = order = language.getVisualOrder(FormattedText.of(NOTE));
			noteLanguage = language;
		}
		return order;
	}

	/** Cut texts, by everything the cut depends on. */
	private static final class ClipKey {
		Object font;
		Style style;
		String str;
		int maxW;
		Object language;
		long reloads;

		ClipKey(Object font, Style style, String str, int maxW, Object language, long reloads) {
			set(font, style, str, maxW, language, reloads);
		}

		void set(Object font, Style style, String str, int maxW, Object language, long reloads) {
			this.font = font;
			this.style = style;
			this.str = str;
			this.maxW = maxW;
			this.language = language;
			this.reloads = reloads;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof ClipKey k && java.util.Objects.equals(font, k.font) && style.equals(k.style) && str.equals(k.str) && maxW == k.maxW
					&& java.util.Objects.equals(language, k.language) && reloads == k.reloads;
		}

		@Override
		public int hashCode() {
			return 31 * (31 * str.hashCode() + maxW) + Long.hashCode(reloads);
		}
	}

	private static final ClipKey CLIP_PROBE = new ClipKey(null, Style.EMPTY, "", 0, null, 0);
	private static final SmallCache<ClipKey, String> CLIPS = new SmallCache<>(8);
	/** The song's name and what follows it, cut to the room there is; see {@link #titleCut}. */
	private record TitleKey(Object font, Style style, String title, String sub, int limit, int floor, Object language, long reloads) {}
	private record TitleCut(String title, float used, @Nullable String sub) {}
	private static final SmallCache<TitleKey, TitleCut> TITLES = new SmallCache<>(3);
	/** The sub text with its separator in front, so the joined string isn't built again every frame. */
	private static final SmallCache<String, String> DOTTED = new SmallCache<>(4);

	public NowPlayingHud(MediaTracker tracker, LyricsService service, AlbumArt art) {
		this.tracker = tracker;
		this.service = service;
		this.art = art;
	}

	/**
	 * Whether the song's name still shows {@code sinceNs} after a song change or the Now playing key, with Song name only
	 * on change: {@link #SHOW_NS} times Minecraft's Notification Display Time ({@code notices}).
	 */
	static boolean songNameUp(long sinceNs, double notices) {
		return sinceNs < noticeNs(SHOW_NS, notices);
	}

	/** Whether the bar has shown the song's name long enough, {@code sinceNs} after the change, to move on to the line being sung. */
	static boolean titleDone(long sinceNs, double notices) {
		return sinceNs >= noticeNs(TITLE_NS, notices);
	}

	/** {@code ns} times Notification Display Time, as Minecraft's own music pop-up stays up for it (it stayed the same whatever the option said). */
	private static long noticeNs(long ns, double notices) {
		return (long) (ToastTime.shownFor(ns / 1e9f, notices) * 1e9);
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
		Guard.run("now-playing bar", () -> draw(g, delta));
	}

	private void draw(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		SkyLyricsConfig cfg = SkyLyricsConfig.get();
		long now = System.nanoTime();
		// A resource reload can change glyph widths: every laid-out line is dropped by it
		boolean loading = loadingScreenUp(mc);
		if (loading || wasLoading) {
			LyricsFont.fontsReloaded(); // also moves LyricsFont.reloadCount(), which keys every laid-out line
		}
		wasLoading = loading;
		LyricsFont.checkFonts(mc.font);
		guiScale = mc.getWindow().getGuiScale();
		float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;

		Track track = tracker.track();
		LyricsService.Snapshot snap = service.snapshot().forSong(track);
		Content line = cfg.barLyrics ? currentLine(cfg, snap.lyrics()) : null;
		float[][] pinned = pinnedLines(mc, now);
		// Once the singing has started the bar stays, showing the song in the gaps instead of blinking off and on
		double notices = ToastTime.multiplier();
		boolean info = forced || !cfg.hudOnlyOnChange || songNameUp(now - shownAt, notices) || cfg.barLyrics && singing(snap.lyrics(), lyricsTime(cfg));
		//? if >=26.2 {
		/*boolean want = cfg.enabled && cfg.nowPlayingHud && !track.equals(Track.NONE) && !mc.gui.hud.isHidden()
		*///?} else {
		boolean want = cfg.enabled && cfg.nowPlayingHud && !track.equals(Track.NONE) && !mc.options.hideGui
		//?}
				&& (info || line != null)
				&& !Visibility.hidden(mc, cfg)
				// The debug screen (F3) fills the top corners, and its text can reach down to a bar at the bottom
				&& !f3HidesBar(mc, cfg, g, now, pinned)
				// The player list (held Tab) is drawn after the bar, over it and see-through
				&& !(cfg.barPosition != BarPosition.BOTTOM && tabListUp(mc))
				&& (cfg.barShow == SkyLyricsConfig.BarShow.ALWAYS || !LyricsInView.onScreen())
				//? if >=26.2 {
				/*&& !(mc.gui.screen() instanceof SkyLyricsScreen settings && settings.coversTopBar());
				*///?} else {
				&& !(mc.screen instanceof SkyLyricsScreen settings && settings.coversTopBar());
				//?}
		alpha = want ? Math.min(1, alpha + dt * 4) : Math.max(0, alpha - dt * 3);
		if (alpha <= 0.01f) {
			bottomBox = null;
			topBox = null;
			content = null;
			old = null;
			shownReading = null;
			width = -1;
			widthSpeed = 0;
			top = -1;
			return;
		}

		// Fading out, the bar keeps what it shows (the stopped music's empty reading would say "Unknown song")
		Content next = TopBarLayout.keptWhileFading(want, content, line != null && titleDone(now - shownAt, notices) ? line : songInfo(track, snap.state()));
		shownReading = TopBarLayout.keptWhileFading(want, shownReading, tracker.snapshot());
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
		Sizes sizes = sizes(mc, cfg, g.guiWidth());
		Style style = sizes.style();
		float textScale = sizes.textScale();
		Style clockStyle = sizes.clockStyle();
		float clockScale = sizes.clockScale();
		String clock = sizes.clock();
		float clockW = sizes.clockW();
		float statsW = sizes.statsW();
		TopBarLayout.Fit fit = sizes.fit();

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
				? bottomTopNow(mc, g.guiHeight())
				: topNow(mc, cfg, g.guiWidth(), g.guiHeight(), sizes, pinned);
		if (Float.isNaN(topTarget)) {
			// Fading out for debug lines pinned too far down the screen: it stays where it was
			topTarget = Math.max(top, 0);
		}
		float k = 1 - (float) Math.exp(-dt * 12);
		top = top < 0 ? topTarget : top + (topTarget - top) * k;
		float y = top - (1 - easeOut(alpha)) * 10;
		boxesDrawn(cfg.barPosition == BarPosition.BOTTOM
				? TopBarLayout.barBox(g.guiWidth(), cfg.barSize, fit.pill(), clockW, fit.clock(), statsW, fit.stats(), top) : null,
				cfg.barPosition == BarPosition.BOTTOM
						? null : TopBarLayout.topBarBox(g.guiWidth(), cfg.barSize, fit.pill(), clockW, fit.clock(), statsW, fit.stats(), top),
				now);

		float barSize = cfg.barSize;
		g.pose().pushMatrix();
		try {
			float pivotY = TopBarLayout.pivotY(cfg.barPosition == BarPosition.BOTTOM, y);
			clipSize = barSize;
			clipPivotY = pivotY;
			clipGuiWidth = g.guiWidth();
			g.pose().translate((1 - barSize) * g.guiWidth() / 2f, (1 - barSize) * pivotY);
			g.pose().scale(barSize, barSize);
			drawPill(g, mc, x, y, now, dt);
			float midY = y + PILL_H / 2f;
			if (fit.clock()) {
				text(g, font, visual(clock, clockStyle), x - CLOCK_GAP - clockW, midY, clockScale, fade(alpha, CLOCK), ON_WORLD_SHADOW);
			}
			if (fit.stats()) {
				drawStats(g, mc, font, style, textScale, x + width + STATS_GAP, midY);
			}
			drawText(g, font, style, textScale, x, midY, now, dt);
		} finally {
			g.pose().popMatrix();
		}
	}

	/** The bar's text style and scale, the clock and its width, the stats' width, and what fits on this screen. */
	private record Sizes(Style style, float textScale, Style clockStyle, float clockScale, String clock, float clockW, float statsW,
			TopBarLayout.Fit fit) {}

	private Sizes sizes(Minecraft mc, SkyLyricsConfig cfg, int guiWidth) {
		Font font = mc.font;
		Style style = LyricsFont.base(cfg);
		float textScale = cfg.smoothFont ? TEXT_SCALE : PIXEL_FONT_SCALE;
		Style clockStyle = cfg.smoothFont ? Style.EMPTY.withFont(LyricsFont.BOLD) : Style.EMPTY.withBold(true);
		float clockScale = cfg.smoothFont ? CLOCK_SCALE : PIXEL_FONT_SCALE;
		String clock = cfg.barClock ? clock() : "";
		float clockW = clock.isEmpty() ? 0 : LyricsFont.cachedWidth(font, clockStyle, clock) * clockScale;
		float statsW = statsWidth(cfg, font, style, textScale);
		return new Sizes(style, textScale, clockStyle, clockScale, clock, clockW, statsW, TopBarLayout.fit(guiWidth, cfg.barSize, clockW, statsW));
	}

	/** Where a bar at the bottom heads for: above the hotbar, the hearts and armour, and any action bar message. */
	private static float bottomTopNow(Minecraft mc, int guiHeight) {
		return TopBarLayout.bottomTop(guiHeight, mc.gameMode == null || mc.gameMode.canHurtPlayer(), healthTopUp(mc), actionBarShowing(mc));
	}

	/** Where a bar at the top heads for without pinned debug lines: below the boss bars, and the effect icons and demo timer it reaches. */
	private static float topClearNow(Minecraft mc, SkyLyricsConfig cfg, int guiWidth, int guiHeight, Sizes sizes) {
		TopBarLayout.Fit fit = sizes.fit();
		return Math.max(Math.max(topBelowBossBars(mc, guiHeight), effectsClear(mc, guiWidth, cfg, fit, sizes.statsW())),
				TopBarLayout.demoClear(mc.isDemo(), guiWidth, demoTimerWidth(mc),
						TopBarLayout.barRight(guiWidth, cfg.barSize, fit.pill(), sizes.statsW(), fit.stats())));
	}

	/** The top bar's box at its widest with its top at {@code top}. */
	private static float[] widestTopBox(SkyLyricsConfig cfg, int guiWidth, Sizes sizes, float top) {
		TopBarLayout.Fit fit = sizes.fit();
		return TopBarLayout.topBarBox(guiWidth, cfg.barSize, fit.pill(), sizes.clockW(), fit.clock(), sizes.statsW(), fit.stats(), top);
	}

	/**
	 * Where a bar at the top heads for ({@link #topClearNow}), and below the debug lines a player pinned to show with F3
	 * closed that its width reaches ({@link #topBelowPinned}; NaN when that is too far down and the bar hides).
	 */
	private static float topNow(Minecraft mc, SkyLyricsConfig cfg, int guiWidth, int guiHeight, Sizes sizes, float[][] pinned) {
		float top = topClearNow(mc, cfg, guiWidth, guiHeight, sizes);
		return pinned.length == 0 ? top : topBelowPinned(top, widestTopBox(cfg, guiWidth, sizes, top), pinned, guiHeight);
	}

	/**
	 * Whether the debug screen (F3) hides the bar now. With F3 open, a bar at the bottom is measured where it heads for, at
	 * the widest this screen lets it be, against F3's text as the game drew it last. With F3 closed, against the lines a
	 * player pinned to show always ({@link #pinnedHides}).
	 */
	private boolean f3HidesBar(Minecraft mc, SkyLyricsConfig cfg, GuiGraphics g, long now, float[][] pinned) {
		if (!f3Open(mc)) {
			if (pinned.length == 0) {
				return false;
			}
			Sizes sizes = sizes(mc, cfg, g.guiWidth());
			boolean bottom = cfg.barPosition == BarPosition.BOTTOM;
			float[] box = bottom
					? TopBarLayout.barBox(g.guiWidth(), cfg.barSize, sizes.fit().pill(), sizes.clockW(), sizes.fit().clock(), sizes.statsW(),
							sizes.fit().stats(), bottomTopNow(mc, g.guiHeight()))
					: widestTopBox(cfg, g.guiWidth(), sizes, topClearNow(mc, cfg, g.guiWidth(), g.guiHeight(), sizes));
			return pinnedHides(bottom, box, pinned, g.guiHeight());
		}
		if (cfg.barPosition != BarPosition.BOTTOM) {
			return f3Hides(false, true, null, null);
		}
		Sizes sizes = sizes(mc, cfg, g.guiWidth());
		TopBarLayout.Fit fit = sizes.fit();
		float[] box = TopBarLayout.barBox(g.guiWidth(), cfg.barSize, fit.pill(), sizes.clockW(), fit.clock(), sizes.statsW(), fit.stats(),
				bottomTopNow(mc, g.guiHeight()));
		return f3Hides(true, true, box, DebugText.recent(now), DebugCharts.shown(mc, g.guiWidth(), g.guiHeight()));
	}

	/** {@link #f3Hides(boolean, boolean, float[], float[][], float[][])} with none of F3's charts showing. */
	static boolean f3Hides(boolean atBottom, boolean f3Open, float @Nullable [] barBox, float @Nullable [][] f3Lines) {
		return f3Hides(atBottom, f3Open, barBox, f3Lines, new float[0][]);
	}

	/**
	 * Whether the open debug screen (F3) hides the bar: at the top always, as F3 fills the top corners; at the bottom when
	 * the bar would sit on F3's text (at GUI scale 3 it does, on a taller screen F3's text can end above it) or on one of
	 * its charts along the bottom (on a screen up to about 830 wide they reach under the bar). Without F3's text measured
	 * (a mod draws F3 its own way), a bottom bar hides when it reaches as high as F3's text usually does, as a Record card
	 * in the bottom left does.
	 *
	 * @param barBox the bar's box (left, top, right, bottom) when it sits at the bottom
	 * @param f3Lines F3's text lines' boxes as the game drew them last, null when it didn't lately
	 * @param f3Charts the boxes of F3's charts showing ({@link DebugCharts#shown})
	 */
	static boolean f3Hides(boolean atBottom, boolean f3Open, float @Nullable [] barBox, float @Nullable [][] f3Lines, float[][] f3Charts) {
		if (!f3Open) {
			return false;
		}
		if (!atBottom || barBox == null) {
			return true;
		}
		if (DebugText.covers(barBox, f3Charts)) {
			return true;
		}
		return f3Lines == null ? barBox[1] < RecordCard.F3_REACH : DebugText.covers(barBox, f3Lines);
	}

	/** No debug lines. */
	static final float[][] NO_LINES = new float[0][];

	/**
	 * The debug lines a player pinned to show with F3 closed, as the game drew them last (from 1.21.9 the debug options
	 * screen can set each of F3's lines to show always; the Performance profile does it for the FPS line). Empty while F3
	 * is open (the full screen has its own rules), while none show, and before 1.21.9. The game's charts can't be pinned:
	 * they show only with F3 open in every version.
	 */
	static float[][] pinnedLines(Minecraft mc, long now) {
		//? if >=1.21.9 {
		if (f3Open(mc) || mc.debugEntries.getCurrentlyEnabled().isEmpty()) {
			return NO_LINES;
		}
		float[][] lines = DebugText.recent(now);
		return lines == null ? NO_LINES : lines;
		//?} else {
		/*return NO_LINES;
		*///?}
	}

	/**
	 * Where a top bar's top goes for the debug lines a player pinned to show with F3 closed (1.21.9 and later): a little
	 * below the lowest one its width reaches, else {@code top}. A pinned FPS line in the top left corner reaches a bar
	 * with its clock on a narrow screen (Auto GUI scale at 1920x1080 is 480 wide). NaN when that is past the top third
	 * of the screen: so many lines fill the top like the full F3 screen, and the bar hides as it does for that.
	 *
	 * @param barBox the bar's box at its widest (left, top, right, bottom)
	 */
	static float topBelowPinned(float top, float[] barBox, float[][] pinned, int guiHeight) {
		float clear = DebugText.clearBelow(pinned, barBox[0], barBox[2]);
		if (clear <= top) {
			return top;
		}
		return clear > guiHeight / 3f ? Float.NaN : clear;
	}

	/**
	 * Whether the debug lines a player pinned to show with F3 closed (1.21.9 and later) hide the bar. At the bottom when
	 * they reach down to it (many lines on a short screen), as F3's text does. At the top, where the bar moves down below
	 * them, when that is too far down ({@link #topBelowPinned}).
	 *
	 * @param barBox the bar's box at its widest, where it heads for without the pinned lines
	 */
	static boolean pinnedHides(boolean atBottom, float[] barBox, float[][] pinned, int guiHeight) {
		return atBottom ? DebugText.covers(barBox, pinned) : Float.isNaN(topBelowPinned(barBox[1], barBox, pinned, guiHeight));
	}

	/** Whether the resource loading screen is up. */
	private static boolean loadingScreenUp(Minecraft mc) {
		//? if >=26.2 {
		/*return mc.gui.overlay() != null;
		*///?} else {
		return mc.getOverlay() != null;
		//?}
	}

	/** How wide the demo's timer text can be (the longer of its two texts), 0 outside the demo. */
	static float demoTimerWidth(Minecraft mc) {
		if (!mc.isDemo()) {
			return 0;
		}
		try {
			return Math.max(mc.font.width(Component.translatable("demo.remainingTime", "00:00")),
					mc.font.width(Component.translatable("demo.demoExpired")));
		} catch (RuntimeException e) {
			return 0;
		}
	}

	/** Whether the chat screen is open (the chat's box and the text field are then drawn over the bottom left). */
	static boolean chatOpen(Minecraft mc) {
		//? if >=26.2 {
		/*return mc.gui.screen() instanceof ChatScreen;
		*///?} else {
		return mc.screen instanceof ChatScreen;
		//?}
	}

	/**
	 * The box the game's subtitles cover (left, top, right, bottom), from the entries it showed last frame (it drops expired
	 * ones as it draws): null when none show. If they can't be read, one narrow box is assumed.
	 */
	static float[] subtitleBox(Minecraft mc, int guiW, int guiH) {
		try {
			//? if >=26.2 {
			/*SubtitleOverlay overlay = ((GuiOverlayAccessor) mc.gui.hud).skylyrics$getSubtitleOverlay();
			*///?} else {
			SubtitleOverlay overlay = ((GuiOverlayAccessor) mc.gui).skylyrics$getSubtitleOverlay();
			//?}
			List<?> shown = ((SubtitleOverlayAccessor) overlay).skylyrics$getAudibleSubtitles();
			int count = shown.size();
			if (count == 0) {
				return null;
			}
			int widest = 0;
			for (int i = 0; i < count; i++) {
				Component text = ((SubtitleTextAccessor) shown.get(i)).skylyrics$getText();
				widest = Math.max(widest, mc.font.width(text));
			}
			int margin = mc.font.width("<") + mc.font.width(" ") + mc.font.width(">") + mc.font.width(" ");
			return TopBarLayout.subtitleBox(guiW, guiH, count, widest, margin);
		} catch (RuntimeException | LinkageError e) {
			return TopBarLayout.subtitleBoxGuess(guiW, guiH);
		}
	}

	/**
	 * The scoreboard sidebar's box (left, top, right, bottom) in GUI units as the game draws it for this player: their
	 * team colour's slot first, else the sidebar slot. Null when no sidebar shows.
	 */
	static float[] sidebarBox(Minecraft mc, int guiW, int guiH) {
		try {
			if (mc.level == null || mc.player == null) {
				return null;
			}
			Scoreboard scoreboard = mc.level.getScoreboard();
			Objective objective = sidebarObjective(scoreboard, mc.player.getScoreboardName());
			if (objective == null) {
				return null;
			}
			long now = System.nanoTime();
			float[] hit = SIDEBAR.lookup(scoreboard, objective, mc.font, guiW, guiH, now);
			if (hit != null) {
				return hit;
			}
			return SIDEBAR.store(measureSidebar(scoreboard, objective, mc.font::width, guiW, guiH), now);
		} catch (RuntimeException | LinkageError e) {
			return null;
		}
	}

	/** The objective the game shows in the sidebar for this player: their team colour's slot first, else the sidebar slot. */
	static Objective sidebarObjective(Scoreboard scoreboard, String playerName) {
		Objective objective = null;
		PlayerTeam team = scoreboard.getPlayersTeam(playerName);
		if (team != null) {
			//? if >=26.2 {
			/*DisplaySlot slot = team.getColor().map(TeamColor::displaySlot).orElse(null);
			*///?} else {
			DisplaySlot slot = DisplaySlot.teamColorToSlot(team.getColor());
			//?}
			if (slot != null) {
				objective = scoreboard.getDisplayObjective(slot);
			}
		}
		return objective != null ? objective : scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
	}

	/** The measured sidebar box, kept while nothing that sizes it has changed (the vanilla scoreboard has no change counter). */
	static final class SidebarCache {
		private static final long MAX_AGE_NANOS = 1_000_000_000L;
		private long signature;
		private long storedAt;
		private float[] box;
		private long pending;
		int measures;

		/** The kept box when the scoreboard looks the same as when it was measured, else null (call {@link #store} next). */
		float[] lookup(Scoreboard scoreboard, Objective objective, Object font, int guiW, int guiH, long now) {
			long sig = signature(scoreboard, objective, font, guiW, guiH);
			pending = sig;
			return box != null && sig == signature && now - storedAt < MAX_AGE_NANOS ? box : null;
		}

		float[] store(float[] measured, long now) {
			box = measured;
			signature = pending;
			storedAt = now;
			measures++;
			return measured;
		}

		private static long mix(long h, int v) {
			return (h ^ v) * 0x9E3779B97F4A7C15L + (h >>> 29);
		}

		private static long signature(Scoreboard scoreboard, Objective objective, Object font, int guiW, int guiH) {
			long h = mix(mix(mix(1, System.identityHashCode(objective)), System.identityHashCode(font)), guiW * 31 + guiH);
			h = mix(h, objective.getDisplayName().hashCode());
			h = mix(h, Objects.hashCode(objective.numberFormat()));
			long entries = 0;
			int count = 0;
			for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
				if (entry.isHidden()) {
					continue;
				}
				PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
				long e = mix(mix(mix(mix(7, entry.owner().hashCode()), entry.value()), Objects.hashCode(entry.display())),
						Objects.hashCode(entry.numberFormatOverride()));
				if (team != null) {
					e = mix(mix(e, team.getPlayerPrefix().hashCode()), team.getPlayerSuffix().hashCode());
				}
				entries += e; // a sum, so the order the scoreboard lists them in doesn't matter
				count++;
			}
			return mix(mix(h, count), (int) (entries ^ (entries >>> 32)));
		}
	}

	private static final SidebarCache SIDEBAR = new SidebarCache();

	/** Measures the sidebar box: up to 15 entries, the most points first, the way the game draws them. */
	static float[] measureSidebar(Scoreboard scoreboard, Objective objective, java.util.function.ToIntFunction<FormattedText> width,
			int guiW, int guiH) {
		NumberFormat format = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
		List<PlayerScoreEntry> entries = scoreboard.listPlayerScores(objective).stream()
				.filter(entry -> !entry.isHidden())
				.sorted(Comparator.comparing(PlayerScoreEntry::value).reversed()
						.thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER))
				.limit(TopBarLayout.SIDEBAR_MAX)
				.toList();
		int[] nameW = new int[entries.size()], scoreW = new int[entries.size()];
		for (int i = 0; i < nameW.length; i++) {
			PlayerScoreEntry entry = entries.get(i);
			nameW[i] = width.applyAsInt(PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName()));
			scoreW[i] = width.applyAsInt(entry.formatValue(format));
		}
		int widest = TopBarLayout.sidebarWidth(width.applyAsInt(objective.getDisplayName()), width.applyAsInt(Component.literal(": ")), nameW, scoreW);
		return TopBarLayout.sidebarBox(guiW, guiH, nameW.length, widest);
	}

	/** How far down a bar at the top must start to keep clear of the potion effect icons (0 when it doesn't reach them). */
	private static float effectsClear(Minecraft mc, int guiWidth, SkyLyricsConfig cfg, TopBarLayout.Fit fit, float statsW) {
		int[] counts = RecordCard.effectCounts(mc);
		if (counts[0] + counts[1] == 0) {
			return 0;
		}
		// The widest the pill can be, not its moving width, so the bar doesn't hop up and down with each line
		float right = TopBarLayout.barRight(guiWidth, cfg.barSize, fit.pill(), statsW, fit.stats());
		return TopBarLayout.effectsClear(guiWidth, right, counts[0], counts[1], TopBarLayout.effectRow(RecordCard.GOOD_ROW, mc.isDemo()),
				TopBarLayout.effectRow(RecordCard.BAD_ROW, mc.isDemo()));
	}

	/**
	 * Boss bars (a dragon, a wither, server events) are drawn from the top of the screen, 19 units apart, as many
	 * as fit in the top third. The bar goes just below the last one instead of covering their names.
	 */
	static float topBelowBossBars(Minecraft mc, int guiHeight) {
		return topBelowBossBars(bossBarCount(mc), guiHeight);
	}

	/**
	 * Whether the full debug screen (F3) is open. From 1.21.9 players can pin single debug lines to show without F3, and
	 * showDebugScreen() is true for those too; they stay small in a corner, so they must not hide the bar the way F3 does
	 * ({@link #pinnedLines} has their own rules).
	 */
	static boolean f3Open(Minecraft mc) {
		//? if >=1.21.11 {
		return mc.debugEntries.isOverlayVisible();
		//?} else if >=1.21.9 {
		/*return mc.debugEntries.isF3Visible();
		*///?} else {
		/*return mc.getDebugOverlay().showDebugScreen();
		*///?}
	}

	/** The bar's box (left, top, right, bottom) while it sits at the bottom of the screen, else null; the Record card keeps clear of it. */
	private static volatile float[] bottomBox;

	/** The same for a bar at the top of the screen (else null); the Record card in a top corner keeps below it. */
	private static volatile float[] topBox;

	/** When the bar drew its box last and the time before, for {@link DrawnLately}. */
	private static volatile long bottomBoxAt, bottomBoxBefore;

	/** The bar's boxes as it drew them at {@code now} (one of them null: it is either at the bottom or at the top). */
	static void boxesDrawn(float[] bottom, float[] top, long now) {
		bottomBox = bottom;
		topBox = top;
		bottomBoxBefore = bottomBoxAt;
		bottomBoxAt = now;
	}

	static float[] topBox() {
		return topBox(System.nanoTime());
	}

	static float[] topBox(long now) {
		return DrawnLately.drawn(now, bottomBoxAt, bottomBoxBefore) ? topBox : null;
	}

	/**
	 * The box, or null when the bar didn't draw it lately (hidden, GUI off, or the bar moved to the top). It uses the
	 * widest the pill can be at this screen size, not its moving width, so it doesn't shift with each line. The Record
	 * card is drawn before the bar in each frame, so it reads the box the bar drew in the frame before: on a slow game
	 * (about 4 frames a second) that one still counts ({@link DrawnLately}), or the card jumped between its two places.
	 */
	static float[] bottomBox() {
		return bottomBox(System.nanoTime());
	}

	static float[] bottomBox(long now) {
		return DrawnLately.drawn(now, bottomBoxAt, bottomBoxBefore) ? bottomBox : null;
	}

	/** How far above the screen's bottom the player's hearts and armour reach, from their health, absorption and armour. */
	static float healthTopUp(Minecraft mc) {
		if (mc.player == null) {
			return TopBarLayout.healthTopUp(20, 0, 0);
		}
		return TopBarLayout.healthTopUp(Math.max(mc.player.getMaxHealth(), mc.player.getHealth()), mc.player.getAbsorptionAmount(), mc.player.getArmorValue());
	}

	/** Whether an action bar message (the text above the hotbar) is on screen. */
	static boolean actionBarShowing(Minecraft mc) {
		try {
			//? if >=26.2 {
			/*GuiOverlayAccessor overlay = (GuiOverlayAccessor) mc.gui.hud;
			*///?} else {
			GuiOverlayAccessor overlay = (GuiOverlayAccessor) mc.gui;
			//?}
			return actionBarShowing(overlay.skylyrics$getOverlayMessageTime(), overlay.skylyrics$getOverlayMessageString());
		} catch (RuntimeException | LinkageError e) {
			return false;
		}
	}

	/** The time is checked first, so no string is built for the text while no message is showing. */
	static boolean actionBarShowing(int ticksLeft, Component text) {
		return ticksLeft > 0 && text != null && actionBarShowing(ticksLeft, text.getString());
	}

	/** An action bar message shows while it has time left and says something: servers send an empty one to clear it. */
	static boolean actionBarShowing(int ticksLeft, String text) {
		return ticksLeft > 0 && !text.isEmpty();
	}

	/**
	 * Whether the player list (held Tab) is drawn, by the game's own test: the key is held, and it isn't a single-player
	 * world with only you listed and nothing on the list scoreboard.
	 */
	static boolean tabListShown(boolean keyDown, boolean localServer, int listedPlayers, boolean listObjective) {
		return keyDown && (!localServer || listedPlayers > 1 || listObjective);
	}

	/** Whether the player list is on screen now; it is drawn over the top bar and a card in a top corner. */
	static boolean tabListUp(Minecraft mc) {
		try {
			if (!mc.options.keyPlayerList.isDown() || mc.player == null || mc.level == null) {
				return false;
			}
			return tabListShown(true, mc.isLocalServer(), mc.player.connection.getListedOnlinePlayers().size(),
					mc.level.getScoreboard().getDisplayObjective(DisplaySlot.LIST) != null);
		} catch (RuntimeException | LinkageError e) {
			return false;
		}
	}

	/** How many boss bars the game has (it draws only the first few that fit). */
	static int bossBarCount(Minecraft mc) {
		//? if >=26.2 {
		/*int count = ((BossHealthOverlayAccessor) mc.gui.hud.getBossOverlay()).skylyrics$getEvents().size();
		*///?} else {
		int count = ((BossHealthOverlayAccessor) mc.gui.getBossOverlay()).skylyrics$getEvents().size();
		//?}
		return count;
	}

	static float topBelowBossBars(int count, int guiHeight) {
		int shown = 0;
		for (int y = 12; shown < count && y < guiHeight / 3; y += 19) {
			shown++;
		}
		return shown == 0 ? 5 : 12 + 19 * (shown - 1) + 5 + 4;
	}

	private String clock() {
		return clock(System.currentTimeMillis());
	}

	/**
	 * The clock's text at {@code millis} on the computer's clock, made again only when the minute or the language changes:
	 * a frame in between builds nothing. Every time zone is a whole number of minutes off, so a new minute since 1970 is a
	 * new minute on the clock, also when the clock is set back or the summer time starts or ends.
	 */
	String clock(long millis) {
		long minute = Math.floorDiv(millis, 60_000L);
		Locale locale = Formats.clockLocale();
		if (minute != clockMinute || !locale.equals(clockLocale)) {
			clockMinute = minute;
			clockLocale = locale;
			clockText = clockText(LocalTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()), locale);
		}
		return clockText;
	}

	/** The time as the game's language writes it, e.g. "21:07" or "9:07 PM" (see {@link Formats#clockLocale}). */
	static String clockText(LocalTime time) {
		return clockText(time, Formats.clockLocale());
	}

	private static String clockText(LocalTime time, Locale locale) {
		// Some languages put a narrow no-break space before AM/PM and a no-break one inside it ("p. m."), which the fonts lack
		return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(time).replace('\u202f', ' ')
				.replace('\u00a0', ' ');
	}

	/** The capsule, its border, the cover and the equaliser, drawn in real pixels so round edges stay smooth. */
	private void drawPill(GuiGraphics g, Minecraft mc, float x, float y, long now, float dt) {
		float a = alpha;
		float s = (float) mc.getWindow().getGuiScale();
		// The cover and equaliser dim for a moment while the text crossfades
		float swap = Mth.clamp((now - swappedAt) / (float) SWAP_NS, 0, 1);
		float dim = old == null ? 1 : 1 - 0.35f * Mth.sin(swap * Mth.PI);

		SkyLyricsConfig cfg = SkyLyricsConfig.get();
		g.pose().pushMatrix();
		try {
			g.pose().scale(1 / s, 1 / s);
			float px = Math.round(x * s), py = Math.round(y * s), pw = Math.round(width * s), ph = Math.round(PILL_H * s);
			float b = Math.max(1, Math.round(BORDER_W * s));
			roundRect(g, px, py, pw, ph, ph / 2, ARGB.color(Math.round(a * 255), BORDER));
			roundRect(g, px + b, py + b, pw - 2 * b, ph - 2 * b, ph / 2 - b,
					ARGB.color(Math.round(a * FILL_ALPHA * cfg.barBackground), FILL));

			int artPx = Math.round(ART * s);
			int artX = Math.round(px + ART_INSET * s), artY = Math.round(py + (ph - artPx) / 2);
			spinSpeed = cfg.barSpin ? TopBarLayout.spinSpeed(spinSpeed, tracker.playing(), dt) : 0;
			spin = cfg.barSpin ? TopBarLayout.spinStep(spin, spinSpeed, SPIN_TURNS, dt, Calm.motion()) : 0;
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
			dev.beli.skylyrics.media.Snapshot reading = shownReading;
			Track track = reading == null ? null : reading.track();
			float done = track == null ? -1 : TopBarLayout.progress(tracker.positionMs(reading), track.lengthMs());
			if (cfg.barProgress && done >= 0) {
				float lx = Math.round((x + ART_INSET + ART + TEXT_GAP) * s);
				float lw = Math.round(TopBarLayout.textRoom(width) * s);
				float lh = Math.max(1, Math.round(PROGRESS_H * s)), ly = Math.round(py + ph - PROGRESS_UP * s);
				roundRect(g, lx, ly, lw, lh, lh / 2, ARGB.color(Math.round(a * PROGRESS_TRACK * 255), accent(cfg)));
				roundRect(g, lx, ly, Math.max(lh, Math.round(lw * done)), lh, lh / 2,
						ARGB.color(Math.round(a * 255), accent(cfg)));
			}
		} finally {
			g.pose().popMatrix();
		}
		if (cfg.barArt && !art.hasTexture()) {
			Font font = mc.font;
			g.pose().pushMatrix();
			try {
				g.pose().translate(x + ART_INSET + ART / 2f, y + PILL_H / 2f);
				float noteSize = noteSize(guiScale, cfg.barSize);
				g.pose().scale(noteSize, noteSize);
				// As drawCenteredString draws it, with its shadow
				FormattedCharSequence note = note(Language.getInstance());
				g.drawString(font, note, -font.width(note) / 2, -4, noteColor(a * dim), true);
			} finally {
				g.pose().popMatrix();
			}
		}
	}

	/**
	 * The no-cover note's colour, {@code a} (0 to 1) visible. Its alpha never goes below 4, like {@link #fade}'s: Minecraft
	 * 1.21.5 and older draw text with less fully opaque, so the note blinked white as the bar appeared or hid.
	 */
	static int noteColor(float a) {
		return ARGB.color(Math.max(4, Math.round(a * 230)), 0xFFFFFF);
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
			w += (cfg.barPing ? SIGNAL_TEXT_GAP : 0) + LyricsFont.cachedWidth(font, style, FPS_SAMPLE) * textScale;
		}
		return w;
	}

	/** Signal bars (lit by ping on a server, dim in single player), then the frame rate; each can be off. */
	private void drawStats(GuiGraphics g, Minecraft mc, Font font, Style style, float textScale, float x, float midY) {
		SkyLyricsConfig cfg = SkyLyricsConfig.get();
		float a = alpha;
		if (cfg.barStats) {
			float tx = cfg.barPing ? x + SIGNAL_W + SIGNAL_TEXT_GAP : x;
			int fps = mc.getFps();
			if (fps != fpsShown) {
				fpsShown = fps;
				fpsText = fps + FPS;
			}
			text(g, font, visual(fpsText, style), tx, midY, textScale, fade(a, STAT_TEXT), ON_WORLD_SHADOW);
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
		try {
			g.pose().scale(1 / s, 1 / s);
			float barW = Math.round(BAR_W * s), pitch = (BAR_W + BAR_GAP) * s;
			float bottom = Math.round((midY + SIGNAL_BOTTOM) * s);
			for (int i = 0; i < SIGNAL_H.length; i++) {
				float h = Math.max(barW, Math.round(SIGNAL_H[i] * s));
				int color = i < lit ? accent(cfg) : SIGNAL_OFF;
				roundRect(g, Math.round(x * s + i * pitch), bottom - h, barW, h, barW / 2, ARGB.color(Math.round(a * 255), color));
			}
		} finally {
			g.pose().popMatrix();
		}
	}

	/** The line or the song's name; the old one fades out where the new one fades in. */
	private void drawText(GuiGraphics g, Font font, Style style, float scale, float x, float midY, long now, float dt) {
		float tx = x + ART_INSET + ART + TEXT_GAP;
		float room = TopBarLayout.textRoom(width);
		float p = Mth.clamp((now - swappedAt) / (float) SWAP_NS, 0, 1);
		int clipLeft = TopBarLayout.textClipLeft(x);
		// Words past the room stop where the highlight would, a little before the equaliser
		int clipRight = TopBarLayout.textClipRight(x, width, clipLeft, clipSize, clipGuiWidth);
		int clipTop = Math.round(midY - PILL_H / 2f) + 1, clipBottom = Math.round(midY + PILL_H / 2f) - 1;
		textClipLeft = clipLeft;
		textClipRight = clipRight;
		//? if <1.21.2 {
		/*// 1.21.1's scissor ignores the pose, so give it the box already scaled like the bar is
		int[] box = TopBarLayout.scaledClip(clipLeft, clipTop, clipRight, clipBottom, clipSize, clipGuiWidth, clipPivotY);
		boolean clipped = ClipBox.open(g, box[0], box[1], box[2], box[3]);
		*///?} else {
		boolean clipped = ClipBox.open(g, clipLeft, clipTop, clipRight, clipBottom);
		//?}
		if (!clipped) {
			// None of the box is in the window: none of the text could show
			return;
		}
		try {
			if (old != null && p < 1) {
				drawContent(g, font, style, scale, old, tx, midY, room, alpha * (1 - p), false, dt);
			} else {
				old = null;
			}
			if (content != null) {
				drawContent(g, font, style, scale, content, tx, midY, room, alpha * (old == null ? 1 : p), true, dt);
			}
		} finally {
			g.disableScissor();
		}
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
			/*TitleCut cut = titleCut(font, style, c.drawString(), c.sub(), limit);
			*///?} else {
			TitleCut cut = titleCut(font, style, c.text(), c.sub(), limit);
			//?}
			text(g, font, style, cut.title(), x, midY, scale, fade(a, SUNG));
			if (cut.sub() != null) {
				text(g, font, style, cut.sub(), x + cut.used() * scale, midY, scale, fade(a, UPCOMING));
			}
			return;
		}
		drawLyric(g, font, style, scale, c, x, midY, room, a, live, dt);
	}

	/** The line word by word: sung words white, the rest grey, and the last two sung on the highlight. */
	private void drawLyric(GuiGraphics g, Font font, Style style, float scale, Content c, float x, float midY, float room,
			float a, boolean live, float dt) {
		//? if >=26.1 {
		/*Laid lay = laid(font, style, scale, c.drawString());
		*///?} else {
		Laid lay = laid(font, style, scale, c.text());
		//?}
		LineLayout words = lay.layout();
		int n = words.size();
		if (n == 0) {
			return;
		}
		boolean rtl = words.rtl;
		float total = words.extent;
		boolean probe = BarProbe.begin(live);
		int current = 0;
		for (int i = 0; i < n; i++) {
			if (words.sung(i, c.sung())) {
				current = i;
			}
		}
		int prev = Math.max(0, current - 1);
		float wordGap = words.gap;
		float pad = TopBarLayout.highlightPad(wordGap);

		// Long lines scroll, in reading order, to keep the word being sung in view
		float target = TopBarLayout.lineShift(total, words.x[current], words.x[current] + words.w[current], room, pad);
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

		if (probe) {
			BarProbe.line(c.text(), rtl, scale, start, shift, pad, wordGap, total, g);
			BarProbe.clip(textClipLeft, textClipRight);
		}
		if (live) {
			float left = Math.min(words.x[prev], words.x[current]), right = Math.max(words.x[prev] + words.w[prev], words.x[current] + words.w[current]);
			if (hlRight < 0) {
				hlLeft = words.x[0];
				hlRight = hlLeft + HL_START_W;
			}
			float hk = 1 - (float) Math.exp(-dt * HL_RATE);
			hlLeft += (left - hlLeft) * hk;
			hlRight += (right - hlRight) * hk;
			float x0 = rtl ? start - hlRight : start + hlLeft;
			float hw = hlRight - hlLeft;
			if (probe) {
				BarProbe.highlight(current, left, right, hlLeft, hlRight, x0);
			}
			roundRectGui(g, x0 - pad, midY - HL_H / 2f, hw + 2 * pad, HL_H, HL_RADIUS,
					ARGB.color(Math.round(a * 255), highlight(accent(SkyLyricsConfig.get()))));
		}

		int sung = fade(a, SUNG), upcoming = fade(a, UPCOMING);
		for (int i = 0; i < n; i++) {
			// The old line greys out the moment it is replaced
			int color = live && words.sung(i, c.sung()) ? sung : upcoming;
			float wx = rtl ? start - words.x[i] - words.w[i] : start + words.x[i];
			if (probe) {
				BarProbe.word(font, style, words.texts[i], rtl, words.x[i], words.w[i], wx, scale);
			}
			text(g, font, lay.seqs()[i], wx, midY, scale, color);
		}
		if (probe) {
			BarProbe.end();
		}
	}

	/**
	 * The song's name cut to {@code limit} and, when there is room left, what follows it ({@code "  ·  " + sub}) cut
	 * to the rest. Worked out once per whole GUI unit of room: the pill's width animation changes the limit every
	 * frame for a moment, and a text is never measured or cut again for a sub-unit change. The room is rounded up, so a
	 * text that fit before still does; the drawing is clipped to the pill anyway.
	 */
	private static TitleCut titleCut(Font font, Style style, String title, String sub, float limit) {
		TitleKey key = new TitleKey(font, style, title, sub, titleRoom(limit), clipRoom(limit), Language.getInstance(), LyricsFont.reloadCount());
		return TITLES.get(key, k -> {
			Font f = (Font) k.font();
			float room = k.limit();
			// A title that fits the rounded-up room is kept whole; a longer one is cut to the rounded-down room
			String t = LyricsFont.cachedWidth(f, k.style(), k.title()) <= room ? k.title() : clip(f, k.style(), k.title(), k.floor());
			float used = LyricsFont.cachedWidth(f, k.style(), t);
			String s = null;
			if (!k.sub().isEmpty() && subFits(used, room)) {
				s = clip(f, k.style(), dotted(k.sub()), room - used);
			}
			return new TitleCut(t, used, s);
		});
	}

	/** Room the sub text needs beyond the title: "  ·  " and a few characters, so it never ends up as a lone ellipsis. */
	static final float SUB_MIN = 30;

	/** Whether the "  ·  Artist" part is shown after a title that used {@code used} of {@code room}. */
	static boolean subFits(float used, float room) {
		return used < room - SUB_MIN;
	}

	/** The room a cut is worked out for, in whole GUI units, rounded down so a cut never overflows. */
	static int clipRoom(float maxW) {
		return (int) Math.floor(maxW);
	}

	/** The room the title and what follows it are laid out for, in whole GUI units, rounded up so a text that fit still does. */
	static int titleRoom(float limit) {
		return (int) Math.ceil(limit);
	}

	/**
	 * {@code "  ·  " + sub}, drawn right after the title. A left-to-right mark keeps the dot on the left, next to the title,
	 * and the sub text is isolated so it keeps its own direction: a Hebrew or Arabic artist (or "112" in a Hebrew game)
	 * would otherwise pull the dot to its far end and run straight into the title, and a cut one keeps its "…" at its own
	 * end. The marks are never drawn and take no room.
	 */
	static String dotted(String sub) {
		return DOTTED.get(sub, x -> "‎  ·  ⁨" + x + "⁩");
	}

	/** The gap between the line's words, in GUI units. */
	private static float wordGap(Font font, Style style, float scale) {
		return LyricsFont.width(font, style, " ") * scale + WORD_EXTRA;
	}

	/**
	 * The line laid out: its words in reading order, a space (and a little) apart, and what each is drawn from. A word
	 * turns white as a whole once its singing starts, which {@link LineLayout#sung} works out each frame. The layout
	 * only changes with its {@link LineKey}, so it is kept while the line is on screen.
	 */
	private Laid laid(Font font, Style style, float scale, String text) {
		LineKey key = new LineKey(text, font, style, scale, Language.getInstance(), guiScale, LyricsFont.reloadCount());
		return laid.get(key, NowPlayingHud::lay);
	}

	private static Laid lay(LineKey k) {
		Font font = (Font) k.font();
		Style style = (Style) k.style();
		LineLayout layout = LineLayout.build(k.text(), k.scale(), wordGap(font, style, k.scale()),
				(piece, rtl) -> wordWidth(font, style, piece, rtl));
		FormattedCharSequence[] seqs = new FormattedCharSequence[layout.size()];
		for (int i = 0; i < seqs.length; i++) {
			seqs[i] = wordSeq(layout.texts[i], layout.rtl, style);
		}
		return new Laid(layout, seqs);
	}

	/** Draws text with its middle at {@code midY}, scaled about its left end. */
	private static void text(GuiGraphics g, Font font, Style style, String text, float x, float midY, float scale, int color) {
		text(g, font, visual(text, style), x, midY, scale, color);
	}

	/** The game's visual order of the text (right-to-left runs reversed), without its invisible direction marks (dropped after the order), kept while the same text is asked for again. */
	static FormattedCharSequence visual(String text, Style style) {
		Language language = Language.getInstance();
		VisualKey probe = VISUAL_PROBE;
		probe.text = text;
		probe.style = style;
		probe.language = language;
		FormattedCharSequence known = VISUAL.find(probe);
		if (known != null) {
			return known;
		}
		FormattedCharSequence made = LyricsFont.dropMarks(language.getVisualOrder(FormattedText.of(text, style)));
		VISUAL.put(new VisualKey(text, style, language), made);
		return made;
	}

	private static void text(GuiGraphics g, Font font, FormattedCharSequence seq, float x, float midY, float scale, int color) {
		text(g, font, seq, x, midY, scale, color, false);
	}

	private static void text(GuiGraphics g, Font font, FormattedCharSequence seq, float x, float midY, float scale, int color, boolean shadow) {
		g.pose().pushMatrix();
		try {
			g.pose().translate(x, midY);
			g.pose().scale(scale, scale);
			if (BarProbe.on()) {
				BarProbe.wordPose(g);
			}
			g.drawString(font, seq, 0, -4, color, shadow);
		} finally {
			g.pose().popMatrix();
		}
	}

	/**
	 * How wide one word is as {@link #word} draws it. A word left to the game is measured after the game joins its
	 * letters, since joined Arabic letters are narrower than the same letters standing alone.
	 */
	private static float wordWidth(Font font, Style style, String text, boolean lineRtl) {
		if (TopBarWords.vanilla(text)) {
			return font.getSplitter().stringWidth(visual(text, style));
		}
		return LyricsFont.width(font, style, TopBarWords.shown(text, lineRtl));
	}

	/** What the bar draws for one word, also what the dev probe measures. */
	static FormattedCharSequence wordSeq(String text, boolean lineRtl, Style style) {
		if (TopBarWords.vanilla(text)) {
			// Arabic with vowel marks: the game's own path
			return visual(text, style);
		}
		String shown = TopBarWords.shown(text, lineRtl);
		return sink -> {
			for (int i = 0, v = 0; i < shown.length(); v++) {
				int cp = shown.codePointAt(i);
				if (!sink.accept(v, style, cp)) {
					return false;
				}
				i += Character.charCount(cp);
			}
			return true;
		};
	}

	private static int fade(float a, int rgb) {
		return ARGB.color(Math.max(4, Math.round(a * 255)), rgb);
	}

	/**
	 * The highlight under the last two sung words for the bar's accent colour: a light accent (a pale album or lyrics
	 * colour, most of the colours to pick) is deepened just enough for the white words on it to read.
	 */
	static int highlight(int accent) {
		return Contrast.deepen(accent, SUNG, Contrast.FLOOR);
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
		Content line = currentLine(cfg, lyricsNow());
		return line == null ? null : line.text();
	}

	/** How far the song is through the line being sung, 0 to 1 (0 between lines). */
	float singingThrough(SkyLyricsConfig cfg) {
		Content line = currentLine(cfg, lyricsNow());
		return line == null ? 0 : line.through();
	}

	/**
	 * The line {@code delta} lines after the one being sung (-1 for the one before), for the card's dimmed
	 * lines. Null when there is none, when it is only a break, or when the song has moved well past its last line.
	 */
	@Nullable String singingNeighbour(SkyLyricsConfig cfg, int delta) {
		Lyrics lyrics = lyricsNow();
		if (!lyrics.synced() || lyrics.isEmpty()) {
			return null;
		}
		long time = lyricsTime(cfg);
		int i = lyrics.indexAt(time);
		if (i >= lyrics.lines().size() || (i >= 0 && time > lyrics.lineEnd(i) + 1500 && !lyrics.get(i).isBreak())) {
			return null;
		}
		// Before the first line there is nothing before, and the first line is next
		int j = i < 0 ? (delta > 0 ? delta - 1 : -1) : i + delta;
		if (j < 0 || j >= lyrics.lines().size()) {
			return null;
		}
		LyricLine line = lyrics.get(j);
		//? if >=26.1 {
		/*String raw = line.drawString();
		*///?} else {
		String raw = line.text();
		//?}
		if (line.isBreak() || raw.isBlank()) {
			return null;
		}
		return (cfg.uppercase ? WorldWords.capitals(raw) : raw).strip();
	}

	private long lyricsTime(SkyLyricsConfig cfg) {
		dev.beli.skylyrics.media.Snapshot now = tracker.snapshot();
		return tracker.positionMs(now) + cfg.offsetFor(now.track()) + LyricsRenderer.LEAD_MS;
	}

	/**
	 * The song is between its first and last line (it may be a gap between lines now), playing or paused: the bar
	 * bridges the gaps instead of blinking off and on, and a pause keeps it as it is, between lines as on a line (whose
	 * line stays while paused). Before the first line, or in a long outro after the last line, the bar goes back to hiding.
	 */
	static boolean singing(Lyrics lyrics, long time) {
		if (!lyrics.synced() || lyrics.isEmpty()) {
			return false;
		}
		return lyrics.indexAt(time) >= 0 && time <= lyrics.get(lyrics.lines().size() - 1).timeMs() + LAST_LINE_MS;
	}

	/** The lyrics of the song the player hears, none while the lyrics still belong to the song before. */
	private Lyrics lyricsNow() {
		return service.snapshot().forSong(tracker.track()).lyrics();
	}

	private @Nullable Content currentLine(SkyLyricsConfig cfg, Lyrics lyrics) {
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
		LineText shown = lineText;
		if (shown == null || shown.source().index() != i || !shown.source().line().equals(line) || shown.source().uppercase() != cfg.uppercase) {
			//? if >=26.1 {
			/*String text = cfg.uppercase ? WorldWords.capitals(line.drawString()) : line.drawString();
			*///?} else {
			String text = cfg.uppercase ? WorldWords.capitals(line.text()) : line.text();
			//?}
			shown = new LineText(new LineSource(i, line, cfg.uppercase), "L" + i + text, text.strip());
			lineText = shown;
		}
		return new Content(shown.key(), true, shown.text(), "", sung, through);
	}

	private Content songInfo(Track track, LyricsService.State state) {
		// Worked out from the song, its state and the language, so it is only built again when one of them changes
		SongSource source = new SongSource(track.title(), track.artist(), tracker.sourceName(), state, Language.getInstance(), SkyLyricsConfig.get().demoMode);
		Content kept = songContent;
		if (kept != null && source.equals(songSource)) {
			return kept;
		}
		Content made = buildSongInfo(track, state, source.demo());
		songSource = source;
		songContent = made;
		return made;
	}

	/** The line under the title: the artist (or player), then the lyrics status, then the Demo mark, each only if there is one. */
	static String subText(String base, String status, String demoMark) {
		StringBuilder sb = new StringBuilder(base);
		for (String part : new String[] {status, demoMark}) {
			if (!part.isEmpty()) {
				sb.append(sb.length() == 0 ? "" : "  ·  ").append(part);
			}
		}
		return sb.toString();
	}

	private Content buildSongInfo(Track track, LyricsService.State state, boolean demo) {
		String sub = track.artist().isEmpty() ? tracker.sourceName() : track.artist();
		String status = switch (state) {
			case LOADING -> Tr.t("finding lyrics…");
			case NOT_FOUND -> Tr.t("no lyrics");
			case ERROR -> Tr.t("lyrics sites unreachable");
			default -> "";
		};
		sub = subText(sub, status, demo ? Tr.t("Demo") : "");
		String title = track.title().isEmpty() ? Tr.t("Unknown song") : track.title();
		return new Content("S" + title + "\n" + sub, false, title, sub, 1, 1);
	}

	/** The text's width on screen, in GUI units. */
	private float contentWidth(Font font, Style style, float scale, Content c) {
		if (c.lyric()) {
			//? if >=26.1 {
			/*return laid(font, style, scale, c.drawString()).layout().extent;
			*///?} else {
			return laid(font, style, scale, c.text()).layout().extent;
			//?}
		}
		//? if >=26.1 {
		/*float w = LyricsFont.cachedWidth(font, style, c.drawString());
		*///?} else {
		float w = LyricsFont.cachedWidth(font, style, c.text());
		//?}
		if (!c.sub().isEmpty()) {
			w += LyricsFont.cachedWidth(font, style, dotted(c.sub()));
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

	/**
	 * Cuts the text to fit, ending with "…". Whether it fits is exact; the cut is kept while the same text is cut to
	 * the same room in whole GUI units (rounded down, so it never overflows), because the room moves every frame while
	 * the pill changes width and a cut worked out for each of those would never be found again.
	 */
	static String clip(Font font, Style style, String text, float maxW) {
		if (LyricsFont.cachedWidth(font, style, text) <= maxW) {
			return text;
		}
		int room = clipRoom(maxW);
		Language language = Language.getInstance();
		long reloads = LyricsFont.reloadCount();
		CLIP_PROBE.set(font, style, text, room, language, reloads);
		String known = CLIPS.find(CLIP_PROBE);
		if (known != null) {
			return known;
		}
		String made = cut(font, style, text, room);
		CLIPS.put(new ClipKey(font, style, text, room, language, reloads), made);
		return made;
	}

	private static String cut(Font font, Style style, String text, int maxW) {
		float room = maxW - LyricsFont.drawnWidth(font, style, "…");
		return dev.beli.skylyrics.util.TextCut.prefix(text, t -> LyricsFont.drawnWidth(font, style, t) <= room).stripTrailing() + "…";
	}

	/** {@link #roundRect} in GUI units, drawn in real pixels. */
	static void roundRectGui(GuiGraphics g, float x, float y, float w, float h, float r, int color) {
		float s = (float) Minecraft.getInstance().getWindow().getGuiScale();
		String before = BarProbe.on() ? BarProbe.pose(g) : null;
		g.pose().pushMatrix();
		try {
			g.pose().scale(1 / s, 1 / s);
			if (before != null) {
				BarProbe.box(x, w, before, BarProbe.pose(g));
			}
			roundRect(g, x * s, y * s, w * s, h * s, r * s, color);
		} finally {
			g.pose().popMatrix();
		}
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
