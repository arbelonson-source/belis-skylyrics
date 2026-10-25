package dev.beli.skylyrics.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.config.SkyLyricsConfig.Animation;
import dev.beli.skylyrics.config.SkyLyricsConfig.Placement;
import dev.beli.skylyrics.lyrics.LyricLine;
import dev.beli.skylyrics.lyrics.Lyrics;
import dev.beli.skylyrics.lyrics.LyricsService;
import dev.beli.skylyrics.media.MediaTracker;

/**
 * Draws the lyrics in the world. Text goes through Minecraft's own text pipeline (the same one
 * name tags use), so it works with Sodium, Iris and shader packs without special cases. When
 * {@link ScreenLayer} is active the same layout is drawn over the finished frame instead, at the
 * spot in the sky where it would have been, so shader lighting and clouds leave it alone.
 */
public final class LyricsRenderer {
	private static final float DEG = Mth.DEG_TO_RAD;
	private static final long ANIM_NS = 450_000_000L;
	/** Lines show a little before their timestamp so the animation has finished when the singing starts. */
	static final long LEAD_MS = 150;
	private static final float ROW_H = 10f;
	private static final float SECONDARY = 0.7f;
	private static final float GAP = 5f;
	private static final String DOTS = "♪  ♪  ♪";
	private static final String INSTRUMENTAL = "♪  Instrumental  ♪";

	private final MediaTracker tracker;
	private final LyricsService service;
	private final AlbumArt art;

	private long lastFrame;
	private float visibility;
	private float anchorYaw = Float.NaN;
	private float anchorPitch;
	private boolean following;
	private float cr = -1, cg, cb;

	private Lyrics shown = Lyrics.NONE;
	private long frozenTime;
	private int lastIndex = Integer.MIN_VALUE;
	private int previousIndex = Integer.MIN_VALUE;
	private long changedAt;
	private final Map<String, List<String>> wrapCache = new HashMap<>();
	private float wrapCacheWidth;
	private boolean wrapCacheSmooth;
	private Pending pending;

	/** A frame laid out by the world pass, waiting for the HUD pass to draw it on screen. */
	private record Pending(Vector3f offset, float scale, float wrapWidth, long time, long now) {}

	public LyricsRenderer(MediaTracker tracker, LyricsService service, AlbumArt art) {
		this.tracker = tracker;
		this.service = service;
		this.art = art;
	}

	/** Whether the lyrics are (even partly) on screen; used to skip work. */
	public boolean visible() {
		return visibility > 0.01f;
	}

	public void render(WorldRenderContext context) {
		pending = null;
		Minecraft mc = Minecraft.getInstance();
		SkyLyricsConfig cfg = SkyLyricsConfig.get();
		Camera camera = mc.gameRenderer.getMainCamera();
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;

		Lyrics current = service.lyrics();
		if (!cfg.showPlainLyrics && !current.synced() && !current.instrumental()) {
			current = Lyrics.NONE; // unsynced lyrics can't follow the song, some people prefer nothing
		}
		long time = tracker.positionMs() + cfg.syncOffsetMs + LEAD_MS;
		boolean hasLyrics = !current.isEmpty() || current.instrumental();
		if (hasLyrics) {
			if (current != shown) {
				shown = current;
				lastIndex = Integer.MIN_VALUE;
				wrapCache.clear();
			}
			frozenTime = time;
		} else {
			time = frozenTime; // fade the old lyrics out where they were instead of jumping
		}

		boolean want = cfg.enabled && hasLyrics
				&& !(cfg.hideWithF1 && mc.options.hideGui)
				&& !(cfg.hideInMenus && mc.screen != null && !(mc.screen instanceof dev.beli.skylyrics.gui.SkyLyricsScreen))
				&& !(cfg.hideWhenPaused && !tracker.playing());
		visibility = approach(visibility, want ? 1 : 0, dt * 3f);
		updateColor(cfg, dt, now);
		updateAnchor(cfg, camera, dt);
		if (visibility <= 0.01f || shown.isEmpty() && !shown.instrumental()) {
			return;
		}

		boolean sky = cfg.placement == Placement.SKY;
		float distance = sky
				? Math.max(8, Math.min(cfg.distance, mc.options.getEffectiveRenderDistance() * 16 * 0.8f))
				: Mth.clamp(cfg.distance / 6f, 4, 16);
		float angular = (sky ? 3.4f : 2.6f) * cfg.size;
		float scale = distance * (float) Math.tan(angular * DEG) / ROW_H;
		float wrapWidth = sky ? 230 : 190;
		if (wrapWidth != wrapCacheWidth || cfg.smoothFont != wrapCacheSmooth) {
			wrapCache.clear();
			wrapCacheWidth = wrapWidth;
			wrapCacheSmooth = cfg.smoothFont;
		}

		Quaternionf orientation = new Quaternionf().rotationYXZ(Mth.PI - anchorYaw * DEG, -anchorPitch * DEG, 0);
		Vector3f offset = new Vector3f(0, 0, -1).rotate(orientation).mul(distance);

		if (ScreenLayer.active(cfg) && ScreenLayer.ready()) {
			pending = new Pending(offset, scale, wrapWidth, time, now);
			return;
		}

		PoseStack pose = context.matrices();
		pose.pushPose();
		pose.translate(offset.x, offset.y, offset.z);
		pose.mulPose(orientation);
		pose.scale(scale, -scale, scale);

		Font.DisplayMode mode = cfg.seeThroughBlocks ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
		Draw draw = new Draw(new WorldCanvas(context.commandQueue(), pose, mode), mc.font, cfg, rgb(), wrapWidth);
		drawContent(draw, cfg, time, now);
		pose.popPose();
	}

