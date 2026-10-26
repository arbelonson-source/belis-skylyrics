package dev.beli.skylyrics.render;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FogType;

import dev.beli.skylyrics.config.SkyLyricsConfig;

public final class Visibility {
	static final int HIDE_AT = 6;
	static final int SHOW_AT = 9;
	private static final long CHECK_NS = 250_000_000L;
	private static long checkedAt;
	private static boolean underground;

	private Visibility() {
	}

	public static boolean hidden(Minecraft mc, SkyLyricsConfig cfg) {
		ClientLevel level = mc.level;
		if (level == null) {
			return false;
		}
		if (cfg.hideInMultiplayer && !mc.hasSingleplayerServer()
				|| cfg.hideInNether && Level.NETHER.equals(level.dimension())
				|| cfg.hideInEnd && Level.END.equals(level.dimension())) {
			return true;
		}
		Camera camera = mc.gameRenderer.mainCamera();
		if (cfg.hideUnderwater && camera.getFluidInCamera() == FogType.WATER) {
			return true;
		}
		return cfg.hideInCaves && level.dimensionType().hasSkyLight() && underground(level, camera);
	}

	private static boolean underground(ClientLevel level, Camera camera) {
		long now = System.nanoTime();
		if (now - checkedAt >= CHECK_NS) {
			checkedAt = now;
			underground = underground(underground, level.getBrightness(LightLayer.SKY, camera.blockPosition()));
		}
		return underground;
	}

	static boolean underground(boolean was, int skyLight) {
		return skyLight <= HIDE_AT || was && skyLight < SHOW_AT;
	}
}
