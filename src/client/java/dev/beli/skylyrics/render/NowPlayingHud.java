package dev.beli.skylyrics.render;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.lyrics.LyricsService;
import dev.beli.skylyrics.media.MediaTracker;
import dev.beli.skylyrics.media.Track;

/** Small "now playing" card at the top of the screen: cover, title, artist and a progress bar. */
public final class NowPlayingHud {
	private static final long SHOW_NS = 6_000_000_000L;
	private static final int W = 168;
	private static final int H = 34;

	private final MediaTracker tracker;
	private final LyricsService service;
	private final AlbumArt art;

	private long shownAt;
	private long lastFrame;
	private float alpha;
	private boolean forced;

	public NowPlayingHud(MediaTracker tracker, LyricsService service, AlbumArt art) {
		this.tracker = tracker;
		this.service = service;
		this.art = art;
	}

	/** Shows the card for a few seconds (called on song change and when the key is pressed). */
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
		boolean want = cfg.enabled && cfg.nowPlayingHud && !track.equals(Track.NONE) && !mc.options.hideGui
				&& (forced || !cfg.hudOnlyOnChange || now - shownAt < SHOW_NS);
		alpha = want ? Math.min(1, alpha + dt * 4) : Math.max(0, alpha - dt * 3);
		if (alpha <= 0.01f) {
			return;
		}
		art.upload();
		draw(g, mc.font, track, alpha, (g.guiWidth() - W) / 2, 6 - Math.round((1 - ease(alpha)) * 10));
	}

	private void draw(GuiGraphics g, Font font, Track track, float a, int x, int y) {
		int accent = art.color();
		roundRect(g, x, y, W, H, ARGB.color(Math.round(a * 200), 0x12101A));

		int cover = H - 8;
		if (art.hasTexture()) {
			g.blit(RenderPipelines.GUI_TEXTURED, AlbumArt.TEXTURE, x + 4, y + 4, 0, 0, cover, cover,
					AlbumArt.SIZE, AlbumArt.SIZE, AlbumArt.SIZE, AlbumArt.SIZE, ARGB.white(a));
		} else {
			g.fill(x + 4, y + 4, x + 4 + cover, y + 4 + cover, ARGB.color(Math.round(a * 255), LyricsRenderer.mix(accent, 0x000000, 0.4f)));
			g.drawCenteredString(font, "♪", x + 4 + cover / 2, y + 4 + (cover - 8) / 2, ARGB.color(Math.round(a * 255), 0xFFFFFF));
		}

		int tx = x + 4 + cover + 7;
		int maxW = x + W - 6 - tx;
		int white = ARGB.color(Math.round(a * 255), 0xFFFFFF);
		int grey = ARGB.color(Math.round(a * 255), 0xA9A3B8);
		g.drawString(font, clip(font, track.title(), maxW), tx, y + 6, white, false);
		String sub = track.artist().isEmpty() ? tracker.sourceName() : track.artist();
		String status = switch (service.state()) {
			case LOADING -> "  · finding lyrics…";
			case NOT_FOUND -> "  · no lyrics";
			case ERROR -> "  · offline";
			default -> "";
		};
		g.drawString(font, clip(font, sub, maxW - font.width(status)) + status, tx, y + 16, grey, false);

		// Progress bar
		int barY = y + H - 7;
		int barW = maxW;
		g.fill(tx, barY, tx + barW, barY + 2, ARGB.color(Math.round(a * 90), 0xFFFFFF));
		if (track.lengthMs() > 0) {
			float p = Mth.clamp(tracker.positionMs() / (float) track.lengthMs(), 0, 1);
			g.fill(tx, barY, tx + Math.round(barW * p), barY + 2, ARGB.color(Math.round(a * 255), accent));
		}
		if (!tracker.playing()) {
			g.drawString(font, "❚❚", x + W - 6 - font.width("❚❚"), y + 6, grey, false);
		}
	}

	/** A rectangle with 1px cut corners, which reads as rounded at GUI scale. */
	static void roundRect(GuiGraphics g, int x, int y, int w, int h, int color) {
		if (ARGB.alpha(color) == 0 || w <= 0 || h <= 0) {
			return;
		}
		g.fill(x + 1, y, x + w - 1, y + h, color);
		g.fill(x, y + 1, x + 1, y + h - 1, color);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
	}

	static String clip(Font font, String text, int maxW) {
		if (font.width(text) <= maxW) {
			return text;
		}
		String ellipsis = "…";
		return font.plainSubstrByWidth(text, Math.max(0, maxW - font.width(ellipsis))) + ellipsis;
	}

	private static float ease(float t) {
		return 1 - (1 - t) * (1 - t);
	}
}
