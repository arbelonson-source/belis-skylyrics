package dev.beli.skylyrics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.config.SkyLyricsConfig.SkyEffect;
import dev.beli.skylyrics.media.MediaTracker;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.dimension.DimensionType.Skybox;
import net.minecraft.world.level.material.FogType;

/**
 * A soft aurora or nebula high in the night sky while music plays, tinted by the lyrics colour.
 * Drawn camera-relative with an additive type, so it needs no shader support and works with Iris and Sodium.
 */
public final class SkyEffects {
	private static final float FADE_SECONDS = 1.5f;
	/** Star brightness of a full overworld night. */
	private static final float NIGHT_STARS = 0.5f;
	private static final int TEAL = 0x3CFFB4;
	private static final int VIOLET = 0x7A4CFF;
	private static final int DEEP_BLUE = 0x2A58E8;
	private static final int MAGENTA = 0xD24CFF;
	private static final int RIBBONS = 3;
	private static final int SEGMENTS = 80;
	private static final int BLOBS = 7;
	private static final int SLICES = 24;
	private static final int DOTS = 60;
	private static final float DEG = (float) (Math.PI / 180);

	private final LyricsRenderer lyrics;
	private final MediaTracker tracker;
	private float presence;
	private float clock;
	private long last;

	private final float[] qx = new float[4], qy = new float[4], qz = new float[4];
	private final int[] qc = new int[4];
	private int corner;

	private SkyEffect effect;
	private float radius;
	private float fade;
	private int tint;

	public SkyEffects(LyricsRenderer lyrics, MediaTracker tracker) {
		this.lyrics = lyrics;
		this.tracker = tracker;
	}

	/**
	 * How dark the night is (vanilla stars peak at {@link #NIGHT_STARS}) times clear weather times the user's
	 * strength, all 0..1.
	 */
	static float visibility(float starBrightness, float rainLevel, float strength) {
		return clamp01(starBrightness / NIGHT_STARS) * (1 - clamp01(rainLevel)) * clamp01(strength);
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

	private static float clamp01(float v) {
		return v < 0 ? 0 : Math.min(v, 1);
	}

	private static int argb(int rgb, float alpha) {
		return Math.round(clamp01(alpha) * 255) << 24 | rgb;
	}

	public void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		SkyLyricsConfig cfg = SkyLyricsConfig.get();
		long now = System.nanoTime();
		float dt = last == 0 ? 0 : Math.min(0.1f, (now - last) / 1e9f);
		last = now;
		boolean on = cfg.sky != SkyEffect.OFF && cfg.enabled && tracker.playing();
		presence = step(presence, on, dt);
		ClientLevel level = mc.level;
		if (presence <= 0 || cfg.sky == SkyEffect.OFF || level == null
				|| level.dimensionType().skybox() != Skybox.OVERWORLD) {
			return;
		}
		Camera camera = mc.gameRenderer.getMainCamera();
		if (camera.getFluidInCamera() != FogType.NONE) {
			return;
		}
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float stars = camera.attributeProbe().getValue(EnvironmentAttributes.STAR_BRIGHTNESS, partial);
		fade = visibility(stars, level.getRainLevel(partial), cfg.skyStrength) * smooth(presence);
		if (fade < 0.01f) {
			return;
		}
		clock += dt;
		effect = cfg.sky;
		radius = radius(mc.options.getEffectiveRenderDistance());
		tint = lyrics.color();

		PoseStack pose = context.matrices();
		pose.pushPose();
		context.commandQueue().submitCustomGeometry(pose, RenderTypes.lightning(), this::draw);
		pose.popPose();
	}

	private void draw(PoseStack.Pose pose, VertexConsumer vc) {
		if (effect == SkyEffect.AURORA) {
			for (int k = 0; k < RIBBONS; k++) {
				ribbon(pose, vc, k);
			}
		} else {
			for (int k = 0; k < BLOBS; k++) {
				blob(pose, vc, k);
			}
			dots(pose, vc);
		}
	}

	private void vertex(VertexConsumer vc, PoseStack.Pose pose, float yaw, float elev, float r, int color) {
		float h = r * Mth.cos(elev);
		point(vc, pose, h * Mth.sin(yaw), r * Mth.sin(elev), -h * Mth.cos(yaw), color);
	}

