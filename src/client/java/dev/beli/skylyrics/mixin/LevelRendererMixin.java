package dev.beli.skylyrics.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.beli.skylyrics.render.ScreenLayer;
import dev.beli.skylyrics.render.SkyTint;
//? if <26.2 {
import net.minecraft.client.Camera;
//?}
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
//? if >=26.2 {
/*import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
*///?} else {
import org.joml.Matrix4f;
//?}
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
	//? if >=26.2 {
	/*@Inject(method = "render", at = @At("HEAD"))
	*///?} else {
	@Inject(method = "renderLevel", at = @At("HEAD"))
	//?}
	private void skylyrics$captureMatrices(GraphicsResourceAllocator allocator, DeltaTracker delta, boolean outline,
			//? if >=26.2 {
			/*CameraRenderState camera, Matrix4fc view, GpuBufferSlice fog, Vector4f fogColor, boolean sky,
			CallbackInfo ci) {
		ScreenLayer.capture(view, camera.projectionMatrix);
			*///?} else {
			Camera camera, Matrix4f view, Matrix4f projection, Matrix4f culling, GpuBufferSlice fog, Vector4f fogColor,
			boolean sky, CallbackInfo ci) {
		ScreenLayer.capture(view, projection);
			//?}
		SkyTint.fog(fogColor);
	}
}
