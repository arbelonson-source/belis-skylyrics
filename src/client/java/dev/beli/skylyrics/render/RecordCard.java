package dev.beli.skylyrics.render;

import java.util.Locale;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.HumanoidArm;

import dev.beli.skylyrics.SkyLyricsClient;
import dev.beli.skylyrics.Tr;
import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.config.SkyLyricsConfig.CardCorner;
import dev.beli.skylyrics.media.MediaTracker;
import dev.beli.skylyrics.media.Track;
import dev.beli.skylyrics.util.Guard;

/**
 * A small card in a corner of the screen: a record that turns while the song plays, with the cover as its label,
 * the song's name and artist, and a line that fills up with the time played and the time left.
 */
public final class RecordCard {
	static final float W = 156;
	static final float H = 46;
	/** The strip under the card that shows the line being sung. */
	static final float LYRIC_H = 13;
	/** A dimmed line above or below the current one, when the card shows three lines. */
	static final float SIDE_H = 9;
	/** The dimmed lines are drawn this much smaller than the current one. */
	private static final float SIDE_SCALE = 0.8f;
	/** Space between the card and the edge of the screen. */
	static final float MARGIN = 6;
	/** Half the hotbar's width, which also covers the hearts and food above it. */
	static final float HOTBAR_HALF = 91;
	/** How far the off-hand slot reaches past the middle of the screen, on the side away from the main hand. */
	static final float OFFHAND_REACH = HOTBAR_HALF + 29;
	/** The same for the hotbar-style attack indicator, which sits on the main-hand side. */
	static final float INDICATOR_REACH = HOTBAR_HALF + 24;
	/** Height of the hotbar with hearts, armour and the XP bar, for a card that has to sit above it. */
	static final float HOTBAR_UP = 52;
	/** The held item's name reaches this far up (this high when the player can't be hurt), and the card keeps this clear of it. */
	static final float NAME_UP = 60, NAME_UP_SAFE = 46, NAME_GAP = 2;
	/** The card keeps this far above the armour, and above the bottom bar. */
	static final float ARMOUR_GAP = 3, BAR_GAP = 3;
	/** Bottom of the good effects row and of the bad effects row under it. */
	static final int GOOD_ROW = 25, BAD_ROW = 51;
	private static final float DISC = 38;
	private static final float PAD = 4;
	/** One turn every 1.8 seconds, a little faster than a real record so it reads as turning when small. */
	private static final float TURNS_PER_SECOND = 1 / 1.8f;
	private static final float TEXT_SCALE = 0.75f;
	private static final int FILL = 0x181523;
	private static final int BORDER = 0x4B4665;
	private static final int VINYL = 0x0E0D12;
	private static final int GROOVE = 0x24222C;
	private static final int TITLE = 0xFCF8FF;
	private static final int SUB = 0x8C879E;
	private static final int NO_ART = 0x3A2E5C;

	private final MediaTracker tracker;
	private final AlbumArt art;
	private final NowPlayingHud hud;
	private float alpha;
	/** The line on the strip and how far it has faded in, so a new line eases in instead of popping. */
	private String lyric = "";
	/** The placeholder title, translated once per language. */
	private final LangText unknownSong = new LangText(() -> Tr.t("Unknown song"));
	private float lyricIn;
	private float spin;
	private float spinSpeed;
	private long lastFrame;
	/** The card's eased top edge (NaN while it isn't showing), so it glides when its place changes. */
	private float cardY = Float.NaN;

	public RecordCard(MediaTracker tracker, AlbumArt art, NowPlayingHud hud) {
		this.tracker = tracker;
		this.art = art;
		this.hud = hud;
	}

	public void render(GuiGraphics g, DeltaTracker delta) {
		Guard.run("record card", () -> draw(g, delta));
	}

