package dev.beli.skylyrics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
 * A glowing hat on your own player, seen in third person (F5). It's only drawn here, so other players never see it.
 * It takes the lyrics colour and flares up whenever a new line starts.
 */
public final class HatLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final int SEGMENTS = 40;
	/** Head space is in blocks with y pointing down; the top of the head is half a block above the neck. */
	private static final float HEAD_TOP = -0.5f;
	private static final float HALO_LIFT = 0.17f, HALO_RADIUS = 0.27f, HALO_TUBE = 0.022f, HALO_SHINE = 0.11f;
	/** How bright the hat sits between flares, and how much a flare adds. */
	static final float REST = 0.7f, FLARE = 0.3f;
	private static final int FULL_BRIGHT = 0xF000F0;

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
		pose.pushPose();
		getParentModel().root().translateAndRotate(pose);
		getParentModel().translateToHead(pose);
		pose.translate(0, HEAD_TOP - HALO_LIFT, 0);
		int core = 0xFF000000 | LyricsRenderer.mix(rgb, 0xFFFFFF, 0.55f * bright);
		out.submitCustomGeometry(pose, RenderTypes.textBackground(), (p, vc) -> tube(p, vc, HALO_RADIUS, HALO_TUBE, core));
		int shine = Math.round(150 * bright) << 24 | rgb;
		out.submitCustomGeometry(pose, RenderTypes.lightning(), (p, vc) -> shine(p, vc, HALO_RADIUS, HALO_SHINE, shine));
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

	/** A ring of square tube lying flat, both sides of every face so it shows from any angle. */
	private static void tube(PoseStack.Pose p, VertexConsumer vc, float radius, float half, int argb) {
		for (int i = 0; i < SEGMENTS; i++) {
			float a0 = i * Mth.TWO_PI / SEGMENTS, a1 = (i + 1) * Mth.TWO_PI / SEGMENTS;
			float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
			float in = radius - half, out = radius + half;
			quad(p, vc, argb, argb, c0, s0, c1, s1, in, out, -half, -half); // top
			quad(p, vc, argb, argb, c0, s0, c1, s1, in, out, half, half); // bottom
			quad(p, vc, argb, argb, c0, s0, c1, s1, out, out, -half, half); // outside
			quad(p, vc, argb, argb, c0, s0, c1, s1, in, in, -half, half); // inside
		}
	}

	/** A soft light round the ring: a flat band and an upright one, bright at the ring and gone at their edges. */
	private static void shine(PoseStack.Pose p, VertexConsumer vc, float radius, float reach, int argb) {
		int clear = argb & 0xFFFFFF;
		for (int i = 0; i < SEGMENTS; i++) {
			float a0 = i * Mth.TWO_PI / SEGMENTS, a1 = (i + 1) * Mth.TWO_PI / SEGMENTS;
			float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
			quad(p, vc, argb, clear, c0, s0, c1, s1, radius, radius - reach, 0, 0);
			quad(p, vc, argb, clear, c0, s0, c1, s1, radius, radius + reach, 0, 0);
			quad(p, vc, argb, clear, c0, s0, c1, s1, radius, radius, 0, -reach * 0.6f);
			quad(p, vc, argb, clear, c0, s0, c1, s1, radius, radius, 0, reach * 0.6f);
		}
	}

	/**
	 * One slice of a ring between two angles, from (r0, y0) to (r1, y1) across, drawn both ways round. The colour runs
	 * from {@code from} at the first edge to {@code to} at the second.
	 */
	private static void quad(PoseStack.Pose p, VertexConsumer vc, int from, int to, float c0, float s0, float c1, float s1,
			float r0, float r1, float y0, float y1) {
		float[] xs = {c0 * r0, c1 * r0, c1 * r1, c0 * r1};
		float[] zs = {s0 * r0, s1 * r0, s1 * r1, s0 * r1};
		float[] ys = {y0, y0, y1, y1};
		int[] cs = {from, from, to, to};
		for (int k = 0; k < 4; k++) {
			vc.addVertex(p, xs[k], ys[k], zs[k]).setColor(cs[k]).setLight(FULL_BRIGHT);
		}
		for (int k = 3; k >= 0; k--) {
			vc.addVertex(p, xs[k], ys[k], zs[k]).setColor(cs[k]).setLight(FULL_BRIGHT);
		}
	}
}
