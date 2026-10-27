package dev.beli.skylyrics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.config.SkyLyricsConfig.Hat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * A hat on your own player, seen in third person (F5): a halo, neon headphones, a conical hat or a crown. It's only drawn
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
	private static final int EAR_DARK = 0xFF24202E;

	private final LyricsRenderer lyrics;

	public HatLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, LyricsRenderer lyrics) {
		super(parent);
		this.lyrics = lyrics;
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector out, int light, AvatarRenderState state, float yaw, float pitch) {
		SkyLyricsConfig cfg = SkyLyricsConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (cfg.hat == Hat.NONE || state.isInvisible || mc.player == null || state.id != mc.player.getId()) {
			return;
		}
		float bright = brightness(cfg.hatPulse ? lyrics.secondsSinceLineChange() : Float.NaN);
		int rgb = lyrics.color();
		int glow = 0xFF000000 | LyricsRenderer.mix(rgb, 0xFFFFFF, 0.55f * bright);
		pose.pushPose();
		getParentModel().root().translateAndRotate(pose);
		getParentModel().translateToHead(pose);
		switch (cfg.hat) {
			case HALO -> {
				pose.translate(0, HEAD_TOP - HALO_LIFT, 0);
				out.submitCustomGeometry(pose, RenderTypes.textBackground(), (p, vc) -> tube(p, vc, HALO_RADIUS, HALO_TUBE, 0, glow, FULL_BRIGHT));
				int shine = Math.round(150 * bright) << 24 | rgb;
				out.submitCustomGeometry(pose, RenderTypes.lightning(), (p, vc) -> shine(p, vc, HALO_RADIUS, HALO_SHINE, shine));
			}
			case HEADPHONES -> {
				out.submitCustomGeometry(pose, RenderTypes.textBackground(), (p, vc) -> headphones(p, vc, light, glow));
				for (int side = -1; side <= 1; side += 2) {
					pose.pushPose();
					pose.translate(side * (CUP_OUT + 0.003f), CUP_Y, 0);
					pose.mulPose(Axis.ZP.rotationDegrees(90));
					out.submitCustomGeometry(pose, RenderTypes.textBackground(), (p, vc) -> tube(p, vc, 0.065f, 0.011f, 0, glow, FULL_BRIGHT));
					pose.popPose();
				}
			}
			case CONICAL -> out.submitCustomGeometry(pose, RenderTypes.textBackground(), (p, vc) -> conical(p, vc, light, glow));
			case CROWN -> out.submitCustomGeometry(pose, RenderTypes.textBackground(), (p, vc) -> crown(p, vc, light, glow));
			case CAT_EARS -> out.submitCustomGeometry(pose, RenderTypes.textBackground(), (p, vc) -> catEars(p, vc, light, glow));
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
			vc.addVertex(p, xyz[k * 3], xyz[k * 3 + 1], xyz[k * 3 + 2]).setColor(k < 2 ? from : to).setLight(light);
		}
		for (int k = 3; k >= 0; k--) {
			vc.addVertex(p, xyz[k * 3], xyz[k * 3 + 1], xyz[k * 3 + 2]).setColor(k < 2 ? from : to).setLight(light);
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
}