	private void draw(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		SkyLyricsConfig cfg = SkyLyricsConfig.get();
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;

		Track track = tracker.track();
		//? if >=26.2 {
		/*boolean want = cfg.enabled && cfg.recordCard && !track.equals(Track.NONE) && !mc.gui.hud.isHidden()
		*///?} else {
		boolean want = cfg.enabled && cfg.recordCard && !track.equals(Track.NONE) && !mc.options.hideGui
		//?}
				&& !Visibility.hidden(mc, cfg)
				// The debug screen (F3) fills the top corners and a card there would be drawn over its text
				&& !(topCorner(cfg.cardCorner) && NowPlayingHud.f3Open(mc))
				// The player list (held Tab) is drawn after the card, over it and see-through
				&& !(topCorner(cfg.cardCorner) && NowPlayingHud.tabListUp(mc));
		int lines = lines(cfg.cardLines, g.guiHeight());
		float h = height(cfg.cardLyrics, lines);
		float[] at = null;
		if (want) {
			at = place(mc, cfg.cardCorner, g.guiWidth(), g.guiHeight(), h);
			// No room clear of the scoreboard sidebar or the chat: fade out like for the player list
			want = at != null;
		}
		alpha = want ? Math.min(1, alpha + dt * 4) : Math.max(0, alpha - dt * 3);
		if (alpha <= 0.01f) {
			cardY = Float.NaN;
			return;
		}
		art.upload();
		if (at == null) {
			// Fading out: stay where the card last was
			at = corner(mc, cfg.cardCorner, g.guiWidth(), g.guiHeight(), effectsBottom(mc), h);
			at[1] = Float.isNaN(cardY) ? at[1] : cardY;
		}
		cardY = ease(cardY, at[1], dt);
		float x = at[0], y = cardY;
		float s = (float) mc.getWindow().getGuiScale();
		spinSpeed = TopBarLayout.spinSpeed(spinSpeed, tracker.playing(), dt);
		spin = TopBarLayout.spinStep(spin, spinSpeed, TURNS_PER_SECOND, dt, Calm.motion());

		// The panel and the record in real pixels, so the round edges stay smooth
		g.pose().pushMatrix();
		try {
			g.pose().scale(1 / s, 1 / s);
			float px = Math.round(x * s), py = Math.round(y * s), pw = Math.round(W * s), ph = Math.round(h * s);
			float b = Math.max(1, Math.round(s / 2));
			float r = Math.round(8 * s);
			NowPlayingHud.roundRect(g, px, py, pw, ph, r, ARGB.color(Math.round(alpha * 255), BORDER));
			NowPlayingHud.roundRect(g, px + b, py + b, pw - 2 * b, ph - 2 * b, r - b,
					ARGB.color(Math.round(alpha * 235 * cfg.barBackground), FILL));
			drawRecord(g, px + Math.round(PAD * s), py + Math.round((H - DISC) / 2 * s), Math.round(DISC * s));
		} finally {
			g.pose().popMatrix();
		}

		Font font = mc.font;
		LyricsFont.checkFonts(font);
		Style style = LyricsFont.base(cfg);
		Style bold = cfg.smoothFont ? Style.EMPTY.withFont(LyricsFont.BOLD) : Style.EMPTY.withBold(true);
		float scale = cfg.smoothFont ? TEXT_SCALE : 1;
		float tx = x + PAD + DISC + 6, room = (x + W - PAD - 2 - tx) / scale;
		String title = track.title().isEmpty() ? unknownSong.get() : track.title();
		String sub = track.artist().isEmpty() ? tracker.sourceName() : track.artist();
		text(g, font, bold, NowPlayingHud.clip(font, bold, title, room), tx, y + 11, scale, fade(TITLE));
		if (!sub.isEmpty()) {
			text(g, font, style, NowPlayingHud.clip(font, style, sub, room), tx, y + 21, scale, fade(SUB));
		}

		long played = tracker.positionMs(), length = track.lengthMs();
		float done = TopBarLayout.progress(played, length);
		float lineW = x + W - PAD - 2 - tx, lineY = y + 31;
		int accent = SkyLyricsClient.get().art().color();
		// Without a song length there is nothing to fill, so no empty line either
		if (done >= 0) {
			NowPlayingHud.roundRectGui(g, tx, lineY, lineW, 2, 1, ARGB.color(Math.round(alpha * 60), 0xFFFFFF));
			NowPlayingHud.roundRectGui(g, tx, lineY, Math.max(2, lineW * done), 2, 1,
					ARGB.color(Math.round(alpha * 255), accent));
			float small = scale * 0.85f;
			String[] times = timesShown(played, length);
			text(g, font, style, times[0], tx, lineY + 7, small, fade(SUB));
			// Kept left to right in a Hebrew or Arabic game, where "-2:35" would read "2:35-"
			String left = LyricsFont.ltr(times[1]);
			float leftW = LyricsFont.cachedWidth(font, style, left) * small;
			text(g, font, style, left, tx + lineW - leftW, lineY + 7, small, fade(SUB));
		}
		if (cfg.cardLyrics) {
			drawLyric(g, font, style, cfg, x, y + H, scale, dt, lines);
		}
	}

