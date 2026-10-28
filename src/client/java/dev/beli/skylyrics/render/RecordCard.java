package dev.beli.skylyrics.render;

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

import dev.beli.skylyrics.SkyLyricsClient;
import dev.beli.skylyrics.Tr;
import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.config.SkyLyricsConfig.CardCorner;
import dev.beli.skylyrics.media.MediaTracker;
import dev.beli.skylyrics.media.Track;

/**
 * A small card in a corner of the screen: a record that turns while the song plays, with the cover as its label,
 * the song's name and artist, and a line that fills up with the time played and the time left.
 */
public final class RecordCard {
	static final float W = 156;
	static final float H = 46;
	/** Space between the card and the edge of the screen. */
	static final float MARGIN = 6;
	/** Half the hotbar's width, which also covers the hearts and food above it. */
	static final float HOTBAR_HALF = 91;
	/** Height of the hotbar with hearts, armour and the XP bar, for a card that has to sit above it. */
	static final float HOTBAR_UP = 52;
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
	private float alpha;
	private float spin;
	private float spinSpeed;
	private long lastFrame;

	public RecordCard(MediaTracker tracker, AlbumArt art) {
		this.tracker = tracker;
		this.art = art;
	}

	public void render(GuiGraphics g, DeltaTracker delta) {
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
				&& !Visibility.hidden(mc, cfg);
		alpha = want ? Math.min(1, alpha + dt * 4) : Math.max(0, alpha - dt * 3);
		if (alpha <= 0.01f) {
			return;
		}
		art.upload();

		float[] at = corner(cfg.cardCorner, g.guiWidth(), g.guiHeight());
		float x = at[0], y = at[1];
		float s = (float) mc.getWindow().getGuiScale();
		spinSpeed = TopBarLayout.spinSpeed(spinSpeed, tracker.playing(), dt);
		spin = (spin + spinSpeed * TURNS_PER_SECOND * Mth.TWO_PI * dt) % Mth.TWO_PI;

		// The panel and the record in real pixels, so the round edges stay smooth
		g.pose().pushMatrix();
		g.pose().scale(1 / s, 1 / s);
		float px = Math.round(x * s), py = Math.round(y * s), pw = Math.round(W * s), ph = Math.round(H * s);
		float b = Math.max(1, Math.round(s / 2));
		float r = Math.round(8 * s);
		NowPlayingHud.roundRect(g, px, py, pw, ph, r, ARGB.color(Math.round(alpha * 255), BORDER));
		NowPlayingHud.roundRect(g, px + b, py + b, pw - 2 * b, ph - 2 * b, r - b,
				ARGB.color(Math.round(alpha * 235 * cfg.barBackground), FILL));
		drawRecord(g, px + Math.round(PAD * s), py + Math.round((H - DISC) / 2 * s), Math.round(DISC * s));
		g.pose().popMatrix();

		Font font = mc.font;
		Style style = LyricsFont.base(cfg);
		Style bold = cfg.smoothFont ? Style.EMPTY.withFont(LyricsFont.BOLD) : Style.EMPTY.withBold(true);
		float scale = cfg.smoothFont ? TEXT_SCALE : 1;
		float tx = x + PAD + DISC + 6, room = (x + W - PAD - 2 - tx) / scale;
		String title = track.title().isEmpty() ? Tr.t("Unknown song") : track.title();
		String sub = track.artist().isEmpty() ? tracker.sourceName() : track.artist();
		text(g, font, bold, NowPlayingHud.clip(font, bold, title, room), tx, y + 11, scale, fade(TITLE));
		if (!sub.isEmpty()) {
			text(g, font, style, NowPlayingHud.clip(font, style, sub, room), tx, y + 21, scale, fade(SUB));
		}

		long played = tracker.positionMs(), length = track.lengthMs();
		float done = TopBarLayout.progress(played, length);
		float lineW = x + W - PAD - 2 - tx, lineY = y + 31;
		int accent = SkyLyricsClient.get().art().color();
		NowPlayingHud.roundRectGui(g, tx, lineY, lineW, 2, 1, ARGB.color(Math.round(alpha * 60), 0xFFFFFF));
		if (done >= 0) {
			NowPlayingHud.roundRectGui(g, tx, lineY, Math.max(2, lineW * done), 2, 1,
					ARGB.color(Math.round(alpha * 255), accent));
			float small = scale * 0.85f;
			text(g, font, style, clock(played), tx, lineY + 7, small, fade(SUB));
			String left = "-" + clock(length - played);
			float leftW = LyricsFont.width(font, style, left) * small;
			text(g, font, style, left, tx + lineW - leftW, lineY + 7, small, fade(SUB));
		}
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
			g.pose().translate(cx, cy);
			g.pose().rotate(spin);
			g.pose().translate(-cx, -cy);
			int lx = Math.round(cx - label / 2), ly = Math.round(cy - label / 2), lp = Math.round(label);
			// The texture is already cut round (AlbumArt)
			g.blit(RenderPipelines.GUI_TEXTURED, AlbumArt.TEXTURE, lx, ly, 0, 0, lp, lp,
					AlbumArt.SIZE, AlbumArt.SIZE, AlbumArt.SIZE, AlbumArt.SIZE, ARGB.white(alpha));
			g.pose().popMatrix();
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
		g.pose().translate(x, midY);
		g.pose().scale(scale, scale);
		g.drawString(font, Language.getInstance().getVisualOrder(FormattedText.of(text, style)), 0, -4, color, false);
		g.pose().popMatrix();
	}

	/** The card's top left corner, in GUI units. Bottom corners sit above the hotbar's reach at the sides. */
	static float[] corner(CardCorner corner, int guiW, int guiH) {
		float left = MARGIN, right = guiW - MARGIN - W;
		float top = MARGIN, bottom = guiH - MARGIN - H;
		// On a narrow screen a bottom corner would cover the hotbar, so the card moves up above it
		boolean onLeft = corner == CardCorner.BOTTOM_LEFT;
		if (onLeft ? left + W > guiW / 2f - HOTBAR_HALF : right < guiW / 2f + HOTBAR_HALF) {
			bottom = guiH - HOTBAR_UP - H;
		}
		return switch (corner) {
			case BOTTOM_LEFT -> new float[] {left, bottom};
			case TOP_LEFT -> new float[] {left, top};
			case TOP_RIGHT -> new float[] {right, top};
			case BOTTOM_RIGHT -> new float[] {right, bottom};
		};
	}

	/** "3:07" for minutes and seconds, "1:02:05" past an hour; never below zero. */
	static String clock(long ms) {
		long sec = Math.max(0, ms) / 1000;
		long h = sec / 3600, m = sec / 60 % 60, s = sec % 60;
		return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
	}
}
