package dev.beli.skylyrics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.config.SkyLyricsConfig.Direction;
import dev.beli.skylyrics.config.SkyLyricsConfig.Placement;
import dev.beli.skylyrics.config.SkyLyricsConfig.SkyColors;
import dev.beli.skylyrics.config.SkyLyricsConfig.SkyEffect;
import dev.beli.skylyrics.lyrics.LyricLine;
import dev.beli.skylyrics.lyrics.Lyrics;
import dev.beli.skylyrics.lyrics.LyricsService;
import dev.beli.skylyrics.media.MediaTracker;
import dev.beli.skylyrics.media.Track;
import dev.beli.skylyrics.util.Guard;
import java.util.Objects;
import java.util.Random;
//? if >=26.1 {
/*import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
*///?} else {
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
//?}
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
//? if >=26.1 {
/*import net.minecraft.util.LightCoordsUtil;
*///?} else {
import net.minecraft.client.renderer.LightTexture;
//?}
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
//? if >=1.21.11 {
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.dimension.DimensionType.Skybox;
//?} else {
/*import net.minecraft.client.renderer.DimensionSpecialEffects;
*///?}
import net.minecraft.world.level.material.FogType;

/**
 * Night sky effects while music plays: a soft aurora or nebula (or both) tinted by the lyrics colour, a synthwave
 * sky with a striped retro sun, a moonlit night with drifting clouds, or an eclipse under a milky way, plus
 * constellations, shooting stars and sparkles
 * round the lyrics with any of them.
 * Drawn camera-relative through Minecraft's own pipelines (additive like the ender dragon's death rays, or plain
 * blending like name tag backgrounds), so it needs no shader support and works with Iris and Sodium.
 */
public final class SkyEffects {
	private static final float FADE_SECONDS = 1.5f;
	/** Star brightness of a full overworld night. */
	static final float NIGHT_STARS = 0.5f;
	private static final int TEAL = 0x3CFFB4;
	private static final int VIOLET = 0x7A4CFF;
	private static final int DEEP_BLUE = 0x2A58E8;
	private static final int MAGENTA = 0xD24CFF;
	private static final int RIBBONS = 3;
	/** Where the sparkles sit on 26.2 and later, in front of every cloud and curtain (see {@link #nearestLayer}). */
	static final float SPARKLE_FRONT = 0.78f;
	/** Where the shooting stars sit on 26.2 and later: in front of the sparkles too, as they burn up in the air. */
	static final float METEOR_FRONT = 0.76f;
	private static final int SEGMENTS = 80;
	private static final int BLOBS = 7;
	private static final int SLICES = 24;
	private static final int DOTS = 60;
	private static final int SPARKLES = 26;
	private static final int METEORS = 3;
	/** Shooting stars per second while the song is calm: about one every fifteen seconds. */
	static final float METEOR_RATE = 1 / 15f;
	private static final float DEG = (float) (Math.PI / 180);
	/** At the default strength the synthwave sky and the suns show fully; lower strengths fade them. */
	private static final float FULL_STRENGTH = 0.7f;
	/** Batch the sky is drawn in; lower batches are drawn first and the world's own things use 0. */
	private static final int SKY_ORDER = -1;
	/** With the lyrics following the view, the suns sit where the real sun went down: the west. */
	static final float FOLLOW_YAW = 90;
	/** Constellations sit in front of the far sky shapes (and behind the aurora and the lyrics). */
	private static final float CONSTELLATION_LAYER = 0.87f;

	private final LyricsRenderer lyrics;
	private final LyricsService service;
	private final AlbumArt art;
	private final MediaTracker tracker;
	private final SubmitNodeCollector.CustomGeometryRenderer drawer = this::draw;
	// Stored, not written as this::name where they are used, which would allocate a new object every frame
	private final SubmitNodeCollector.CustomGeometryRenderer painter = this::drawPainted;
	private final SubmitNodeCollector.CustomGeometryRenderer discDrawer = this::drawEclipseDisc;
	private final SubmitNodeCollector.CustomGeometryRenderer frontDrawer = this::drawFront;
	private final Random random = new Random();
	private float presence;
	private float clock;
	private long last;

	private final float[] qx = new float[4], qy = new float[4], qz = new float[4];
	private final int[] qc = new int[4];
	private int corner;

	private SkyEffect effect;
	private boolean starPatterns;
	// Basis of the spot being drawn: its centre, and right and up directions across the sky there
	private float bx, by, bz, rx, rz, ux, uy, uz;

	private boolean aurora, nebula, sparkles;
	private float sparkleShow, sparkleSpread;
	/** The star brightness the sky effects use: the real one eased, NaN to snap to the real one next frame. */
	private float skyStars = Float.NaN;
	private float radius;
	private float far;
	private float fade;
	/** With the sky pulse on: how bright the sky sits between lines, as a share of its full strength. */
	static final float BEAT_REST = 0.75f;
	/** 0..1: how much of the synthwave sky, the eclipse and the constellations shows. */
	private float amount;
	private float baseYaw;
	private float pixel;
	/** Where the camera looks this frame, so the painted skies can leave out what is off screen. */
	private final SkyView view = new SkyView();
	/** The world frame {@link #view} was last pointed for; see {@link #look}. */
	private long viewFrame = -1;
	private int tint;

	private final SynthwaveSky synthwave = new SynthwaveSky();
	private final MoonlitSky moonlit = new MoonlitSky();
	private final EclipseSky eclipse = new EclipseSky();
	private final GalaxySky galaxy = new GalaxySky();
	private final CometSky comet = new CometSky();
	private final Constellations constellations = new Constellations();
	private final Target light = new Target(false), paint = new Target(true);

	private float cloudAlpha;
	private int auroraLow, auroraHigh, starColor, meteorColor;
	private final int[] clouds = new int[3];
	private float albumR = -1, albumG, albumB;

	private float anchorYaw = Float.NaN, anchorPitch;
	private boolean following;

	private final float[] meteorYaw = new float[METEORS], meteorElev = new float[METEORS];
	private final float[] meteorAngle = new float[METEORS], meteorSpeed = new float[METEORS];
	private final float[] meteorLength = new float[METEORS];
	private final float[] meteorAge = new float[METEORS], meteorLife = new float[METEORS];
	private int meteorsAlive;

	/** You joined a world: forget the old anchor so the effects start where you look now (Lazy follow). */
	public void onJoinWorld() {
		resetAnchor();
	}

	void resetAnchor() {
		anchorYaw = Float.NaN;
		following = false;
	}

	boolean anchorNeedsInit() {
		return FollowAnchor.needsInit(anchorYaw, anchorPitch);
	}

