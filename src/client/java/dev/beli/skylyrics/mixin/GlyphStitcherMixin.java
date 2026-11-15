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
	//?} else if >=1.21.5 {
	/*@Shadow
	@Final
	private Identifier name;

	@Inject(method = "stitch", at = @At("HEAD"))
	private void skylyrics$picturesForOurFonts(SheetGlyphInfo bitmap, CallbackInfoReturnable<?> cir) {
		skylyrics$mark(bitmap, name);
	}
	*///?} else {
	/*@Shadow
	@Final
	private Identifier name;

	@com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "stitch")
	private net.minecraft.client.gui.font.glyphs.BakedGlyph skylyrics$picturesForOurFonts(SheetGlyphInfo bitmap,
			com.llamalad7.mixinextras.injector.wrapoperation.Operation<net.minecraft.client.gui.font.glyphs.BakedGlyph> original) {
		skylyrics$mark(bitmap, name);
		// 1.21.4's font pages have no name: tell the page about to be made whose letters it is for, and only until
		// stitch returns (or throws), so no other font's page is ever made while the flag is up
		dev.beli.skylyrics.render.FontPageFlag.ours = "skylyrics".equals(name.getNamespace());
		try {
			return original.call(bitmap);
		} finally {
			dev.beli.skylyrics.render.FontPageFlag.ours = false;
		}
	}
	*///?}

	@Unique
	private static void skylyrics$mark(Object bitmap, Identifier font) {
		if (bitmap instanceof RgbaGlyph glyph && "skylyrics".equals(font.getNamespace())) {
			glyph.skylyrics$asPicture();
		}
	}
}