	/** HUD pass: draws what the world pass laid out, projected to where it sits in the sky. */
	public void renderHud(GuiGraphics g, DeltaTracker delta) {
		Pending frame = pending;
		pending = null;
		if (frame == null) {
			return;
		}
		Vector3f at = ScreenLayer.project(frame.offset(), g.guiWidth(), g.guiHeight());
		if (at == null) {
			return; // behind you
		}
		Minecraft mc = Minecraft.getInstance();
		SkyLyricsConfig cfg = SkyLyricsConfig.get();
		float size = frame.scale() * at.z;
		g.pose().pushMatrix();
		g.pose().translate(at.x, at.y);
		g.pose().scale(size, size);
		Draw draw = new Draw(new ScreenCanvas(g, mc.font), mc.font, cfg, rgb(), frame.wrapWidth());
		drawContent(draw, cfg, frame.time(), frame.now());
		g.pose().popMatrix();
	}

	private void drawContent(Draw draw, SkyLyricsConfig cfg, long time, long now) {
		if (shown.instrumental()) {
			draw.line(INSTRUMENTAL, 0, 1, visibility, 0.6f, 1, -1);
		} else {
			drawLines(draw, cfg, time, now);
		}
	}

	private void drawLines(Draw draw, SkyLyricsConfig cfg, long time, long now) {
		int index = shown.indexAt(time);
		if (index != lastIndex) {
			previousIndex = lastIndex;
			lastIndex = index;
			changedAt = previousIndex == Integer.MIN_VALUE ? now - ANIM_NS : now;
		}
		float p = Mth.clamp((now - changedAt) / (float) ANIM_NS, 0, 1);
		boolean stepForward = index == previousIndex + 1;

		switch (cfg.animation) {
			case RISE -> {
				if (stepForward) {
					arrangement(draw, cfg, index, time, 1 - easeInOut(p), visibility, 1, now);
				} else {
					crossfade(draw, cfg, index, time, p, now);
				}
			}
			case FADE -> crossfade(draw, cfg, index, time, p, now);
			case POP -> {
				if (p < 1 && previousIndex != Integer.MIN_VALUE) {
					arrangement(draw, cfg, previousIndex, time, 0, visibility * Math.max(0, 1 - p * 2.5f), 1, now);
				}
				float pop = 0.8f + 0.2f * easeOutBack(p);
				arrangement(draw, cfg, index, time, 0, visibility * Math.min(1, p * 2f), pop, now);
			}
			case TYPEWRITER -> arrangement(draw, cfg, index, time, 0, visibility, 1, now);
		}
	}

	private void crossfade(Draw draw, SkyLyricsConfig cfg, int index, long time, float p, long now) {
		float e = easeInOut(p);
		if (p < 1 && previousIndex != Integer.MIN_VALUE) {
			arrangement(draw, cfg, previousIndex, time, 0, visibility * (1 - e), 1, now);
		}
		arrangement(draw, cfg, index, time, 0, visibility * e, 1, now);
	}

