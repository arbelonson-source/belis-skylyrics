package dev.beli.skylyrics.render;

import java.text.Bidi;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.dimension.DimensionType.Skybox;
import net.minecraft.world.level.material.FogType;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import dev.beli.skylyrics.Tr;
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

	private final MediaTracker tracker;
	private final LyricsService service;
	private final AlbumArt art;

	private long lastFrame;
	private float visibility;
	private float daylight;
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
	/** Where each character of a row starts, in display order (one extra entry: the row's width). */
	private final Map<String, float[]> offsetCache = new HashMap<>();
	private float wrapCacheWidth;
	private Style wrapCacheStyle = Style.EMPTY;
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
		long time = tracker.positionMs() + cfg.offsetFor(tracker.track()) + LEAD_MS;
		boolean hasLyrics = !current.isEmpty() || current.instrumental();
		if (hasLyrics) {
			if (current != shown) {
				shown = current;
				lastIndex = Integer.MIN_VALUE;
				wrapCache.clear();
				offsetCache.clear();
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
		updateDaylight(mc, camera, dt);
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
		Style wrapStyle = LyricsFont.sky(cfg);
		if (wrapWidth != wrapCacheWidth || !wrapStyle.equals(wrapCacheStyle)) {
			wrapCache.clear();
			offsetCache.clear();
			wrapCacheWidth = wrapWidth;
			wrapCacheStyle = wrapStyle;
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
			draw.line(Tr.t("♪  Instrumental  ♪"), 0, 1, visibility, 0.6f, 1, -1, Float.NaN, Float.NaN);
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
			case WORDS -> {
				// The old line fades away while the new one's words come in one after another
				if (p < 1 && previousIndex != Integer.MIN_VALUE) {
					arrangement(draw, cfg, previousIndex, time, 0, visibility * (1 - easeInOut(Math.min(1, p * 1.6f))), 1, now);
				}
				arrangement(draw, cfg, index, time, 0, visibility, 1, now);
			}
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
		record Item(String text, float r, float scale, float alpha, float highlight, float sung, int maxChars, float wordsT, float flareT) {
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
			float wordsT = Float.NaN, flareT = Float.NaN;
			if (i == center && lastIndex == center && previousIndex != Integer.MIN_VALUE) {
				float since = (now - changedAt) / 1e9f;
				if (cfg.animation == Animation.WORDS) {
					wordsT = since - 0.06f; // a beat after the old line starts fading
				}
				if (cfg.pulse) {
					flareT = Float.isNaN(wordsT) ? since : wordsT;
				}
			}
			items.add(new Item(text, r, scale, a * alpha, highlight, sung, maxChars, wordsT, flareT));
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
			draw.line(it.text, centers[k] - focus, it.scale, it.alpha, it.highlight, it.sung, it.maxChars, it.wordsT, it.flareT);
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
		void push(float x, float y, float scale);

		void pop();

		/** {@code glow} copies sit a hair behind the real text. */
		void text(float x, float y, FormattedCharSequence seq, boolean shadow, int argb, boolean glow);
	}

	private record WorldCanvas(SubmitNodeCollector out, PoseStack pose, Font.DisplayMode mode) implements Canvas {
		@Override
		public void push(float x, float y, float scale) {
			pose.pushPose();
			pose.translate(x, y, 0);
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
		public void push(float x, float y, float scale) {
			g.pose().pushMatrix();
			g.pose().translate(x, y);
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
			this.base = LyricsFont.sky(cfg);
		}

		List<String> rows(String text) {
			return wrapCache.computeIfAbsent(text, t -> wrap(font, base, t, wrapWidth));
		}

		float[] offsets(String row, Display d) {
			return offsetCache.computeIfAbsent(row, r -> {
				float[] at = new float[d.cps.length + 1];
				for (int v = 1; v <= d.cps.length; v++) {
					at[v] = LyricsFont.width(font, base, new String(d.cps, 0, v));
				}
				return at;
			});
		}

		/** How much bigger each character is drawn (1 = normal): the word being sung swells, then settles. */
		float @Nullable [] growth(String row, Display d, int charIndex, float sungChars, float highlight) {
			int n = d.cps.length;
			int[] rank = wordRanks(row);
			int words = wordCount(row);
			if (words == 0) {
				return null;
			}
			int[] first = new int[words], last = new int[words];
			Arrays.fill(first, Integer.MAX_VALUE);
			for (int v = 0; v < n; v++) {
				if (d.cps[v] != ' ') {
					int li = d.logical[v], w = rank[li];
					first[w] = Math.min(first[w], li);
					last[w] = Math.max(last[w], li);
				}
			}
			float[] byWord = new float[words];
			boolean any = false;
			for (int w = 0; w < words; w++) {
				float start = charIndex + first[w], end = charIndex + last[w] + 1;
				float in = smooth((sungChars - start) / GROW_RAMP), out = smooth((end + GROW_RAMP - sungChars) / GROW_RAMP);
				byWord[w] = 1 + GROW * in * out * highlight;
				any |= byWord[w] > 1.001f;
			}
			if (!any) {
				return null;
			}
			float[] grow = new float[n];
			for (int v = 0; v < n; v++) {
				grow[v] = d.cps[v] == ' ' ? 1 : byWord[rank[d.logical[v]]];
			}
			return grow;
		}

		/**
		 * @param highlight 1 for the current line, 0 for the dim neighbours
		 * @param sung      0..1, how much of the line has been sung (karaoke fill)
		 * @param maxChars  typewriter limit, or -1
		 * @param wordsT    seconds since the line came in, for the word-by-word fade-in, or NaN
		 * @param flareT    seconds into the glow pulse (per word when words fade in), or NaN
		 */
		void line(String text, float y, float scale, float alpha, float highlight, float sung, int maxChars, float wordsT,
				float flareT) {
			alpha *= cfg.opacity;
			if (alpha <= 0.01f) {
				return;
			}
			List<String> rows = rows(text);
			int total = 0, words = 0;
			for (String row : rows) {
				total += row.codePointCount(0, row.length());
				words += wordCount(row);
			}
			float sungChars = sung * total;
			float stagger = Math.min(WORD_STAGGER, 0.7f / Math.max(1, words));

			float day = daylight;
			int bright = mix(color, 0xFFFFFF, 0.55f - 0.08f * day);
			int dim = mix(color, 0x000000, 0.45f - 0.4f * day); // dark words would sink into the dark halo
			int secondary = mix(color, 0xFFFFFF, 0.25f - 0.25f * day);
			int halo = mix(color, 0x000000, 0.75f * day);
			float haloWeight = 1 + 0.9f * day;
			boolean byWord = !Float.isNaN(wordsT);

			boolean growing = cfg.growWord && cfg.karaoke && shown.synced() && highlight > 0.01f && sung > 0;

			canvas.push(0, y, scale);
			float top = -rows.size() * ROW_H / 2;
			int charIndex = 0, wordIndex = 0;
			for (int r = 0; r < rows.size(); r++) {
				String row = rows.get(r);
				Display d = Display.of(row);
				int n = d.cps.length;
				// How visible each character is: typewriter cuts the line off, words fade in one at a time
				float[] reveal = new float[n];
				float[] flare = new float[n];
				int[] rank = wordRanks(row);
				for (int v = 0; v < n; v++) {
					int ci = charIndex + d.logical[v];
					float wordStart = byWord ? (wordIndex + rank[d.logical[v]]) * stagger : 0;
					float f = 1;
					if (maxChars >= 0 && ci >= maxChars) {
						f = 0;
					} else if (byWord) {
						f = Mth.clamp((wordsT - wordStart) / WORD_FADE, 0, 1);
					}
					reveal[v] = f;
					// Each word flares as it finishes fading in; other animations flare the whole line at once
					flare[v] = Float.isNaN(flareT) ? 0 : pulse(flareT - wordStart - (byWord ? WORD_FADE * 0.6f : 0)) * highlight;
				}
				float[] at = offsets(row, d);
				float[] grow = growing ? growth(row, d, charIndex, sungChars, highlight) : null;
				if (grow != null) {
					// A bigger word pushes its neighbours aside instead of covering them
					float[] spread = new float[n + 1];
					for (int v = 0; v < n; v++) {
						spread[v + 1] = spread[v] + (at[v + 1] - at[v]) * grow[v];
					}
					at = spread;
				}
				float x = -at[n] / 2f;
				float ry = top + r * ROW_H + 1;
				// Draw runs of characters that share the same visibility and size; normally that's the whole row
				for (int from = 0; from < n; ) {
					int to = from + 1;
					while (to < n && (reveal[to] == reveal[from] && flare[to] == flare[from] && (grow == null || grow[to] == grow[from])
							|| d.cps[to] == ' ')) {
						to++;
					}
					float f = reveal[from];
					if (f > 0.004f) {
						float sx = x + at[from];
						float e = 1 - (1 - f) * (1 - f);
						float sy = ry + (byWord ? (1 - e) * 3 : 0);
						float size = grow == null ? 1 : grow[from];
						if (size != 1) {
							// Grow around the middle of the letters, so the word swells up and down evenly
							float cy = ry + GLYPH_MIDDLE;
							canvas.push(sx, cy, size);
							sx = 0;
							sy -= cy;
						}
						float fl = flare[from];
						int start = charIndex;
						int a0 = from, a1 = to;
						FormattedCharSequence seq = sink -> {
							for (int v = a0; v < a1; v++) {
								float fill = Mth.clamp(sungChars - (start + d.logical[v]), 0, 1);
								int c = mix(mix(secondary, mix(dim, bright, fill), highlight), 0xFFFFFF, 0.35f * fl);
								if (!sink.accept(v, base.withColor(c), d.cps[v])) {
									return false;
								}
							}
							return true;
						};
						float ga = (cfg.glow + 0.9f * fl) * alpha * Mth.lerp(day, 0.25f + 0.75f * highlight, 0.7f + 0.3f * highlight)
								* f * haloWeight;
						if (ga > 0.02f) {
							FormattedCharSequence glowSeq = sink -> {
								for (int v = a0; v < a1; v++) {
									if (!sink.accept(v, base, d.cps[v])) {
										return false;
									}
								}
								return true;
							};
							for (int ring = 1; ring <= 2; ring++) {
								float radius = ring * 0.85f * (1 + 0.7f * fl);
								int alphaByte = Math.min(255, Math.round(ga * (ring == 1 ? 0.16f : 0.08f) * 255));
								if (alphaByte < 2) {
									continue;
								}
								int glowColor = alphaByte << 24 | halo;
								for (int k = 0; k < 8; k++) {
									float ang = k * Mth.PI / 4 + (ring == 2 ? Mth.PI / 8 : 0);
									canvas.text(sx + Mth.cos(ang) * radius, sy + Mth.sin(ang) * radius,
											glowSeq, false, glowColor, true);
								}
							}
						}
						canvas.text(sx, sy, seq, cfg.shadow, Math.max(4, Math.round(alpha * f * 255)) << 24 | 0xFFFFFF, false);
						if (size != 1) {
							canvas.pop();
						}
					}
					from = to;
				}
				charIndex += n;
				wordIndex += wordCount(row);
			}
			canvas.pop();
		}
	}

	private static final float WORD_STAGGER = 0.07f;
	private static final float WORD_FADE = 0.25f;
	private static final float PULSE_RISE = 0.07f;
	private static final float PULSE_FALL = 0.65f;
	/** How much bigger the word being sung gets, and over how many letters it swells and settles. */
	static final float GROW = 0.14f;
	private static final float GROW_RAMP = 1.5f;
	/** From the top of a row of text to the middle of its letters. */
	private static final float GLYPH_MIDDLE = 3.5f;

	private static float smooth(float t) {
		t = Mth.clamp(t, 0, 1);
		return t * t * (3 - 2 * t);
	}

	/** A quick swell and a slower fade, {@code t} seconds after a line starts. */
	static float pulse(float t) {
		if (t < 0 || t >= PULSE_RISE + PULSE_FALL) {
			return 0;
		}
		if (t < PULSE_RISE) {
			return t / PULSE_RISE;
		}
		float k = 1 - (t - PULSE_RISE) / PULSE_FALL;
		return k * k;
	}

	/** A row in display order: right-to-left runs (Hebrew, Arabic) reversed, with each character's logical index. */
	record Display(int[] cps, int[] logical) {
		/** The same few rows are drawn every frame, so their layout is kept instead of recomputed. Render thread only. */
		private static final Map<String, Display> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, Display> eldest) {
				return size() > 64;
			}
		};

		static Display of(String row) {
			return CACHE.computeIfAbsent(row, Display::compute);
		}

		private static Display compute(String row) {
			int[] cps = row.codePoints().toArray();
			int n = cps.length;
			int[] logical = new int[n];
			for (int i = 0; i < n; i++) {
				logical[i] = i;
			}
			if (!Bidi.requiresBidi(row.toCharArray(), 0, row.length())) {
				return new Display(cps, logical);
			}
			int[] charToCp = new int[row.length() + 1];
			for (int off = 0, i = 0; off < row.length(); i++) {
				int len = Character.charCount(row.codePointAt(off));
				for (int k = 0; k < len; k++) {
					charToCp[off + k] = i;
				}
				off += len;
			}
			charToCp[row.length()] = n;
			Bidi bidi = new Bidi(row, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
			int runs = bidi.getRunCount();
			byte[] levels = new byte[runs];
			Integer[] order = new Integer[runs];
			for (int i = 0; i < runs; i++) {
				levels[i] = (byte) bidi.getRunLevel(i);
				order[i] = i;
			}
			Bidi.reorderVisually(levels, 0, order, 0, runs);
			int[] outCps = new int[n];
			int[] outLogical = new int[n];
			int v = 0;
			for (int run : order) {
				int from = charToCp[bidi.getRunStart(run)], to = charToCp[bidi.getRunLimit(run)];
				boolean rtl = (bidi.getRunLevel(run) & 1) == 1;
				for (int k = 0; k < to - from; k++) {
					int i = rtl ? to - 1 - k : from + k;
					outCps[v] = rtl ? mirror(cps[i]) : cps[i];
					outLogical[v++] = i;
				}
			}
			return new Display(outCps, outLogical);
		}

		private static int mirror(int cp) {
			return switch (cp) {
				case '(' -> ')';
				case ')' -> '(';
				case '[' -> ']';
				case ']' -> '[';
				case '{' -> '}';
				case '}' -> '{';
				case '<' -> '>';
				case '>' -> '<';
				default -> cp;
			};
		}
	}

	/** Which word (0, 1, 2, …) each code point of the row belongs to; spaces count with the word before. */
	private static final Map<String, int[]> RANKS = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, int[]> eldest) {
			return size() > 64;
		}
	};

	private static int[] wordRanks(String row) {
		return RANKS.computeIfAbsent(row, LyricsRenderer::computeWordRanks);
	}

	private static int[] computeWordRanks(String row) {
		int[] cps = row.codePoints().toArray();
		int[] rank = new int[cps.length];
		int w = -1;
		boolean inWord = false;
		for (int i = 0; i < cps.length; i++) {
			boolean space = cps[i] == ' ';
			if (!space && !inWord) {
				w++;
			}
			inWord = !space;
			rank[i] = Math.max(0, w);
		}
		return rank;
	}

	private static int wordCount(String row) {
		int[] rank = wordRanks(row);
		boolean any = row.chars().anyMatch(c -> c != ' ');
		return any ? rank[rank.length - 1] + 1 : 0;
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

	private void updateDaylight(Minecraft mc, Camera camera, float dt) {
		ClientLevel level = mc.level;
		float target = 0;
		if (level != null && level.dimensionType().skybox() == Skybox.OVERWORLD
				&& camera.getFluidInCamera() == FogType.NONE) {
			float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
			target = daylight(camera.attributeProbe().getValue(EnvironmentAttributes.STAR_BRIGHTNESS, partial),
					level.getRainLevel(partial));
		}
		daylight = approach(daylight, target, dt * 2f);
	}

	/**
	 * 1 under a clear day sky, 0 at night; rain greys the sky so it counts half. A light glow vanishes against a
	 * bright sky, so by day the lyrics get a dark halo instead.
	 */
	static float daylight(float starBrightness, float rainLevel) {
		return (1 - Mth.clamp(starBrightness / SkyEffects.NIGHT_STARS, 0, 1)) * (1 - 0.5f * Mth.clamp(rainLevel, 0, 1));
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

	/** The smoothed lyrics colour as packed RGB. */
	public int color() {
		return rgb();
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
