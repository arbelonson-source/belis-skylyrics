package dev.beli.skylyrics.render;

import dev.beli.skylyrics.SkyLyricsClient;
import dev.beli.skylyrics.config.SkyLyricsConfig;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Matrix4f;
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
	private static float pixelsPerUnit;
	private static float unitsAcross;
	private static boolean captured;
	private static Method irisInstance, irisShadersOn;
	private static boolean irisLookedUp;

	private ScreenLayer() {}

	/** Called at the start of every world frame (see LevelRendererMixin). */
	public static void capture(Matrix4f view, Matrix4f projection) {
		viewProjection.set(projection).mul(view);
		pixelsPerUnit = projection.m11();
		unitsAcross = projection.m00();
		captured = true;
	}

	public static boolean ready() {
		return captured;
	}

	public static boolean active(SkyLyricsConfig cfg) {
		// The HUD isn't drawn while F1 hides it, so lyrics that should stay visible go back to the world.
		if (Minecraft.getInstance().options.hideGui) {
			return false;
		}
		return switch (cfg.layer) {
			case SCREEN -> true;
			case WORLD -> false;
			// See-through text in the world gets painted over by clouds, so only keep it there
			// when blocks are meant to hide it and nothing would wash it out.
			case AUTO -> cfg.seeThroughBlocks || shadersOn() || raining();
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

	/** Asks Iris through its public API, without needing Iris to build or run. */
	static boolean shadersOn() {
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