	/**
	 * The line being sung, across the strip under the record. Between lines (or with no timed lyrics)
	 * it shows a quiet note, so the card keeps its size instead of jumping.
	 */
	private void drawLyric(GuiGraphics g, Font font, Style style, SkyLyricsConfig cfg, float x, float top, float scale, float dt, int lines) {
		String now = hud.singingNow(cfg);
		String want = now == null ? "" : now;
		if (!want.equals(lyric)) {
			lyric = want;
			lyricIn = 0;
		}
		lyricIn = Math.min(1, lyricIn + dt * LYRIC_FADE_IN);
		float rowTop = top + currentRowTop(lines);
		float midY = rowTop + LYRIC_H / 2 - 2;
		float left = x + PAD + 2, room = W - 2 * PAD - 4;
		// A hairline between the song and its words
		NowPlayingHud.roundRectGui(g, left, top - 2, room, 1, 0, ARGB.color(Math.round(alpha * 40), 0xFFFFFF));
		float a = alpha * lyricIn;
		if (lines >= 3) {
			float side = scale * SIDE_SCALE;
			dimLine(g, font, style, hud.singingNeighbour(cfg, -1), x, top + SIDE_H / 2 - 1.5f, side, left, room, a);
			dimLine(g, font, style, hud.singingNeighbour(cfg, 1), x, rowTop + LYRIC_H + SIDE_H / 2 - 1.5f, side, left, room, a);
		}
		if (lyric.isEmpty()) {
			float noteW = LyricsFont.cachedWidth(font, style, REST) * scale;
			text(g, font, style, REST, x + (W - noteW) / 2, midY, scale, ARGB.color(Math.max(4, Math.round(a * 160)), SUB));
			return;
		}
		float lineW = LyricsFont.cachedWidth(font, style, lyric) * scale;
		int color = ARGB.color(Math.max(4, Math.round(a * 255)), SkyLyricsClient.get().renderer().color());
		if (lineW <= room) {
			text(g, font, style, lyric, x + (W - lineW) / 2, midY, scale, color);
			return;
		}
		// Too long for the strip: the line slides along as it is sung, so its end can be read too
		float shift = lyricShift(lineW, room, hud.singingThrough(cfg));
		float start = NowPlayingHud.isRtl(lyric) ? left - (lineW - room) + shift : left - shift;
		g.enableScissor(Math.round(left), Math.round(rowTop), Math.round(left + room), Math.round(rowTop + LYRIC_H));
		try {
			text(g, font, style, lyric, start, midY, scale, color);
		} finally {
			g.disableScissor();
		}
	}

	/** A line before or after the current one: dimmed, smaller, centred, and cut with "…" when too long. */
	private void dimLine(GuiGraphics g, Font font, Style style, String line, float x, float midY, float scale, float left, float room, float a) {
		if (line == null || line.isEmpty()) {
			return;
		}
		String cut = NowPlayingHud.clip(font, style, line, room / scale);
		float w = LyricsFont.cachedWidth(font, style, cut) * scale;
		text(g, font, style, cut, x + (W - w) / 2, midY, scale, ARGB.color(Math.max(4, Math.round(a * 110)), SUB));
	}