	private void ribbon(PoseStack.Pose pose, VertexConsumer vc, int k) {
		float t = clock;
		float r = radius * (1 - 0.07f * k);
		float centre = (k * 2.1f + 0.6f) + t * 0.004f * (k % 2 == 0 ? 1 : -1);
		float span = (100 + 20 * k) * DEG;
		float baseElev = (27 + 9 * k) * DEG;
		int low = mix(mix(TEAL, tint, 0.3f), TEAL, 0.35f);
		int high = mix(mix(VIOLET, tint, 0.5f), low, 0.2f);
		float peak = 0.85f * fade;
		float prevYaw = 0, prevBase = 0, prevTop = 0, prevB = 0;
		for (int i = 0; i <= SEGMENTS; i++) {
			float u = (float) i / SEGMENTS;
			float yaw = centre + (u - 0.5f) * span + 0.07f * Mth.sin(t * 0.05f + u * 5 + k);
			float base = baseElev + 4 * DEG * Mth.sin(t * 0.07f + u * 6 + k * 2);
			float top = base + (22 + 6 * Mth.sin(t * 0.09f + u * 9 + k)) * DEG;
			float ripple = 0.55f + 0.45f * Mth.sin(t * 0.5f - u * 8 + k * 1.7f);
			// Fine vertical rays drifting along the curtain
			float rays = 0.72f + 0.28f * Mth.sin(u * 70 + t * 0.35f + k) * Mth.sin(u * 23 - t * 0.2f);
			float b = peak * ripple * rays * endFade(u);
			if (i > 0) {
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
		float yaw = k * 0.9f + 0.3f + t * 0.003f;
		float elev = (38 + 14 * Mth.sin(k * 1.7f + 1)) * DEG + 2 * DEG * Mth.sin(t * 0.04f + k);
		float dist = radius * (0.97f - 0.025f * k);
		float cosE = Mth.cos(elev);
		float dx = cosE * Mth.sin(yaw), dy = Mth.sin(elev), dz = -cosE * Mth.cos(yaw);
		float ux = -dz, uz = dx;
		float ul = Mth.sqrt(ux * ux + uz * uz);
		ux /= ul;
		uz /= ul;
		float vx = dy * uz, vy = dz * ux - dx * uz, vz = -dy * ux;
		float cx = dx * dist, cy = dy * dist, cz = dz * dist;
		float size = dist * (0.36f + 0.08f * Mth.sin(k * 1.9f)) * (1 + 0.06f * Mth.sin(t * 0.12f + k));
		int base = k % 3 == 0 ? VIOLET : k % 3 == 1 ? DEEP_BLUE : MAGENTA;
		int hue = mix(base, tint, 0.4f);
		float alpha = 0.7f * fade * (0.8f + 0.2f * Mth.sin(t * 0.15f + k * 2));
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
			if (i > 0) {
				point(vc, pose, cx, cy, cz, centreC);
				point(vc, pose, cx, cy, cz, centreC);
				point(vc, pose, mx, my, mz, midC);
				point(vc, pose, prevMx, prevMy, prevMz, midC);
				point(vc, pose, prevMx, prevMy, prevMz, midC);
				point(vc, pose, mx, my, mz, midC);
				point(vc, pose, ex, ey, ez, edgeC);
				point(vc, pose, prevEx, prevEy, prevEz, edgeC);
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
		int white = mix(0xFFFFFF, tint, 0.35f);
		for (int i = 0; i < DOTS; i++) {
			float yaw = i * 2.399963f;
			float elev = (22 + 40 * (Mth.sin(i * 12.9898f) * 0.5f + 0.5f)) * DEG;
			float tw = 0.5f + 0.5f * Mth.sin(t * (0.6f + (i % 5) * 0.15f) + i * 3.1f);
			int c = argb(white, 0.55f * fade * tw);
			float s = 0.5f * radius / 100f;
			float h = radius * Mth.cos(elev);
			float x = h * Mth.sin(yaw), y = radius * Mth.sin(elev), z = -h * Mth.cos(yaw);
			float rx = Mth.cos(yaw) * s, rz = Mth.sin(yaw) * s;
			point(vc, pose, x - rx, y - s, z - rz, c);
			point(vc, pose, x + rx, y - s, z + rz, c);
			point(vc, pose, x + rx, y + s, z + rz, c);
			point(vc, pose, x - rx, y + s, z - rz, c);
		}
	}

	/** Collects four corners, then emits the quad twice (both windings), as the pipeline culls back faces. */
	private void point(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, int color) {
		qx[corner] = x;
		qy[corner] = y;
		qz[corner] = z;
		qc[corner] = color;
		if (++corner < 4) {
			return;
		}
		corner = 0;
		for (int i = 0; i < 4; i++) {
			vc.addVertex(pose, qx[i], qy[i], qz[i]).setColor(qc[i]);
		}
		for (int i = 3; i >= 0; i--) {
			vc.addVertex(pose, qx[i], qy[i], qz[i]).setColor(qc[i]);
		}
	}
}