	/**
	 * Draws the line at {@code center} plus its neighbours. {@code shift} slides everything down by
	 * that many slots (1 = the previous layout, 0 = settled), which makes the scrolling animation.
	 */
	private void arrangement(Draw draw, SkyLyricsConfig cfg, int center, long time, float shift, float alpha, float popScale, long now) {
		if (alpha <= 0.01f) {
			return;
		}
		record Item(String text, float r, float scale, float alpha, float highlight, float sung, int maxChars) {
		}
		List<Item> items = new ArrayList<>();
		for (int i = center - 2; i <= center + 2; i++) {
			float r = (i - center) + shift;
			if (r <= -2 || r >= 2) {
				continue;
			}
			float a = slotAlpha(cfg, r);
			if (a <= 0.01f) {
				continue;
			}
			String text;
			float sung;
			if (i < 0) {
				// Before the first line: little notes that fill up until the singing starts
				long first = shown.lines().isEmpty() ? 0 : shown.get(0).timeMs();
				if (i < -1 || first < 2500) {
					continue;
				}
				text = DOTS;
				sung = Mth.clamp(time / (float) first, 0, 1);
			} else if (i >= shown.lines().size()) {
				continue;
			} else {
				LyricLine line = shown.get(i);
				if (line.isBreak()) {
					text = DOTS;
					long end = shown.endOf(i, line.timeMs() + 4000);
					sung = Mth.clamp((time - line.timeMs()) / (float) Math.max(1, end - line.timeMs()), 0, 1);
				} else {
					text = cfg.uppercase ? line.text().toUpperCase(Locale.ROOT) : line.text();
					if (i < center || i == center && (!cfg.karaoke || !shown.synced())) {
						sung = i == center || i < center ? 1 : 0;
					} else if (i == center) {
						long end = Math.min(shown.endOf(i, line.timeMs() + 5000), line.timeMs() + 12000);
						sung = Mth.clamp((time - line.timeMs()) / (float) Math.max(1, end - line.timeMs()), 0, 1);
					} else {
						sung = 0;
					}
				}
			}
			float highlight = Mth.clamp(1 - Math.abs(r), 0, 1);
			float scale = Mth.lerp(highlight, SECONDARY, 1f) * (i == center ? popScale : 1);
			int maxChars = -1;
			if (cfg.animation == Animation.TYPEWRITER && i == center && lastIndex == center) {
				maxChars = (int) ((now - changedAt) / 28_000_000L);
			}
			items.add(new Item(text, r, scale, a * alpha, highlight, sung, maxChars));
		}
		if (items.isEmpty()) {
			return;
		}

		// Stack the lines top to bottom, then move the stack so the r=0 point sits at the anchor
		float[] centers = new float[items.size()];
		float y = 0;
		for (int k = 0; k < items.size(); k++) {
			Item it = items.get(k);
			float h = draw.rows(it.text).size() * ROW_H * it.scale;
			centers[k] = y + h / 2;
			y += h + GAP;
		}
		float focus = centers[0] - items.getFirst().r * (items.size() > 1 ? centers[1] - centers[0] : 0);
		for (int k = 0; k + 1 < items.size(); k++) {
			float r0 = items.get(k).r, r1 = items.get(k + 1).r;
			if (r0 <= 0 && r1 >= 0) {
				focus = Mth.lerp(-r0 / (r1 - r0), centers[k], centers[k + 1]);
				break;
			}
			if (k + 2 == items.size()) {
				focus = centers[k + 1] - r1 * (centers[k + 1] - centers[k]);
			}
		}
		for (int k = 0; k < items.size(); k++) {
			Item it = items.get(k);
			draw.line(it.text, centers[k] - focus, it.scale, it.alpha, it.highlight, it.sung, it.maxChars);
		}
	}

	private static float slotAlpha(SkyLyricsConfig cfg, float r) {
		float prev = cfg.showPrevious ? 0.5f : 0;
		float next = cfg.showNext ? 0.42f : 0;
		if (r <= -1) return prev * (2 + r);
		if (r < 0) return Mth.lerp(r + 1, prev, 1);
		if (r <= 1) return Mth.lerp(r, 1, next);
		return next * (2 - r);
	}

	/** One frame's worth of drawing settings. */
	/** Where the text ends up: in the world, or over the finished screen. */
	private interface Canvas {
		void push(float y, float scale);

		void pop();

		/** {@code glow} copies sit a hair behind the real text. */
		void text(float x, float y, FormattedCharSequence seq, boolean shadow, int argb, boolean glow);
	}

