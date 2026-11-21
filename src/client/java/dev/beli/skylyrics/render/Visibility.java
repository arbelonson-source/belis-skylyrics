package dev.beli.skylyrics.render;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FogType;
import org.jspecify.annotations.Nullable;

import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.gui.SkyLyricsScreen;

public final class Visibility {
	static final int HIDE_AT = 6;
	static final int SHOW_AT = 9;
	private static final long CHECK_NS = 250_000_000L;
	private static long checkedAt;
	private static boolean underground;

	private Visibility() {
	}

	/** Forget the cached cave answer (joining or leaving a world), so the next frame asks the new world. */
	static void forget() {
		checkedAt = System.nanoTime() - CHECK_NS;
		underground = false;
	}

	/**
	 * Whether the open screen counts as a menu for Hide in menus. The death screen doesn't: the world still shows through
	 * it, and the words drop away there. Nor do the mod's own settings, so changes can be seen on the real lyrics, nor
	 * the pause of F3 + Esc, which stops the game without drawing a menu.
	 */
	static boolean inMenu(@Nullable Screen screen) {
		return screen != null && !(screen instanceof SkyLyricsScreen) && !(screen instanceof DeathScreen)
				&& !(screen instanceof PauseScreen pause && !pause.showsPauseMenu());
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
		//? if >=26.2 {
		/*Camera camera = mc.gameRenderer.mainCamera();
		*///?} else {
		Camera camera = mc.gameRenderer.getMainCamera();
		//?}
		if (cfg.hideUnderwater && camera.getFluidInCamera() == FogType.WATER) {
			return true;
		}
		return cfg.hideInCaves && level.dimensionType().hasSkyLight() && underground(level, camera);
	}

	/**
	 * Whether the player is under cover right now (no open sky above), with the cave-mouth hysteresis. The one answer
	 * for Show underground too. Where the dimension has no sky light it still counts when there is a ceiling (the
	 * Nether, under its bedrock roof), but not in the End, which has neither. Hide in caves keeps its own rule in
	 * {@link #hidden}: no sky light, no hiding.
	 */
	public static boolean underground(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || !canBeUnderground(level.dimensionType().hasSkyLight(), level.dimensionType().hasCeiling())) {
			return false;
		}
		//? if >=26.2 {
		/*Camera camera = mc.gameRenderer.mainCamera();
		*///?} else {
		Camera camera = mc.gameRenderer.getMainCamera();
		//?}
		return underground(level, camera);
	}

	private static boolean underground(ClientLevel level, Camera camera) {
		long now = System.nanoTime();
		if (now - checkedAt >= CHECK_NS) {
			checkedAt = now;
			underground = underground(underground, level.getBrightness(LightLayer.SKY, camera.blockPosition()),
					camera.getFluidInCamera() == FogType.WATER);
		}
		return underground;
	}

	/** Whether "under cover" means anything in a dimension: it has sky light to lose, or a ceiling over you. */
	static boolean canBeUnderground(boolean hasSkyLight, boolean hasCeiling) {
		return hasSkyLight || hasCeiling;
	}

	/**
	 * Diving deep in open water dims the sky light too, so going under never starts counting as under cover. But it
	 * doesn't end it either: a pool in a dark cave stays a cave, until the sky light says open sky ({@link #SHOW_AT}).
	 */
	static boolean underground(boolean was, int skyLight, boolean inWater) {
		return inWater ? was && skyLight < SHOW_AT : underground(was, skyLight);
	}

	static boolean underground(boolean was, int skyLight) {
		return skyLight <= HIDE_AT || was && skyLight < SHOW_AT;
	}
}