	/**
	 * How far a line wider than the strip has slid, from 0 (its start showing) to {@code lineW - room} (its end
	 * showing). It rests at the start for the first part of the line's time, glides across, and rests at the end.
	 *
	 * @param through how far the song is through the line's time, 0 to 1
	 */
	static float lyricShift(float lineW, float room, float through) {
		float most = Math.max(0, lineW - room);
		float t = Mth.clamp((through - SLIDE_FROM) / (SLIDE_TO - SLIDE_FROM), 0, 1);
		return most * t * t * (3 - 2 * t);
	}

	/** A long line starts sliding this far through its time and has finished by here. */
	private static final float SLIDE_FROM = 0.15f, SLIDE_TO = 0.75f;

	/** How fast a new line fades in on the strip: a quarter of a second. */
	private static final float LYRIC_FADE_IN = 4;
	/** What the strip shows between lines. */
	private static final String REST = "♪";

	/** The card's height, with the lyric strip when it is on. */
	static float height(boolean lyrics) {
		return height(lyrics, 1);
	}

	/** The card's height with the lyric strip of {@code lines} lines (1 or 3) when it is on. */
	static float height(boolean lyrics, int lines) {
		return lyrics ? H + stripHeight(lines) : H;
	}

	/** The strip's height: the current line, or the current line between a dimmed one above and below. */
	static float stripHeight(int lines) {
		return lines >= 3 ? LYRIC_H + 2 * SIDE_H : LYRIC_H;
	}

	/** Where the current line's row starts, down from the top of the strip. */
	static float currentRowTop(int lines) {
		return lines >= 3 ? SIDE_H : 0;
	}

	/**
	 * How many lines the strip shows: the wanted 1 or 3, but one when three would not fit on this screen
	 * with the card's margins.
	 */
	static int lines(int wanted, int guiH) {
		return wanted >= 3 && height(true, 3) + 2 * MARGIN <= guiH ? 3 : 1;
	}

	/** A dark disc with a few grooves, the cover turning in the middle as its label, and the hole. */
	private void drawRecord(GuiGraphics g, float x, float y, float d) {
		float cx = x + d / 2, cy = y + d / 2;
		disc(g, cx, cy, d, VINYL, 1);
		for (float ring : GROOVES) {
			disc(g, cx, cy, d * ring, GROOVE, 0.9f);
			disc(g, cx, cy, d * ring - Math.max(2, d / 40), VINYL, 1);
		}
		float label = Math.round(d * 0.5f);
		if (art.hasTexture()) {
			g.pose().pushMatrix();
			try {
				g.pose().translate(cx, cy);
				g.pose().rotate(spin);
				g.pose().translate(-cx, -cy);
				int lx = Math.round(cx - label / 2), ly = Math.round(cy - label / 2), lp = Math.round(label);
				// The texture is already cut round (AlbumArt)
				g.blit(RenderPipelines.GUI_TEXTURED, AlbumArt.TEXTURE, lx, ly, 0, 0, lp, lp,
						AlbumArt.SIZE, AlbumArt.SIZE, AlbumArt.SIZE, AlbumArt.SIZE, ARGB.white(alpha));
			} finally {
				g.pose().popMatrix();
			}
		} else {
			disc(g, cx, cy, label, NO_ART, 1);
			// A mark on the plain label, so you can still see it turn
			float dot = Math.max(2, label / 7);
			float mx = cx + Mth.cos(spin) * label / 4, my = cy + Mth.sin(spin) * label / 4;
			disc(g, mx, my, dot, 0xFFFFFF, 0.7f);
		}
		// A soft shine across the top left, the way light catches vinyl
		float shine = d * 0.82f;
		disc(g, cx - d * 0.04f, cy - d * 0.04f, shine, 0xFFFFFF, 0.05f);
		float hole = Math.max(3, Math.round(d * 0.08f));
		disc(g, cx, cy, hole + 2, 0xFFFFFF, 0.55f);
		disc(g, cx, cy, hole, FILL, 1);
	}

