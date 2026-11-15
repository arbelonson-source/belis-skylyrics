package dev.beli.skylyrics.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
//? if >=1.21.6 {
import com.mojang.blaze3d.pipeline.RenderPipeline;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
import java.util.function.Function;
*///?}
//? if <1.21.2 {
/*import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
*///?}
import dev.beli.skylyrics.SkyLyricsClient;
import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.config.SkyLyricsConfig.Crosshair;
import dev.beli.skylyrics.render.CrosshairShape;
//? if <26.2 {
import net.minecraft.client.gui.Gui;
//?}
import net.minecraft.client.gui.GuiGraphics;
//? if >=26.2 {
/*import net.minecraft.client.gui.Hud;
*///?}
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Swaps the crosshair picture for one of our shapes. Minecraft still decides when it shows, and the attack meter stays. */
//? if >=26.2 {
/*@Mixin(Hud.class)
*///?} else {
@Mixin(Gui.class)
//?}
abstract class GuiMixin {
	//? if >=26.1 {
	/*@WrapOperation(method = "extractCrosshair", at = @At(value = "INVOKE", ordinal = 0,
	*///?} else {
	@WrapOperation(method = "renderCrosshair", at = @At(value = "INVOKE", ordinal = 0,
	//?}
			//? if >=1.21.6 {
			target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"), require = 0)
	private void skylyrics$crosshair(GuiGraphics g, RenderPipeline pipeline, Identifier sprite, int x, int y, int width,
			int height, Operation<Void> original) {
			//?} else if >=1.21.2 {
			/*target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Ljava/util/function/Function;Lnet/minecraft/resources/ResourceLocation;IIII)V"), require = 0)
	private void skylyrics$crosshair(GuiGraphics g, Function<ResourceLocation, RenderType> pipeline, ResourceLocation sprite,
			int x, int y, int width, int height, Operation<Void> original) {
			*///?} else {
			/*target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"), require = 0)
	private void skylyrics$crosshair(GuiGraphics g, ResourceLocation sprite, int x, int y, int width, int height,
			Operation<Void> original) {
			*///?}
		SkyLyricsConfig cfg = SkyLyricsConfig.get();
		Crosshair style = cfg.crosshair;
		SkyLyricsClient mod = SkyLyricsClient.get();
		if (style == Crosshair.NORMAL || mod == null || mod.renderer() == null) {
			//? if >=1.21.2 {
			original.call(g, pipeline, sprite, x, y, width, height);
			//?} else {
			/*original.call(g, sprite, x, y, width, height);
			*///?}
			return;
		}
		int rgb = mod.renderer().color();
		if (cfg.crosshairPulse) {
			rgb = CrosshairShape.flash(rgb, mod.renderer().secondsSinceLineChange());
		}
		//? if <1.21.2 {
		/*// 1.21.1 draws the crosshair with inverted colours; ours are plain, so put the normal blending back for them
		// and the inverted blending back for whatever vanilla draws next (the attack meter)
		RenderSystem.defaultBlendFunc();
		CrosshairShape.draw(g, x + width / 2, y + height / 2, style, rgb);
		RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
				GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
		*///?} else {
		CrosshairShape.draw(g, x + width / 2, y + height / 2, style, rgb);
		//?}
	}
}
