package dev.beli.skylyrics.mixin;

import java.util.function.Supplier;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft packs a font's letters onto 256-pixel pages. Ours are drawn six times over size so they stay sharp up
 * close, so one font spills over several pages, and every switch from one page to the next inside a line ends a draw
 * call; with shaders each of those is costly, and the glow's extra copies multiply them. Our own pages (named
 * "skylyrics:...") are 1024 pixels, so a whole font fits on one. Left out when ImmediatelyFast is installed, since it
 * enlarges every font page itself (see {@link SkyLyricsMixinPlugin}).
 */
@Mixin(FontTexture.class)
abstract class FontPageMixin {
	@Unique
	private static final int SKYLYRICS_PAGE = 1024;
	/** This page's width and height when it's one of ours, 0 for the vanilla size. */
	@Unique
	private int skylyrics$page;

	@Inject(method = "<init>", at = @At("CTOR_HEAD"))
	private void skylyrics$pickSize(Supplier<String> name, GlyphRenderTypes renderTypes, boolean colored,
			CallbackInfo ci) {
		if (name.get().startsWith("skylyrics:")) {
			skylyrics$page = SKYLYRICS_PAGE;
		}
	}

	/** The page's texture and its free-space tree. */
	@ModifyConstant(method = "<init>", constant = @Constant(intValue = 256))
	private int skylyrics$pageSize(int size) {
		return skylyrics$page > 0 ? skylyrics$page : size;
	}

	/** Where each letter sits on the page, as a fraction of its size. */
	@ModifyConstant(method = "add", constant = @Constant(floatValue = 256.0F))
	private float skylyrics$pageScale(float size) {
		return skylyrics$page > 0 ? skylyrics$page : size;
	}
}