	/** Where grooves sit, as a part of the record's width. */
	private static final float[] GROOVES = {0.92f, 0.8f, 0.68f};

	private void disc(GuiGraphics g, float cx, float cy, float d, int rgb, float a) {
		if (a <= 0) {
			return;
		}
		NowPlayingHud.roundRect(g, cx - d / 2, cy - d / 2, d, d, d / 2, ARGB.color(Math.round(alpha * a * 255), rgb));
	}

	private int fade(int rgb) {
		return ARGB.color(Math.max(4, Math.round(alpha * 255)), rgb);
	}

	/** Draws text with its middle at {@code midY}, scaled about its left end. */
	private static void text(GuiGraphics g, Font font, Style style, String text, float x, float midY, float scale, int color) {
		g.pose().pushMatrix();
		try {
			g.pose().translate(x, midY);
			g.pose().scale(scale, scale);
			g.drawString(font, NowPlayingHud.visual(text, style), 0, -4, color, false);
		} finally {
			g.pose().popMatrix();
		}
	}

	/** Whether the corner is at the top of the screen, where the debug screen (F3) draws its text. */
	static boolean topCorner(CardCorner corner) {
		return corner == CardCorner.TOP_LEFT || corner == CardCorner.TOP_RIGHT;
	}

	/** How many good and bad potion effect icons Minecraft shows in the top right corner. */
	static int[] effectCounts(Minecraft mc) {
		int good = 0, bad = 0;
		if (mc.player != null) {
			for (MobEffectInstance effect : mc.player.getActiveEffects()) {
				if (effect.showIcon()) {
					if (effect.getEffect().value().isBeneficial()) {
						good++;
					} else {
						bad++;
					}
				}
			}
		}
		return new int[] {good, bad};
	}

	/**
	 * Where the bottom of the potion effect icons is, which Minecraft draws in the top right corner:
	 * one row for good effects, a second row under it for bad ones. Zero with no icons.
	 */
	static int effectsBottom(Minecraft mc) {
		if (mc.player == null) {
			return 0;
		}
		int bottom = 0;
		for (MobEffectInstance effect : mc.player.getActiveEffects()) {
			if (effect.showIcon()) {
				bottom = Math.max(bottom, TopBarLayout.effectRow(effect.getEffect().value().isBeneficial() ? GOOD_ROW : BAD_ROW, mc.isDemo()));
			}
		}
		return bottom;
	}

	static float[] corner(CardCorner corner, int guiW, int guiH) {
		return corner(corner, guiW, guiH, 0);
	}

	static float[] corner(CardCorner corner, int guiW, int guiH, int effects) {
		return corner(corner, guiW, guiH, effects, H);
	}

	/**
	 * The card's top left corner for a right-handed player without the hotbar attack indicator; see the
	 * longer form below.
	 */
	static float[] corner(CardCorner corner, int guiW, int guiH, int effects, float h) {
		return corner(corner, guiW, guiH, effects, h, true, false);
	}

	/**
	 * How far from the middle of the screen the things beside the hotbar reach on one side: the off-hand slot
	 * on the side away from the main hand, the attack indicator (when it is in the hotbar style) on the other,
	 * and just the hotbar's half-width otherwise.
	 */
	static float sideReach(boolean leftSide, boolean rightHanded, boolean indicator) {
		boolean offHandSide = leftSide == rightHanded;
		if (offHandSide) {
			return OFFHAND_REACH;
		}
		return indicator ? INDICATOR_REACH : HOTBAR_HALF;
	}

