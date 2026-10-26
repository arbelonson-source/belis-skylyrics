package dev.beli.skylyrics.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.beli.skylyrics.render.ScreenLayer;
import dev.beli.skylyrics.render.SkyTint;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void skylyrics$captureMatrices(GraphicsResourceAllocator allocator, DeltaTracker delta, boolean outline,
			CameraRenderState camera, Matrix4fc view, GpuBufferSlice fog, Vector4f fogColor, boolean sky,
			CallbackInfo ci) {
		ScreenLayer.capture(view, camera.projectionMatrix);
		SkyTint.fog(fogColor);
	}
}
