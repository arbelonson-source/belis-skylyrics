package dev.beli.skylyrics.dev;

import net.fabricmc.loader.api.FabricLoader;

/** Whether the dev test hook is on. Touches no Minecraft class, so the mixin plugin can ask this early. */
public final class TestHookSwitch {
	private TestHookSwitch() {
	}

	/** On in the dev game, and in runProductionClient, which sets -Dskylyrics.testHook=true. */
	public static boolean enabled() {
		return FabricLoader.getInstance().isDevelopmentEnvironment() || Boolean.getBoolean("skylyrics.testHook");
	}
}
