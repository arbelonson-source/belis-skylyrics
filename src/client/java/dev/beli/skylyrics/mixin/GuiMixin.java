package dev.beli.skylyrics.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
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
	//? if >=26.2 {
	/*@WrapOperation(method = "extractCrosshair", at = @At(value = "INVOKE", ordinal = 0,
	*///?} else {
	@WrapOperation(method = "renderCrosshair", at = @At(value = "INVOKE", ordinal = 0,
	//?}
			target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
	private void skylyrics$crosshair(GuiGraphics g, RenderPipeline pipeline, Identifier sprite, int x, int y, int width,
			int height, Operation<Void> original) {
		Crosshair style = SkyLyricsConfig.get().crosshair;
		SkyLyricsClient mod = SkyLyricsClient.get();
		if (style == Crosshair.NORMAL || mod == null || mod.renderer() == null) {
			original.call(g, pipeline, sprite, x, y, width, height);
			return;
		}
		CrosshairShape.draw(g, x + width / 2, y + height / 2, style, mod.renderer().color());
	}
}
