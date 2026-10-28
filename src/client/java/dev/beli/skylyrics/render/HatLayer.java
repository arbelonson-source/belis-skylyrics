package dev.beli.skylyrics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.beli.skylyrics.SkyLyricsClient;
import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.config.SkyLyricsConfig.Hat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
//? if <1.21.9 {
/*import net.minecraft.client.renderer.MultiBufferSource;
*///?}
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * A hat on your own player, seen in third person (F5): a halo, neon headphones, a conical hat, a crown, cat ears, a pumpkin head or a top
 * hat. It's only drawn
 * here, so other players never see it. Its glowing parts take the lyrics colour and flare up whenever a new line starts.
 */
public final class HatLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final int SEGMENTS = 40;
	/** Head space is in blocks with y pointing down; the top of the head is half a block above the neck. */
	private static final float HEAD_TOP = -0.5f;
	private static final float HALO_LIFT = 0.17f, HALO_RADIUS = 0.27f, HALO_TUBE = 0.022f, HALO_SHINE = 0.11f;
	/** How bright the hat sits between flares, and how much a flare adds. */
	static final float REST = 0.7f, FLARE = 0.3f;
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final int DARK = 0xFF24242E, STRAW = 0xFFD8B878, STRAW_DARK = 0xFFC6A462, STRAW_TIP = 0xFFE6CC92;
	private static final int GOLD = 0xFFF2C14E, GOLD_DARK = 0xFFC08A2A, GOLD_LIGHT = 0xFFFFE08A;
	/** Ear cups sit just outside the head (half a block wide), around ear height. */
	private static final float CUP_IN = 0.27f, CUP_OUT = 0.33f, CUP_Y = -0.23f, CUP_HALF = 0.11f, BAND_TOP = -0.6f;
	static final float CONE_BRIM = 0.5f, CONE_BASE = HEAD_TOP + 0.05f, CONE_APEX = HEAD_TOP - 0.32f;
	private static final int CROWN_POINTS = 8;
	/**
	 * Music notes: how many circle the head, how far out and at what height (head space, y down), how fast they go round
	 * (radians a second), bob up and down and rock, and how much they swell when a line starts.
	 */
	private static final int NOTES = 3;
	private static final float NOTE_RADIUS = 0.42f, NOTE_Y = HEAD_TOP + 0.16f, NOTE_SPREAD = 0.13f, NOTE_DEPTH = 0.014f;
	static final float NOTE_ORBIT = 0.6f, NOTE_BOB = 0.035f, NOTE_BOB_SPEED = 2.2f, NOTE_SWAY = 0.2f, NOTE_SWAY_SPEED = 1.6f;
	private static final float NOTE_POP = 0.2f;
	/** The notes' clock starts over after this many seconds, when every one of their motions has come full circle. */
	static final double NOTE_LOOP = 10 * Math.PI;
	private static final int EAR_DARK = 0xFF24202E;
	private static final float PUMPKIN_HALF = 0.32f, PUMPKIN_TOP = HEAD_TOP - 0.07f, PUMPKIN_BOTTOM = 0.07f;
	private static final int RIBS = 4, GRIN_COLUMNS = 5;
	/** The top hat sits just above the head's outer layer; its brim is wide enough to clear that layer's corners. */
	static final float TOP_HAT_BRIM_Y = HEAD_TOP - 0.035f, TOP_HAT_HEIGHT = 0.38f, TOP_HAT_RADIUS = 0.27f, TOP_HAT_BRIM = 0.43f;
	private static final int SILK = 0xFF3A3A4A;
	/** Each horn starts inside the head's outer layer and curls from leaning out to just past straight up. */
	static final float HORN_X = 0.14f, HORN_BASE = HEAD_TOP - 0.01f, HORN_Z = -0.06f, HORN_LENGTH = 0.3f, HORN_RADIUS = 0.06f;
	private static final float HORN_LEAN = 40, HORN_CURL = 105;
	static final int HORN_STEPS = 8;
	private static final int HORN_SIDES = 10, HORN = 0xFF8A1622, HORN_DARK = 0xFF5A0E18;
	/**
	 * Shades: each lens's inner and outer edge at the top and bottom (head space, x out from the middle, y down), how
	 * far in front of the face they sit, the rim round them and where the arms run back along the head.
	 */
	static final float LENS_IN = 0.03f, LENS_OUT = 0.225f, LENS_IN_LOW = 0.05f, LENS_OUT_LOW = 0.185f;
	static final float LENS_TOP = HEAD_TOP + 0.19f, LENS_BOTTOM = HEAD_TOP + 0.325f, LENS_Z = -0.295f, RIM = 0.014f;
	private static final float ARM_X = 0.29f, ARM_BACK = 0.12f;
	private static final int LENS = 0xFF101018;
	private static final int ORANGE = 0xFFE3801F, ORANGE_DARK = 0xFFC9661A, STEM = 0xFF4F6A2B;

	/** One plain white patch; on 26.3 the hat's colours are painted from it so they still darken at night. */
	private static final Identifier WHITE = SkyLyricsClient.id("textures/misc/white.png");

	private final LyricsRenderer lyrics;

	public HatLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, LyricsRenderer lyrics) {
		super(parent);
		this.lyrics = lyrics;
	}

	/**
	 * Plain colour that takes the light where you stand. 26.3 dropped the untextured lit type, so there the colours are
	 * text drawn from a white patch, which the light still reaches.
	 */
	private static RenderType paint() {
		//? if >=26.3 {
		/*return RenderTypes.text(WHITE);
		*///?} else {
		return RenderTypes.textBackground();
		//?}
	}

	//? if >=1.21.9 {
	@Override
	public void submit(PoseStack pose, SubmitNodeCollector out, int light, AvatarRenderState state, float yaw, float pitch) {
	//?} else {
	/*@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AvatarRenderState state, float yaw, float pitch) {
		SubmitNodeCollector out = new SubmitNodeCollector(buffers);
	*///?}
		SkyLyricsConfig cfg = SkyLyricsConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (cfg.hat == Hat.NONE || state.isInvisible || mc.player == null || state.id != mc.player.getId()) {
			return;
		}
		float bright = brightness(cfg.hatPulse ? lyrics.secondsSinceLineChange() : Float.NaN);
		int rgb = lyrics.color();
		int glow = 0xFF000000 | LyricsRenderer.mix(rgb, 0xFFFFFF, 0.55f * bright);
		pose.pushPose();
		//? if >=1.21.9 {
		getParentModel().root().translateAndRotate(pose);
		getParentModel().translateToHead(pose);
		//?} else {
		/*getParentModel().getHead().translateAndRotate(pose);
		*///?}
		switch (cfg.hat) {
			case HALO -> {
				pose.translate(0, HEAD_TOP - HALO_LIFT, 0);
				out.submitCustomGeometry(pose, paint(), (p, vc) -> tube(p, vc, HALO_RADIUS, HALO_TUBE, 0, glow, FULL_BRIGHT));
				int shine = Math.round(150 * bright) << 24 | rgb;
				out.submitCustomGeometry(pose, RenderTypes.lightning(), (p, vc) -> shine(p, vc, HALO_RADIUS, HALO_SHINE, shine));
			}
			case HEADPHONES -> {
				out.submitCustomGeometry(pose, paint(), (p, vc) -> headphones(p, vc, light, glow));
				for (int side = -1; side <= 1; side += 2) {
					pose.pushPose();
					pose.translate(side * (CUP_OUT + 0.003f), CUP_Y, 0);
					pose.mulPose(Axis.ZP.rotationDegrees(90));
					out.submitCustomGeometry(pose, paint(), (p, vc) -> tube(p, vc, 0.065f, 0.011f, 0, glow, FULL_BRIGHT));
					pose.popPose();
				}
			}
			case CONICAL -> out.submitCustomGeometry(pose, paint(), (p, vc) -> conical(p, vc, light, glow));
			case CROWN -> out.submitCustomGeometry(pose, paint(), (p, vc) -> crown(p, vc, light, glow));
			case CAT_EARS -> out.submitCustomGeometry(pose, paint(), (p, vc) -> catEars(p, vc, light, glow));
			case PUMPKIN -> out.submitCustomGeometry(pose, paint(), (p, vc) -> pumpkin(p, vc, light, glow));
			case TOP_HAT -> out.submitCustomGeometry(pose, paint(), (p, vc) -> topHat(p, vc, light, glow));
			case DEVIL_HORNS -> out.submitCustomGeometry(pose, paint(), (p, vc) -> horns(p, vc, light, glow));
			case SHADES -> out.submitCustomGeometry(pose, paint(), (p, vc) -> shades(p, vc, light, glow));
			case MUSIC_NOTES -> {
				float t = seconds();
				// With Glow with the lyrics on, the notes swell a little as each new line starts
				float pop = 1 + NOTE_POP * (bright - REST) / FLARE;
				for (int i = 0; i < NOTES; i++) {
					boolean pair = i % 2 == 1;
					float phase = i * Mth.TWO_PI / NOTES;
					pose.pushPose();
					pose.translate(0, NOTE_Y + NOTE_BOB * Mth.sin(t * NOTE_BOB_SPEED + phase * 2), 0);
					pose.mulPose(Axis.YP.rotation(t * NOTE_ORBIT + phase));
					pose.translate(0, 0, NOTE_RADIUS);
					pose.mulPose(Axis.ZP.rotation(NOTE_SWAY * Mth.sin(t * NOTE_SWAY_SPEED + phase)));
					pose.scale(pop, pop, pop);
					out.submitCustomGeometry(pose, paint(), (p, vc) -> note(p, vc, pair, glow));
					pose.popPose();
				}
			}
			case NONE -> { }
		}
		pose.popPose();
	}

	/**
	 * How bright the hat is: steady at rest, flaring for a moment after a new line starts.
	 *
	 * @param since seconds since the lyrics moved to a new line, or NaN to stay steady
	 */
	static float brightness(float since) {
		return Float.isNaN(since) ? REST + FLARE / 2 : REST + FLARE * LyricsRenderer.pulse(since);
	}

	/** A ring of square tube lying flat at height {@code y}, both sides of every face so it shows from any angle. */
	private static void tube(PoseStack.Pose p, VertexConsumer vc, float radius, float half, float y, int argb, int light) {
		for (int i = 0; i < SEGMENTS; i++) {
			float a0 = i * Mth.TWO_PI / SEGMENTS, a1 = (i + 1) * Mth.TWO_PI / SEGMENTS;
			float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
			float in = radius - half, out = radius + half;
			quad(p, vc, light, argb, argb, c0, s0, c1, s1, in, out, y - half, y - half); // top
			quad(p, vc, light, argb, argb, c0, s0, c1, s1, in, out, y + half, y + half); // bottom
			quad(p, vc, light, argb, argb, c0, s0, c1, s1, out, out, y - half, y + half); // outside
			quad(p, vc, light, argb, argb, c0, s0, c1, s1, in, in, y - half, y + half); // inside
		}
	}

	/** A soft light round the ring: a flat band and an upright one, bright at the ring and gone at their edges. */
	private static void shine(PoseStack.Pose p, VertexConsumer vc, float radius, float reach, int argb) {
		int clear = argb & 0xFFFFFF;
		for (int i = 0; i < SEGMENTS; i++) {
			float a0 = i * Mth.TWO_PI / SEGMENTS, a1 = (i + 1) * Mth.TWO_PI / SEGMENTS;
			float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
			quad(p, vc, FULL_BRIGHT, argb, clear, c0, s0, c1, s1, radius, radius - reach, 0, 0);
			quad(p, vc, FULL_BRIGHT, argb, clear, c0, s0, c1, s1, radius, radius + reach, 0, 0);
			quad(p, vc, FULL_BRIGHT, argb, clear, c0, s0, c1, s1, radius, radius, 0, -reach * 0.6f);
			quad(p, vc, FULL_BRIGHT, argb, clear, c0, s0, c1, s1, radius, radius, 0, reach * 0.6f);
		}
	}

	/**
	 * One slice of a ring between two angles, from (r0, y0) to (r1, y1) across, drawn both ways round. The colour runs
	 * from {@code from} at the first edge to {@code to} at the second.
	 */
	private static void quad(PoseStack.Pose p, VertexConsumer vc, int light, int from, int to, float c0, float s0, float c1,
			float s1, float r0, float r1, float y0, float y1) {
		face(p, vc, light, from, to, c0 * r0, y0, s0 * r0, c1 * r0, y0, s1 * r0, c1 * r1, y1, s1 * r1, c0 * r1, y1, s0 * r1);
	}

	/** Four corners (x, y, z each), drawn both ways round; the first two take {@code from}, the last two {@code to}. */
	private static void face(PoseStack.Pose p, VertexConsumer vc, int light, int from, int to, float... xyz) {
		for (int k = 0; k < 4; k++) {
			vc.addVertex(p, xyz[k * 3], xyz[k * 3 + 1], xyz[k * 3 + 2]).setColor(k < 2 ? from : to).setUv(0.5f, 0.5f).setLight(light);
		}
		for (int k = 3; k >= 0; k--) {
			vc.addVertex(p, xyz[k * 3], xyz[k * 3 + 1], xyz[k * 3 + 2]).setColor(k < 2 ? from : to).setUv(0.5f, 0.5f).setLight(light);
		}
	}

	/** A box between two corners, all one colour. */
	private static void box(PoseStack.Pose p, VertexConsumer vc, int light, int argb, float x0, float y0, float z0, float x1,
			float y1, float z1) {
		face(p, vc, light, argb, argb, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
		face(p, vc, light, argb, argb, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		face(p, vc, light, argb, argb, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		face(p, vc, light, argb, argb, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
		face(p, vc, light, argb, argb, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		face(p, vc, light, argb, argb, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
	}

	/** Dark ear cups joined by a band over the head, with a neon line along the band. The rings on the cups come apart. */
	private static void headphones(PoseStack.Pose p, VertexConsumer vc, int light, int glow) {
		for (int side = -1; side <= 1; side += 2) {
			box(p, vc, light, DARK, side * CUP_IN, CUP_Y - CUP_HALF, -CUP_HALF * 0.9f, side * CUP_OUT, CUP_Y + CUP_HALF, CUP_HALF * 0.9f);
		}
		float rx = (CUP_IN + CUP_OUT) / 2, ry = CUP_Y - BAND_TOP, t = 0.03f, w = 0.035f, line = 0.012f;
		int n = SEGMENTS / 2;
		for (int i = 0; i < n; i++) {
			float[] a = bandPoint(i * Mth.PI / n, rx, ry), b = bandPoint((i + 1) * Mth.PI / n, rx, ry);
			float ax = a[0], ay = a[1], bx = b[0], by = b[1];
			float ox0 = ax + a[2] * t, oy0 = ay + a[3] * t, ox1 = bx + b[2] * t, oy1 = by + b[3] * t;
			face(p, vc, light, DARK, DARK, ax, ay, -w, bx, by, -w, bx, by, w, ax, ay, w);
			face(p, vc, light, DARK, DARK, ox0, oy0, -w, ox1, oy1, -w, ox1, oy1, w, ox0, oy0, w);
			face(p, vc, light, DARK, DARK, ax, ay, -w, bx, by, -w, ox1, oy1, -w, ox0, oy0, -w);
			face(p, vc, light, DARK, DARK, ax, ay, w, bx, by, w, ox1, oy1, w, ox0, oy0, w);
			float e = t + 0.003f;
			face(p, vc, FULL_BRIGHT, glow, glow, ax + a[2] * e, ay + a[3] * e, -line, bx + b[2] * e, by + b[3] * e, -line,
					bx + b[2] * e, by + b[3] * e, line, ax + a[2] * e, ay + a[3] * e, line);
		}
	}

	/** A point on the headband (x, y) and its outward direction (x, y), {@code a} running from one ear (0) to the other (pi). */
	static float[] bandPoint(float a, float rx, float ry) {
		float c = Mth.cos(a), s = Mth.sin(a);
		float nx = c / rx, ny = -s / ry, len = Mth.sqrt(nx * nx + ny * ny);
		return new float[] {rx * c, CUP_Y - ry * s, nx / len, ny / len};
	}

	/** A wide straw cone with a glowing rim and a glowing band. */
	private static void conical(PoseStack.Pose p, VertexConsumer vc, int light, int glow) {
		for (int i = 0; i < SEGMENTS; i++) {
			float a0 = i * Mth.TWO_PI / SEGMENTS, a1 = (i + 1) * Mth.TWO_PI / SEGMENTS;
			float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
			int straw = i % 2 == 0 ? STRAW : STRAW_DARK;
			quad(p, vc, light, straw, STRAW_TIP, c0, s0, c1, s1, CONE_BRIM, 0, CONE_BASE, CONE_APEX);
			float y0 = CONE_BASE - 0.07f, y1 = CONE_BASE - 0.1f;
			quad(p, vc, FULL_BRIGHT, glow, glow, c0, s0, c1, s1, coneRadius(y0) + 0.004f, coneRadius(y1) + 0.004f, y0, y1);
		}
		tube(p, vc, CONE_BRIM, 0.01f, CONE_BASE, glow, FULL_BRIGHT);
	}

	/** How wide the conical hat is at height {@code y}: the full brim at its base, nothing at the tip. */
	static float coneRadius(float y) {
		return CONE_BRIM * Mth.clamp((y - CONE_APEX) / (CONE_BASE - CONE_APEX), 0, 1);
	}

	/** A gold band with points round the top and a glowing gem under each point. */
	private static void crown(PoseStack.Pose p, VertexConsumer vc, int light, int glow) {
		float r = 0.275f, bottom = HEAD_TOP + 0.03f, top = HEAD_TOP - 0.09f, tip = HEAD_TOP - 0.21f;
		for (int i = 0; i < SEGMENTS; i++) {
			float a0 = i * Mth.TWO_PI / SEGMENTS, a1 = (i + 1) * Mth.TWO_PI / SEGMENTS;
			quad(p, vc, light, GOLD_DARK, GOLD, Mth.cos(a0), Mth.sin(a0), Mth.cos(a1), Mth.sin(a1), r, r, bottom, top);
		}
		float half = Mth.TWO_PI / CROWN_POINTS * 0.4f, gemY = (bottom + top) / 2, gemH = 0.032f, gemA = gemH / r, gr = r + 0.004f;
		for (int k = 0; k < CROWN_POINTS; k++) {
			float a = k * Mth.TWO_PI / CROWN_POINTS, l = a - half, rt = a + half;
			face(p, vc, light, GOLD, GOLD_LIGHT, Mth.cos(l) * r, top, Mth.sin(l) * r, Mth.cos(rt) * r, top, Mth.sin(rt) * r,
					Mth.cos(a) * r, tip, Mth.sin(a) * r, Mth.cos(a) * r, tip, Mth.sin(a) * r);
			face(p, vc, FULL_BRIGHT, glow, glow, Mth.cos(a) * gr, gemY - gemH, Mth.sin(a) * gr,
					Mth.cos(a + gemA) * gr, gemY, Mth.sin(a + gemA) * gr, Mth.cos(a) * gr, gemY + gemH, Mth.sin(a) * gr,
					Mth.cos(a - gemA) * gr, gemY, Mth.sin(a - gemA) * gr);
		}
	}

	/** One cat ear, side -1 or 1: base from x0 to x1 on top of the head, tip at (tipX, tipY), leaning out a little. */
	static float[] ear(float side) {
		float c = side * 0.15f;
		return new float[] {c - 0.09f, c + 0.09f, c + side * 0.035f, HEAD_TOP - 0.2f};
	}

	/** Two dark cat ears with glowing insides. */
	private static void catEars(PoseStack.Pose p, VertexConsumer vc, int light, int glow) {
		float base = HEAD_TOP + 0.01f, z = 0.02f;
		for (float side : new float[] {-1, 1}) {
			float[] e = ear(side);
			face(p, vc, light, EAR_DARK, EAR_DARK, e[0], base, z, e[1], base, z, e[2], e[3], z, e[2], e[3], z);
			// The inside, a smaller triangle just in front
			float in = 0.045f, mid = (e[0] + e[1]) / 2;
			face(p, vc, FULL_BRIGHT, glow, glow, e[0] + in, base - 0.015f, z - 0.004f, e[1] - in, base - 0.015f, z - 0.004f,
					Mth.lerp(0.25f, e[2], mid), e[3] + 0.06f, z - 0.004f, Mth.lerp(0.25f, e[2], mid), e[3] + 0.06f, z - 0.004f);
		}
	}

	/** A tall black top hat with a silky sheen, a wide brim and a glowing band round it. */
	private static void topHat(PoseStack.Pose p, VertexConsumer vc, int light, int glow) {
		float brim = TOP_HAT_BRIM_Y, top = brim - TOP_HAT_HEIGHT, r = TOP_HAT_RADIUS;
		for (int i = 0; i < SEGMENTS; i++) {
			float a0 = i * Mth.TWO_PI / SEGMENTS, a1 = (i + 1) * Mth.TWO_PI / SEGMENTS;
			float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
			quad(p, vc, light, DARK, SILK, c0, s0, c1, s1, r, r, brim, top); // side
			quad(p, vc, light, SILK, SILK, c0, s0, c1, s1, r, 0, top, top); // lid
			quad(p, vc, light, DARK, DARK, c0, s0, c1, s1, TOP_HAT_BRIM, r, brim, brim); // brim
			quad(p, vc, FULL_BRIGHT, glow, glow, c0, s0, c1, s1, r + 0.004f, r + 0.004f, brim - 0.025f, brim - 0.08f); // band
		}
		tube(p, vc, TOP_HAT_BRIM, 0.008f, brim, glow, FULL_BRIGHT);
	}

	/**
	 * Dark sunglasses: each lens a little wider at the top, set on a glowing rim just behind it so only the edge shows,
	 * with a soft streak of light across the glass, a glowing bridge and thin arms back over the ears.
	 */
	private static void shades(PoseStack.Pose p, VertexConsumer vc, int light, int glow) {
		float rimZ = LENS_Z + 0.004f, shineZ = LENS_Z - 0.002f, r = RIM;
		int shine = 0x60000000 | LyricsRenderer.mix(glow & 0xFFFFFF, 0xFFFFFF, 0.6f);
		for (int side = -1; side <= 1; side += 2) {
			float in = side * LENS_IN, out = side * LENS_OUT, inLow = side * LENS_IN_LOW, outLow = side * LENS_OUT_LOW;
			face(p, vc, FULL_BRIGHT, glow, glow, in - side * r, LENS_TOP - r, rimZ, out + side * r, LENS_TOP - r, rimZ,
					outLow + side * r, LENS_BOTTOM + r, rimZ, inLow - side * r, LENS_BOTTOM + r, rimZ);
			face(p, vc, light, LENS, LENS, in, LENS_TOP, LENS_Z, out, LENS_TOP, LENS_Z, outLow, LENS_BOTTOM, LENS_Z, inLow,
					LENS_BOTTOM, LENS_Z);
			// The streak, leaning the same way on both lenses like light from one side
			float a = Mth.lerp(0.35f, in, out), w = 0.03f, drop = 0.045f;
			face(p, vc, FULL_BRIGHT, shine, shine, a, LENS_TOP + 0.012f, shineZ, a + w, LENS_TOP + 0.012f, shineZ,
					a + w - drop, LENS_BOTTOM - 0.012f, shineZ, a - drop, LENS_BOTTOM - 0.012f, shineZ);
			// A hinge out to the side of the head, then the arm back over the ear
			float x0 = Math.min(out, side * ARM_X), x1 = Math.max(out, side * ARM_X);
			box(p, vc, FULL_BRIGHT, glow, x0, LENS_TOP - r, rimZ - 0.004f, x1, LENS_TOP + 0.012f, rimZ + 0.006f);
			float a0 = side * ARM_X, a1 = side * (ARM_X + 0.012f);
			box(p, vc, light, LENS, Math.min(a0, a1), LENS_TOP - r, rimZ, Math.max(a0, a1), LENS_TOP + 0.01f, ARM_BACK);
		}
		box(p, vc, FULL_BRIGHT, glow, -LENS_IN, LENS_TOP - r, rimZ - 0.002f, LENS_IN, LENS_TOP + 0.012f, rimZ + 0.004f);
	}

	/**
	 * Seconds on a steady clock, for things that move on their own. It starts over at a moment when the notes are back
	 * where they began, so the restart never shows as a jump, and stays small enough for smooth float maths.
	 */
	private static float seconds() {
		return (float) (System.nanoTime() / 1e9 % NOTE_LOOP);
	}

	/**
	 * One glowing note standing flat to the side as it circles: an eighth note with its flag, or two joined by a beam.
	 * The head is two crossed boxes so it reads round rather than square.
	 */
	private static void note(PoseStack.Pose p, VertexConsumer vc, boolean pair, int glow) {
		float d = NOTE_DEPTH, top = -0.24f;
		int heads = pair ? 2 : 1;
		for (int h = 0; h < heads; h++) {
			float x = (h - (heads - 1) / 2f) * NOTE_SPREAD;
			box(p, vc, FULL_BRIGHT, glow, x - 0.045f, -0.025f, -d, x + 0.03f, 0.005f, d);
			box(p, vc, FULL_BRIGHT, glow, x - 0.035f, -0.035f, -d, x + 0.02f, 0.015f, d);
			box(p, vc, FULL_BRIGHT, glow, x + 0.012f, top, -d, x + 0.03f, -0.02f, d);
		}
		if (pair) {
			box(p, vc, FULL_BRIGHT, glow, -NOTE_SPREAD / 2 + 0.012f, top, -d, NOTE_SPREAD / 2 + 0.03f, top + 0.035f, d);
		} else {
			box(p, vc, FULL_BRIGHT, glow, 0.03f, top, -d, 0.07f, top + 0.03f, d);
			box(p, vc, FULL_BRIGHT, glow, 0.055f, top + 0.02f, -d, 0.085f, top + 0.09f, d);
		}
	}

	/** Two ridged red horns that curl up from the top of the head, their tips glowing. */
	private static void horns(PoseStack.Pose p, VertexConsumer vc, int light, int glow) {
		for (float side : new float[] {-1, 1}) {
			float[][] h = horn(side);
			for (int j = 0; j < HORN_STEPS; j++) {
				float[] a = h[j], b = h[j + 1];
				boolean tip = j >= HORN_STEPS - 2;
				int from = tip ? glow : j % 2 == 0 ? HORN : HORN_DARK;
				int to = j + 1 >= HORN_STEPS - 2 ? glow : (j + 1) % 2 == 0 ? HORN : HORN_DARK;
				for (int i = 0; i < HORN_SIDES; i++) {
					float k0 = i * Mth.TWO_PI / HORN_SIDES, k1 = (i + 1) * Mth.TWO_PI / HORN_SIDES;
					face(p, vc, tip ? FULL_BRIGHT : light, from, to, ring(a, k0, 0), ring(a, k0, 1), ring(a, k0, 2),
							ring(a, k1, 0), ring(a, k1, 1), ring(a, k1, 2), ring(b, k1, 0), ring(b, k1, 1), ring(b, k1, 2),
							ring(b, k0, 0), ring(b, k0, 1), ring(b, k0, 2));
				}
			}
		}
	}

	/**
	 * The middle of one horn from base to tip, as x, y, z, radius and the way it points (sideways, up) at each step.
	 * {@code side} is -1 for the left horn and 1 for the right.
	 */
	static float[][] horn(float side) {
		float[][] points = new float[HORN_STEPS + 1][];
		float x = side * HORN_X, y = HORN_BASE, z = HORN_Z, step = HORN_LENGTH / HORN_STEPS;
		for (int j = 0; j <= HORN_STEPS; j++) {
			float t = (float) j / HORN_STEPS, angle = Mth.lerp(t, HORN_LEAN, HORN_CURL) * Mth.DEG_TO_RAD;
			float out = side * Mth.cos(angle), up = Mth.sin(angle);
			points[j] = new float[] {x, y, z, HORN_RADIUS * (1 - t) + 0.006f, out, up};
			x += out * step;
			y -= up * step;
		}
		return points;
	}

	/** One coordinate of the point at angle {@code k} round a horn's ring, square to the way the horn points there. */
	private static float ring(float[] c, float k, int axis) {
		float across = Mth.cos(k) * c[3], deep = Mth.sin(k) * c[3];
		return switch (axis) {
			case 0 -> c[0] + across * c[5];
			case 1 -> c[1] + across * c[4];
			default -> c[2] + deep;
		};
	}

	/** A carved pumpkin over the whole head, ribbed in two oranges, with a stem and a face that glows. */
	private static void pumpkin(PoseStack.Pose p, VertexConsumer vc, int light, int glow) {
		float h = PUMPKIN_HALF, top = PUMPKIN_TOP, bottom = PUMPKIN_BOTTOM;
		for (int i = 0; i < RIBS; i++) {
			float a = -h + 2 * h * i / RIBS, b = -h + 2 * h * (i + 1) / RIBS;
			int c = i % 2 == 0 ? ORANGE : ORANGE_DARK;
			face(p, vc, light, c, c, a, top, -h, b, top, -h, b, bottom, -h, a, bottom, -h);
			face(p, vc, light, c, c, a, top, h, b, top, h, b, bottom, h, a, bottom, h);
			face(p, vc, light, c, c, -h, top, a, -h, top, b, -h, bottom, b, -h, bottom, a);
			face(p, vc, light, c, c, h, top, a, h, top, b, h, bottom, b, h, bottom, a);
			face(p, vc, light, c, c, a, top, -h, b, top, -h, b, top, h, a, top, h);
		}
		face(p, vc, light, ORANGE_DARK, ORANGE_DARK, -h, bottom, -h, h, bottom, -h, h, bottom, h, -h, bottom, h);
		box(p, vc, light, STEM, -0.035f, top - 0.09f, -0.035f, 0.035f, top, 0.035f);
		// The face, carved into the front (the side facing -z)
		float z = -h - 0.004f;
		for (float side : new float[] {-1, 1}) {
			float x0 = side * 0.05f, x1 = side * 0.18f;
			face(p, vc, FULL_BRIGHT, glow, glow, x0, -0.22f, z, x1, -0.22f, z, (x0 + x1) / 2, -0.35f, z, (x0 + x1) / 2, -0.35f, z);
		}
		face(p, vc, FULL_BRIGHT, glow, glow, -0.03f, -0.15f, z, 0.03f, -0.15f, z, 0, -0.2f, z, 0, -0.2f, z);
		for (int k = 0; k < GRIN_COLUMNS; k++) {
			float[] g = grinColumn(k);
			face(p, vc, FULL_BRIGHT, glow, glow, g[0], g[2], z, g[1], g[3], z, g[1], g[5], z, g[0], g[4], z);
		}
	}

	/**
	 * One slice of the pumpkin's grin, left to right: {x0, x1, top at x0, top at x1, bottom at x0, bottom at x1}. The grin
	 * curves up at the corners; a tooth hangs down into the second and fourth slices and one stands up in the middle.
	 */
	static float[] grinColumn(int k) {
		float width = 0.42f, step = width / GRIN_COLUMNS, x0 = -width / 2 + k * step, x1 = x0 + step;
		float lowerTop = k == 1 || k == 3 ? 0.035f : 0, raiseBottom = k == 2 ? 0.035f : 0;
		return new float[] {x0, x1, grin(x0, -0.11f) + lowerTop, grin(x1, -0.11f) + lowerTop,
				grin(x0, -0.03f) - raiseBottom, grin(x1, -0.03f) - raiseBottom};
	}

	/** The grin's edge at {@code x}: {@code y} in the middle, rising towards the corners. */
	private static float grin(float x, float y) {
		float t = x / 0.21f;
		return y - 0.07f * t * t;
	}
}