	public SkyEffects(LyricsRenderer lyrics, LyricsService service, AlbumArt art, MediaTracker tracker) {
		this.lyrics = lyrics;
		this.service = service;
		this.art = art;
		this.tracker = tracker;
	}

	/**
	 * How bright the sky is for the pulse: steady at full strength with the pulse off, otherwise a little dimmer
	 * between lines and full for a moment as each one starts.
	 *
	 * @param since seconds since the lyrics moved to a new line, or NaN with the pulse off
	 * @param syncedShown whether synced lyrics are on show: with none (unsynced lyrics, no lyrics, an instrumental
	 *                    track, lyrics hidden) there is nothing to pulse with, so the sky stays at full strength.
	 *                    A break inside synced lyrics still counts as shown, so the sky doesn't jump there.
	 */
	static float beat(float since, boolean syncedShown) {
		return Float.isNaN(since) || !syncedShown ? 1 : BEAT_REST + (1 - BEAT_REST) * LyricsRenderer.pulse(since);
	}

	/**
	 * Moves the sky's star brightness towards the real one, so a time jump (/time set, a server skipping the night)
	 * fades over about half a second like the lyrics' daylight does, and normal dusk and dawn, which are slower, look
	 * as before. NaN {@code current} (nothing shown yet) snaps.
	 */
	static float easeStars(float current, float target, float dt) {
		if (!Float.isFinite(current)) {
			return target;
		}
		float step = dt * 2 * NIGHT_STARS;
		return current < target ? Math.min(target, current + step) : Math.max(target, current - step);
	}

	/** Whether the sparkles have just come back from off, so their anchor must snap to where the lyrics are now. */
	static boolean sparklesStarting(float before, float after) {
		return before <= 0 && after > 0;
	}

	/**
	 * How dark the night is (vanilla stars peak at {@link #NIGHT_STARS}) times clear weather times the user's
	 * strength, all 0..1.
	 */
	static float visibility(float starBrightness, float rainLevel, float strength) {
		return visibility(starBrightness, rainLevel, strength, false);
	}

	/**
	 * Like {@link #visibility(float, float, float)}; with {@code rainInStars} (the star value already dims with rain, as
	 * on 1.21.11 and newer) the rain level is not applied a second time.
	 */
	static float visibility(float starBrightness, float rainLevel, float strength, boolean rainInStars) {
		return clamp01(starBrightness / NIGHT_STARS) * (rainInStars ? 1 : 1 - clamp01(rainLevel)) * clamp01(strength);
	}

	/** The sky's visibility as the render loop uses it: {@link #visibility} with this version's {@link #STARS_DIM_WITH_RAIN}. */
	static float starVisibility(float starBrightness, float rainLevel, float strength) {
		return visibility(starBrightness, rainLevel, strength, STARS_DIM_WITH_RAIN);
	}

	/** Whether {@link #readStarBrightness} already fades the stars with rain and thunder (vanilla's environment attribute does). */
	static final boolean STARS_DIM_WITH_RAIN =
			//? if >=1.21.11 {
			true;
			//?} else {
			/*false;
			*///?}

	/** Whether vanilla pulls the sky fog in for a boss fight (Wither, Ender Dragon), which hides the sky effects. */
	private static boolean bossFog(Minecraft mc) {
		//? if >=26.2 {
		/*return mc.gui.hud.getBossOverlay().shouldCreateWorldFog();
		*///?} else {
		return mc.gui.getBossOverlay().shouldCreateWorldFog();
		//?}
	}

	/** Whether the sky should be on: wanted, not waiting for the old sky to fade out, and the camera seeing an open sky. */
	static boolean presenceTarget(boolean wanted, boolean swapping, boolean open) {
		return wanted && !swapping && open;
	}

