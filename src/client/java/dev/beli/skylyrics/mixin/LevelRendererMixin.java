package dev.beli.skylyrics.mixin;

//? if >=1.21.6 {
import com.mojang.blaze3d.buffers.GpuBufferSlice;
//?}
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.beli.skylyrics.render.ScreenLayer;
import dev.beli.skylyrics.render.SkyTint;
//? if <26.1 {
import net.minecraft.client.Camera;
//?}
import net.minecraft.client.DeltaTracker;
//? if <1.21.6 {
/*import net.minecraft.client.renderer.GameRenderer;
*///?}
import net.minecraft.client.renderer.LevelRenderer;
//? if >=26.1 && <26.2 {
/*import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
*///?}
//? if >=26.1 {
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
	private void skylyrics$captureMatrices(GraphicsResourceAllocator allocator,
			//? if >=26.3 {
			/*boolean outline, CameraRenderState camera, GpuBufferSlice fog, Vector4f fogColor, boolean sky,
			boolean sectionOcclusion, CallbackInfo ci) {
		ScreenLayer.capture(camera.viewRotationMatrix, camera.projectionMatrix);
			*///?} else if >=26.2 {
			/*DeltaTracker delta, boolean outline, CameraRenderState camera, Matrix4fc view, GpuBufferSlice fog,
			Vector4f fogColor, boolean sky, CallbackInfo ci) {
		ScreenLayer.capture(view, camera.projectionMatrix);
			*///?} else if >=26.1 {
			/*DeltaTracker delta, boolean outline, CameraRenderState camera, Matrix4fc view, GpuBufferSlice fog,
			Vector4f fogColor, boolean sky, ChunkSectionsToRender sections, CallbackInfo ci) {
		ScreenLayer.capture(view, camera.projectionMatrix);
			*///?} else if >=1.21.9 {
			DeltaTracker delta, boolean outline, Camera camera, Matrix4f view, Matrix4f projection, Matrix4f culling,
			GpuBufferSlice fog, Vector4f fogColor, boolean sky, CallbackInfo ci) {
		ScreenLayer.capture(view, projection);
			//?} else if >=1.21.6 {
			/*DeltaTracker delta, boolean outline, Camera camera, Matrix4f view, Matrix4f projection,
			GpuBufferSlice fog, Vector4f fogColor, boolean sky, CallbackInfo ci) {
		ScreenLayer.capture(view, projection);
			*///?} else {
			/*DeltaTracker delta, boolean outline, Camera camera, GameRenderer renderer, Matrix4f view,
			Matrix4f projection, CallbackInfo ci) {
		ScreenLayer.capture(view, projection);
			*///?}
		//? if >=1.21.6 {
		SkyTint.fog(fogColor);
		//?}
	}
	//? if <1.21.6 {
	/*
	// 1.21.5 works the fog colour out inside renderLevel instead of passing it in
	@com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/FogRenderer;computeFogColor(Lnet/minecraft/client/Camera;FLnet/minecraft/client/multiplayer/ClientLevel;IF)Lorg/joml/Vector4f;"))
	private Vector4f skylyrics$fogColor(Vector4f fogColor) {
		SkyTint.fog(fogColor);
		return fogColor;
	}
	*///?}
}