	/**
	 * The card's top left corner, in GUI units. Bottom corners sit above the hotbar's reach at the sides,
	 * including the off-hand slot and the attack indicator. {@code effects} is how far down the effect icons
	 * reach, so a top right card can sit under them, and {@code h} is the card's height. The card never
	 * leaves the screen.
	 */
	static float[] corner(CardCorner corner, int guiW, int guiH, int effects, float h, boolean rightHanded, boolean indicator) {
		return corner(corner, guiW, guiH, effects, h, rightHanded, indicator, 0);
	}

	/**
	 * How far down a top corner card has to start to clear the boss bars: the bars are 182 wide in the middle, so on a
	 * narrow screen the card would reach under them. 0 when it doesn't, or for a bottom corner.
	 */
	static float bossClear(CardCorner corner, int guiW, int guiH, int bossBars) {
		if (bossBars <= 0 || corner == CardCorner.BOTTOM_LEFT || corner == CardCorner.BOTTOM_RIGHT) {
			return 0;
		}
		boolean reaches = corner == CardCorner.TOP_LEFT
				? MARGIN + W > guiW / 2f - HOTBAR_HALF
				: guiW - MARGIN - W < guiW / 2f + HOTBAR_HALF;
		return reaches ? NowPlayingHud.topBelowBossBars(bossBars, guiH) : 0;
	}

	/** The full form: {@code bossClear} is the least a top card's top may be, from {@link #bossClear}. */
	static float[] corner(CardCorner corner, int guiW, int guiH, int effects, float h, boolean rightHanded, boolean indicator,
			float bossClear) {
		return corner(corner, guiW, guiH, effects, h, rightHanded, indicator, bossClear, HOTBAR_UP, null);
	}

	/**
	 * How far above the screen's bottom a lifted card's lower edge sits: above the hotbar's hearts and armour
	 * ({@code healthTopUp}, from {@link TopBarLayout#healthTopUp}) and above the held item's name (which a player that
	 * can't be hurt gets lower down), never lower than {@link #HOTBAR_UP}.
	 */
	static float liftUp(boolean canHurt, float healthTopUp) {
		if (!canHurt) {
			return Math.max(HOTBAR_UP, NAME_UP_SAFE + NAME_GAP);
		}
		return Math.max(Math.max(HOTBAR_UP, NAME_UP + NAME_GAP), healthTopUp + ARMOUR_GAP);
	}

	/**
	 * The card's place above the bottom bar: its lower edge {@link #BAR_GAP} above the bar's top when the card's box
	 * reaches into the bar's box ({@code bar}: left, top, right, bottom). Returns {@code bottom} when it doesn't, or when
	 * there is no room above the bar.
	 */
	static float aboveBar(float[] bar, float x, float bottom, float h) {
		if (bar == null || x + W <= bar[0] || x >= bar[2] || bottom + h <= bar[1] - BAR_GAP || bottom >= bar[3]) {
			return bottom;
		}
		float up = bar[1] - BAR_GAP - h;
		return up >= 0 ? up : bottom;
	}

	/** The full form with the lift ({@link #liftUp}) and the bottom bar's box (null when it isn't there). */
	static float[] corner(CardCorner corner, int guiW, int guiH, int effects, float h, boolean rightHanded, boolean indicator,
			float bossClear, float liftUp, float[] bar) {
		return corner(corner, guiW, guiH, effects, h, rightHanded, indicator, bossClear, liftUp, bar, false);
	}

	/**
	 * Half the width the action bar message is taken to cover around the screen's middle, and how far up its top reaches
	 * with a gap: a lifted card whose side reaches into that span while a message shows goes above the text.
	 */
	static final float ACTION_HALF = 90, ACTION_LIFT = TopBarLayout.ACTION_UP + TopBarLayout.ACTION_GAP;

	/** One step of the card's glide towards its place; the first value (NaN) takes the place at once. Same spring as the bar's. */
	static float ease(float current, float target, float dt) {
		if (Float.isNaN(current)) {
			return target;
		}
		float next = current + (target - current) * (1 - (float) Math.exp(-dt * 12));
		return Math.abs(target - next) < 0.05f ? target : next;
	}

