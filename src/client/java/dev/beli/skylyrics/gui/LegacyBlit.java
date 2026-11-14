package dev.beli.skylyrics.gui;

//? if <1.21.2 {
/*import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Minecraft 1.21.1 blits without render types or a colour; the colour goes in through the shader colour instead.
public final class LegacyBlit {
	private LegacyBlit() {
	}

	// Without a tint
	public static void blit(GuiGraphics g, ResourceLocation texture, int x, int y, float u, float v, int width, int height,
			int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
		blit(g, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, 0xFFFFFFFF);
	}

	// Same arguments as the newer GuiGraphics.blit after its render type: x, y, u, v, drawn size, region size, texture size, tint
	public static void blit(GuiGraphics g, ResourceLocation texture, int x, int y, float u, float v, int width, int height,
			int regionWidth, int regionHeight, int textureWidth, int textureHeight, int argb) {
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShaderColor((argb >> 16 & 0xFF) / 255f, (argb >> 8 & 0xFF) / 255f, (argb & 0xFF) / 255f,
				(argb >>> 24) / 255f);
		g.blit(texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
		RenderSystem.setShaderColor(1, 1, 1, 1);
	}
}
*///?}
