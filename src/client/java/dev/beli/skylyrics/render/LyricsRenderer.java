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
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.LightCoordsUtil;
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
import dev.beli.skylyrics.config.SkyLyricsConfig.Layout;
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
	/** Copies of a word drawn around it for the glow, in the inner and the outer ring. */
	private static final int GLOW_INNER = 6, GLOW_OUTER = 4;
	/** Copies in the one ring that light effects draw. */
	private static final int GLOW_LIGHT = 3;

	/**
	 * Copies to draw in a glow ring (1 inner, 2 outer) at this strength. A faint glow can't show a rounder
	 * halo, so it gets fewer copies of the same total brightness and no outer ring, which saves frames on
	 * the lines not being sung. Light effects draw just a small inner ring.
	 */
	static int glowCopies(int ring, float strength, boolean light) {
		if (light) {
			return ring == 1 ? GLOW_LIGHT : 0;
		}
		if (ring == 1) {
			return strength >= 0.4f ? GLOW_INNER : 4;
		}
		return strength >= 0.3f ? GLOW_OUTER : 0;
	}
	/** In the WORLD layout, a line's last words stay this long after it's over before they fade too. */
	private static final long WORLD_LINGER_MS = 450;
	private static final float ROW_H = 10f;
	/** Title screen: rows of height the lyrics need, the size limits, and the wrap width (in text pixels). */
	private static final float MENU_ROWS = 3.6f * ROW_H, MENU_MAX_SCALE = 1.5f, MENU_MIN_SCALE = 0.55f, MENU_WRAP = 260;
	private static final float SECONDARY = 0.7f;
	private static final float GAP = 5f;
	private static final String DOTS = "♪  ♪  ♪";

	/** The arc layout's letters are a bit smaller than the stacked lines': about 3 % of the screen height. */
	private static final float ARC_SIZE = 0.7f;
	/** A sung line leaves in this long, fading as it drifts up. */
	private static final long ARC_LEAVE_NS = 230_000_000L;
	/** How far it drifts up meanwhile, and how far a new word rises as it fades in (text units, about 2 % and 0.75 % of the screen height). */
	private static final float ARC_LEAVE_RISE = 7.4f, ARC_APPEAR_RISE = 2.6f;
	/** A word fades in over this long once it's sung, starting this long after, so the old line is gone first. */
	private static final long ARC_WORD_FADE_MS = 300, ARC_WORD_DELAY_MS = 100;
	/** The word being sung is a little whiter, and settles to the normal colour over this long. */
	private static final long ARC_CURRENT_MS = 250;
	private static final float CURRENT_WHITE = 0.3f;
	/** Each line sits this many degrees left or right of where you look, and this many above the crosshair. */
	private static final float ARC_SIDE = 6f, ARC_ABOVE_MIN = 5f, ARC_ABOVE_MAX = 8f;
	private static final int SALT_SIDE = 101, SALT_UP = 102;
	/**
	 * A line further than this many degrees left/right or up/down from where you look (near the screen's edge or
	 * past it), for this long, moves to where you look now.
	 */
	private static final float ARC_LOST_YAW = 35f, ARC_LOST_PITCH = 25f;
	private static final long ARC_LOST_NS = 1_000_000_000L;
	/** A line that moved to a new spot fades in there over this long. */
	private static final long ARC_ARRIVE_NS = 300_000_000L;
	/** Depth copies: the dark one this far down, the light one this far up (text units, about 0.1 and 0.05 em). */
	private static final float DEPTH_DOWN = 1.05f, DEPTH_UP = 0.5f;
	/** The dark copy's brightness by night and by day (against a bright sky it needs to be darker to show). */
	private static final float DEPTH_DARK_NIGHT = 0.66f, DEPTH_DARK_DAY = 0.45f;
	/** The dark copy is a bit more saturated than the face, like a shaded side. */
	private static final float DEPTH_SATURATION = 1.55f;
	/** The next word shows faintly and smaller before it's sung, brightening as its turn comes (reference pictures). */
	private static final float NEXT_ALPHA_MIN = 0.3f, NEXT_ALPHA_MAX = 0.6f, NEXT_SCALE = 0.75f;
	/** The light copy is this much brighter than the face. */
	private static final float DEPTH_LIGHT = 1.035f;
	/** How far apart the layers behind the text sit in the world, in text units. */
	private static final float LAYER_STEP = 0.35f;

	private final MediaTracker tracker;
	private final LyricsService service;
	private final AlbumArt art;

	private long lastFrame;
	private final FrameBudget budget = new FrameBudget();
	/** Fewer glow copies this frame, because the settings ask for it or the game is slow. */
	private boolean light;
	private float visibility;
	private float daylight;
	/** How strongly a bright painted sky (synthwave, moonlight) shows behind the lyrics, 0 to 1. */
	private float backdrop;
	private boolean hasLyrics;
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
	/** Width of a few letters when the caches were filled; changes when fonts are reloaded. */
	private float wrapCacheProbe;
	private @Nullable Pending pending;
	private final WorldWords worldWords = new WorldWords();
	/** Arc layouts of the lines on show, keyed by line number and text. */
	private final Map<String, ArcText> arcCache = new HashMap<>();
	/** Where in the sky each arc line was put, by line number. */
	private final Map<Integer, ArcAnchor> arcAnchors = new HashMap<>();

	/** A frame laid out by the world pass, waiting for the HUD pass to draw it on screen. */
	private record Pending(List<Spot> spots, float scale, float wrapWidth, long time, long now) {}

	/** One block of text and where it sits around the camera; {@code arc} is null for the stacked lines. */
	private record Spot(Vector3f offset, Quaternionf orientation, @Nullable ArcShot arc) {}

	/**
	 * An arc line to draw: which line, how far it is through leaving (or -1 while it's being sung), and how far
	 * through fading in at its spot (0..1).
	 */
	private record ArcShot(int index, float leave, float arrive) {}

	/** An arc line's spot in the sky, and since when it's been there or out of view. */
	private static final class ArcAnchor {
		float yaw, pitch;
		/** How far left or right of where you looked it was placed. */
		float side;
		long placed;
		long lostSince = -1;
	}

	/** A line cut into words and placed for the arc layout. */
	private record ArcText(String[] words, float[] widths, int[] firstChar, int total, List<ArcLayout.Group> groups) {}

	public LyricsRenderer(MediaTracker tracker, LyricsService service, AlbumArt art) {
		this.tracker = tracker;
		this.service = service;
		this.art = art;
	}

	/** Whether the lyrics are (even partly) on screen; used to skip work. */
	/** Whether the lyrics are drawing light effects right now. */
	public boolean lightEffects() {
		return light;
	}

	/** The frame rate as the lyrics see it, smoothed; 0 before the world was first drawn. */
	public float fps() {
		return budget.fps();
	}

	public boolean visible() {
		return visibility > 0.01f;
	}

	/**
	 * Water, ice and stained glass are drawn after entities, so lyrics drawn before them get painted over
	 * when see-through, or cut holes in them when not. Drawn after them instead, both look right.
	 * Shader packs run their own passes, so with one on the lyrics stay at the earlier, tested spot.
	 */
	public void renderEarly(LevelRenderContext context) {
		if (ScreenLayer.shadersOn()) {
			render(context);
		}
	}

	public void renderLate(LevelRenderContext context) {
		if (!ScreenLayer.shadersOn()) {
			render(context);
		}
	}

	private void render(LevelRenderContext context) {
		pending = null;
		Minecraft mc = Minecraft.getInstance();
		SkyLyricsConfig cfg = SkyLyricsConfig.get();
		Camera camera = mc.gameRenderer.mainCamera();
		long now = System.nanoTime();
		float dt = step(mc, cfg, now);
		long time = takeLyrics(cfg);

		boolean want = cfg.enabled && hasLyrics
				&& !(cfg.hideWithF1 && mc.gui.hud.isHidden())
				&& !(cfg.hideInMenus && mc.gui.screen() != null && !(mc.gui.screen() instanceof dev.beli.skylyrics.gui.SkyLyricsScreen))
				&& !(cfg.hideWhenPaused && !tracker.playing()) && !Visibility.hidden(mc, cfg);
		visibility = approach(visibility, want ? 1 : 0, dt * 3f);
		updateColor(cfg, dt, now);
		updateDaylight(mc, camera, dt);
		updateAnchor(cfg, camera, dt);
		if (cfg.layout == Layout.WORLD && !shown.instrumental()) {
			renderWorldWords(context, mc, cfg, camera, time, now);
			return;
		}
		worldWords.clear();
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
		wrapAt(mc, cfg, wrapWidth);

		List<Spot> spots = new ArrayList<>();
		if (cfg.layout == Layout.ARC && !shown.instrumental()) {
			for (ArcShot shot : arcShots(cfg, camera, time, now)) {
				ArcAnchor at = arcAnchors.get(shot.index());
				spots.add(spot(at.yaw, at.pitch, distance, shot));
			}
		} else {
			spots.add(spot(anchorYaw, anchorPitch, distance, null));
		}
		if (visibility > 0.5f) {
			for (Spot spot : spots) {
				LyricsInView.check(spot.offset(), 0.3f * wrapWidth * scale, 1.5f * ROW_H * scale);
			}
		}

		if (ScreenLayer.active(cfg) && ScreenLayer.ready()) {
			pending = new Pending(spots, scale, wrapWidth, time, now);
			return;
		}

		PoseStack pose = context.poseStack();
		Font.DisplayMode mode = cfg.seeThroughBlocks ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
		Draw draw = new Draw(new WorldCanvas(context.submitNodeCollector(), pose, mode), mc.font, cfg, rgb(), wrapWidth);
		for (Spot spot : spots) {
			pose.pushPose();
			pose.translate(spot.offset().x, spot.offset().y, spot.offset().z);
			pose.mulPose(spot.orientation());
			pose.scale(scale, -scale, scale);
			drawSpot(draw, spot, cfg, time, now);
			pose.popPose();
		}
	}

	/** Time since the last frame, in seconds, and whether the effects have to be light this frame. */
	private float step(Minecraft mc, SkyLyricsConfig cfg, long now) {
		float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		budget.update(dt, mc.getFramerateLimitTracker().getFramerateLimit());
		light = cfg.effects == SkyLyricsConfig.Effects.LIGHT || cfg.effects == SkyLyricsConfig.Effects.AUTO && budget.light();
		return dt;
	}

	/** Takes up the lyrics to show, sets {@link #hasLyrics}, and returns the song time to show them at. */
	private long takeLyrics(SkyLyricsConfig cfg) {
		Lyrics current = service.lyrics();
		if (!cfg.showPlainLyrics && !current.synced() && !current.instrumental()) {
			current = Lyrics.NONE; // unsynced lyrics can't follow the song, some people prefer nothing
		}
		long time = tracker.positionMs() + cfg.offsetFor(tracker.track()) + LEAD_MS;
		hasLyrics = !current.isEmpty() || current.instrumental();
		if (hasLyrics) {
			if (current != shown) {
				shown = current;
				lastIndex = Integer.MIN_VALUE;
				wrapCache.clear();
				offsetCache.clear();
				arcCache.clear();
				arcAnchors.clear();
				worldWords.clear();
			}
			frozenTime = time;
		} else {
			time = frozenTime; // fade the old lyrics out where they were instead of jumping
		}
		return time;
	}

	/** Lines are wrapped once and kept; this drops them when the width or the font changes. */
	private void wrapAt(Minecraft mc, SkyLyricsConfig cfg, float wrapWidth) {
		Style wrapStyle = LyricsFont.sky(cfg);
		float probe = LyricsFont.width(mc.font, wrapStyle, "Mw0♪");
		if (wrapWidth != wrapCacheWidth || !wrapStyle.equals(wrapCacheStyle) || probe != wrapCacheProbe) {
			wrapCacheProbe = probe;
			wrapCache.clear();
			offsetCache.clear();
			arcCache.clear();
			wrapCacheWidth = wrapWidth;
			wrapCacheStyle = wrapStyle;
		}
	}

	/**
	 * Title screen: no world is drawn there, so the lines go flat in the gap between the lowest button
	 * ({@code top}) and the copyright text ({@code bottom}), sized to fit it.
	 */
	public void renderMenu(GuiGraphicsExtractor g, float top, float bottom) {
		Minecraft mc = Minecraft.getInstance();
		SkyLyricsConfig cfg = SkyLyricsConfig.get();
		long now = System.nanoTime();
		float dt = step(mc, cfg, now);
		long time = takeLyrics(cfg);
		boolean want = cfg.enabled && cfg.titleScreenLyrics && hasLyrics && !(cfg.hideWhenPaused && !tracker.playing());
		visibility = approach(visibility, want ? 1 : 0, dt * 3f);
		updateColor(cfg, dt, now);
		daylight = approach(daylight, 1, dt * 2f); // menus have no time of day, and the dark halo reads on any backdrop
		float scale = Math.min((bottom - top) / MENU_ROWS, MENU_MAX_SCALE * cfg.size);
		if (visibility <= 0.01f || shown.isEmpty() && !shown.instrumental() || scale < MENU_MIN_SCALE) {
			return;
		}
		wrapAt(mc, cfg, MENU_WRAP);
		Draw draw = new Draw(new ScreenCanvas(g, mc.font), mc.font, cfg, rgb(), MENU_WRAP);
		g.pose().pushMatrix();
		g.pose().translate(g.guiWidth() / 2f, (top + bottom) / 2f);
		g.pose().scale(scale, scale);
		drawContent(draw, cfg, time, now);
		g.pose().popMatrix();
	}

	private static Spot spot(float yaw, float pitch, float distance, @Nullable ArcShot arc) {
		Quaternionf orientation = new Quaternionf().rotationYXZ(Mth.PI - yaw * DEG, -pitch * DEG, 0);
		return new Spot(new Vector3f(0, 0, -1).rotate(orientation).mul(distance), orientation, arc);
	}

	private void drawSpot(Draw draw, Spot spot, SkyLyricsConfig cfg, long time, long now) {
		ArcShot arc = spot.arc();
		if (arc != null) {
			draw.arcLine(arc, time, now);
		} else {
			drawContent(draw, cfg, time, now);
		}
	}

	/** The WORLD layout: cards in the world pass, even when the rest goes on screen, so each keeps its own perspective. */
	private void renderWorldWords(LevelRenderContext context, Minecraft mc, SkyLyricsConfig cfg, Camera camera, long time,
			long now) {
		int index = shown.indexAt(time);
		String text = null;
		float sungChars = 0;
		boolean over = false;
		if (sungLine(index)) {
			LyricLine line = shown.get(index);
			text = line.text();
			int total = text.codePointCount(0, text.length());
			sungChars = shown.synced() ? progress(time - LEAD_MS, line.timeMs(), lineEnd(index)) * total : total;
			over = shown.synced() && time - LEAD_MS > lineEnd(index) + WORLD_LINGER_MS;
		}
		Style style = LyricsFont.sky(cfg);
		worldWords.tune(cfg.worldWords, cfg.worldLinger, cfg.worldDistance, cfg.worldTilt, cfg.worldRise, cfg.worldFlow, cfg.worldHold, cfg.uppercase);
		worldWords.glide(camera.position(), camera.yRot(), camera.xRot(), cfg.worldGlide && mc.player != null
				&& (mc.player.isFallFlying() || mc.player.isPassenger() || mc.player.getAbilities().flying), now);
		worldWords.update(index, text, sungChars, over, cfg.direction.yaw, camera, mc.level, mc.font, style, now);
		float alpha = visibility * cfg.opacity;
		if (alpha <= 0.01f) {
			return;
		}
		Font.DisplayMode mode = cfg.seeThroughBlocks ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
		int wordsRgb = cfg.worldTint ? SkyTint.tint(rgb(), SkyTint.sky(), SkyTint.AMOUNT) : rgb();
		worldWords.render(context.poseStack(), context.submitNodeCollector(), camera, mode, style, wordsRgb, alpha, cfg.glow, daylight, cfg.size, light, now);
	}

	/** When the singing of a line counts as over, for spreading its words over its time. */
	private long lineEnd(int index) {
		long start = shown.get(index).timeMs();
		return Math.min(shown.endOf(index, start + 5000), start + 12000);
	}

	/**
	 * The arc lines to draw this frame: the one being sung, plus the previous one while it leaves. Each line gets
	 * its spot in the sky when it starts (where you're looking, give or take a little) and keeps it.
	 */
	private List<ArcShot> arcShots(SkyLyricsConfig cfg, Camera camera, long time, long now) {
		int index = shown.indexAt(time);
		noteIndex(index, now);
		List<ArcShot> shots = new ArrayList<>(2);
		float leave = (now - changedAt) / (float) ARC_LEAVE_NS;
		int previous = previousIndex;
		if (previous != index && leave < 1 && sungLine(previous)) {
			shots.add(new ArcShot(previous, leave, arrival(arcAnchor(cfg, camera, previous, now, false), now)));
		}
		if (sungLine(index)) {
			shots.add(new ArcShot(index, -1, arrival(arcAnchor(cfg, camera, index, now, true), now)));
		}
		arcAnchors.keySet().removeIf(i -> i != index && i != previous);
		return shots;
	}

	private boolean sungLine(int index) {
		return index >= 0 && index < shown.lines().size() && !shown.get(index).isBreak();
	}

	private static float arrival(ArcAnchor anchor, long now) {
		return Mth.clamp((now - anchor.placed) / (float) ARC_ARRIVE_NS, 0, 1);
	}

	/**
	 * The line's spot: where you're looking when it starts, a little above the crosshair, and then it stays put
	 * like the clouds. A line you've turned well away from for a moment comes back to where you look now, so a
	 * long line isn't lost behind you. In the sky it turns with you unless lazy follow is on; with a fixed
	 * compass direction the lines keep to it instead.
	 */
	private ArcAnchor arcAnchor(SkyLyricsConfig cfg, Camera camera, int index, long now, boolean follow) {
		boolean fixed = cfg.placement == Placement.SKY && cfg.direction != SkyLyricsConfig.Direction.FOLLOW;
		ArcAnchor anchor = arcAnchors.get(index);
		if (anchor != null && !fixed && follow) {
			if (Math.abs(Mth.wrapDegrees(anchor.yaw - camera.yRot())) > ARC_LOST_YAW
					|| Math.abs(anchor.pitch - camera.xRot()) > ARC_LOST_PITCH) {
				if (anchor.lostSince < 0) {
					anchor.lostSince = now;
				} else if (now - anchor.lostSince > ARC_LOST_NS) {
					anchor = null;
				}
			} else {
				anchor.lostSince = -1;
			}
		}
		if (anchor == null) {
			long seed = ArcLayout.seed(index, shown.get(index).text());
			float side = (ArcLayout.unit(seed, 0, SALT_SIDE) * 2 - 1) * ARC_SIDE;
			float up = ARC_ABOVE_MIN + ArcLayout.unit(seed, 0, SALT_UP) * (ARC_ABOVE_MAX - ARC_ABOVE_MIN);
			anchor = new ArcAnchor();
			anchor.placed = now;
			anchor.side = side;
			if (fixed) {
				anchor.yaw = cfg.direction.yaw + side;
				anchor.pitch = -cfg.height - (up - (ARC_ABOVE_MIN + ARC_ABOVE_MAX) / 2);
			} else {
				anchor.yaw = camera.yRot() + side;
				anchor.pitch = Mth.clamp(camera.xRot() - up, -85, 85);
			}
			arcAnchors.put(index, anchor);
		}
		if (follow && cfg.placement == Placement.SKY && cfg.direction == SkyLyricsConfig.Direction.FOLLOW
				&& !cfg.lazyFollow) {
			anchor.yaw = camera.yRot() + anchor.side; // turns with you, no waiting
		}
		return anchor;
	}

	/** HUD pass: draws what the world pass laid out, projected to where it sits in the sky. */
	public void renderHud(GuiGraphicsExtractor g, DeltaTracker delta) {
		Pending frame = pending;
		pending = null;
		if (frame == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		SkyLyricsConfig cfg = SkyLyricsConfig.get();
		Draw draw = new Draw(new ScreenCanvas(g, mc.font), mc.font, cfg, rgb(), frame.wrapWidth());
		for (Spot spot : frame.spots()) {
			Vector3f at = ScreenLayer.project(spot.offset(), g.guiWidth(), g.guiHeight());
			if (at == null) {
				continue; // behind you
			}
			float size = frame.scale() * at.z;
			g.pose().pushMatrix();
			g.pose().translate(at.x, at.y);
			g.pose().scale(size, size);
			drawSpot(draw, spot, cfg, frame.time(), frame.now());
			g.pose().popMatrix();
		}
	}

	private void drawContent(Draw draw, SkyLyricsConfig cfg, long time, long now) {
		if (shown.instrumental()) {
			draw.line(Tr.t("♪  Instrumental  ♪"), 0, 1, visibility, 0.6f, 1, -1, Float.NaN, Float.NaN);
		} else {
			drawLines(draw, cfg, time, now);
		}
	}

	/** Remembers when the line being sung changed, for the animations between lines. */
	private void noteIndex(int index, long now) {
		if (index != lastIndex) {
			previousIndex = lastIndex;
			lastIndex = index;
			changedAt = previousIndex == Integer.MIN_VALUE ? now - ANIM_NS : now;
		}
	}

	private void drawLines(Draw draw, SkyLyricsConfig cfg, long time, long now) {
		int index = shown.indexAt(time);
		noteIndex(index, now);
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
						sung = progress(time, line.timeMs(), end);
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
		/** Moves to (x, y), turns by {@code angle} degrees (clockwise on screen) and scales. */
		void push(float x, float y, float scale, float angle);

		void pop();

		/**
		 * Copies with a higher {@code behind} sit a hair further back than the real text (0), so glow and depth
		 * layers never flicker through it in the world.
		 */
		void text(float x, float y, FormattedCharSequence seq, boolean shadow, int argb, int behind);
	}

	private record WorldCanvas(SubmitNodeCollector out, PoseStack pose, Font.DisplayMode mode) implements Canvas {
		@Override
		public void push(float x, float y, float scale, float angle) {
			pose.pushPose();
			pose.translate(x, y, 0);
			if (angle != 0) {
				// The text space has y pointing down, so a positive turn about z is clockwise on screen
				pose.mulPose(new Quaternionf().rotationZ(angle * DEG));
			}
			pose.scale(scale, scale, scale);
		}

		@Override
		public void pop() {
			pose.popPose();
		}

		@Override
		public void text(float x, float y, FormattedCharSequence seq, boolean shadow, int argb, int behind) {
			if (behind > 0) {
				pose.pushPose();
				pose.translate(0, 0, -LAYER_STEP * behind);
			}
			out.submitText(pose, x, y, seq, shadow, mode, LightCoordsUtil.FULL_BRIGHT, argb, 0, 0);
			if (behind > 0) {
				pose.popPose();
			}
		}
	}

	private record ScreenCanvas(GuiGraphicsExtractor g, Font font) implements Canvas {
		@Override
		public void push(float x, float y, float scale, float angle) {
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			if (angle != 0) {
				g.pose().rotate(angle * DEG); // y points down here too, so positive is clockwise
			}
			g.pose().scale(scale, scale);
		}

		@Override
		public void pop() {
			g.pose().popMatrix();
		}

		@Override
		public void text(float x, float y, FormattedCharSequence seq, boolean shadow, int argb, int behind) {
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.text(font, seq, 0, 0, argb, shadow);
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
			int[][] bounds = wordBounds(row);
			int[] first = bounds[0], last = bounds[1];
			int words = first.length;
			if (words == 0) {
				return null;
			}
			float[] byWord = new float[words];
			boolean any = false;
			for (int w = 0; w < words; w++) {
				float start = charIndex + first[w], end = charIndex + last[w] + 1;
				byWord[w] = wordGrowth(sungChars, start, end, highlight);
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

		/** Cuts a line into words and places them for the arc layout. */
		ArcText arcText(int index, String text) {
			List<String> words = new ArrayList<>();
			List<Integer> first = new ArrayList<>();
			int[] cps = text.codePoints().toArray();
			for (int i = 0; i < cps.length; ) {
				if (cps[i] == ' ') {
					i++;
					continue;
				}
				int start = i;
				while (i < cps.length && cps[i] != ' ') {
					i++;
				}
				words.add(new String(cps, start, i - start));
				first.add(start);
			}
			int n = words.size();
			float[] widths = new float[n];
			boolean[] pause = new boolean[n];
			int[] firstChar = new int[n];
			for (int w = 0; w < n; w++) {
				String word = words.get(w);
				widths[w] = LyricsFont.width(font, base, word);
				pause[w] = ",.;:!?…".indexOf(word.charAt(word.length() - 1)) >= 0;
				firstChar[w] = first.get(w);
			}
			boolean rtl = Bidi.requiresBidi(text.toCharArray(), 0, text.length())
					&& !new Bidi(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight();
			float space = LyricsFont.width(font, base, " ");
			List<ArcLayout.Group> groups = ArcLayout.place(widths, ArcLayout.rows(widths, pause), space,
					ArcLayout.seed(index, text), rtl);
			return new ArcText(words.toArray(String[]::new), widths, firstChar, Math.max(1, cps.length), groups);
		}

		/**
		 * One line of the arc layout. Words appear one at a time as they're sung, fading in as they rise a little;
		 * once sung they hold still until the whole line leaves together, fading as it drifts up.
		 */
		void arcLine(ArcShot shot, long time, long now) {
			int index = shot.index();
			LyricLine line = shown.get(index);
			String text = cfg.uppercase ? line.text().toUpperCase(Locale.ROOT) : line.text();
			ArcText arc = arcCache.computeIfAbsent(index + "\n" + text, k -> arcText(index, text));
			boolean leaving = shot.leave() >= 0;
			float leave = leaving ? shot.leave() : 0;
			float alpha = visibility * cfg.opacity * (1 - leave) * shot.arrive();
			if (alpha <= 0.01f) {
				return;
			}
			// Without word timing (karaoke off, plain lyrics) the words come in one after another as the line starts
			boolean timed = cfg.karaoke && shown.synced();
			long start = line.timeMs(), end = lineEnd(index);
			long sungAt = time - LEAD_MS - ARC_WORD_DELAY_MS;
			float since = (now - changedAt) / 1e9f;
			int dark = depthDark(color, daylight), light = depthLight(color);
			int words = arc.words().length;
			// The one word after those already sung, shown faintly ahead of its time
			int nextWord = -1;
			if (!leaving && timed) {
				for (int w = 0; w < words; w++) {
					if (wordAt(arc, w, start, end) > sungAt) {
						nextWord = w;
						break;
					}
				}
			}

			canvas.push(0, -ARC_LEAVE_RISE * easeOutCubic(leave), ARC_SIZE, 0);
			for (ArcLayout.Group g : arc.groups()) {
				canvas.push(g.x(), g.y(), g.scale(), g.angle());
				for (int w = g.from(); w < g.to(); w++) {
					float e = 1, current = 0, ahead = 0;
					if (!leaving && timed) {
						long at = wordAt(arc, w, start, end);
						long next = w + 1 < words ? wordAt(arc, w + 1, start, end) : end;
						e = Mth.clamp((sungAt - at) / (float) ARC_WORD_FADE_MS, 0, 1);
						if (sungAt >= at) {
							current = Mth.clamp((next - sungAt) / (float) ARC_CURRENT_MS, 0, 1);
						}
					} else if (!leaving) {
						e = Mth.clamp((since - w * WORD_STAGGER) * 1000f / ARC_WORD_FADE_MS, 0, 1);
					}
					if (w == nextWord) {
						long before = w > 0 ? wordAt(arc, w - 1, start, end) : start;
						long at = wordAt(arc, w, start, end);
						ahead = Mth.lerp(Mth.clamp((sungAt - before) / (float) Math.max(1, at - before), 0, 1), NEXT_ALPHA_MIN, NEXT_ALPHA_MAX);
					}
					e = easeOutCubic(e);
					if (e <= 0.004f && ahead <= 0) {
						continue;
					}
					float cx = g.wordX()[w - g.from()];
					float y = -GLYPH_MIDDLE + (1 - e) * ARC_APPEAR_RISE;
					// A waiting word is smaller and flat; it grows to full size and gets its depth as it's sung
					float size = ahead > 0 ? Mth.lerp(e, NEXT_SCALE, 1) : 1;
					canvas.push(cx, 0, size, 0);
					word(arc.words()[w], -arc.widths()[w] / 2, y, alpha * Math.max(ahead, e), mix(color, 0xFFFFFF, CURRENT_WHITE * current),
							dark, light, g.angle(), e > 0.004f);
					canvas.pop();
				}
				canvas.pop();
			}
			canvas.pop();
		}

		/**
		 * One word with its depth: a darker copy just below and a lighter one just above, solid, so the letters look
		 * raised. The offsets point straight down the screen however the word is turned.
		 */
		private void word(String word, float x, float y, float alpha, int face, int dark, int light, float angle, boolean withDepth) {
			Display d = Display.of(word);
			int argb = Math.max(4, Math.round(alpha * 255)) << 24 | 0xFFFFFF;
			if (cfg.glow > 0.01f) {
				int copies = LyricsRenderer.this.light ? GLOW_LIGHT : GLOW_INNER;
				int glowByte = Math.round(cfg.glow * alpha * 1.28f / copies * 255);
				if (glowByte >= 2) {
					int halo = mix(face, 0x000000, 0.75f * daylight);
					for (int k = 0; k < copies; k++) {
						float ang = k * Mth.TWO_PI / copies + Mth.PI / copies;
						canvas.text(x + Mth.cos(ang) * 0.85f, y + Mth.sin(ang) * 0.85f, colored(d, halo), false, glowByte << 24 | halo, 3);
					}
				}
			}
			if (cfg.depth && withDepth) {
				float sin = Mth.sin(angle * DEG), cos = Mth.cos(angle * DEG);
				canvas.text(x + sin * DEPTH_DOWN, y + cos * DEPTH_DOWN, colored(d, dark), false, argb, 2);
				canvas.text(x - sin * DEPTH_UP, y - cos * DEPTH_UP, colored(d, light), false, argb, 1);
			}
			canvas.text(x, y, colored(d, face), cfg.shadow && !cfg.depth, argb, 0);
		}

		/** When a word of the line starts being sung, spreading the line's time over its letters. */
		private static long wordAt(ArcText arc, int w, long start, long end) {
			return start + (end - start) * arc.firstChar()[w] / arc.total();
		}

		private FormattedCharSequence colored(Display d, int rgb) {
			Style style = base.withColor(rgb);
			return sink -> {
				for (int v = 0; v < d.cps.length; v++) {
					if (!sink.accept(v, style, d.cps[v])) {
						return false;
					}
				}
				return true;
			};
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

			canvas.push(0, y, scale, 0);
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
							canvas.push(sx, cy, size, 0);
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
							// Two rings of copies make the halo; few copies each, as every copy costs as much as the word itself
							for (int ring = 1; ring <= 2; ring++) {
								int copies = glowCopies(ring, ga, light);
								if (copies == 0) {
									continue;
								}
								float radius = ring * 0.85f * (1 + 0.7f * fl);
								int alphaByte = Math.min(255, Math.round(ga * (ring == 1 ? 1.28f : 0.64f) / copies * 255));
								if (alphaByte < 2) {
									continue;
								}
								int glowColor = alphaByte << 24 | halo;
								float offset = ring == 1 ? Mth.PI / copies : Mth.PI / 4;
								for (int k = 0; k < copies; k++) {
									float ang = k * Mth.TWO_PI / copies + offset;
									canvas.text(sx + Mth.cos(ang) * radius, sy + Mth.sin(ang) * radius,
											glowSeq, false, glowColor, 1);
								}
							}
						}
						canvas.text(sx, sy, seq, cfg.shadow, Math.max(4, Math.round(alpha * f * 255)) << 24 | 0xFFFFFF, 0);
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

	/**
	 * How far through its line the singing is: 0 at the start, 1 at the end. It keeps counting a little past 1 so
	 * the last word settles back to its normal size after it's sung; the karaoke fill stops at 1 by itself.
	 */
	static float progress(long time, long start, long end) {
		return Mth.clamp((time - start) / (float) Math.max(1, end - start), 0, 2);
	}

	/**
	 * Size of a word spanning letters {@code start..end} of the line when {@code sungChars} letters have been sung:
	 * it swells as the singing reaches it and settles once the singing has moved on.
	 */
	static float wordGrowth(float sungChars, float start, float end, float highlight) {
		float in = smooth((sungChars - start) / GROW_RAMP), out = smooth((end + GROW_RAMP - sungChars) / GROW_RAMP);
		return 1 + GROW * in * out * highlight;
	}

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

	/** Each word's first and last letter (logical order), kept per row since the same rows are drawn every frame. */
	private static final Map<String, int[][]> BOUNDS = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, int[][]> eldest) {
			return size() > 64;
		}
	};

	private static int[][] wordBounds(String row) {
		return BOUNDS.computeIfAbsent(row, r -> {
			int[] rank = wordRanks(r);
			int[] cps = r.codePoints().toArray();
			int words = wordCount(r);
			int[] first = new int[words], last = new int[words];
			Arrays.fill(first, Integer.MAX_VALUE);
			for (int i = 0; i < cps.length; i++) {
				if (cps[i] != ' ') {
					first[rank[i]] = Math.min(first[rank[i]], i);
					last[rank[i]] = Math.max(last[rank[i]], i);
				}
			}
			return new int[][] {first, last};
		});
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
		daylight = approach(daylight, Math.max(target, BACKDROP * backdrop), dt * 2f);
	}

	/** A painted sky this bright at full strength makes the lyrics stand out as they do by day: darker shadow, dark halo. */
	static final float BACKDROP = 0.8f;

	/** Set by the sky each frame: how strongly a bright painted sky shows, 0 to 1. */
	public void backdrop(float strength) {
		backdrop = Mth.clamp(strength, 0, 1);
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
			// Right where you look; lazy follow stays put while you look around a bit and catches up when you turn away
			float diff = Mth.wrapDegrees(camYaw - anchorYaw);
			if (!cfg.lazyFollow || Math.abs(diff) > 38) {
				following = true;
			} else if (Math.abs(diff) < 4) {
				following = false;
			}
			targetYaw = following ? camYaw : anchorYaw;
			targetPitch = -cfg.height;
			speed = cfg.lazyFollow ? 2.2f : Float.POSITIVE_INFINITY;
		} else {
			targetYaw = cfg.direction.yaw;
			targetPitch = -cfg.height;
			speed = 3f;
		}
		float k = dt == 0 || Float.isInfinite(speed) ? 1 : 1 - (float) Math.exp(-dt * speed);
		anchorYaw += Mth.wrapDegrees(targetYaw - anchorYaw) * k;
		anchorPitch += (targetPitch - anchorPitch) * k;
	}

	private static float approach(float value, float target, float step) {
		return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
	}

	private static float easeInOut(float t) {
		return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
	}

	private static float easeOutCubic(float t) {
		float u = 1 - t;
		return 1 - u * u * u;
	}

	/** The dark depth copy: darker and a little more saturated than the face, darker still by day. */
	static int depthDark(int face, float day) {
		float[] hsb = java.awt.Color.RGBtoHSB(face >> 16 & 0xFF, face >> 8 & 0xFF, face & 0xFF, null);
		float b = hsb[2] * Mth.lerp(Mth.clamp(day, 0, 1), DEPTH_DARK_NIGHT, DEPTH_DARK_DAY);
		return java.awt.Color.HSBtoRGB(hsb[0], Math.min(1, hsb[1] * DEPTH_SATURATION), b) & 0xFFFFFF;
	}

	/** The light depth copy: the face a touch brighter. */
	static int depthLight(int face) {
		int r = Math.min(255, Math.round((face >> 16 & 0xFF) * DEPTH_LIGHT));
		int g = Math.min(255, Math.round((face >> 8 & 0xFF) * DEPTH_LIGHT));
		int b = Math.min(255, Math.round((face & 0xFF) * DEPTH_LIGHT));
		return r << 16 | g << 8 | b;
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
