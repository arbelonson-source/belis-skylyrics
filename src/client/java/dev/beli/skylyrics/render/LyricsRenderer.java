package dev.beli.skylyrics.render;

import java.text.Bidi;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.ArabicShaping;
import com.ibm.icu.text.ArabicShapingException;
import com.mojang.blaze3d.vertex.PoseStack;
//? if >=26.1 {
/*import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
*///?} else {
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
//?}
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
//? if >=26.1 {
/*import net.minecraft.util.LightCoordsUtil;
*///?} else {
import net.minecraft.client.renderer.LightTexture;
//?}
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
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
import dev.beli.skylyrics.media.TitleCleaner;
import dev.beli.skylyrics.media.Track;
import dev.beli.skylyrics.util.Guard;
import dev.beli.skylyrics.util.TextCut;

/**
 * Draws the lyrics in the world. Text goes through Minecraft's own text pipeline (the same one
 * name tags use), so it works with Sodium, Iris and shader packs without special cases. When
 * {@link ScreenLayer} is active the same layout is drawn over the finished frame instead, at the
 * spot in the sky where it would have been, so shader lighting and clouds leave it alone.
 */
public final class LyricsRenderer {
	private static final float DEG = Mth.DEG_TO_RAD;
	private static final long ANIM_NS = 450_000_000L;
	/** A sliding line comes in from, and leaves to, this share of the line width off to the side. */
	static final float SLIDE_PART = 0.35f;
	/** Lines show a little before their timestamp so the animation has finished when the singing starts. */
	public static final long LEAD_MS = 150;
	/** Copies of a word drawn around it for the glow, in the inner and the outer ring. */
	private static final int GLOW_INNER = 6, GLOW_OUTER = 4;
	/** Copies in the one ring that light effects draw. */
	private static final int GLOW_LIGHT = 3;

	/** A word's glow in the Arc layout: the inner ring, with as few copies as {@link #glowCopies} allows for its strength. */
	static void glowRing(Canvas canvas, float x, float y, FormattedCharSequence haloSeq, float glow, float alpha, int halo, boolean light) {
		float strength = glow * alpha;
		int copies = glowCopies(1, strength, light);
		int glowByte = Math.round(strength * 1.28f / copies * 255);
		if (!glowVisible(glowByte)) {
			return;
		}
		for (int k = 0; k < copies; k++) {
			float ang = k * Mth.TWO_PI / copies + Mth.PI / copies;
			canvas.letters(x + Mth.cos(ang) * 0.85f, y + Mth.sin(ang) * 0.85f, haloSeq, false, glowByte << 24 | halo, 3);
		}
	}

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
	/** A line over this long (the top bar's limit) fades out of Lines and Arc, over the second time. */
	static final long STALE_AFTER_MS = 1500, STALE_FADE_MS = 400;
	/** Text with less alpha than this is drawn fully opaque by the game's font code on 1.21.1 to 1.21.5, so it never gets less. */
	static final int MIN_TEXT_ALPHA = 4;

	/** Whether a glow copy of this alpha (0 to 255) is worth drawing: fainter ones are invisible, or on 1.21.1 to 1.21.5 would come out solid. */
	static boolean glowVisible(int alphaByte) {
		return alphaByte >= MIN_TEXT_ALPHA;
	}
	/** How far back the song time must drop between two frames to count as a seek back, twice the tracker's 350 ms of jitter. */
	static final long SEEK_BACK_MS = 700;
	/** The limit for a drop that comes with a pause being noticed late (up to a poll of extrapolated position). */
	static final long SEEK_BACK_LATE_PAUSE_MS = 1500;
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
	private static final long ARC_WORD_FADE_MS = 300, ARC_WORD_DELAY_MS = 100, ARC_LEAVE_RAMP_MS = 80;
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
	private boolean wasReloading;
	/** A gap this long between lyrics frames means they weren't drawn for a while, not that the game is slow. */
	private static final long GAP_FORGIVE_NS = 1_000_000_000L;
	/** Fewer glow copies this frame, because the settings ask for it or the game is slow. */
	private boolean light;
	private float visibility;
	private float daylight;
	/** 0 at night, 1 in the day, from the stars alone (rain and painted skies ignored); for the Time of day colour. */
	private float sunlight;
	/** How strongly a bright painted sky (synthwave, moonlight) shows behind the lyrics, 0 to 1. */
	private float backdrop;
	private boolean hasLyrics;
	private float anchorYaw = Float.NaN;
	private float anchorPitch;
	private boolean following;
	private float cr = -1, cg, cb;

	private Lyrics shown = Lyrics.NONE;
	private long frozenTime;
	private final LineClock clock = new LineClock();
	private final Map<String, List<String>> wrapCache = new HashMap<>();
	/** Where each character of a row starts, in display order (one extra entry: the row's width). */
	private final Map<String, float[]> offsetCache = new HashMap<>();
	/** The same for rows laid out right to left; a map of its own so no key string is built per frame. */
	private final Map<String, float[]> offsetCacheRtl = new HashMap<>();
	/** What depends only on a line's text and its rows (where rows start, word counts, direction); see {@link LineInfo}. */
	private final Map<String, LineInfo> infoCache = new HashMap<>();
	/** Scratch for {@code Draw.line}, reused so a row doesn't allocate its per-character arrays every frame. Render thread only. */
	private float[] scratchReveal = new float[64], scratchFlare = new float[64], scratchSpread = new float[65];
	/** The instrumental song's note line, translated once per language. */
	private final LangText instrumentalText = new LangText(() -> Tr.t("♪  Instrumental  ♪"));
	private float wrapCacheWidth;
	private Style wrapCacheStyle = Style.EMPTY;
	/** Width of a few letters when the caches were filled; changes when fonts are reloaded. */
	private float wrapCacheProbe;
	private long wrapCacheEpoch;
	private @Nullable Pending pending;
	private final WorldWords worldWords = new WorldWords();
	/** The song time the World words were last updated at, to notice a jump back; MIN_VALUE when they weren't. */
	private long worldTime = Long.MIN_VALUE;
	/** Whether that earlier frame's reading was playing. */
	private boolean worldPlaying;

	/** You left a world: let go of its level so the whole thing can be freed while you sit on the title screen. */
	public void onLeaveWorld() {
		wordsLevel = null;
		announced = null;
		SkyEffects.forgetStars();
		WorldWords.forgetPlayers();
		Visibility.forget();
	}

	/** You joined a world: its first song title floats up again. A dimension change within a world doesn't count. */
	public void onJoinWorld() {
		announced = null;
		resetAnchor();
		// The arc lines' spots belong to the last world's view too
		arcAnchors.clear();
		Visibility.forget();
	}

	/** Forget where the lyrics were anchored, so the next frame starts them where you look now (Lazy follow). */
	void resetAnchor() {
		anchorYaw = Float.NaN;
		following = false;
	}

	boolean anchorNeedsInit() {
		return FollowAnchor.needsInit(anchorYaw, anchorPitch);
	}

