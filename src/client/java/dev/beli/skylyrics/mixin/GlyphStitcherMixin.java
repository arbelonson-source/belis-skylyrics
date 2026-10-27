package dev.beli.skylyrics.mixin;

//? if >=1.21.9 {
import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphInfo;
import net.minecraft.client.gui.font.GlyphStitcher;
//?} else {
/*import com.mojang.blaze3d.font.SheetGlyphInfo;
import net.minecraft.client.gui.font.FontSet;
*///?}
import dev.beli.skylyrics.render.RgbaGlyph;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Where a font's letters are put onto its pages. Letters of our own fonts are marked to be stored as full pictures
 * (see {@link RgbaGlyph}) before Minecraft picks a page for them; every other font is left alone.
 */
//? if >=1.21.9 {
@Mixin(GlyphStitcher.class)
//?} else {
/*@Mixin(FontSet.class)
*///?}
abstract class GlyphStitcherMixin {
	//? if >=1.21.9 {
	@Shadow
	@Final
	private Identifier texturePrefix;

	@Inject(method = "stitch", at = @At("HEAD"))
	private void skylyrics$picturesForOurFonts(GlyphInfo info, GlyphBitmap bitmap, CallbackInfoReturnable<?> cir) {
		skylyrics$mark(bitmap, texturePrefix);
	}
	//?} else {
	/*@Shadow
	@Final
	private Identifier name;

	@Inject(method = "stitch", at = @At("HEAD"))
	private void skylyrics$picturesForOurFonts(SheetGlyphInfo bitmap, CallbackInfoReturnable<?> cir) {
		skylyrics$mark(bitmap, name);
	}
	*///?}

	@Unique
	private static void skylyrics$mark(Object bitmap, Identifier font) {
		if (bitmap instanceof RgbaGlyph glyph && "skylyrics".equals(font.getNamespace())) {
			glyph.skylyrics$asPicture();
		}
	}
}