	private record WorldCanvas(SubmitNodeCollector out, PoseStack pose, Font.DisplayMode mode) implements Canvas {
		@Override
		public void push(float y, float scale) {
			pose.pushPose();
			pose.translate(0, y, 0);
			pose.scale(scale, scale, scale);
		}

		@Override
		public void pop() {
			pose.popPose();
		}

		@Override
		public void text(float x, float y, FormattedCharSequence seq, boolean shadow, int argb, boolean glow) {
			if (glow) {
				pose.pushPose();
				pose.translate(0, 0, -0.35f);
			}
			out.submitText(pose, x, y, seq, shadow, mode, LightTexture.FULL_BRIGHT, argb, 0, 0);
			if (glow) {
				pose.popPose();
			}
		}
	}

	private record ScreenCanvas(GuiGraphics g, Font font) implements Canvas {
		@Override
		public void push(float y, float scale) {
			g.pose().pushMatrix();
			g.pose().translate(0, y);
			g.pose().scale(scale, scale);
		}

		@Override
		public void pop() {
			g.pose().popMatrix();
		}

		@Override
		public void text(float x, float y, FormattedCharSequence seq, boolean shadow, int argb, boolean glow) {
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.drawString(font, seq, 0, 0, argb, shadow);
			g.pose().popMatrix();
		}
	}

	private final class Draw {
		final Canvas canvas;
		final Font font;
		final SkyLyricsConfig cfg;
		final int color;
		final float wrapWidth;
		final Style base;

		Draw(Canvas canvas, Font font, SkyLyricsConfig cfg, int color, float wrapWidth) {
			this.canvas = canvas;
			this.font = font;
			this.cfg = cfg;
			this.color = color;
			this.wrapWidth = wrapWidth;
			this.base = LyricsFont.base(cfg);
		}

		List<String> rows(String text) {
			return wrapCache.computeIfAbsent(text, t -> wrap(font, base, t, wrapWidth));
		}

		/**
		 * @param highlight 1 for the current line, 0 for the dim neighbours
		 * @param sung      0..1, how much of the line has been sung (karaoke fill)
		 * @param maxChars  typewriter limit, or -1
		 */
		void line(String text, float y, float scale, float alpha, float highlight, float sung, int maxChars) {
			alpha *= cfg.opacity;
			if (alpha <= 0.01f) {
				return;
			}
			List<String> rows = rows(text);
			int total = 0;
			for (String row : rows) {
				total += row.codePointCount(0, row.length());
			}
			float sungChars = sung * total;

			int bright = mix(color, 0xFFFFFF, 0.55f);
			int dim = mix(color, 0x000000, 0.45f);
			int secondary = mix(color, 0xFFFFFF, 0.25f);
			int a = Math.round(alpha * 255);

			canvas.push(y, scale);
			float top = -rows.size() * ROW_H / 2;
			int charIndex = 0;
			for (int r = 0; r < rows.size(); r++) {
				String row = rows.get(r);
				float x = -LyricsFont.width(font, base, row) / 2f;
				float ry = top + r * ROW_H + 1;
				int start = charIndex;
				FormattedCharSequence seq = sink -> {
					int ci = start;
					for (int off = 0; off < row.length(); ) {
						int cp = row.codePointAt(off);
						if (maxChars >= 0 && ci >= maxChars) {
							return true;
						}
						float fill = Mth.clamp(sungChars - ci, 0, 1);
						int c = mix(dim, bright, fill);
						c = mix(secondary, c, highlight);
						if (!sink.accept(off, base.withColor(c), cp)) {
							return false;
						}
						off += Character.charCount(cp);
						ci++;
					}
					return true;
				};

				float glowAlpha = cfg.glow * alpha * (0.25f + 0.75f * highlight);
				if (glowAlpha > 0.02f) {
					FormattedCharSequence glowSeq = maxChars < 0 ? FormattedCharSequence.forward(row, base) : seq;
					for (int ring = 1; ring <= 2; ring++) {
						float radius = ring * 0.85f;
						int ga = Math.round(glowAlpha * (ring == 1 ? 0.16f : 0.08f) * 255);
						if (ga < 2) {
							continue;
						}
						int glowColor = ga << 24 | color;
						for (int k = 0; k < 8; k++) {
							float ang = k * Mth.PI / 4 + (ring == 2 ? Mth.PI / 8 : 0);
							canvas.text(x + Mth.cos(ang) * radius, ry + Mth.sin(ang) * radius,
									glowSeq, false, glowColor, true);
						}
					}
				}
				canvas.text(x, ry, seq, cfg.shadow, a << 24 | 0xFFFFFF, false);
				charIndex += row.codePointCount(0, row.length());
			}
			canvas.pop();
		}
	}