	/** Vanilla draws no sky under Blindness or Darkness, so neither do we. */
	private static boolean skyBlocked(Minecraft mc) {
		return mc.getCameraEntity() instanceof LivingEntity living
				&& (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
	}

	/** Moves {@code presence} towards 1 (on) or 0 (off) so a full fade takes {@link #FADE_SECONDS}. */
	static float step(float presence, boolean on, float dt) {
		float next = presence + (on ? dt : -dt) / FADE_SECONDS;
		return clamp01(next);
	}

	static float smooth(float x) {
		float t = clamp01(x);
		return t * t * (3 - 2 * t);
	}

	/** Distance of the effect from the camera: well inside the render-distance fog. */
	static float radius(int renderDistanceChunks) {
		return Math.min(renderDistanceChunks * 16 * 0.45f, 110f);
	}

	/**
	 * Distance of the sky-wide shapes: just short of where the render-distance fog starts, so hills
	 * nearer than that still stand in front of the synthwave sky and the suns.
	 */
	static float farRadius(int renderDistanceChunks) {
		float blocks = renderDistanceChunks * 16;
		float fogStart = blocks - Mth.clamp(blocks / 10, 4, 64);
		return fogStart * 0.9f;
	}

	/** The skies Random picks from. */
	static final SkyEffect[] RANDOM_SKIES = {SkyEffect.AURORA, SkyEffect.NEBULA, SkyEffect.BOTH, SkyEffect.SYNTHWAVE,
			SkyEffect.ECLIPSE, SkyEffect.MOONLIGHT, SkyEffect.GALAXY, SkyEffect.COMET};

	/** The sky to draw: the chosen one, or for Random one picked by the song, so it holds for the whole song. */
	static SkyEffect pick(SkyEffect chosen, Track track) {
		if (chosen != SkyEffect.RANDOM) {
			return chosen;
		}
		int seed = track == null ? 0 : Objects.hash(track.title(), track.artist());
		return RANDOM_SKIES[Math.floorMod(seed * 0x9E3779B9, RANDOM_SKIES.length)];
	}

	/** Whether the sky shown has to fade out before another one (Random's pick for a new song, or a changed setting) can fade in. */
	static boolean waitsForFade(SkyEffect shown, SkyEffect next, float presence) {
		return shown != null && next != shown && presence > 0;
	}

	/** Compass direction the lyrics face, which the suns and constellations are placed around. */
	static float baseYaw(Direction direction) {
		return direction == Direction.FOLLOW ? FOLLOW_YAW : direction.yaw;
	}

	/** Ribbon ends fade out over the first and last 18% of the arc. */
	static float endFade(float u) {
		return smooth(u / 0.18f) * smooth((1 - u) / 0.18f);
	}

	static int mix(int a, int b, float t) {
		float k = clamp01(t);
		int r = Math.round(((a >> 16 & 255) * (1 - k)) + ((b >> 16 & 255) * k));
		int g = Math.round(((a >> 8 & 255) * (1 - k)) + ((b >> 8 & 255) * k));
		int bl = Math.round(((a & 255) * (1 - k)) + ((b & 255) * k));
		return r << 16 | g << 8 | bl;
	}

	/**
	 * Shooting stars per second: a gentle base rate, up to three times as many while lyric lines rush by.
	 * {@code lineSeconds} is how long the current line lasts, 0 for none or an instrumental break.
	 */
	static float meteorRate(float lineSeconds) {
		if (lineSeconds <= 0) {
			return METEOR_RATE;
		}
		return METEOR_RATE * (1 + 2 * clamp01((5 - lineSeconds) / 3));
	}

	/** A sparkle's brightness, 0..1: mostly dim, with a short sharp glint. */
	static float glint(float wave) {
		float w = 0.5f + 0.5f * wave;
		return w * w * w;
	}

	/** A shooting star fades in fast and out more slowly over its {@code life}. */
	static float meteorEnvelope(float age, float life) {
		return smooth(age / 0.12f) * smooth((life - age) / 0.35f);
	}

	private static float clamp01(float v) {
		return v < 0 ? 0 : Math.min(v, 1);
	}

	private static int argb(int rgb, float alpha) {
		return Math.round(clamp01(alpha) * 255) << 24 | rgb;
	}

	//? if >=26.1 {
	/*public void render(LevelRenderContext context) {
		Guard.run("sky effects", () -> {
			PoseStack.Pose mark = context.poseStack().last();
			try {
				draw(context);
			} catch (RuntimeException | LinkageError e) {
				LyricsRenderer.unwindTo(context.poseStack(), mark);
				throw e;
			}
		});
	}

	private void draw(LevelRenderContext context) {
	*///?} else {
	public void render(WorldRenderContext context) {
		Guard.run("sky effects", () -> {
			PoseStack.Pose mark = context.matrices().last();
			try {
				draw(context);
			} catch (RuntimeException | LinkageError e) {
				LyricsRenderer.unwindTo(context.matrices(), mark);
				throw e;
			}
		});
	}

	private void draw(WorldRenderContext context) {
	//?}
		Minecraft mc = Minecraft.getInstance();
		SkyLyricsConfig cfg = SkyLyricsConfig.get();
		long now = System.nanoTime();
		float dt = last == 0 ? 0 : Math.min(0.1f, (now - last) / 1e9f);
		last = now;
		lyrics.backdrop(0);
		SkyTint.painted(0, 0);
		boolean wanted = cfg.sky != SkyEffect.OFF || cfg.constellations || cfg.shootingStars || cfg.twinkle;
		SkyEffect next = pick(cfg.sky, tracker.track());
		// A new sky (a new song's random pick or a changed setting) waits for the old one to fade out
		boolean swapping = waitsForFade(effect, next, presence);
		ClientLevel level = mc.level;
		//? if >=26.2 {
		/*Camera camera = mc.gameRenderer.mainCamera();
		*///?} else {
		Camera camera = mc.gameRenderer.getMainCamera();
		//?}
		// The nether, the end, water and a blinding effect count as off, so the sky fades back in when they end
		boolean open = level != null && overworldSky(level) && camera.getFluidInCamera() == FogType.NONE
				&& !skyBlocked(mc) && !bossFog(mc);
		boolean on = presenceTarget(wanted && cfg.enabled && tracker.playing() && !Visibility.hidden(mc, cfg), swapping, open);
		presence = step(presence, on, dt);
		// Swap once the old sky is gone; it has to happen here, as nothing below runs while the sky is hidden
		if (!swapping || presence <= 0) {
			effect = next;
		}
		if (presence <= 0 || !wanted || !open) {
			skyStars = Float.NaN;
			return;
		}
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		skyStars = easeStars(skyStars, starBrightness(level, camera, partial), dt);
		boolean syncedShown = cfg.skyPulse && lyrics.visible() && syncedLyricsShown();
		fade = starVisibility(skyStars, level.getRainLevel(partial), cfg.skyStrength) * smooth(presence)
				* beat(cfg.skyPulse ? lyrics.secondsSinceLineChange() : Float.NaN, syncedShown);
		if (fade < 0.01f) {
			return;
		}
		clock += dt * cfg.skySpeed.factor;
		starPatterns = cfg.constellations;
		radius = radius(mc.options.getEffectiveRenderDistance());
		far = farRadius(mc.options.getEffectiveRenderDistance());
		amount = clamp01(fade / FULL_STRENGTH);
		lyrics.backdrop(painted() ? amount : 0);
		if (painted()) {
			SkyTint.painted(effect == SkyEffect.MOONLIGHT ? MoonlitSky.skyColor(SkyTint.WORDS_ELEVATION)
					: SynthwaveSky.skyColor(SkyTint.WORDS_ELEVATION), amount);
		}
		baseYaw = baseYaw(cfg.direction);
		// One screen pixel as a tangent of its angle, so lines stay about a pixel wide at any size
		// from the live view, which a spyglass, Speed or sprinting changes, and not from the slider
		pixel = pixelAngle(ScreenLayer.tanHalfV(), mc.options.fov().get(), mc.getWindow().getHeight());
		tint = lyrics.color();
		radius = radius(mc.options.getEffectiveRenderDistance());
		aurora = effect == SkyEffect.AURORA || effect == SkyEffect.BOTH;
		nebula = effect == SkyEffect.NEBULA || effect == SkyEffect.BOTH;
		// Under an aurora the clouds step back so the two together don't wash out
		cloudAlpha = aurora ? 0.55f : 1;
		// Sparkles gather round the lyrics, so they come and go with them
		float sparkleBefore = sparkleShow;
		sparkleShow = step(sparkleShow, cfg.twinkle && lyrics.visible(), dt);
		sparkles = sparkleShow > 0;
		updateColors(cfg, dt);
		if (sparkles) {
			// The anchor isn't followed while sparkles are off: start from where the lyrics hang now, not a stale spot
			if (sparklesStarting(sparkleBefore, sparkleShow)) {
				resetAnchor();
			}
			updateAnchor(cfg, camera, dt);
			// A bad first camera yaw leaves no anchor yet: draw no sparkles rather than ones at NaN
			sparkles = !anchorNeedsInit();
		}
		if (sparkles) {
			sparkleSpread = Mth.clamp(cfg.size, 0.6f, 1.6f) * (cfg.placement == Placement.SKY ? 1 : 0.8f);
		}
		updateMeteors(cfg, dt);
		if (effect == SkyEffect.OFF && !starPatterns && !sparkles && meteorsAlive == 0) {
			return;
		}

		// The synthwave sky has to cover the vanilla one and the eclipse disc has to be black, so those
		// blend normally; the rest adds light. Each render type gets its shapes far to near, so the
		// layers stack the same way whether or not the pipeline sorts them.
		// The whole sky goes in an earlier batch than the lyrics: shapes are drawn after text in a batch,
		// and see-through text leaves no depth behind, so an opaque sky drawn later would paint over it.
		// The added light leaves no depth behind either: it sits inside the world (at most 110 blocks out), and
		// glass, water and ice are drawn after it, so marking its depth would cut holes in any of them further away.
		//? if >=26.1 {
		/*OrderedSubmitNodeCollector sky = context.submitNodeCollector().order(SKY_ORDER);
		PoseStack pose = context.poseStack();
		*///?} else {
		OrderedSubmitNodeCollector sky = context.commandQueue().order(SKY_ORDER);
		PoseStack pose = context.matrices();
		//?}
		pose.pushPose();
		if (painted()) {
			sky.submitCustomGeometry(pose, RenderTypes.textBackground(), painter);
		}
		if (!painted() || sparkles || meteorsAlive > 0) {
			sky.submitCustomGeometry(pose, RenderTypes.dragonRays(), drawer);
		}
		if (effect == SkyEffect.ECLIPSE) {
			sky.submitCustomGeometry(pose, RenderTypes.textBackground(), discDrawer);
			// Shooting stars and sparkles come after the disc, or it would black them out where they cross the sun
			if (sparkles || meteorsAlive > 0) {
				sky.submitCustomGeometry(pose, RenderTypes.dragonRays(), frontDrawer);
			}
		}
		pose.popPose();
	}

	/** True for the skies that cover the vanilla one: synthwave and moonlight. */
	private boolean painted() {
		return effect == SkyEffect.SYNTHWAVE || effect == SkyEffect.MOONLIGHT;
	}

	/**
	 * Points {@link #view} at where the camera looked this frame. The three draw callbacks of a frame all want the same
	 * answer (it only changes when the world frame captures a new camera), so it is worked out for the first one only.
	 */
	private SkyView look() {
		long frame = ScreenLayer.frames();
		if (frame != viewFrame) {
			ScreenLayer.lookInto(view);
			viewFrame = frame;
		}
		return view;
	}

	/** Normally blended: the synthwave or moonlit sky and its constellations. */
	private void drawPainted(PoseStack.Pose pose, VertexConsumer vc) {
		paint.begin(pose, vc);
		look();
		if (effect == SkyEffect.MOONLIGHT) {
			moonlit.draw(paint, far, baseYaw, amount, clock, pixel, view);
		} else {
			synthwave.draw(paint, far, baseYaw, amount, clock, pixel, view);
		}
		if (starPatterns) {
			constellations.draw(paint, far * CONSTELLATION_LAYER, baseYaw, amount, clock, pixel, view);
		}
	}

	private void drawEclipseDisc(PoseStack.Pose pose, VertexConsumer vc) {
		paint.begin(pose, vc);
		eclipse.drawDisc(paint, far, baseYaw, amount, look());
	}


	private void updateColors(SkyLyricsConfig cfg, float dt) {
		tint = lyrics.color();
		if (cfg.skyColors == SkyColors.ALBUM) {
			// The cover's colour jumps when the song changes, so ease towards it
			int target = art.color();
			float k = albumR < 0 ? 1 : 1 - (float) Math.exp(-dt * 2.5f);
			albumR = Math.max(albumR, 0) + ((target >> 16 & 255) - Math.max(albumR, 0)) * k;
			albumG += ((target >> 8 & 255) - albumG) * k;
			albumB += ((target & 255) - albumB) * k;
			tint = Math.round(albumR) << 16 | Math.round(albumG) << 8 | Math.round(albumB);
		}
		float follow = cfg.skyColors == SkyColors.CLASSIC ? 0 : SkyPalette.follow(tint);
		int teal = SkyPalette.recolor(TEAL, TEAL, tint, follow);
		int violet = SkyPalette.recolor(VIOLET, TEAL, tint, follow);
		auroraLow = mix(mix(teal, tint, 0.3f), teal, 0.35f);
		auroraHigh = mix(mix(violet, tint, 0.5f), auroraLow, 0.2f);
		for (int k = 0; k < clouds.length; k++) {
			int base = k == 0 ? VIOLET : k == 1 ? DEEP_BLUE : MAGENTA;
			clouds[k] = mix(SkyPalette.recolor(base, VIOLET, tint, follow), tint, 0.4f);
		}
		starColor = mix(0xFFFFFF, tint, 0.35f);
		meteorColor = mix(0xFFFFFF, tint, 0.2f);
	}

	/**
	 * One screen pixel as a tangent of its angle: twice the tangent of half the vertical field of view, over the window's
	 * height. {@code tanHalfV} comes from the projection the world was drawn with this frame, so it follows a spyglass,
	 * Speed and sprinting; with none captured yet (0) the field of view slider stands in.
	 */
	static float pixelAngle(float tanHalfV, double sliderFovDeg, int windowHeight) {
		float tan = tanHalfV > 0 && !Float.isInfinite(tanHalfV) ? tanHalfV : (float) Math.tan(sliderFovDeg * DEG / 2);
		return 2 * tan / Math.max(1, windowHeight);
	}

	/** Where the lyrics hang, by the same rules as {@link LyricsRenderer}, so the sparkles can gather round. */
	private void updateAnchor(SkyLyricsConfig cfg, Camera camera, float dt) {
		float camYaw = camera.yRot();
		if (!Float.isFinite(camYaw) || !Float.isFinite(camera.xRot())) {
			return;
		}
		if (FollowAnchor.needsInit(anchorYaw, anchorPitch)) {
			anchorYaw = camYaw;
			anchorPitch = -cfg.height;
		}
		float targetYaw;
		float targetPitch = -cfg.height;
		float speed;
		float[] lock = ViewLock.update(cfg, camYaw, camera.xRot());
		if (lock != null) {
			targetYaw = lock[0];
			targetPitch = lock[1];
			speed = 3f;
		} else if (cfg.placement == Placement.FLOATING) {
			targetYaw = camYaw;
			targetPitch = Mth.clamp(camera.xRot() - Math.min(cfg.height, 30) * 0.5f, -85, 85);
			speed = 5f;
		} else if (cfg.direction == Direction.FOLLOW) {
			boolean lazy = FollowAnchor.lazy(cfg.lazyFollow, cfg.layout);
			following = FollowAnchor.following(following, lazy, camYaw - anchorYaw);
			targetYaw = following ? camYaw : anchorYaw;
			speed = FollowAnchor.speed(lazy);
		} else {
			targetYaw = cfg.direction.yaw;
			speed = 3f;
		}
		float k = dt == 0 || Float.isInfinite(speed) ? 1 : 1 - (float) Math.exp(-dt * speed);
		anchorYaw += Mth.wrapDegrees(targetYaw - anchorYaw) * k;
		anchorPitch += (targetPitch - anchorPitch) * k;
	}

	private void updateMeteors(SkyLyricsConfig cfg, float dt) {
		meteorsAlive = 0;
		int free = -1;
		for (int i = 0; i < METEORS; i++) {
			meteorAge[i] = cfg.shootingStars ? meteorAge[i] + dt : meteorLife[i];
			if (meteorAge[i] < meteorLife[i]) {
				meteorsAlive++;
			} else {
				free = i;
			}
		}
		// Roll against the busiest rate first, so the lyrics are only looked at on the rare frames that might launch
		float busiest = 3 * METEOR_RATE;
		if (free >= 0 && cfg.shootingStars && random.nextFloat() < busiest * dt
				&& random.nextFloat() * busiest < meteorRate(lineSeconds(cfg))) {
			launch(free);
			meteorsAlive++;
		}
	}

	/** Whether the playing song has synced lyrics to show (not unsynced, none, or an instrumental track). */
	private boolean syncedLyricsShown() {
		Lyrics shown = service.snapshot().forSong(tracker.track()).lyrics();
		return shown != null && shown.synced() && !shown.isEmpty();
	}

	/** How long the line being sung lasts, or 0 between lines and without synced lyrics. */
	private float lineSeconds(SkyLyricsConfig cfg) {
		Track playing = tracker.track();
		Lyrics shown = service.snapshot().forSong(playing).lyrics();
		if (shown == null || !shown.synced() || shown.isEmpty()) {
			return 0;
		}
		int index = shown.indexAt(tracker.positionMs() + cfg.offsetFor(playing) + LyricsRenderer.LEAD_MS);
		LyricLine line = shown.get(index);
		if (line == null || line.isBreak()) {
			return 0;
		}
		return (shown.endOf(index, line.timeMs() + 5000) - line.timeMs()) / 1000f;
	}

	private void launch(int i) {
		meteorYaw[i] = random.nextFloat() * Mth.TWO_PI;
		meteorElev[i] = (42 + random.nextFloat() * 28) * DEG;
		// Falling at a slant, to the left or the right
		float slant = (0.12f + random.nextFloat() * 0.3f) * Mth.PI;
		meteorAngle[i] = random.nextBoolean() ? -slant : slant - Mth.PI;
		meteorSpeed[i] = (20 + random.nextFloat() * 10) * DEG;
		meteorLength[i] = (7 + random.nextFloat() * 5) * DEG;
		meteorLife[i] = 0.6f + random.nextFloat() * 0.5f;
		meteorAge[i] = 0;
	}

	/** Added light, far to near: the eclipse, galaxy or comet, the constellations, the nebula and aurora, sparkles, shooting stars. */
	private void draw(PoseStack.Pose pose, VertexConsumer vc) {
		light.begin(pose, vc);
		look();
		if (effect == SkyEffect.ECLIPSE) {
			eclipse.draw(light, far, baseYaw, amount, clock, view);
		}
		if (effect == SkyEffect.GALAXY) {
			galaxy.draw(light, far, baseYaw, amount, clock, view);
		}
		if (effect == SkyEffect.COMET) {
			comet.draw(light, far, baseYaw, amount, clock, view);
		}
		if (starPatterns && !painted()) {
			constellations.draw(light, far * CONSTELLATION_LAYER, baseYaw, amount, clock, pixel, view);
		}
		//? if >=26.2 {
		/*// 26.2 on: the added-light pipeline writes depth, so a nearer shape drawn first hides every farther one drawn
		// after it (a nebula cloud cutting off the aurora). Draw the stars, then the clouds and curtains far to near,
		// merged by their distances (blob: 0.97 - 0.025 k, ribbon: 1 - 0.07 k, the same as in blob() and ribbon()).
		if (nebula) {
			dots(pose, vc);
		}
		int cloud = nebula ? 0 : BLOBS, curtain = aurora ? 0 : RIBBONS;
		while (cloud < BLOBS || curtain < RIBBONS) {
			if (curtain < RIBBONS && (cloud >= BLOBS || ribbonFraction(curtain) >= blobFraction(cloud))) {
				ribbon(pose, vc, curtain++);
			} else {
				blob(pose, vc, cloud++);
			}
		}
		*///?} else {
		if (nebula) {
			for (int k = 0; k < BLOBS; k++) {
				blob(pose, vc, k);
			}
			dots(pose, vc);
		}
		if (aurora) {
			for (int k = 0; k < RIBBONS; k++) {
				ribbon(pose, vc, k);
			}
		}
		//?}
		if (effect != SkyEffect.ECLIPSE) {
			drawFront(pose, vc);
		}
	}

	/** The sparkles and shooting stars, the nearest layers; the eclipse draws them on their own after its disc. */
	private void drawFront(PoseStack.Pose pose, VertexConsumer vc) {
		if (sparkles) {
			sparkles(pose, vc);
		}
		if (meteorsAlive > 0) {
			meteors(pose, vc);
		}
	}

	/** Hands the sky shapes' corners to one render type's vertex buffer for this frame. */
	private static final class Target extends SkyCanvas {
		/**
		 * The normally blended type takes quads and a light level (full bright keeps the colours as given); the
		 * added-light one takes triangles, so its corners wait until a quad is complete.
		 */
		private final boolean lit;
		private final float[] tx = new float[4], ty = new float[4], tz = new float[4];
		private final int[] tc = new int[4];
		private int corner;
		/** Set by {@link #begin} before any corner arrives. */
		private PoseStack.Pose pose;
		private VertexConsumer vc;

		Target(boolean lit) {
			this.lit = lit;
		}

		void begin(PoseStack.Pose pose, VertexConsumer vc) {
			this.pose = pose;
			this.vc = vc;
		}

		@Override
		protected void put(float x, float y, float z, int argb) {
			if (lit) {
				//? if >=26.1 {
				/*vc.addVertex(pose, x, y, z).setColor(argb).setLight(LightCoordsUtil.FULL_BRIGHT);
				*///?} else {
				vc.addVertex(pose, x, y, z).setColor(argb).setLight(LightTexture.FULL_BRIGHT);
				//?}
				return;
			}
			tx[corner] = x;
			ty[corner] = y;
			tz[corner] = z;
			tc[corner] = argb;
			if (++corner == 4) {
				corner = 0;
				triangles(vc, pose, tx, ty, tz, tc, FRONT);
			}
		}
	}

	/** Corner orders splitting a quad into the two triangles the added-light type takes, as wound, and turned round. */
	private static final int[] FRONT = {0, 1, 2, 0, 2, 3}, BACK = {3, 2, 1, 3, 1, 0};

	private static void triangles(VertexConsumer vc, PoseStack.Pose pose, float[] x, float[] y, float[] z, int[] c, int[] order) {
		for (int k : order) {
			vc.addVertex(pose, x[k], y[k], z[k]).setColor(c[k]);
		}
	}

	private void vertex(VertexConsumer vc, PoseStack.Pose pose, float yaw, float elev, float r, int color) {
		float h = r * Mth.cos(elev);
		pointFront(vc, pose, h * Mth.sin(yaw), r * Mth.sin(elev), -h * Mth.cos(yaw), color);
	}

	/** A curtain's distance as a share of the sky radius: the first is the farthest. */
	static float ribbonFraction(int k) {
		return 1 - 0.07f * k;
	}

	/** A nebula cloud's distance as a share of the sky radius: the first is the farthest. */
	static float blobFraction(int k) {
		return 0.97f - 0.025f * k;
	}

	/** The share of the sky radius of the nearest cloud or curtain. */
	static float nearestLayer() {
		return Math.min(blobFraction(BLOBS - 1), ribbonFraction(RIBBONS - 1));
	}

	/** Where along the sky (radians, drawn as (sin, ., -cos)) a curtain's column {@code u} (0..1) stands. */
	static float ribbonYaw(float t, int k, float u) {
		float centre = (k * 2.1f + 0.6f) + t * 0.004f * (k % 2 == 0 ? 1 : -1);
		float span = (100 + 20 * k) * DEG;
		return centre + (u - 0.5f) * span + 0.07f * Mth.sin(t * 0.05f + u * 5 + k);
	}

	/** The height (radians) of a curtain's foot at column {@code u}. */
	static float ribbonBase(float t, int k, float u) {
		float baseElev = (27 + 9 * k) * DEG;
		return baseElev + 4 * DEG * Mth.sin(t * 0.07f + u * 6 + k * 2);
	}

	/** The height (radians) of a curtain's top at column {@code u}, given its foot {@code base}. */
	static float ribbonTop(float t, int k, float u, float base) {
		return base + (22 + 6 * Mth.sin(t * 0.09f + u * 9 + k)) * DEG;
	}

	/**
	 * Whether a curtain segment between two columns can be on screen. The curtain is drawn at (sin yaw, ., -cos yaw), a
	 * direction whose Minecraft yaw (0 = south, +x = -sin) is that yaw plus half a turn, so the angles are turned
	 * before they meet the view.
	 */
	static boolean ribbonVisible(SkyView view, float yawA, float yawB, float lowElev, float highElev) {
		float lo = (float) Math.toDegrees(Math.min(yawA, yawB)) + 180, hi = (float) Math.toDegrees(Math.max(yawA, yawB)) + 180;
		return view.yawVisible(lo, hi) && view.heightVisible((float) Math.toDegrees(lowElev), (float) Math.toDegrees(highElev));
	}

	/** Where a nebula cloud sits, as the yaw and height (radians) of its middle, drawn the same way as the curtains. */
	static float blobYaw(float t, int k) {
		return k * 0.9f + 0.3f + t * 0.003f;
	}

	static float blobElev(float t, int k) {
		return (38 + 14 * Mth.sin(k * 1.7f + 1)) * DEG + 2 * DEG * Mth.sin(t * 0.04f + k);
	}

	/** The widest a cloud's edge wobbles out: 1 + 0.2 + 0.1 + 0.05. */
	static final float BLOB_WOBBLE_MAX = 1.35f;

	/** Whether a cloud whose edge reaches {@code size} (of {@code dist} away) from its middle can be on screen. */
	static boolean blobVisible(SkyView view, float dx, float dy, float dz, float size, float dist) {
		// A little over the true reach, so rounding never drops a cloud that just touches the view
		return view.capVisible(dx, dy, dz, (float) Math.atan(size * BLOB_WOBBLE_MAX / dist) * 1.02f);
	}

	/** The direction (a unit vector) of nebula star {@code i}, and how far (radians) its quad reaches from it. */
	static float dotYaw(int i) {
		return i * 2.399963f;
	}

	static float dotElev(int i) {
		return (22 + 40 * (Mth.sin(i * 12.9898f) * 0.5f + 0.5f)) * DEG;
	}

	/** A nebula star's quad is a hundredth of its distance across, so this holds it with room to spare. */
	static final float DOT_REACH = 0.02f;

	static boolean dotVisible(SkyView view, float yaw, float elev) {
		float c = Mth.cos(elev);
		return view.capVisible(c * Mth.sin(yaw), Mth.sin(elev), -c * Mth.cos(yaw), DOT_REACH);
	}

	private void ribbon(PoseStack.Pose pose, VertexConsumer vc, int k) {
		float t = clock;
		float r = radius * ribbonFraction(k);
		int low = auroraLow;
		int high = auroraHigh;
		float peak = 0.85f * fade;
		float prevYaw = 0, prevBase = 0, prevTop = 0, prevB = 0;
		for (int i = 0; i <= SEGMENTS; i++) {
			float u = (float) i / SEGMENTS;
			float yaw = ribbonYaw(t, k, u);
			float base = ribbonBase(t, k, u);
			float top = ribbonTop(t, k, u, base);
			float ripple = 0.55f + 0.45f * Mth.sin(t * 0.5f - u * 8 + k * 1.7f);
			// Fine vertical rays drifting along the curtain
			float rays = 0.72f + 0.28f * Mth.sin(u * 70 + t * 0.35f + k) * Mth.sin(u * 23 - t * 0.2f);
			float b = peak * ripple * rays * endFade(u);
			// A segment the camera can't see is skipped, but the values the next one starts from still move on
			if (i > 0 && ribbonVisible(view, prevYaw, yaw, Math.min(prevBase, base), Math.max(prevTop, top))) {
				float mid0 = prevBase + (prevTop - prevBase) * 0.14f;
				float mid1 = base + (top - base) * 0.14f;
				int foot = argb(low, 0);
				int lowC0 = argb(low, prevB), lowC1 = argb(low, b);
				int topC = argb(high, 0);
				vertex(vc, pose, prevYaw, prevBase, r, foot);
				vertex(vc, pose, yaw, base, r, foot);
				vertex(vc, pose, yaw, mid1, r, lowC1);
				vertex(vc, pose, prevYaw, mid0, r, lowC0);
				vertex(vc, pose, prevYaw, mid0, r, lowC0);
				vertex(vc, pose, yaw, mid1, r, lowC1);
				vertex(vc, pose, yaw, top, r, topC);
				vertex(vc, pose, prevYaw, prevTop, r, topC);
			}
			prevYaw = yaw;
			prevBase = base;
			prevTop = top;
			prevB = b;
		}
	}

	private void blob(PoseStack.Pose pose, VertexConsumer vc, int k) {
		float t = clock;
		// The clouds lie along a wavy band around the sky, like a galaxy; far ones are drawn first
		float yaw = blobYaw(t, k);
		float elev = blobElev(t, k);
		float dist = radius * blobFraction(k);
		float cosE = Mth.cos(elev);
		float dx = cosE * Mth.sin(yaw), dy = Mth.sin(elev), dz = -cosE * Mth.cos(yaw);
		float ux = -dz, uz = dx;
		float ul = Mth.sqrt(ux * ux + uz * uz);
		ux /= ul;
		uz /= ul;
		float vx = dy * uz, vy = dz * ux - dx * uz, vz = -dy * ux;
		float cx = dx * dist, cy = dy * dist, cz = dz * dist;
		float size = dist * (0.36f + 0.08f * Mth.sin(k * 1.9f)) * (1 + 0.06f * Mth.sin(t * 0.12f + k));
		if (!blobVisible(view, dx, dy, dz, size, dist)) {
			return;
		}
		int hue = clouds[k % 3];
		float alpha = 0.7f * fade * cloudAlpha * (0.8f + 0.2f * Mth.sin(t * 0.15f + k * 2));
		int centreC = argb(mix(hue, 0xFFFFFF, 0.15f), alpha);
		int midC = argb(mix(hue, DEEP_BLUE, 0.3f), alpha * 0.45f);
		int edgeC = argb(hue, 0);
		float ph = t * 0.05f + k;
		float prevMx = 0, prevMy = 0, prevMz = 0, prevEx = 0, prevEy = 0, prevEz = 0;
		for (int i = 0; i <= SLICES; i++) {
			float a = (float) i / SLICES * Mth.TWO_PI;
			float wob = 1 + 0.2f * Mth.sin(3 * a + ph) + 0.1f * Mth.sin(5 * a - ph * 1.3f + k) + 0.05f * Mth.sin(9 * a + k);
			float ca = Mth.cos(a), sa = Mth.sin(a);
			float lx = ca * ux + sa * vx, ly = sa * vy, lz = ca * uz + sa * vz;
			float mx = cx + lx * size * 0.5f * wob, my = cy + ly * size * 0.5f * wob, mz = cz + lz * size * 0.5f * wob;
			float ex = cx + lx * size * wob, ey = cy + ly * size * wob, ez = cz + lz * size * wob;
			//? if >=26.2 {
			/*// Slide each corner along its line of sight onto the sphere the cloud's centre is on. It looks the same from
			// the middle, but the cloud no longer cuts through the aurora's sphere, so the depth the 26.2 pipeline writes
			// can hide a farther layer only where a nearer one really lies in front of it
			float toSphere = dist / Mth.sqrt(mx * mx + my * my + mz * mz);
			mx *= toSphere;
			my *= toSphere;
			mz *= toSphere;
			toSphere = dist / Mth.sqrt(ex * ex + ey * ey + ez * ez);
			ex *= toSphere;
			ey *= toSphere;
			ez *= toSphere;
			*///?}
			if (i > 0) {
				pointFront(vc, pose, cx, cy, cz, centreC);
				pointFront(vc, pose, cx, cy, cz, centreC);
				pointFront(vc, pose, mx, my, mz, midC);
				pointFront(vc, pose, prevMx, prevMy, prevMz, midC);
				pointFront(vc, pose, prevMx, prevMy, prevMz, midC);
				pointFront(vc, pose, mx, my, mz, midC);
				pointFront(vc, pose, ex, ey, ez, edgeC);
				pointFront(vc, pose, prevEx, prevEy, prevEz, edgeC);
			}
			prevMx = mx;
			prevMy = my;
			prevMz = mz;
			prevEx = ex;
			prevEy = ey;
			prevEz = ez;
		}
	}

	private void dots(PoseStack.Pose pose, VertexConsumer vc) {
		float t = clock;
		int white = starColor;
		for (int i = 0; i < DOTS; i++) {
			float yaw = dotYaw(i);
			float elev = dotElev(i);
			if (!dotVisible(view, yaw, elev)) {
				continue;
			}
			float tw = 0.5f + 0.5f * Mth.sin(t * (0.6f + (i % 5) * 0.15f) + i * 3.1f);
			int c = argb(white, 0.55f * fade * tw);
			float s = 0.5f * radius / 100f;
			float h = radius * Mth.cos(elev);
			float x = h * Mth.sin(yaw), y = radius * Mth.sin(elev), z = -h * Mth.cos(yaw);
			float rx = Mth.cos(yaw) * s, rz = Mth.sin(yaw) * s;
			pointFront(vc, pose, x - rx, y - s, z - rz, c);
			pointFront(vc, pose, x + rx, y - s, z + rz, c);
			pointFront(vc, pose, x + rx, y + s, z + rz, c);
			pointFront(vc, pose, x - rx, y + s, z - rz, c);
		}
	}

	/** Soft four-pointed sparkles scattered round the lyrics, each glinting now and then. */
	private void sparkles(PoseStack.Pose pose, VertexConsumer vc) {
		float t = clock;
		float r = radius * 0.92f;
		//? if >=26.2 {
		/*// The added-light pipeline writes depth here, so a nearer cloud would hide a sparkle: put them in front of all
		r = radius * SPARKLE_FRONT;
		*///?}
		float centreYaw = (anchorYaw + 180) * DEG, centreElev = -anchorPitch * DEG;
		int clear = argb(starColor, 0);
		for (int i = 0; i < SPARKLES; i++) {
			float tw = glint(Mth.sin(t * (0.8f + (i % 7) * 0.13f) + i * 2.7f));
			float alpha = fade * smooth(sparkleShow) * tw;
			if (alpha < 0.01f) {
				continue;
			}
			// A golden-angle spiral in a wide oval, kept clear of the middle where the words are
			float a = i * 2.399963f;
			float ring = (0.45f + 0.55f * Mth.sqrt((i + 0.5f) / SPARKLES)) * sparkleSpread;
			float elev = centreElev + Mth.sin(a) * ring * 20 * DEG;
			float yaw = centreYaw + Mth.cos(a) * ring * 62 * DEG / Math.max(0.4f, Mth.cos(elev));
			basis(yaw, elev, r);
			float s = r * 0.011f * (0.55f + 0.45f * tw) * (0.75f + 0.25f * Mth.sin(i * 7.3f));
			float arm = s * 2.4f, thin = s * 0.22f;
			int c = argb(starColor, alpha);
			at(vc, pose, -arm, 0, clear);
			at(vc, pose, 0, thin, c);
			at(vc, pose, arm, 0, clear);
			at(vc, pose, 0, -thin, c);
			at(vc, pose, 0, -arm, clear);
			at(vc, pose, thin, 0, c);
			at(vc, pose, 0, arm, clear);
			at(vc, pose, -thin, 0, c);
			int core = argb(starColor, alpha * 0.6f);
			at(vc, pose, -thin * 2.5f, 0, core);
			at(vc, pose, 0, thin * 2.5f, core);
			at(vc, pose, thin * 2.5f, 0, core);
			at(vc, pose, 0, -thin * 2.5f, core);
		}
	}

	/** Each shooting star is one tapered quad: a bright head and a tail that fades to nothing. */
	private void meteors(PoseStack.Pose pose, VertexConsumer vc) {
		float r = radius * 0.95f;
		//? if >=26.2 {
		/*// The added-light pipeline writes depth here, so a nearer cloud or curtain would cut a streak: put them in front of all
		r = radius * METEOR_FRONT;
		*///?}
		// A share of their distance, so they look as wide wherever they sit
		float w = r * 0.003f;
		int clear = argb(meteorColor, 0);
		for (int i = 0; i < METEORS; i++) {
			float age = meteorAge[i];
			if (age >= meteorLife[i]) {
				continue;
			}
			float alpha = fade * meteorEnvelope(age, meteorLife[i]);
			float ca = Mth.cos(meteorAngle[i]), sa = Mth.sin(meteorAngle[i]);
			float stretch = 1 / Math.max(0.3f, Mth.cos(meteorElev[i]));
			float head = age * meteorSpeed[i];
			float tail = Math.max(0, head - meteorLength[i]);
			basis(meteorYaw[i] + ca * tail * stretch, meteorElev[i] + sa * tail, r);
			float tx = bx, ty = by, tz = bz;
			basis(meteorYaw[i] + ca * head * stretch, meteorElev[i] + sa * head, r);
			int c = argb(meteorColor, alpha);
			at(vc, pose, -sa * w, ca * w, c);
			at(vc, pose, ca * w * 2.5f, sa * w * 2.5f, c);
			at(vc, pose, sa * w, -ca * w, c);
			point(vc, pose, tx, ty, tz, clear);
		}
	}

	/** Sets the spot on the sky sphere ({@code yaw}, {@code elev}, distance {@code r}) that {@link #at} draws round. */
	private void basis(float yaw, float elev, float r) {
		float sy = Mth.sin(yaw), cy = Mth.cos(yaw), se = Mth.sin(elev), ce = Mth.cos(elev);
		bx = r * ce * sy;
		by = r * se;
		bz = -r * ce * cy;
		rx = cy;
		rz = sy;
		ux = -se * sy;
		uy = ce;
		uz = se * cy;
	}

	/** A corner {@code right} and {@code up} away from the spot set by {@link #basis}. */
	private void at(VertexConsumer vc, PoseStack.Pose pose, float right, float up, int color) {
		point(vc, pose, bx + rx * right + ux * up, by + uy * up, bz + rz * right + uz * up, color);
	}

	/** Collects four corners, then emits the quad twice (both windings) as triangles, as the pipeline culls back faces. */
	private void point(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, int color) {
		corner(x, y, z, color);
		if (corner < 4) {
			return;
		}
		corner = 0;
		triangles(vc, pose, qx, qy, qz, qc, FRONT);
		triangles(vc, pose, qx, qy, qz, qc, BACK);
	}

	/**
	 * Like {@link #point}, but emits only the winding that faces the camera at the origin. Only for shapes whose corners
	 * run counter-clockwise seen from the origin (the curtains, clouds and stars): the other winding is always culled.
	 */
	private void pointFront(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, int color) {
		corner(x, y, z, color);
		if (corner < 4) {
			return;
		}
		corner = 0;
		triangles(vc, pose, qx, qy, qz, qc, FRONT);
	}

	private void corner(float x, float y, float z, int color) {
		qx[corner] = x;
		qy[corner] = y;
		qz[corner] = z;
		qc[corner] = color;
		corner++;
	}

	/** Whether the level has the normal overworld sky, with a sun, moon and stars */
	static boolean overworldSky(ClientLevel level) {
		//? if >=1.21.11 {
		return level.dimensionType().skybox() == Skybox.OVERWORLD;
		//?} else {
		/*return level.effects().skyType() == DimensionSpecialEffects.SkyType.OVERWORLD;
		*///?}
	}

	/**
	 * How bright the stars are: 0 in the day, highest on a clear night. The sky and the lyrics both ask every frame,
	 * so the last answer is kept for as long as everything it comes from is the same (level, tick and partial tick,
	 * camera and where it is). Render thread only.
	 */
	static float starBrightness(ClientLevel level, Camera camera, float partial) {
		var at = camera.position();
		long tick = level.getGameTime();
		if (starsLevel == level && starsCamera == camera && starsTick == tick && starsPartial == partial
				&& starsX == at.x && starsY == at.y && starsZ == at.z) {
			return starsValue;
		}
		float value = readStarBrightness(level, camera, partial);
		starsLevel = level;
		starsCamera = camera;
		starsTick = tick;
		starsPartial = partial;
		starsX = at.x;
		starsY = at.y;
		starsZ = at.z;
		starsValue = value;
		return value;
	}

	private static ClientLevel starsLevel;
	private static Camera starsCamera;
	private static long starsTick;
	private static float starsPartial, starsValue;
	private static double starsX, starsY, starsZ;

	/** Lets go of the kept level and camera, so leaving a world doesn't keep it alive. */
	static void forgetStars() {
		starsLevel = null;
		starsCamera = null;
	}

	private static float readStarBrightness(ClientLevel level, Camera camera, float partial) {
		//? if >=1.21.11 {
		return camera.attributeProbe().getValue(EnvironmentAttributes.STAR_BRIGHTNESS, partial);
		//?} else {
		/*return level.getStarBrightness(partial);
		*///?}
	}
}
