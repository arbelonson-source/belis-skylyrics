package dev.beli.skylyrics.mixin;

import java.util.List;

import dev.beli.skylyrics.render.DebugText;
import dev.beli.skylyrics.util.Guard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Notes where the debug screen (F3) draws its text, column by column, so a bar at the bottom can step aside when it would
 * sit on it ({@link DebugText}). Optional (require = 0): a mod that draws F3 its own way only leaves the bar to its
 * estimate.
 */
@Mixin(DebugScreenOverlay.class)
abstract class DebugScreenOverlayMixin {
	//? if >=26.3 {
	/*@Inject(method = "extractLines", at = @At("HEAD"), require = 0)
	private void skylyrics$lines(GuiGraphics g, List<String> lines, boolean left, int guiWidth, CallbackInfo ci) {
	*///?} else if >=26.1 {
	/*@Inject(method = "extractLines", at = @At("HEAD"), require = 0)
	private void skylyrics$lines(GuiGraphics g, List<String> lines, boolean left, CallbackInfo ci) {
	*///?} else {
	@Inject(method = "renderLines", at = @At("HEAD"), require = 0)
	private void skylyrics$lines(GuiGraphics g, List<String> lines, boolean left, CallbackInfo ci) {
	//?}
		int width = g.guiWidth();
		Guard.run("debug screen text", () -> DebugText.drawn(lines, left, width, Minecraft.getInstance().font::width));
	}
}
