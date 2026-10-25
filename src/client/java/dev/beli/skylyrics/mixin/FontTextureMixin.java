package dev.beli.skylyrics.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.util.function.Supplier;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft samples glyph pages with nearest-pixel filtering, which suits its pixel font but makes our
 * TrueType letters jagged whenever they are drawn larger or smaller than the page. Only our own font pages
 * (named "skylyrics:...") get smooth filtering; every other font keeps the vanilla look.
 */
@Mixin(FontTexture.class)
abstract class FontTextureMixin extends AbstractTexture {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void skylyrics$smoothGlyphs(Supplier<String> name, GlyphRenderTypes renderTypes, boolean colored,
			CallbackInfo ci) {
		if (name.get().startsWith("skylyrics:")) {
			// Clamped so glyphs on the page border don't pick up the opposite edge
			this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		}
	}
}