	/** The same, with {@code actionBar} saying a message shows above the hotbar. */
	static float[] corner(CardCorner corner, int guiW, int guiH, int effects, float h, boolean rightHanded, boolean indicator,
			float bossClear, float liftUp, float[] bar, boolean actionBar) {
		return corner(corner, guiW, guiH, effects, h, rightHanded, indicator, bossClear, liftUp, bar, actionBar, null);
	}

	/** A top card's top: at least {@link #BAR_GAP} below the top bar's box ({@code topBar}, null when none) when their widths overlap. */
	static float belowTopBar(float[] topBar, float x, float top) {
		if (topBar == null || x + W <= topBar[0] || x >= topBar[2]) {
			return top;
		}
		return Math.max(top, topBar[3] + BAR_GAP);
	}

	/** The same, with the top bar's box ({@code topBar}: left, top, right, bottom; null when it isn't at the top). */
	static float[] corner(CardCorner corner, int guiW, int guiH, int effects, float h, boolean rightHanded, boolean indicator,
			float bossClear, float liftUp, float[] bar, boolean actionBar, float[] topBar) {
		float left = MARGIN, right = guiW - MARGIN - W;
		float top = MARGIN, bottom = guiH - MARGIN - h;
		if (corner == CardCorner.TOP_RIGHT && effects > 0) {
			top = effects + 2;
		}
		top = Math.max(top, bossClear);
		if (corner == CardCorner.TOP_LEFT || corner == CardCorner.TOP_RIGHT) {
			top = belowTopBar(topBar, corner == CardCorner.TOP_LEFT ? left : right, top);
		}
		// On a narrow screen a bottom corner would cover the hotbar or what sits beside it, so the card moves up above it
		boolean onLeft = corner == CardCorner.BOTTOM_LEFT;
		float reach = sideReach(onLeft, rightHanded, indicator);
		if (onLeft ? left + W > guiW / 2f - reach : right < guiW / 2f + reach) {
			boolean overText = onLeft ? left + W > guiW / 2f - ACTION_HALF : right < guiW / 2f + ACTION_HALF;
			bottom = guiH - (actionBar && overText ? Math.max(liftUp, ACTION_LIFT) : liftUp) - h;
		}
		bottom = aboveBar(bar, onLeft ? left : right, bottom, h);
		top = onScreen(top, guiH, h);
		bottom = onScreen(bottom, guiH, h);
		return switch (corner) {
			case BOTTOM_LEFT -> new float[] {left, bottom};
			case TOP_LEFT -> new float[] {left, top};
			case TOP_RIGHT -> new float[] {right, top};
			case BOTTOM_RIGHT -> new float[] {right, bottom};
		};
	}

	private static float onScreen(float y, int guiH, float h) {
		return Math.max(0, Math.min(y, guiH - h));
	}

