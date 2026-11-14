package dev.beli.skylyrics.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import dev.beli.skylyrics.render.RgbaGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
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
	*///?} else if >=1.21.6 {
	@Unique
	private static final String WRITE = "Lcom/mojang/blaze3d/systems/CommandEncoder;writeToTexture(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/platform/NativeImage;IIIIIIII)V";
	//?} else {
	/*@Unique
	private static final String WRITE = "Lcom/mojang/blaze3d/systems/CommandEncoder;writeToTexture(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/platform/NativeImage;IIIIIII)V";
	*///?}

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

	//? if >=1.21.5 {
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
	//?} else if >=1.21.2 {
	/*// 1.21.4 uploads the mask by calling upload on the image itself, so the call is swapped instead of its argument
	@Redirect(method = "upload", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/platform/NativeImage;upload(IIIIIIIZ)V"))
	private void skylyrics$toPicture(NativeImage mask, int level, int x, int y, int skipX, int skipY, int width,
			int height, boolean autoClose) {
		if (!skylyrics$picture) {
			mask.upload(level, x, y, skipX, skipY, width, height, autoClose);
			return;
		}
		NativeImage picture = new NativeImage(NativeImage.Format.RGBA, mask.getWidth(), mask.getHeight(), false);
		for (int py = 0; py < mask.getHeight(); py++) {
			for (int px = 0; px < mask.getWidth(); px++) {
				picture.setPixel(px, py, (mask.getLuminanceOrAlpha(px, py) & 0xFF) * 0x01010101);
			}
		}
		picture.upload(level, x, y, skipX, skipY, width, height, true);
		if (autoClose) {
			mask.close();
		}
	}
	*///?} else {
	/*// 1.21.1 uploads the mask the same way, with blur and close flags instead of the 1.21.4 close flag; our pictures also
	// ask for smooth scaling and clamped edges here, since each upload sets them
	@Redirect(method = "upload", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/platform/NativeImage;upload(IIIIIIIZZ)V"))
	private void skylyrics$toPicture(NativeImage mask, int level, int x, int y, int skipX, int skipY, int width,
			int height, boolean blur, boolean autoClose) {
		if (!skylyrics$picture) {
			mask.upload(level, x, y, skipX, skipY, width, height, blur, autoClose);
			return;
		}
		NativeImage picture = new NativeImage(NativeImage.Format.RGBA, mask.getWidth(), mask.getHeight(), false);
		for (int py = 0; py < mask.getHeight(); py++) {
			for (int px = 0; px < mask.getWidth(); px++) {
				picture.setPixelRGBA(px, py, (mask.getLuminanceOrAlpha(px, py) & 0xFF) * 0x01010101);
			}
		}
		picture.upload(level, x, y, skipX, skipY, width, height, true, true, false, true);
		if (autoClose) {
			mask.close();
		}
	}
	*///?}
}
