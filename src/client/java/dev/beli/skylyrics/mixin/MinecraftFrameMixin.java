package dev.beli.skylyrics.mixin;

import dev.beli.skylyrics.dev.DevTestHook;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Once per rendered frame, before the next one starts (when the game window holds the last finished frame), so the
 * test hook's {@code burst} can grab successive frames. Does nothing unless the test hook is running.
 */
@Mixin(Minecraft.class)
abstract class MinecraftFrameMixin {
	// Developer-only: optional (require = 0) and without the target's arguments, so a game version that changes this
	// method only loses the burst command instead of failing to start
	@Inject(method = "runTick", at = @At("HEAD"), require = 0)
	private void skylyrics$frame(CallbackInfo ci) {
		DevTestHook.frame((Minecraft) (Object) this);
	}
}