	/**
	 * The card's place with everything on the right and left that the game draws over it taken into account: the scoreboard
	 * sidebar and (bottom right) the subtitles move a right card out of their way, and the open chat screen covers a bottom
	 * left one. Null when there is no room clear of them, which fades the card.
	 */
	private static float[] place(Minecraft mc, CardCorner corner, int guiW, int guiH, float h) {
		float[] at = corner(mc, corner, guiW, guiH, effectsBottom(mc), h);
		boolean chatOpen = NowPlayingHud.chatOpen(mc);
		if (corner == CardCorner.BOTTOM_LEFT) {
			at[1] = TopBarLayout.aboveChatInput(chatOpen, at[1], h, guiH);
			if (chatOpen) {
				double scale = mc.options.chatScale().get();
				float chatW = (float) (ChatComponent.getWidth(mc.options.chatWidth().get()) * scale);
				float chatH = (float) (ChatComponent.getHeight(mc.options.chatHeightFocused().get()) * scale);
				boolean hidden = mc.options.chatVisibility().get() == ChatVisiblity.HIDDEN;
				if (TopBarLayout.chatBoxCovers(true, hidden, guiH, chatW, chatH, at[0], at[1], W, h)) {
					return null;
				}
			}
			return at;
		}
		if (corner == CardCorner.TOP_LEFT) {
			return at;
		}
		if (corner == CardCorner.BOTTOM_RIGHT) {
			at[1] = TopBarLayout.aboveChatInput(chatOpen, at[1], h, guiH);
		}
		float[] sidebar = NowPlayingHud.sidebarBox(mc, guiW, guiH);
		float[] subtitles = corner == CardCorner.BOTTOM_RIGHT && mc.options.showSubtitles().get()
				? NowPlayingHud.subtitleBox(mc, guiW, guiH) : null;
		if (sidebar == null && subtitles == null) {
			return at;
		}
		// Going up, it stops at where a card in the top right corner would start
		float minTop = corner == CardCorner.TOP_RIGHT ? at[1] : corner(mc, CardCorner.TOP_RIGHT, guiW, guiH, effectsBottom(mc), h)[1];
		float y = TopBarLayout.dodgeY(at[0], at[1], W, h, minTop, guiH, sidebar, subtitles);
		if (Float.isNaN(y)) {
			return null;
		}
		return new float[] {at[0], y};
	}

	/** The card with the hotbar's neighbours taken from the player's settings. */
	private static float[] corner(Minecraft mc, CardCorner corner, int guiW, int guiH, int effects, float h) {
		boolean rightHanded = mc.options.mainHand().get() == HumanoidArm.RIGHT;
		boolean indicator = mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR;
		int bars = NowPlayingHud.bossBarCount(mc);
		boolean canHurt = mc.gameMode == null || mc.gameMode.canHurtPlayer();
		float demo = TopBarLayout.demoClear(mc.isDemo(), guiW, NowPlayingHud.demoTimerWidth(mc),
				corner == CardCorner.TOP_LEFT ? MARGIN + W : guiW - MARGIN);
		return corner(corner, guiW, guiH, effects, h, rightHanded, indicator, Math.max(bossClear(corner, guiW, guiH, bars), topCorner(corner) ? demo : 0),
				liftUp(canHurt, NowPlayingHud.healthTopUp(mc)), NowPlayingHud.bottomBox(), NowPlayingHud.actionBarShowing(mc), NowPlayingHud.topBox());
	}

	/** The last {@link #times}, kept while the same whole seconds are asked for (the card asks every frame). */
	private long timesPlayedSec = -1, timesLengthSec = -1;
	private String[] timesShown = new String[0];

	private String[] timesShown(long playedMs, long lengthMs) {
		long playedSec = Math.max(0, playedMs) / 1000, lengthSec = Math.round(Math.max(0, lengthMs) / 1000.0);
		if (playedSec != timesPlayedSec || lengthSec != timesLengthSec) {
			timesPlayedSec = playedSec;
			timesLengthSec = lengthSec;
			timesShown = times(playedMs, lengthMs);
		}
		return timesShown;
	}

	/**
	 * The time played and the time left, like "1:05" and "-2:35". Both count whole seconds of a song
	 * rounded to the nearest second, so together they always make its length, and the time played
	 * stops at the end instead of running past it.
	 */
	static String[] times(long playedMs, long lengthMs) {
		long length = Math.round(Math.max(0, lengthMs) / 1000.0);
		long played = Math.min(Math.max(0, playedMs) / 1000, length);
		long left = length - played;
		return new String[] {clock(played * 1000), (left > 0 ? "-" : "") + clock(left * 1000)};
	}

	/** "3:07" for minutes and seconds, "1:02:05" past an hour; never below zero. */
	static String clock(long ms) {
		long sec = Math.max(0, ms) / 1000;
		long h = sec / 3600, m = sec / 60 % 60, s = sec % 60;
		return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) : String.format(Locale.ROOT, "%d:%02d", m, s);
	}
}