	/** You swung at something; a World word under the crosshair gets knocked. */
	public void swing() {
		worldWords.swing();
	}
	/** Whether the player was dead last frame, so the words start fresh once you respawn. */
	private boolean wasDead;
	/** The world the words were placed in; joining another server or world starts them over there. */
	private @Nullable Object wordsLevel;
	/** The song whose title World words last showed, to show the next one's when it changes. */
	private @Nullable Track announced;
	/** Arc layouts of the lines on show, keyed by line number and text. */
	private final Map<String, List<ArcSlot>> arcCache = new HashMap<>();
	/** Where in the sky each arc line was put, by line number. */
	private final Map<Integer, ArcAnchor> arcAnchors = new HashMap<>();
	@Nullable
	private ArcKey arcKey;

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
		/** How far above the middle height it was placed, in degrees (within the above-range). */
		float up;
		/** Whether it was placed by a compass direction or the view lock, not where you were looking. */
		boolean fixed;
		long placed;
		long lostSince = -1;
	}

	/** One arc layout of a text, for the line number it was made for. */
	private record ArcSlot(int index, ArcText arc) {}

	/** A line cut into words and placed for the arc layout. */
	private record ArcText(String[] words, float[] widths, int[] firstChar, int total, List<ArcLayout.Group> groups, boolean rtl) {}

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
	/**
	 * On 26.1 the solid and translucent feature passes run, and the submit nodes are cleared, before END_MAIN fires,
	 * so anything submitted there is never drawn. There the words must be submitted at COLLECT_SUBMITS (26.2+ and
	 * the 1.21.x line still draw what END_MAIN submits).
	 */
	//? if >=26.1 && <26.2 {
	/*private static final boolean LATE_SUBMITS_LOST = true;
	*///?} else {
	private static final boolean LATE_SUBMITS_LOST = false;
	//?}

	//? if >=26.1 {
	/*public void renderEarly(LevelRenderContext context) {
	*///?} else {
	public void renderEarly(WorldRenderContext context) {
	//?}
		if (ScreenLayer.shadersOn() || LATE_SUBMITS_LOST) {
			guardedRender(context);
		}
	}

	//? if >=26.1 {
	/*public void renderLate(LevelRenderContext context) {
	*///?} else {
	public void renderLate(WorldRenderContext context) {
	//?}
		if (!ScreenLayer.shadersOn() && !LATE_SUBMITS_LOST) {
			guardedRender(context);
		}
	}

	//? if >=26.1 {
	/*private void guardedRender(LevelRenderContext context) {
		Guard.run("lyrics in the world", () -> {
			PoseStack.Pose mark = context.poseStack().last();
			try {
				render(context);
			} catch (RuntimeException | LinkageError e) {
				unwindTo(context.poseStack(), mark);
				throw e;
			}
		});
	}
	*///?} else {
	private void guardedRender(WorldRenderContext context) {
		Guard.run("lyrics in the world", () -> {
			PoseStack.Pose mark = context.matrices().last();
			try {
				render(context);
			} catch (RuntimeException | LinkageError e) {
				unwindTo(context.matrices(), mark);
				throw e;
			}
		});
	}
	//?}

	/** Pops what a failed draw left pushed on the game's pose stack, back to the pose it had on the way in. */
	static void unwindTo(PoseStack pose, PoseStack.Pose mark) {
		//? if >=1.21.5 {
		while (pose.last() != mark && !pose.isEmpty()) {
		//?} else {
		/*while (pose.last() != mark && !pose.clear()) { // before 1.21.5 clear() says whether only the base pose is left
		*///?}
			pose.popPose();
		}
	}

	//? if >=26.1 {
	/*private void render(LevelRenderContext context) {
	*///?} else {
	private void render(WorldRenderContext context) {
	//?}
		pending = null;
		Minecraft mc = Minecraft.getInstance();
		SkyLyricsConfig cfg = SkyLyricsConfig.get();
		//? if >=26.2 {
		/*Camera camera = mc.gameRenderer.mainCamera();
		*///?} else {
		Camera camera = mc.gameRenderer.getMainCamera();
		//?}
		long now = System.nanoTime();
		float dt = step(mc, cfg, now);
		long time = takeLyrics(cfg);

		boolean want = cfg.enabled && hasLyrics
				// The death screen isn't a menu: the world still shows through it, and the words drop away there
				//? if >=26.2 {
				/*&& !(cfg.hideWithF1 && mc.gui.hud.isHidden())
				&& !(cfg.hideInMenus && mc.gui.screen() != null && !(mc.gui.screen() instanceof dev.beli.skylyrics.gui.SkyLyricsScreen)
						&& !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.DeathScreen))
				*///?} else {
				&& !(cfg.hideWithF1 && mc.options.hideGui)
				&& !(cfg.hideInMenus && mc.screen != null && !(mc.screen instanceof dev.beli.skylyrics.gui.SkyLyricsScreen)
						&& !(mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen))
				//?}
				&& !(cfg.hideWhenPaused && !tracker.playing()) && !Visibility.hidden(mc, cfg);
		visibility = approach(visibility, want ? 1 : 0, dt * 3f);
		updateColor(cfg, dt, now, shown.indexAt(time));
		updateDaylight(mc, camera, dt);
		updateAnchor(cfg, camera, dt);
		// Every layout, so the crosshair, sky and hat flashes follow the lines even where nothing here animates them
		clock.frame(visibility > 0.01f && !shown.isEmpty(), shown.instrumental(), shown.indexAt(time), now, ANIM_NS);
		if (visibility <= 0.01f && !shown.isEmpty() && !shown.instrumental()) {
			// Hidden: keep up with the song, so showing the lyrics again isn't mistaken for a line change
			clock.follow(shown.indexAt(time), now, ANIM_NS);
		}
		// Only the Arc layout prunes these; coming back to it must fade each line in afresh, not snap to an old spot
		if (cfg.layout != Layout.ARC) {
			arcAnchors.clear();
		}
		if (cfg.layout == Layout.WORLD && !shown.instrumental()) {
			renderWorldWords(context, mc, cfg, camera, time, now);
			return;
		}
		worldWords.clear();
		worldTime = Long.MIN_VALUE;
		if (visibility <= 0.01f || shown.isEmpty() && !shown.instrumental()) {
			return;
		}

		boolean sky = cfg.placement == Placement.SKY;
		float distance = sky
				? Math.max(8, Math.min(cfg.distance, mc.options.getEffectiveRenderDistance() * 16 * 0.8f))
				: floatingDistance(cfg.distance);
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

		//? if >=26.1 {
		/*PoseStack pose = context.poseStack();
		*///?} else {
		PoseStack pose = context.matrices();
		//?}
		Font.DisplayMode mode = cfg.seeThroughBlocks ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
		//? if >=26.1 {
		/*Draw draw = new Draw(new WorldCanvas(context.submitNodeCollector(), pose, mode), mc.font, cfg, rgb(), wrapWidth);
		*///?} else {
		Draw draw = new Draw(new WorldCanvas(context.commandQueue(), pose, mode), mc.font, cfg, rgb(), wrapWidth);
		//?}
		for (Spot spot : spots) {
			if (!LyricsInView.worthDrawing(spot.offset(), 1.2f * wrapWidth * scale, 5f * ROW_H * scale)) {
				continue; // looking away: skip the line and all its glow copies
			}
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
		long gap = lastFrame == 0 ? 0 : now - lastFrame;
		float dt = Math.min(0.1f, gap / 1e9f);
		lastFrame = now;
		// A resource reload (its loading screen is up), a shader switch or a second without lyrics frames (a reload this
		// path doesn't see, world loading) stalls the game: don't count it as a slow game
		boolean reloading = loadingScreenUp(mc);
		if (reloading || wasReloading) {
			LyricsFont.fontsReloaded();
		}
		if (reloading || wasReloading || gap > GAP_FORGIVE_NS || ScreenLayer.takeShaderFlip()) {
			budget.forgive();
		}
		wasReloading = reloading;
		budget.update(dt, mc.getFramerateLimitTracker().getFramerateLimit());
		light = cfg.effects == SkyLyricsConfig.Effects.LIGHT || cfg.effects == SkyLyricsConfig.Effects.AUTO && budget.light();
		return dt;
	}

	/** Whether the resource loading screen is up. */
	private static boolean loadingScreenUp(Minecraft mc) {
		//? if >=26.2 {
		/*return mc.gui.overlay() != null;
		*///?} else {
		return mc.getOverlay() != null;
		//?}
	}

	/** Takes up the lyrics to show, sets {@link #hasLyrics}, and returns the song time to show them at. */
	long takeLyrics(SkyLyricsConfig cfg) {
		// One read of the tracker, so the song and its position belong together
		dev.beli.skylyrics.media.Snapshot now = tracker.snapshot();
		Track playing = now.track();
		LyricsService.Snapshot snap = service.snapshot();
		Track loaded = snap.track();
		// The tracker moves to a new song a moment before the lyrics are asked for: never show the old song's lines then
		// (title and artist only: a late album alone doesn't reload the lyrics)
		Lyrics current = loaded.title().equals(playing.title()) && loaded.artist().equals(playing.artist()) ? snap.lyrics() : Lyrics.NONE;
		if (!cfg.showPlainLyrics && !current.synced() && !current.instrumental()) {
			current = Lyrics.NONE; // unsynced lyrics can't follow the song, some people prefer nothing
		}
		long time = tracker.positionMs(now) + cfg.offsetFor(playing) + LEAD_MS;
		hasLyrics = !current.isEmpty() || current.instrumental();
		if (hasLyrics) {
			if (current != shown) {
				shown = current;
				clock.reset();
				wrapCache.clear();
				offsetCache.clear();
				offsetCacheRtl.clear();
				infoCache.clear();
				arcCache.clear();
				arcAnchors.clear();
				worldWords.newLyrics();
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
		LyricsFont.checkFonts(mc.font);
		// Remembered until a resource reload, which is what can change it; measured again only then
		float probe = LyricsFont.cachedWidth(mc.font, wrapStyle, "Mw0♪漢א ع");
		long epoch = LyricsFont.reloadCount(); // moves on a reload and on Force Unicode Font / Japanese glyph variants
		if (wrapWidth != wrapCacheWidth || !wrapStyle.equals(wrapCacheStyle) || probe != wrapCacheProbe || epoch != wrapCacheEpoch) {
			wrapCacheProbe = probe;
			wrapCacheEpoch = epoch;
			wrapCache.clear();
			offsetCache.clear();
			offsetCacheRtl.clear();
			infoCache.clear();
			arcCache.clear();
			wrapCacheWidth = wrapWidth;
			wrapCacheStyle = wrapStyle;
		}
	}

	/**
	 * Title screen: no world is drawn there, so the lines go flat in the gap between the lowest button
	 * ({@code top}) and the copyright text ({@code bottom}), sized to fit it.
	 */
	public void renderMenu(GuiGraphics g, float top, float bottom, float maxWidth) {
		Guard.run("lyrics on the title screen", () -> drawMenu(g, top, bottom, maxWidth));
	}

	/** The title screen lyrics' size: what fits the height under the buttons and the width beside them, at most 1.5 x Size. */
	static float menuScale(float height, float maxWidth, float size) {
		return Math.min(Math.min(height / MENU_ROWS, MENU_MAX_SCALE * size), maxWidth / MENU_WRAP);
	}

	private void drawMenu(GuiGraphics g, float top, float bottom, float maxWidth) {
		Minecraft mc = Minecraft.getInstance();
		SkyLyricsConfig cfg = SkyLyricsConfig.get();
		long now = System.nanoTime();
		float dt = step(mc, cfg, now);
		long time = takeLyrics(cfg);
		boolean want = cfg.enabled && cfg.titleScreenLyrics && hasLyrics && !(cfg.hideWhenPaused && !tracker.playing());
		visibility = approach(visibility, want ? 1 : 0, dt * 3f);
		updateColor(cfg, dt, now, shown.indexAt(time));
		daylight = approach(daylight, 1, dt * 2f); // menus have no time of day, and the dark halo reads on any backdrop
		float scale = menuScale(bottom - top, maxWidth, cfg.size);
		if (visibility <= 0.01f || shown.isEmpty() && !shown.instrumental() || scale < MENU_MIN_SCALE) {
			return;
		}
		wrapAt(mc, cfg, MENU_WRAP);
		Draw draw = new Draw(new ScreenCanvas(g, mc.font), mc.font, cfg, rgb(), MENU_WRAP);
		g.pose().pushMatrix();
		try {
			g.pose().translate(g.guiWidth() / 2f, (top + bottom) / 2f);
			g.pose().scale(scale, scale);
			drawContent(draw, cfg, time, now);
		} finally {
			draw.canvas.unwind();
			g.pose().popMatrix();
		}
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

	/** A title without "- Remastered 2011", "(feat. …)" and the like, unless that would leave nothing. */
	static String shownTitle(String title) {
		String clean = TitleCleaner.forSearch(title);
		return clean.isEmpty() ? title : clean;
	}

	/** The WORLD layout: cards in the world pass, even when the rest goes on screen, so each keeps its own perspective. */
	//? if >=26.1 {
	/*private void renderWorldWords(LevelRenderContext context, Minecraft mc, SkyLyricsConfig cfg, Camera camera, long time,
	*///?} else {
	private void renderWorldWords(WorldRenderContext context, Minecraft mc, SkyLyricsConfig cfg, Camera camera, long time,
	//?}
			long now) {
		int index = shown.indexAt(time);
		String text = null;
		float sungChars = 0;
		boolean over = false;
		if (sungLine(index)) {
			LyricLine line = shown.get(index);
			//? if >=26.1 {
			/*text = line.drawString();
			*///?} else {
			text = line.text();
			//?}
			int total = text.codePointCount(0, text.length());
			sungChars = sungChars(shown, index, time, total);
			over = lineOver(shown, index, time);
		}
		Style style = LyricsFont.world(cfg);
		worldWords.glowColour(cfg.glowPick ? cfg.glowColor : -1);
		worldWords.tone(cfg.twoTone ? cfg.toneColor : -1);
		worldWords.nudge(cfg.worldNudge);
		worldWords.arrival(cfg.worldArrival);
		worldWords.leave(cfg.worldLeave);
		worldWords.soft(cfg.worldSoft);
		worldWords.big(cfg.worldBig);
		worldWords.players(cfg.worldAvoidPlayers, cfg.worldKnock);
		worldWords.clearMiddle(cfg.worldClearMiddle);
		worldWords.curve(cfg.worldCurve);
		worldWords.tune(cfg.worldWords, cfg.worldFade, cfg.worldDistance, cfg.worldTilt, cfg.worldRise, cfg.worldFlow, cfg.worldHold, cfg.uppercase, cfg.size);
		worldWords.glide(camera.position(), camera.yRot(), camera.xRot(), cfg.worldGlide && mc.player != null
				&& (mc.player.isFallFlying() || mc.player.isAutoSpinAttack() || mc.player.isPassenger() || mc.player.getAbilities().flying), now);
		if (mc.level != wordsLevel) {
			wordsLevel = mc.level;
			worldWords.clear();
		}
		// While you're dead the words drop away and nothing new comes; after respawning they start over
		boolean dead = cfg.worldDeath && mc.player != null && mc.player.isDeadOrDying();
		if (dead) {
			worldWords.fall(now);
		} else if (wasDead) {
			worldWords.clear();
		}
		wasDead = dead;
		// A new song's name floats up first, once the words know which way they gather
		float[] lock = ViewLock.update(cfg, camera.yRot(), camera.xRot());
		float fixedYaw = lock != null ? lock[0] : cfg.direction.yaw;
		float fixedPitch = lock != null ? lock[1] : Float.NaN;
		// One read of the tracker, so the title and the position belong to the same song
		dev.beli.skylyrics.media.Snapshot song = tracker.snapshot();
		Track track = song.track();
		float alpha = visibility * cfg.opacity;
		// Not while the lyrics are still loading (nothing is visible yet, and the title's few seconds would pass unseen)
		if (!dead && !track.isNone() && !sameSong(track, announced) && titleCanShow(hasLyrics, alpha)) {
			announced = track;
			if (cfg.worldTitle) {
				worldWords.announce(shownTitle(track.title()), TitleCleaner.shownArtist(track.artist()), fixedYaw, fixedPitch, camera, mc.font, style, now);
			}
		}
		// The raw song position, not the lyrics time: a timing slider moves the lyrics time without any seek
		long position = tracker.positionMs(song);
		if (jumpedBack(worldTime, position, worldPlaying, song.playing())) {
			worldWords.rewind(now);
		}
		worldTime = position;
		worldPlaying = song.playing();
		if (!dead) {
			worldWords.update(index, text, sungChars, over, fixedYaw, fixedPitch, camera, mc.level, mc.font, style, now);
		}
		if (alpha <= 0.01f) {
			worldWords.dropSwing();
			return;
		}
		Font.DisplayMode mode = cfg.seeThroughBlocks ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
		int wordsRgb = cfg.worldTint ? SkyTint.tint(rgb(), SkyTint.sky(), SkyTint.AMOUNT) : rgb();
		//? if >=26.1 {
		/*worldWords.render(context.poseStack(), context.submitNodeCollector(), camera, mode, style, wordsRgb, alpha, cfg.glow, daylight, cfg.size, light, now);
		*///?} else {
		worldWords.render(context.matrices(), context.commandQueue(), camera, mode, style, wordsRgb, alpha, cfg.glow, daylight, cfg.size, light, now);
		//?}
	}

	/** Whether a new song's title can be announced now: its lyrics are in and the words are visible, so it isn't used up unseen. */
	static boolean titleCanShow(boolean hasLyrics, float alpha) {
		return hasLyrics && alpha > 0.01f;
	}

	/** Same title and artist as the track already announced; the same Track object is the common case and costs nothing. */
	static boolean sameSong(Track track, @Nullable Track announced) {
		return track == announced
				|| announced != null && track.title().equals(announced.title()) && track.artist().equals(announced.artist());
	}

	/** When a line counts as over, so its World words can go. */
	private long lineEnd(int index) {
		return shown.lineEnd(index);
	}

	/**
	 * The arc lines to draw this frame: the one being sung, plus the previous one while it leaves. Each line gets
	 * its spot in the sky when it starts (where you're looking, give or take a little) and keeps it.
	 */
	private List<ArcShot> arcShots(SkyLyricsConfig cfg, Camera camera, long time, long now) {
		int index = shown.indexAt(time);
		noteIndex(index, now);
		List<ArcShot> shots = new ArrayList<>(2);
		float leave = (now - clock.changedAt()) / (float) ARC_LEAVE_NS;
		int previous = clock.previous();
		if (previous != index && leave < 1 && sungLine(previous)) {
			shots.add(new ArcShot(previous, leave, arrival(arcAnchor(cfg, camera, previous, now, false), now)));
		}
		if (sungLine(index)) {
			shots.add(new ArcShot(index, -1, arrival(arcAnchor(cfg, camera, index, now, true), now)));
		}
		arcAnchors.keySet().removeIf(i -> i != index && i != previous);
		return shots;
	}

	/**
	 * How many of a line's {@code total} letters have been sung at {@code time}: spread over the line's time slot. Plain
	 * (unsynced) lyrics have estimated slots too, so their words come out one by one the same way instead of all at once.
	 */
	static float sungChars(Lyrics lyrics, int index, long time, int total) {
		long start = lyrics.get(index).timeMs(), end = lyrics.sungEnd(index);
		// The next line takes over LEAD_MS early, and the cards of this one are let go then: the words must all be out
		// by then (a little before, for a frame to land in), or the last ones of a short line never show
		long last = Math.min(end, lyrics.lineEnd(index) - LEAD_MS - FLIP_SLACK_MS);
		// A short line is on show for little longer than it takes to sing, so its words start as it comes in
		long first = start - (long) (LEAD_MS * Mth.clamp(1 - (end - start) / (float) FAST_LINE_MS, 0, 1));
		return progress(time - LEAD_MS, first, Math.max(last, first + MIN_REVEAL_MS)) * total;
	}

	/**
	 * How far a word is out while its line leaves, {@code leave} (0 to 1) of the way: its own {@code reveal}, or more
	 * as the leaving goes on, so a word not out yet fades in over {@link #ARC_LEAVE_RAMP_MS} and nothing flashes.
	 */
	static float leaveReveal(float reveal, float leave) {
		return Math.max(reveal, Mth.clamp(leave * (ARC_LEAVE_NS / 1_000_000f) / ARC_LEAVE_RAMP_MS, 0, 1));
	}

	/** Lines shorter than this have their words start before their stamp; the earlier the shorter the line is. */
	static final long FAST_LINE_MS = 1000;
	/** How long before the next line takes over a line's last word is out, and the least time its words get. */
	static final long FLIP_SLACK_MS = 50, MIN_REVEAL_MS = 40;

	/** Whether a line's slot ended a moment ago, so its last words can leave (also for the last line, which has no next one). */
	static boolean lineOver(Lyrics lyrics, int index, long time) {
		return time - LEAD_MS > lyrics.lineEnd(index) + WORLD_LINGER_MS;
	}

	/**
	 * Whether the song time jumped back far enough to be a seek or a restart. The reported position carries some noise
	 * (the tracker alone lets 350 ms pass as jitter), so only a drop past {@link #SEEK_BACK_MS} counts; a pause (the same
	 * time again), a line change and a forward jump never do. {@code previousMs} is {@link Long#MIN_VALUE} when there is
	 * no earlier frame.
	 */
	static boolean jumpedBack(long previousMs, long timeMs) {
		return jumpedBack(previousMs, timeMs, true, true);
	}

	/**
	 * The same with the play states of the two frames. A pause is noticed one poll late (the position is extrapolated
	 * until then), so the reading that says paused can sit up to a poll behind: a drop that comes with playing to paused
	 * keeps the old {@link #SEEK_BACK_LATE_PAUSE_MS} limit. A drop with the same play state (a real seek, while playing
	 * or paused) or into playing, and a buffering stall's snap back (still playing), count past {@link #SEEK_BACK_MS}.
	 */
	static boolean jumpedBack(long previousMs, long timeMs, boolean wasPlaying, boolean playing) {
		long limit = wasPlaying && !playing ? SEEK_BACK_LATE_PAUSE_MS : SEEK_BACK_MS;
		return previousMs != Long.MIN_VALUE && previousMs - timeMs > limit;
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
		float[] lock = ViewLock.update(cfg, camera.yRot(), camera.xRot());
		boolean fixed = lock != null || cfg.placement == Placement.SKY && cfg.direction != SkyLyricsConfig.Direction.FOLLOW;
		// Height, Direction, Placement or the View lock changed: every line's spot is worked out again from its own side and height
		ArcKey key = ArcKey.of(cfg.placement, cfg.direction, cfg.height, lock);
		if (!key.equals(arcKey)) {
			arcKey = key;
			for (ArcAnchor each : arcAnchors.values()) {
				// A line placed where you were looking keeps that spot while you stay in Follow or Floating; only the rest is aimed again
				if (fixed || each.fixed) {
					aim(each, cfg, camera, lock, fixed);
					each.lostSince = -1;
				}
			}
		}
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
			//? if >=26.1 {
			/*long seed = ArcLayout.seed(index, shown.get(index).drawString());
			*///?} else {
			long seed = ArcLayout.seed(index, shown.get(index).text());
			//?}
			anchor = new ArcAnchor();
			anchor.placed = now;
			anchor.side = (ArcLayout.unit(seed, 0, SALT_SIDE) * 2 - 1) * ARC_SIDE;
			anchor.up = ARC_ABOVE_MIN + ArcLayout.unit(seed, 0, SALT_UP) * (ARC_ABOVE_MAX - ARC_ABOVE_MIN);
			aim(anchor, cfg, camera, lock, fixed);
			arcAnchors.put(index, anchor);
		}
		if (lock == null && follow && cfg.placement == Placement.SKY && cfg.direction == SkyLyricsConfig.Direction.FOLLOW
				&& !cfg.lazyFollow) {
			anchor.yaw = camera.yRot() + anchor.side; // turns with you, no waiting
		}
		return anchor;
	}

	/** Puts the anchor where its side and height say, for the settings and the view right now. */
	private static void aim(ArcAnchor anchor, SkyLyricsConfig cfg, Camera camera, @Nullable float[] lock, boolean fixed) {
		anchor.fixed = fixed;
		if (fixed) {
			float[] at = ViewLock.target(lock, cfg.direction, camera.yRot(), cfg.height);
			anchor.yaw = at[0] + anchor.side;
			anchor.pitch = at[1] - (anchor.up - (ARC_ABOVE_MIN + ARC_ABOVE_MAX) / 2);
		} else {
			// Where you look, a little above it
			anchor.yaw = camera.yRot() + anchor.side;
			anchor.pitch = Mth.clamp(camera.xRot() - anchor.up, -85, 85);
		}
	}

	/** The settings an arc line's spot was worked out from. NaN stands for no pinned view. */
	record ArcKey(Placement placement, SkyLyricsConfig.Direction direction, float height, float lockYaw, float lockPitch) {
		static ArcKey of(Placement placement, SkyLyricsConfig.Direction direction, float height, @Nullable float[] lock) {
			return new ArcKey(placement, direction, height, lock == null ? Float.NaN : lock[0], lock == null ? Float.NaN : lock[1]);
		}
	}

	/**
	 * The pitch the Lines layout sits at when it follows your view: the Height setting as an angle above the horizon
	 * in the sky, and floating a little below your view (half the Height, at most 15 degrees).
	 */
	static float followPitch(boolean floating, float camPitch, float height) {
		return floating ? Mth.clamp(camPitch - Math.min(height, 30) * 0.5f, -85, 85) : -height;
	}

	/**
	 * How far in front of you the lyrics float with Placement Floating: the Distance slider's range (8 to 160) spread
	 * over 4 to 16 blocks, with the default 64 on the same 10.7 blocks as before, so every step moves the lyrics.
	 */
	public static float floatingDistance(float distance) {
		float d = Mth.clamp(distance, 8, 160);
		return d <= 64 ? Mth.lerp((d - 8) / 56f, 4f, 32f / 3f) : Mth.lerp((d - 64) / 96f, 32f / 3f, 16f);
	}

	/**
	 * How much of the line being sung still shows. It is all of it until the line has been over for a moment (the
	 * top bar lets go of it then too), then it fades out over {@link #STALE_FADE_MS}, so the last lyric doesn't
	 * stay lit through the outro or a long gap with no pause marker. Pauses, unsynced lyrics and the time before the first line are left as they are.
	 */
	static float staleFade(Lyrics lyrics, int index, long time) {
		if (!lyrics.synced() || index < 0 || index >= lyrics.lines().size() || lyrics.get(index).isBreak()) {
			return 1;
		}
		long late = time - (lyrics.lineEnd(index) + STALE_AFTER_MS);
		return late <= 0 ? 1 : Mth.clamp(1 - late / (float) STALE_FADE_MS, 0, 1);
	}

	/** HUD pass: draws what the world pass laid out, projected to where it sits in the sky. */
	public void renderHud(GuiGraphics g, DeltaTracker delta) {
		Guard.run("lyrics on the HUD", () -> drawHud(g, delta));
	}

	private void drawHud(GuiGraphics g, DeltaTracker delta) {
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
			if (!LyricsInView.worthDrawing(spot.offset(), 1.2f * frame.wrapWidth() * frame.scale(), 5f * ROW_H * frame.scale())) {
				continue; // well off to the side
			}
			float size = frame.scale() * at.z;
			g.pose().pushMatrix();
			try {
				g.pose().translate(at.x, at.y);
				g.pose().scale(size, size);
				drawSpot(draw, spot, cfg, frame.time(), frame.now());
			} finally {
				draw.canvas.unwind();
				g.pose().popMatrix();
			}
		}
	}

	private void drawContent(Draw draw, SkyLyricsConfig cfg, long time, long now) {
		if (shown.instrumental()) {
			draw.line(instrumentalText.get(), 0, 1, visibility, 0.6f, 1, -1, Float.NaN, Float.NaN);
		} else {
			drawLines(draw, cfg, time, now);
		}
	}

	/** Remembers when the line being sung changed, for the animations between lines. */
	private void noteIndex(int index, long now) {
		clock.note(index, now, ANIM_NS);
	}

	private void drawLines(Draw draw, SkyLyricsConfig cfg, long time, long now) {
		int index = shown.indexAt(time);
		noteIndex(index, now);
		float p = Mth.clamp((now - clock.changedAt()) / (float) ANIM_NS, 0, 1);
		boolean stepForward = index == clock.previous() + 1;

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
				if (p < 1 && clock.previous() != Integer.MIN_VALUE) {
					arrangement(draw, cfg, clock.previous(), time, 0, visibility * Math.max(0, 1 - p * 2.5f), 1, now);
				}
				float pop = Calm.around(0.8f + 0.2f * easeOutBack(p), 1, Calm.motion());
				arrangement(draw, cfg, index, time, 0, visibility * Math.min(1, p * 2f), pop, now);
			}
			case TYPEWRITER -> arrangement(draw, cfg, index, time, 0, visibility, 1, now);
			case SLIDE -> {
				// The old line slides off one side as the new one glides in from the other, the way the song is going
				float side = (index >= clock.previous() ? 1 : -1) * draw.wrapWidth;
				if (p < 1 && clock.previous() != Integer.MIN_VALUE) {
					slid(draw, cfg, clock.previous(), time, -side * slideOut(p), visibility * (1 - easeInOut(p)), now);
				}
				slid(draw, cfg, index, time, side * slideIn(p), visibility * Math.min(1, p * 1.5f), now);
			}
			case WORDS -> {
				// The old line fades away while the new one's words come in one after another
				if (p < 1 && clock.previous() != Integer.MIN_VALUE) {
					arrangement(draw, cfg, clock.previous(), time, 0, visibility * (1 - easeInOut(Math.min(1, p * 1.6f))), 1, now);
				}
				arrangement(draw, cfg, index, time, 0, visibility, 1, now);
			}
		}
	}

	private void slid(Draw draw, SkyLyricsConfig cfg, int center, long time, float x, float alpha, long now) {
		draw.canvas.push(x, 0, 1, 0);
		arrangement(draw, cfg, center, time, 0, alpha, 1, now);
		draw.canvas.pop();
	}

	/** How far off to the side a sliding line still is, as a share of the line width, {@code p} into the change. */
	static float slideIn(float p) {
		return SLIDE_PART * (1 - easeOutCubic(Mth.clamp(p, 0, 1)));
	}

	/** How far the old line has slid away, as a share of the line width. */
	static float slideOut(float p) {
		return SLIDE_PART * easeInOut(Mth.clamp(p, 0, 1));
	}

	private void crossfade(Draw draw, SkyLyricsConfig cfg, int index, long time, float p, long now) {
		float e = easeInOut(p);
		if (p < 1 && clock.previous() != Integer.MIN_VALUE) {
			arrangement(draw, cfg, clock.previous(), time, 0, visibility * (1 - e), 1, now);
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
					//? if >=26.1 {
					/*text = cfg.uppercase ? WorldWords.capitals(line.drawString()) : line.drawString();
					*///?} else {
					text = cfg.uppercase ? WorldWords.capitals(line.text()) : line.text();
					//?}
					if (i < center || i == center && (!cfg.karaoke || !shown.synced())) {
						sung = i == center || i < center ? 1 : 0;
					} else if (i == center) {
						sung = progress(time, line.timeMs(), shown.sungEnd(i));
					} else {
						sung = 0;
					}
				}
			}
			float highlight = Mth.clamp(1 - Math.abs(r), 0, 1);
			float scale = Mth.lerp(highlight, SECONDARY, 1f) * (i == center ? popScale : 1);
			int maxChars = -1;
			if (cfg.animation == Animation.TYPEWRITER && i == center && clock.last() == center) {
				maxChars = (int) ((now - clock.changedAt()) / 28_000_000L);
			}
			float wordsT = Float.NaN, flareT = Float.NaN;
			if (i == center && clock.last() == center && clock.previous() != Integer.MIN_VALUE) {
				float since = (now - clock.changedAt()) / 1e9f;
				if (cfg.animation == Animation.WORDS) {
					wordsT = since - 0.06f; // a beat after the old line starts fading
				}
				if (cfg.pulse) {
					// The flash follows the flash clock (at most three starts a second) and is left out when flashes are hidden
					float flash = Calm.flashSince(Calm.hideFlashes(), clock.flashSince(now));
					flareT = cfg.animation == Animation.WORDS ? flash - 0.06f : flash;
				}
			}
			// The line being sung goes when it has been over for a while; the lines around it stay as they are
			float stale = i == center ? staleFade(shown, i, time) : 1;
			items.add(new Item(text, r, scale, a * alpha * stale, highlight, sung, maxChars, wordsT, flareT));
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
	interface Canvas {
		/** Moves to (x, y), turns by {@code angle} degrees (clockwise on screen) and scales. */
		void push(float x, float y, float scale, float angle);

		void pop();

		/** Pops whatever a failed draw left pushed, so the pose is back where it was. */
		default void unwind() {
		}

		/**
		 * Copies with a higher {@code behind} sit a hair further back than the real text (0), so glow and depth
		 * layers never flicker through it in the world.
		 */
		void letters(float x, float y, FormattedCharSequence seq, boolean shadow, int argb, int behind);
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
		public void letters(float x, float y, FormattedCharSequence seq, boolean shadow, int argb, int behind) {
			if (behind > 0) {
				pose.pushPose();
				pose.translate(0, 0, -LAYER_STEP * behind);
			}
			//? if >=26.1 {
			/*out.submitText(pose, x, y, seq, shadow, mode, LightCoordsUtil.FULL_BRIGHT, argb, 0, 0);
			*///?} else {
			out.submitText(pose, x, y, seq, shadow, mode, LightTexture.FULL_BRIGHT, argb, 0, 0);
			//?}
			if (behind > 0) {
				pose.popPose();
			}
		}
	}

	private record ScreenCanvas(GuiGraphics g, Font font, int[] depth) implements Canvas {
		ScreenCanvas(GuiGraphics g, Font font) {
			this(g, font, new int[1]);
		}

		@Override
		public void unwind() {
			while (depth[0] > 0) {
				depth[0]--;
				g.pose().popMatrix();
			}
		}

		@Override
		public void push(float x, float y, float scale, float angle) {
			depth[0]++;
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			if (angle != 0) {
				g.pose().rotate(angle * DEG); // y points down here too, so positive is clockwise
			}
			g.pose().scale(scale, scale);
		}

		@Override
		public void pop() {
			depth[0]--;
			g.pose().popMatrix();
		}

		@Override
		public void letters(float x, float y, FormattedCharSequence seq, boolean shadow, int argb, int behind) {
			g.pose().pushMatrix();
			try {
				g.pose().translate(x, y);
				g.drawString(font, seq, 0, 0, argb, shadow);
			} finally {
				g.pose().popMatrix();
			}
		}
	}

	/**
	 * Styles of one base in a few colours, each built once. The depth copies share one colour for the whole frame and the
	 * face colour repeats from word to word, so most words find theirs here. Render thread only; one per frame.
	 */
	static final class ColorStyles {
		private static final int SIZE = 16;
		private Style base;
		private final int[] rgbs = new int[SIZE];
		private final Style[] styles = new Style[SIZE];
		private int count, next;

		Style of(Style base, int rgb) {
			if (base != this.base) {
				this.base = base;
				count = 0;
				next = 0;
			}
			for (int i = 0; i < count; i++) {
				if (rgbs[i] == rgb) {
					return styles[i];
				}
			}
			Style made = base.withColor(rgb);
			int at = count < SIZE ? count++ : next++ % SIZE;
			rgbs[at] = rgb;
			styles[at] = made;
			return made;
		}
	}

	private final class Draw {
		final Canvas canvas;
		final Font font;
		final SkyLyricsConfig cfg;
		final int color;
		final float wrapWidth;
		final Style base;
		/** The few colours a frame's words are drawn in, as Styles built once instead of per word and copy. */
		private final ColorStyles colors = new ColorStyles();
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

		float[] offsets(String row, Display d, boolean rtl) {
			// The same row is laid out differently by line direction (and apart from the default Display.of(row)), so it is part of the key
			return (rtl ? offsetCacheRtl : offsetCache).computeIfAbsent(row, r -> {
				float[] at = new float[d.cps.length + 1];
				for (int v = 1; v <= d.cps.length; v++) {
					at[v] = LyricsFont.width(font, base, new String(d.cps, 0, v));
				}
				return at;
			});
		}

		LineInfo info(String text, List<String> rows) {
			return infoCache.computeIfAbsent(text, t -> LineInfo.of(t, rows));
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

		/** The text {@link #word} draws for a word, left to right: what its width is measured on. */
		static String drawn(String word, boolean rtl) {
			int[] cps = Display.of(word, rtl).cps();
			return new String(cps, 0, cps.length);
		}

		/** Cuts a line into words and places them for the arc layout. */
		ArcText arcText(int index, String text) {
			ArcWords split = arcWords(text);
			List<String> words = split.words();
			int[] cps = text.codePoints().toArray();
			int n = words.size();
			float[] widths = new float[n];
			boolean[] pause = new boolean[n];
			int[] firstChar = new int[n];
			boolean rtl = WorldWords.rightToLeft(text);
			List<TopBarWords.Piece> pieces = new ArrayList<>(n);
			for (int w = 0; w < n; w++) {
				String word = words.get(w);
				// Measured as drawn: the shaped, mirrored letters, which can be a different width than the word as written
				widths[w] = LyricsFont.width(font, base, drawn(word, rtl));
				pause[w] = endsWithPause(word);
				firstChar[w] = split.first()[w];
				pieces.add(new TopBarWords.Piece(word, w == 0 || split.tight() == null || !split.tight()[w - 1]));
			}
			float space = LyricsFont.width(font, base, " ");
			List<ArcLayout.Group> groups = ArcLayout.place(widths, ArcLayout.rows(widths, pause, split.tight()), space,
					ArcLayout.seed(index, text), split.tight(), TopBarWords.visualRanks(pieces));
			return new ArcText(words.toArray(String[]::new), widths, firstChar, Math.max(1, cps.length), groups, rtl);
		}

		/**
		 * One line of the arc layout. Words appear one at a time as they're sung, fading in as they rise a little;
		 * once sung they hold still until the whole line leaves together, fading as it drifts up.
		 */
		void arcLine(ArcShot shot, long time, long now) {
			int index = shot.index();
			LyricLine line = shown.get(index);
			//? if >=26.1 {
			/*String text = cfg.uppercase ? WorldWords.capitals(line.drawString()) : line.drawString();
			*///?} else {
			String text = cfg.uppercase ? WorldWords.capitals(line.text()) : line.text();
			//?}
			// Looked up by text, then by line number: no key string is built per frame
			List<ArcSlot> slots = arcCache.computeIfAbsent(text, k -> new ArrayList<>(1));
			ArcText arc = null;
			for (int i = 0; i < slots.size(); i++) {
				if (slots.get(i).index() == index) {
					arc = slots.get(i).arc();
					break;
				}
			}
			if (arc == null) {
				arc = arcText(index, text);
				slots.add(new ArcSlot(index, arc));
			}
			boolean leaving = shot.leave() >= 0;
			float leave = leaving ? shot.leave() : 0;
			float alpha = visibility * cfg.opacity * (1 - leave) * shot.arrive() * (leaving ? 1 : staleFade(shown, index, time));
			if (alpha <= 0.01f) {
				return;
			}
			// Without word timing (karaoke off, plain lyrics) the words come in one after another as the line starts
			boolean timed = cfg.karaoke && shown.synced();
			long start = line.timeMs(), end = shown.sungEnd(index);
			long sungAt = time - LEAD_MS - ARC_WORD_DELAY_MS;
			float since = (now - clock.changedAt()) / 1e9f;
			int dark = depthDark(color, daylight), light = depthLight(color);
			int words = arc.words().length;
			boolean rtl = arc.rtl();
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

			// A giant line has hundreds of rows, nearly all far outside the view: leave those out
			float angular = (cfg.placement == Placement.SKY ? 3.4f : 2.6f) * cfg.size;
			float reach = ArcLayout.reach(ARC_SIZE * (float) Math.tan(angular * DEG) / ROW_H);
			canvas.push(0, -ARC_LEAVE_RISE * easeOutCubic(leave), ARC_SIZE, 0);
			// A line of hundreds of words is many rows tall and comes out from its top row: scroll it to the words being sung
			float sungWord = 0;
			if (leaving) {
				sungWord = words - 1;
			} else if (timed) {
				int w = 0;
				for (int i = 0; i < words && wordAt(arc, i, start, end) <= sungAt; i++) {
					w = i;
				}
				long at = wordAt(arc, w, start, end);
				long next = w + 1 < words ? wordAt(arc, w + 1, start, end) : end;
				sungWord = w + Mth.clamp((sungAt - at) / (float) Math.max(1, next - at), 0, 1);
			} else {
				sungWord = Math.min(words - 1, since / WORD_STAGGER);
			}
			float focus = ArcLayout.focus(arc.groups(), sungWord);
			for (ArcLayout.Group g : arc.groups()) {
				if (!ArcLayout.worthDrawing(g, reach, focus)) {
					continue;
				}
				canvas.push(g.x(), g.y() - focus, g.scale(), g.angle());
				for (int w = g.from(); w < g.to(); w++) {
					float e = 1, current = 0, ahead = 0;
					if (leaving && timed) {
						// A word not out yet comes in over a moment as the line fades, instead of popping up in full
						e = leaveReveal(Mth.clamp((sungAt - wordAt(arc, w, start, end)) / (float) ARC_WORD_FADE_MS, 0, 1), leave);
					} else if (!leaving && timed) {
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
					int count = arc.words().length;
					word(arc.words()[w], rtl, -arc.widths()[w] / 2, y, alpha * Math.max(ahead, e),
							mix(toned(count < 2 ? 0 : w / (float) (count - 1)), 0xFFFFFF, CURRENT_WHITE * current),
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
		/** The words' colour {@code along} the line (0 first word, 1 last), fading into the second colour when Two-tone is on. */
		private int toned(float along) {
			return WorldWords.toned(color, cfg.twoTone ? cfg.toneColor : -1, along);
		}

		private void word(String word, boolean rtl, float x, float y, float alpha, int face, int dark, int light, float angle, boolean withDepth) {
			Display d = Display.of(word, rtl);
			int argb = Math.max(4, Math.round(alpha * 255)) << 24 | 0xFFFFFF;
			if (cfg.glow > 0.01f) {
				int halo = mix(glowOf(face), 0x000000, 0.75f * daylight);
				glowRing(canvas, x, y, colored(d, halo), cfg.glow, alpha, halo, LyricsRenderer.this.light);
			}
			if (cfg.depth && withDepth) {
				float sin = Mth.sin(angle * DEG), cos = Mth.cos(angle * DEG);
				canvas.letters(x + sin * DEPTH_DOWN, y + cos * DEPTH_DOWN, colored(d, dark), false, argb, 2);
				canvas.letters(x - sin * DEPTH_UP, y - cos * DEPTH_UP, colored(d, light), false, argb, 1);
			}
			canvas.letters(x, y, colored(d, face), cfg.shadow && !cfg.depth, argb, 0);
		}

		/** When a word of the line starts being sung, spreading the line's time over its letters. */
		private static long wordAt(ArcText arc, int w, long start, long end) {
			return start + (end - start) * arc.firstChar()[w] / arc.total();
		}

		private FormattedCharSequence colored(Display d, int rgb) {
			Style style = colors.of(base, rgb);
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
			if (rows.isEmpty()) {
				return; // only a line of nothing but spaces wraps to no rows
			}
			LineInfo info = info(text, rows);
			int[] starts = info.starts;
			int total = info.total, words = info.words;
			float sungChars = sung * total;
			float stagger = Math.min(WORD_STAGGER, 0.7f / Math.max(1, words));

			float day = daylight;
			int bright = mix(color, 0xFFFFFF, 0.55f - 0.08f * day);
			int dim = mix(color, 0x000000, 0.45f - 0.4f * day); // dark words would sink into the dark halo
			int secondary = mix(color, 0xFFFFFF, 0.25f - 0.25f * day);
			int halo = mix(glowOf(color), 0x000000, 0.75f * day);
			// Two-tone: the same shades of the second colour, blended in letter by letter along the line
			int tone = cfg.twoTone ? cfg.toneColor : -1;
			int brightTo = tone < 0 ? bright : mix(tone, 0xFFFFFF, 0.55f - 0.08f * day);
			int dimTo = tone < 0 ? dim : mix(tone, 0x000000, 0.45f - 0.4f * day);
			int secondaryTo = tone < 0 ? secondary : mix(tone, 0xFFFFFF, 0.25f - 0.25f * day);
			int haloTo = tone < 0 ? halo : mix(glowOf(tone), 0x000000, 0.75f * day);
			int letters = total;
			float haloWeight = 1 + 0.9f * day;
			boolean byWord = !Float.isNaN(wordsT);

			boolean growing = cfg.growWord && cfg.karaoke && shown.synced() && highlight > 0.01f && sung > 0;

			canvas.push(0, y, scale, 0);
			float top = -rows.size() * ROW_H / 2;
			boolean rtl = info.rtl;
			int wordIndex = 0;
			for (int r = 0; r < rows.size(); r++) {
				String row = rows.get(r);
				int charIndex = starts[r];
				Display d = Display.of(row, rtl);
				int n = d.cps.length;
				// How visible each character is: typewriter cuts the line off, words fade in one at a time
				if (scratchReveal.length < n) {
					scratchReveal = new float[n * 2];
					scratchFlare = new float[n * 2];
				}
				float[] reveal = scratchReveal;
				float[] flare = scratchFlare;
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
				float[] at = offsets(row, d, rtl);
				float[] grow = growing ? growth(row, d, charIndex, sungChars, highlight) : null;
				if (grow != null) {
					// A bigger word pushes its neighbours aside instead of covering them
					if (scratchSpread.length < n + 1) {
						scratchSpread = new float[n * 2 + 1];
					}
					float[] spread = scratchSpread;
					spread[0] = 0;
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
						// One-entry cache: a run mostly shares one colour, so build its Style once, not per glyph per frame
						Style[] lastStyle = {null};
						int[] lastColor = {0};
						float[] lastFill = {Float.NaN};
						FormattedCharSequence seq = sink -> {
							for (int v = a0; v < a1; v++) {
								float fill = Mth.clamp(sungChars - (start + d.logical[v]), 0, 1);
								Style st;
								if (tone < 0 && lastStyle[0] != null && fill == lastFill[0]) {
									// Without a second tone the colour depends on the fill alone
									st = lastStyle[0];
								} else {
									int c = mix(mix(secondary, mix(dim, bright, fill), highlight), 0xFFFFFF, 0.35f * fl);
									if (tone >= 0) {
										float t = letters < 2 ? 0 : (start + d.logical[v]) / (float) (letters - 1);
										int shade = mix(mix(secondaryTo, mix(dimTo, brightTo, fill), highlight), 0xFFFFFF, 0.35f * fl);
										c = mix(c, shade, t);
									}
									if (lastStyle[0] == null || c != lastColor[0]) {
										lastStyle[0] = base.withColor(c);
										lastColor[0] = c;
									}
									lastFill[0] = fill;
									st = lastStyle[0];
								}
								if (!sink.accept(v, st, d.cps[v])) {
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
									// Two-tone: the glow follows the letters into the second colour; only its strength comes from the copy
									Style glowStyle = tone < 0 ? base
											: base.withColor(mix(halo, haloTo, letters < 2 ? 0 : (start + d.logical[v]) / (float) (letters - 1)));
									if (!sink.accept(v, glowStyle, d.cps[v])) {
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
								if (!glowVisible(alphaByte)) {
									continue;
								}
								int glowColor = alphaByte << 24 | halo;
								float offset = ring == 1 ? Mth.PI / copies : Mth.PI / 4;
								for (int k = 0; k < copies; k++) {
									float ang = k * Mth.TWO_PI / copies + offset;
									canvas.letters(sx + Mth.cos(ang) * radius, sy + Mth.sin(ang) * radius,
											glowSeq, false, glowColor, 1);
								}
							}
						}
						canvas.letters(sx, sy, seq, cfg.shadow, Math.max(4, Math.round(alpha * f * 255)) << 24 | 0xFFFFFF, 0);
						if (size != 1) {
							canvas.pop();
						}
					}
					from = to;
				}
				wordIndex += info.rowWords[r];
			}
			canvas.pop();
		}
	}

	/**
	 * Where each wrapped row starts, counted in the line's own code points (the unit of how far it has been sung). Not the
	 * rows' drawn lengths: shaping merges lam-alef and drops marks, so those run shorter and would put later rows ahead.
	 * {@code wrap} drops the space where it breaks a row (and collapses doubled ones), so the rows are lined up against
	 * the line's text to count those too; a cut inside a long word has no space to count.
	 */
	static int[] rowStarts(String text, List<String> rows) {
		int[] cps = text.codePoints().toArray();
		int[] starts = new int[rows.size()];
		int at = 0;
		for (int r = 0; r < starts.length; r++) {
			while (at < cps.length && cps[at] == ' ') {
				at++;
			}
			starts[r] = at;
			int[] row = rows.get(r).codePoints().toArray();
			for (int i = 0; i < row.length; i++) {
				at++;
				if (row[i] == ' ' && (i + 1 == row.length || row[i + 1] != ' ')) {
					while (at < cps.length && cps[at] == ' ') {
						at++;
					}
				}
			}
		}
		return starts;
	}

	private static final float WORD_STAGGER = 0.07f;
	private static final float WORD_FADE = 0.25f;
	static final float PULSE_RISE = 0.07f;
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
		if (Float.isNaN(t) || t < 0 || t >= PULSE_RISE + PULSE_FALL) {
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
		/**
		 * The same rows and words are drawn every frame, so their layout is kept instead of recomputed. Big enough for
		 * the World cards, the sky rows and every word of the top bar's old and new line at once, since a cache that
		 * cycles past its size misses every time. Render thread only.
		 */
		private static final Map<String, Display> CACHE = new LinkedHashMap<>(256, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, Display> eldest) {
				return size() > 256;
			}
		};

		static Display of(String row) {
			Display hit = CACHE.get(row);
			if (hit == null) { // not computeIfAbsent: its lambda would be allocated on every hit too
				hit = compute(row, null);
				CACHE.put(row, hit);
			}
			return hit;
		}

		/**
		 * A piece of a line that reads right to left, such as one word of it: its punctuation and numbers belong on the
		 * line's side, which the piece alone (starting with Hebrew, say) would not tell, or a lone "5" would not. The same
		 * goes for a left-to-right line: the comma of "hello שלום," belongs on the right of שלום.
		 */
		static Display of(String row, boolean rtl) {
			Map<String, Display> cache = rtl ? CACHE_RTL : CACHE_LTR;
			Display hit = cache.get(row);
			if (hit == null) { // not computeIfAbsent: its lambda captures rtl, so it would be allocated on every hit
				hit = compute(row, rtl);
				cache.put(row, hit);
			}
			return hit;
		}

		/** Rows drawn right to left on purpose, kept apart from the default-direction {@link #CACHE}; same size and LRU. */
		private static final Map<String, Display> CACHE_RTL = lru();
		/** Likewise for rows drawn left to right on purpose (the first strong letter would decide by default). */
		private static final Map<String, Display> CACHE_LTR = lru();

		private static Map<String, Display> lru() {
			return new LinkedHashMap<>(256, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<String, Display> eldest) {
					return size() > 256;
				}
			};
		}

		/** @param rightBase true for a right-to-left base, false for left-to-right, null for the row's first strong letter */
		private static Display compute(String row, @Nullable Boolean rightBase) {
			int[] cps = row.codePoints().toArray();
			int n = cps.length;
			int[] logical = new int[n];
			for (int i = 0; i < n; i++) {
				logical[i] = i;
			}
			if (!Bidi.requiresBidi(row.toCharArray(), 0, row.length())) {
				return dropMarks(new Display(cps, logical));
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
			int[] shaped = joinArabic(row).codePoints().toArray();
			Bidi bidi = new Bidi(row, rightBase == null ? Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT
					: rightBase ? Bidi.DIRECTION_RIGHT_TO_LEFT : Bidi.DIRECTION_LEFT_TO_RIGHT);
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
					if (shaped[i] == ' ' && cps[i] != ' ') {
						// The alef of a lam-alef, which is now part of the lam's letter
						continue;
					}
					if (arabicMark(cps[i])) {
						// Vowel marks would need stacking on the letter, which the font can't do; they'd leave a gap
						continue;
					}
					if (directionMark(cps[i])) {
						// Steered the order above; never drawn (the font would show a box)
						continue;
					}
					outCps[v] = rtl ? mirror(shaped[i]) : shaped[i];
					outLogical[v++] = i;
				}
			}
			return new Display(Arrays.copyOf(outCps, v), Arrays.copyOf(outLogical, v));
		}

		/**
		 * Arabic letters take a different form at the start, middle and end of a word so they join up; the font only
		 * has the separate forms, so this picks them. Keeps the length: a lam-alef pair becomes one letter and a space.
		 */
		static String joinArabic(String row) {
			try {
				return new ArabicShaping(ArabicShaping.LETTERS_SHAPE | ArabicShaping.LENGTH_FIXED_SPACES_NEAR).shape(row);
			} catch (ArabicShapingException e) {
				return row;
			}
		}

		/** LRM, RLM, ALM, the embeddings and overrides, and the isolates: invisible, but they steer the order. */
		static boolean directionMark(int cp) {
			return cp == 0x200E || cp == 0x200F || cp == 0x061C
				|| (cp >= 0x202A && cp <= 0x202E) || (cp >= 0x2066 && cp <= 0x2069);
		}

		/** The same display without the direction marks; {@code logical} keeps pointing at the raw text. */
		private static Display dropMarks(Display d) {
			int[] cps = d.cps();
			int v = 0;
			for (int cp : cps) {
				if (!directionMark(cp)) {
					v++;
				}
			}
			if (v == cps.length) {
				return d;
			}
			int[] outCps = new int[v];
			int[] outLogical = new int[v];
			v = 0;
			for (int i = 0; i < cps.length; i++) {
				if (!directionMark(cps[i])) {
					outCps[v] = cps[i];
					outLogical[v++] = d.logical()[i];
				}
			}
			return new Display(outCps, outLogical);
		}

		static boolean arabicMark(int cp) {
			return Character.getType(cp) == Character.NON_SPACING_MARK
				&& Character.UnicodeBlock.of(cp) == Character.UnicodeBlock.ARABIC;
		}

		/** The character a mirrored one is drawn as in a right-to-left run: ( ) [ ] { } < > « » ‹ › ≤ ≥ and the rest of Unicode's list. */
		private static int mirror(int cp) {
			return UCharacter.getMirror(cp);
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

	/** Chinese and Japanese stretches count one word per character, as in {@link #arcWords}. */
	static int[] computeWordRanks(String row) {
		int[] cps = row.codePoints().toArray();
		int[] rank = new int[cps.length];
		int w = -1;
		for (int i = 0; i < cps.length; ) {
			if (cps[i] == ' ') {
				rank[i++] = Math.max(0, w);
				continue;
			}
			int start = i;
			boolean cjk = false;
			while (i < cps.length && cps[i] != ' ') {
				cjk |= WorldWords.cjk(cps[i]);
				i++;
			}
			if (!cjk) {
				w++;
				Arrays.fill(rank, start, i, w);
				continue;
			}
			int at = start;
			for (int[] unit : WorldWords.cjkUnits(cps, start, i)) {
				w++;
				Arrays.fill(rank, at, unit[1], w);
				at = unit[1];
			}
			Arrays.fill(rank, at, i, w);
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

	static int wordCount(String row) {
		int[] rank = wordRanks(row);
		boolean any = false;
		for (int i = 0; i < row.length() && !any; i++) {
			any = row.charAt(i) != ' ';
		}
		return any ? rank[rank.length - 1] + 1 : 0;
	}

	/**
	 * Everything about a line that depends only on its text and how it wraps: where each row starts, how many letters
	 * and words it has, its direction. Kept with the wrapped rows so a frame doesn't count them again.
	 */
	static final class LineInfo {
		final int[] starts;
		/** Letters through the last row, so the break spaces are part of what has to be sung. */
		final int total;
		final int words;
		final int[] rowWords;
		final boolean rtl;

		private LineInfo(int[] starts, int total, int words, int[] rowWords, boolean rtl) {
			this.starts = starts;
			this.total = total;
			this.words = words;
			this.rowWords = rowWords;
			this.rtl = rtl;
		}

		static LineInfo of(String text, List<String> rows) {
			int[] starts = rowStarts(text, rows);
			int last = rows.size() - 1;
			int total = last < 0 ? 0 : starts[last] + rows.get(last).codePointCount(0, rows.get(last).length());
			int[] rowWords = new int[rows.size()];
			int words = 0;
			for (int r = 0; r < rowWords.length; r++) {
				rowWords[r] = wordCount(rows.get(r));
				words += rowWords[r];
			}
			return new LineInfo(starts, total, words, rowWords, WorldWords.rightToLeft(text));
		}
	}

	/** Whether a word ends with a mark where a row may nicely end. */
	static boolean endsWithPause(String word) {
		int end = word.length();
		while (end > 0 && Display.directionMark(word.charAt(end - 1))) {
			end--;
		}
		return end > 0 && ",.;:!?…،؟。、，！？".indexOf(word.charAt(end - 1)) >= 0;
	}

	/** A line cut into arc words: each word's text, where it starts (code points), and whether it runs straight into the next. */
	record ArcWords(List<String> words, int[] first, boolean[] tight) {}

	/**
	 * Cuts a line into the words the arc layout places. Words are split at spaces; a stretch with Chinese or Japanese
	 * in it is cut into single characters (see {@link WorldWords#cjkUnits}) that sit side by side with no gap, so a
	 * long line can wrap into rows and its characters come in one at a time. Other lines are cut exactly at spaces.
	 */
	static ArcWords arcWords(String text) {
		List<String> words = new ArrayList<>();
		List<Integer> first = new ArrayList<>();
		List<Boolean> tight = new ArrayList<>();
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
			boolean cjk = false;
			for (int k = start; k < i && !cjk; k++) {
				cjk = WorldWords.cjk(cps[k]);
			}
			if (!cjk) {
				words.add(new String(cps, start, i - start));
				first.add(start);
				tight.add(false);
				continue;
			}
			List<int[]> units = WorldWords.cjkUnits(cps, start, i);
			for (int u = 0; u < units.size(); u++) {
				int[] unit = units.get(u);
				words.add(new String(cps, unit[0], unit[1] - unit[0]));
				first.add(unit[0]);
				tight.add(u + 1 < units.size());
			}
		}
		boolean[] tightArr = new boolean[tight.size()];
		for (int k = 0; k < tightArr.length; k++) {
			tightArr[k] = tight.get(k);
		}
		return new ArcWords(words, first.stream().mapToInt(Integer::intValue).toArray(), tightArr);
	}

	static List<String> wrap(Font font, Style style, String text, float maxWidth) {
		return wrap(s -> LyricsFont.width(font, style, s), text, maxWidth);
	}

	/** {@link #wrap(Font, Style, String, float)} with the width measure passed in, so it can be tested without a game. */
	static List<String> wrap(ToDoubleFunction<String> width, String text, float maxWidth) {
		List<String> rows = new ArrayList<>();
		if (width.applyAsDouble(text) <= maxWidth) {
			rows.add(text);
			return rows;
		}
		// Balance the rows: aim for even widths instead of a long row and a one-word orphan
		int rowsNeeded = (int) Math.ceil(width.applyAsDouble(text) / maxWidth);
		float target = (float) Math.min(maxWidth, width.applyAsDouble(text) / rowsNeeded + 12);
		StringBuilder row = new StringBuilder();
		for (String word : text.split(" ")) {
			if (word.isEmpty()) {
				continue;
			}
			List<String> pieces = fitPieces(width, breakPoints(word), maxWidth);
			for (int p = 0; p < pieces.size(); p++) {
				String piece = pieces.get(p);
				String candidate = row.isEmpty() ? piece : row + (p == 0 ? " " : "") + piece;
				if (!row.isEmpty() && width.applyAsDouble(candidate) > target) {
					rows.add(row.toString());
					row.setLength(0);
					row.append(piece);
				} else {
					row.setLength(0);
					row.append(candidate);
				}
			}
		}
		if (!row.isEmpty()) {
			rows.add(row.toString());
		}
		return rows;
	}

	/**
	 * Makes sure no piece is wider than a row. Thai is written without spaces, so its words are found with the
	 * line-break rules; a piece still too wide (a long unbroken word) is cut at the nearest break the rules allow, and
	 * as a last resort between whole characters, never inside a letter with its accents. Pieces that already fit are
	 * left alone, so spaced languages wrap as they always did.
	 */
	private static List<String> fitPieces(ToDoubleFunction<String> width, List<String> pieces, float maxWidth) {
		List<String> out = new ArrayList<>();
		for (String piece : pieces) {
			boolean thai = piece.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.THAI);
			if (!thai && width.applyAsDouble(piece) <= maxWidth) {
				out.add(piece);
				continue;
			}
			for (String part : lineBreaks(piece)) {
				String rest = part;
				while (width.applyAsDouble(rest) > maxWidth && rest.length() > 1) {
					String head = TextCut.prefix(rest, s -> width.applyAsDouble(s) <= maxWidth);
					if (head.isEmpty()) {
						head = firstCharacter(rest);
					}
					out.add(head);
					rest = rest.substring(head.length());
				}
				if (!rest.isEmpty()) {
					out.add(rest);
				}
			}
		}
		return out;
	}

	private static String firstCharacter(String text) {
		BreakIterator it = BreakIterator.getCharacterInstance();
		it.setText(text);
		return text.substring(0, it.next());
	}

	/** The pieces of a word between the places where the line-break rules (and, for Thai, its dictionary) allow a break. */
	static List<String> lineBreaks(String word) {
		List<String> out = new ArrayList<>();
		BreakIterator it = BreakIterator.getLineInstance(Locale.ROOT);
		it.setText(word);
		for (int from = 0, to = it.next(); to != BreakIterator.DONE; from = to, to = it.next()) {
			out.add(word.substring(from, to));
		}
		return out;
	}

	/**
	 * A word cut where a row may break inside it: Chinese and Japanese are written without spaces, so a row may end
	 * after any of their characters. Other words come back whole.
	 */
	static List<String> breakPoints(String word) {
		int[] cps = word.codePoints().toArray();
		if (Arrays.stream(cps).noneMatch(WorldWords::cjk)) {
			return List.of(word);
		}
		List<String> pieces = new ArrayList<>();
		for (int[] unit : WorldWords.cjkUnits(cps, 0, cps.length)) {
			pieces.add(new String(cps, unit[0], unit[1] - unit[0]));
		}
		return pieces;
	}

	private void updateDaylight(Minecraft mc, Camera camera, float dt) {
		ClientLevel level = mc.level;
		float target = 0;
		float sun = 0;
		if (level != null && SkyEffects.overworldSky(level)) {
			float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
			float height = SkyEffects.sunHeight(level, camera, partial);
			if (camera.getFluidInCamera() == FogType.NONE) {
				target = daylight(height, level.getRainLevel(partial));
			}
			sun = sunUp(height);
		}
		sunlight = approach(sunlight, sun, dt * 2f);
		daylight = approach(daylight, Math.max(target, BACKDROP * backdrop), dt * 2f);
	}

	/** A painted sky this bright at full strength makes the lyrics stand out as they do by day: darker shadow, dark halo. */
	static final float BACKDROP = 0.8f;

	/** Set by the sky each frame: how strongly a bright painted sky shows, 0 to 1. */
	public void backdrop(float strength) {
		backdrop = Mth.clamp(strength, 0, 1);
	}

	/**
	 * How much the sun is up (sunHeight -1 midnight, 1 noon): 1 from about a third of the way up the sky, 0 from just
	 * below the horizon, eased in between. It is the old clear-weather daylight (star brightness before 1.21.11, turned
	 * upside down), so clear skies look as they always did.
	 */
	static float sunUp(float sunHeight) {
		float s = Mth.clamp(2 * sunHeight + 0.25f, 0, 1);
		return 1 - (1 - s) * (1 - s);
	}

	/**
	 * 1 under a clear day sky, 0 at night; rain greys the day sky so it counts half. Follows the sun, not the stars
	 * (those are dimmed by rain). A light glow vanishes against a bright sky, so by day the lyrics get a dark halo instead.
	 */
	static float daylight(float sunHeight, float rainLevel) {
		return sunUp(sunHeight) * (1 - 0.5f * Mth.clamp(rainLevel, 0, 1));
	}

	private void updateColor(SkyLyricsConfig cfg, float dt, long now, int line) {
		int target = switch (cfg.colorMode) {
			case ALBUM -> art.color();
			case FIXED -> cfg.color;
			case RAINBOW -> java.awt.Color.HSBtoRGB((now / 1e9f / 16f) % 1f, 0.55f, 1f) & 0xFFFFFF;
			case TIME -> timeOfDay(sunlight);
			case EACH_LINE -> lineColor(line);
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

	static final int NIGHT = 0xB3A1FF, TWILIGHT = 0xFFAD85, DAY = 0xF2F7FF;

	/** The Each line colours, in order: pink, cyan, lilac, mint, amber, blue. */
	static final int[] LINE_COLORS = {0xFF7AD9, 0x7AE7FF, 0xB79CFF, 0x8CFFB5, 0xFFC46B, 0x8FB8FF};

	/** The Each line colour for line {@code index}; before the first line it is the first colour. */
	static int lineColor(int index) {
		return LINE_COLORS[Math.floorMod(Math.max(index, 0), LINE_COLORS.length)];
	}

	/** The Time of day colour for {@code day} from 0 (night) through twilight (0.5) to 1 (full day). */
	static int timeOfDay(float day) {
		day = Mth.clamp(day, 0, 1);
		return day < 0.5f ? mix(NIGHT, TWILIGHT, day * 2) : mix(TWILIGHT, DAY, day * 2 - 1);
	}

	private int rgb() {
		return Math.round(cr * 255) << 16 | Math.round(cg * 255) << 8 | Math.round(cb * 255);
	}

	/** The smoothed lyrics colour as packed RGB. */
	public int color() {
		return rgb();
	}

	/** Seconds since the lyrics last moved to a new line, for things that flare up with them. */
	public float secondsSinceLineChange() {
		return Calm.flashSince(Calm.hideFlashes(), clock.flashSince(System.nanoTime()));
	}

	/** Like {@link #secondsSinceLineChange} but whatever the accessibility options say: when a flash would start (test hook). */
	public float secondsSinceFlashStart() {
		return clock.flashSince(System.nanoTime());
	}

	private void updateAnchor(SkyLyricsConfig cfg, Camera camera, float dt) {
		float camYaw = camera.yRot();
		float camPitch = camera.xRot();
		if (!Float.isFinite(camYaw) || !Float.isFinite(camPitch)) {
			return;
		}
		if (FollowAnchor.needsInit(anchorYaw, anchorPitch)) {
			anchorYaw = camYaw;
			anchorPitch = -cfg.height;
		}
		float targetYaw;
		float targetPitch;
		float speed;
		float[] lock = ViewLock.update(cfg, camYaw, camPitch);
		if (cfg.placement == Placement.FLOATING && lock == null) {
			targetYaw = camYaw;
			targetPitch = followPitch(true, camPitch, cfg.height);
			speed = 5f;
		} else if (lock != null) {
			float[] at = ViewLock.target(lock, cfg.direction, camYaw, cfg.height);
			targetYaw = at[0];
			targetPitch = at[1];
			speed = 3f;
		} else if (cfg.direction == SkyLyricsConfig.Direction.FOLLOW) {
			// Right where you look; lazy follow stays put while you look around a bit and catches up when you turn away
			following = FollowAnchor.following(following, cfg.lazyFollow, camYaw - anchorYaw);
			targetYaw = following ? camYaw : anchorYaw;
			targetPitch = -cfg.height;
			speed = FollowAnchor.speed(cfg.lazyFollow);
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

	/** The glow's colour around words of the given colour: a deeper shade of theirs, like neon, or the one picked for it. */
	private static int glowOf(int words) {
		SkyLyricsConfig cfg = SkyLyricsConfig.get();
		return cfg.glowPick ? cfg.glowColor : WorldWords.neon(words);
	}

	static int mix(int a, int b, float t) {
		t = Mth.clamp(t, 0, 1);
		int r = Math.round(Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF));
		int g = Math.round(Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF));
		int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
		return r << 16 | g << 8 | bl;
	}
}
