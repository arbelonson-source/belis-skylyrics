package dev.beli.skylyrics.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import dev.beli.skylyrics.render.RgbaGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A TrueType letter's image, as Minecraft uploads it onto a font page. Once marked (see {@link GlyphStitcherMixin}) it
 * says it has colour, so it lands on a full-colour page, and its grey mask is turned into a picture on the way there.
 * Every pixel gets the mask's value in all four channels, which with Minecraft's plain text shader looks exactly like
 * the mask did with its mask shader.
 */
@Mixin(targets = "com.mojang.blaze3d.font.TrueTypeGlyphProvider$Glyph$1")
abstract class TrueTypeGlyphMixin implements RgbaGlyph {
	//? if >=26.3 {
	/*@Unique
	private static final String WRITE = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;writeToTexture(Lcom/mojang/renderpearl/api/textures/GpuTexture;Lcom/mojang/blaze3d/platform/NativeImage;IIII)V";
	*///?} else if >=26.2 {
	/*@Unique
	private static final String WRITE = "Lcom/mojang/blaze3d/systems/CommandEncoder;writeToTexture(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/platform/NativeImage;IIII)V";
	*///?} else {
	@Unique
	private static final String WRITE = "Lcom/mojang/blaze3d/systems/CommandEncoder;writeToTexture(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/platform/NativeImage;IIIIIIII)V";
	//?}

	@Unique
	private boolean skylyrics$picture;

	/** The picture made for the upload in progress, freed once it's on the page. */
	@Unique
	private NativeImage skylyrics$uploading;

	@Override
	public void skylyrics$asPicture() {
		skylyrics$picture = true;
	}

	@Inject(method = "isColored", at = @At("HEAD"), cancellable = true)
	private void skylyrics$colored(CallbackInfoReturnable<Boolean> cir) {
		if (skylyrics$picture) {
			cir.setReturnValue(true);
		}
	}

	@ModifyArg(method = "upload", at = @At(value = "INVOKE", target = WRITE), index = 1)
	private NativeImage skylyrics$toPicture(NativeImage mask) {
		if (!skylyrics$picture) {
			return mask;
		}
		int width = mask.getWidth();
		int height = mask.getHeight();
		NativeImage picture = new NativeImage(NativeImage.Format.RGBA, width, height, false);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				picture.setPixel(x, y, (mask.getLuminanceOrAlpha(x, y) & 0xFF) * 0x01010101);
			}
		}
		skylyrics$uploading = picture;
		return picture;
	}

	@Inject(method = "upload", at = @At("RETURN"))
	private void skylyrics$freePicture(CallbackInfo ci) {
		if (skylyrics$uploading != null) {
			skylyrics$uploading.close();
			skylyrics$uploading = null;
		}
	}
}
