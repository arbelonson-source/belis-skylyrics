package dev.beli.skylyrics.render;

import dev.beli.skylyrics.SkyLyricsClient;
import dev.beli.skylyrics.config.SkyLyricsConfig;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
//? if >=26.1 {
/*import org.joml.Matrix4fc;
*///?}
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Lets the sky lyrics be drawn on the HUD instead of in the world. Shader packs light and fog
 * everything drawn in the world and paint their own clouds over it; the HUD comes after all of
 * that, so text drawn there stays bright. The lyrics keep their spot in the sky because the
 * spot is projected with the exact matrices the world was drawn with this frame.
 */
public final class ScreenLayer {
	private static final Matrix4f viewProjection = new Matrix4f();
	private static final Vector4f clip = new Vector4f();
	private static final FrustumIntersection frustum = new FrustumIntersection();
	private static float pixelsPerUnit;
	private static float unitsAcross;
	private static float lookX, lookY, lookZ, tanHalfH, tanHalfV;
	private static boolean captured;
	private static Method irisInstance, irisShadersOn;
	private static boolean irisLookedUp;
	/** World frames captured so far; with {@link #shadersAskedAt} it says whether the shader answer is from this frame. */
	private static long frames;
	private static long shadersAskedFrame = -1, shadersAskedAt;
	private static boolean shadersAnswer;
	/** Longest a shader answer is kept even when no world frame comes (a menu): short enough that a toggle shows at once. */
	private static final long SHADERS_KEEP_NS = 50_000_000L;

	private ScreenLayer() {}

	/** Called at the start of every world frame (see LevelRendererMixin). */
	//? if >=26.1 {
	/*public static void capture(Matrix4fc view, Matrix4fc projection) {
	*///?} else {
	public static void capture(Matrix4f view, Matrix4f projection) {
	//?}
		viewProjection.set(projection).mul(view);
		frustum.set(viewProjection);
		pixelsPerUnit = projection.m11();
		unitsAcross = projection.m00();
		// The view turns the world into camera space, which looks down -z, so the look direction is minus its third row
		lookX = -view.m02();
		lookY = -view.m12();
		lookZ = -view.m22();
		tanHalfH = 1 / projection.m00();
		tanHalfV = 1 / projection.m11();
		captured = true;
		frames++;
	}

	/** Points {@code view} at where the camera looked this frame; with no frame captured yet it counts everything as visible. */
	static SkyView lookInto(SkyView view) {
		return captured ? view.set(lookX, lookY, lookZ, tanHalfH, tanHalfV) : view.clear();
	}

	public static boolean ready() {
		return captured;
	}

	public static boolean active(SkyLyricsConfig cfg) {
		// The HUD isn't drawn while F1 hides it, so lyrics that should stay visible go back to the world.
		//? if >=26.2 {
		/*if (Minecraft.getInstance().gui.hud.isHidden()) {
		*///?} else {
		if (Minecraft.getInstance().options.hideGui) {
		//?}
			return false;
		}
		// Hide in caves wins over Show underground: they'd contradict, and hidden lyrics aren't drawn anyway.
		boolean showUnderground = cfg.showUnderground && !cfg.hideInCaves;
		return wanted(cfg.layer, cfg.seeThroughBlocks, shadersOn(), raining(),
				showUnderground && Visibility.underground(Minecraft.getInstance()), showUnderground);
	}

	/**
	 * Whether the lyrics are drawn on the screen layer, from everything that bears on it. One answer per frame, so a
	 * frame draws the world copy or the screen copy, never both and never neither.
	 *
	 * @param underground     under cover, as {@link Visibility#underground(Minecraft)} says (with its hysteresis)
	 * @param showUnderground the Show underground too setting, already off when Hide in caves is on
	 */
	static boolean wanted(SkyLyricsConfig.Layer layer, boolean seeThrough, boolean shaders, boolean rain,
			boolean underground, boolean showUnderground) {
		if (layer == SkyLyricsConfig.Layer.SCREEN) {
			return true;
		}
		return switch (layer) {
			case SCREEN -> true;
			// "Never draw on top" is the player's own choice: blocks are meant to hide the lyrics
			case WORLD -> false;
			// See-through text in the world gets painted over by clouds, so only keep it there
			// when blocks are meant to hide it and nothing would wash it out.
			// Under rock the sky is behind it, so the screen is the only place to see it
			case AUTO -> seeThrough || shaders || rain || underground && showUnderground;
		};
	}