	static List<String> wrap(Font font, Style style, String text, float maxWidth) {
		List<String> rows = new ArrayList<>();
		if (LyricsFont.width(font, style, text) <= maxWidth) {
			rows.add(text);
			return rows;
		}
		// Balance the rows: aim for even widths instead of a long row and a one-word orphan
		int rowsNeeded = (int) Math.ceil(LyricsFont.width(font, style, text) / maxWidth);
		float target = Math.min(maxWidth, LyricsFont.width(font, style, text) / (float) rowsNeeded + 12);
		StringBuilder row = new StringBuilder();
		for (String word : text.split(" ")) {
			if (word.isEmpty()) {
				continue;
			}
			String candidate = row.isEmpty() ? word : row + " " + word;
			if (!row.isEmpty() && LyricsFont.width(font, style, candidate) > target) {
				rows.add(row.toString());
				row.setLength(0);
				row.append(word);
			} else {
				row.setLength(0);
				row.append(candidate);
			}
		}
		if (!row.isEmpty()) {
			rows.add(row.toString());
		}
		return rows;
	}

	private void updateColor(SkyLyricsConfig cfg, float dt, long now) {
		int target = switch (cfg.colorMode) {
			case ALBUM -> art.color();
			case FIXED -> cfg.color;
			case RAINBOW -> java.awt.Color.HSBtoRGB((now / 1e9f / 16f) % 1f, 0.55f, 1f) & 0xFFFFFF;
		};
		float tr = (target >> 16 & 0xFF) / 255f, tg = (target >> 8 & 0xFF) / 255f, tb = (target & 0xFF) / 255f;
		if (cr < 0 || cfg.colorMode == SkyLyricsConfig.ColorMode.RAINBOW) {
			cr = tr;
			cg = tg;
			cb = tb;
			return;
		}
		float k = 1 - (float) Math.exp(-dt * 2.5f);
		cr += (tr - cr) * k;
		cg += (tg - cg) * k;
		cb += (tb - cb) * k;
	}

	private int rgb() {
		return Math.round(cr * 255) << 16 | Math.round(cg * 255) << 8 | Math.round(cb * 255);
	}

	private void updateAnchor(SkyLyricsConfig cfg, Camera camera, float dt) {
		float camYaw = camera.yRot();
		float camPitch = camera.xRot();
		if (Float.isNaN(anchorYaw)) {
			anchorYaw = camYaw;
			anchorPitch = -cfg.height;
		}
		float targetYaw;
		float targetPitch;
		float speed;
		if (cfg.placement == Placement.FLOATING) {
			targetYaw = camYaw;
			targetPitch = Mth.clamp(camPitch - Math.min(cfg.height, 30) * 0.5f, -85, 85);
			speed = 5f;
		} else if (cfg.direction == SkyLyricsConfig.Direction.FOLLOW) {
			// Lazy follow: stay put while you look around a bit, catch up when you turn away
			float diff = Mth.wrapDegrees(camYaw - anchorYaw);
			if (Math.abs(diff) > 38) {
				following = true;
			} else if (Math.abs(diff) < 4) {
				following = false;
			}
			targetYaw = following ? camYaw : anchorYaw;
			targetPitch = -cfg.height;
			speed = 2.2f;
		} else {
			targetYaw = cfg.direction.yaw;
			targetPitch = -cfg.height;
			speed = 3f;
		}
		float k = dt == 0 ? 1 : 1 - (float) Math.exp(-dt * speed);
		anchorYaw += Mth.wrapDegrees(targetYaw - anchorYaw) * k;
		anchorPitch += (targetPitch - anchorPitch) * k;
	}

	private static float approach(float value, float target, float step) {
		return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
	}

	private static float easeInOut(float t) {
		return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
	}

	private static float easeOutBack(float t) {
		float c1 = 1.70158f, c3 = c1 + 1;
		return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
	}

	static int mix(int a, int b, float t) {
		t = Mth.clamp(t, 0, 1);
		int r = Math.round(Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF));
		int g = Math.round(Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF));
		int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
		return r << 16 | g << 8 | bl;
	}
}