	/** Rain pulls the fog in close enough to swallow the sky text, so it goes on screen too. */
	static boolean raining() {
		ClientLevel level = Minecraft.getInstance().level;
		return level != null && level.getRainLevel(1f) > 0f;
	}

	/**
	 * Screen position of a camera-relative point, with z holding how many GUI pixels one block
	 * spans at that distance. Null when the point is behind the camera.
	 */
	public static Vector3f project(Vector3f offset, int width, int height) {
		clip.set(offset.x, offset.y, offset.z, 1f).mul(viewProjection);
		if (clip.w < 0.05f) {
			return null;
		}
		float x = (clip.x / clip.w * 0.5f + 0.5f) * width;
		float y = (0.5f - clip.y / clip.w * 0.5f) * height;
		return new Vector3f(x, y, height * 0.5f * pixelsPerUnit / clip.w);
	}

	/**
	 * Whether a camera-relative box (half size in blocks, facing the camera) reaches into the view,
	 * with an extra margin as a fraction of the screen. Allocates nothing.
	 */
	static boolean inView(Vector3f centre, float halfW, float halfH, float margin) {
		if (!captured) {
			return false;
		}
		clip.set(centre.x, centre.y, centre.z, 1f).mul(viewProjection);
		if (clip.w < 0.05f) {
			return false;
		}
		float x = Math.abs(clip.x / clip.w) - halfW * unitsAcross / clip.w;
		float y = Math.abs(clip.y / clip.w) - halfH * pixelsPerUnit / clip.w;
		return x < 1 + 2 * margin && y < 1 + 2 * margin;
	}

	/** Whether a ball round a camera-relative point, radius in blocks, reaches into the view. Allocates nothing. */
	static boolean ballInView(Vector3f centre, float radius) {
		return captured && frustum.testSphere(centre.x, centre.y, centre.z, radius);
	}

	/** Asks Iris through its public API, without needing Iris to build or run. */
	static boolean shadersOn() {
		// Asked up to three times a frame, and each ask is a reflective call: answer once per world frame
		long now = System.nanoTime();
		if (answerKept(shadersAskedFrame, frames, shadersAskedAt, now)) {
			return shadersAnswer;
		}
		shadersAnswer = askIris();
		shadersAskedFrame = frames;
		shadersAskedAt = now;
		return shadersAnswer;
	}

	/** Whether an answer given at {@code askedAt} in frame {@code askedFrame} still stands in frame {@code frame} at {@code now}. */
	static boolean answerKept(long askedFrame, long frame, long askedAt, long now) {
		return askedFrame == frame && now - askedAt < SHADERS_KEEP_NS;
	}

	private static boolean askIris() {
		if (!irisLookedUp) {
			irisLookedUp = true;
			if (FabricLoader.getInstance().isModLoaded("iris")) {
				try {
					Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
					irisInstance = api.getMethod("getInstance");
					irisShadersOn = api.getMethod("isShaderPackInUse");
				} catch (ReflectiveOperationException e) {
					SkyLyricsClient.LOG.warn("Couldn't reach the Iris API, assuming no shaders", e);
				}
			}
		}
		if (irisShadersOn == null) {
			return false;
		}
		try {
			return (boolean) irisShadersOn.invoke(irisInstance.invoke(null));
		} catch (ReflectiveOperationException e) {
			irisShadersOn = null;
			return false;
		}
	}
}
