package dev.beli.skylyrics.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Rounded rectangles drawn as four textured corners plus at most three flat fills. Drawing the curves row by
 * row costs hundreds of GUI elements per shape, and Minecraft compares every new element against the ones
 * before it, so the top bar alone used to eat a tenth of the frame.
 */
final class RoundRect {
	/** Quarter-disc sizes; the smallest one at least as big as the radius is used, so it is never shrunk more than 2×. */
	private static final int[] SIZES = {4, 8, 16, 32, 64, 128};
	private static final Identifier[] IDS = new Identifier[SIZES.length];
	private static boolean ready;

	private RoundRect() {}

	/** Draws in the current pose's units; corners are snapped to whole units like {@link GuiGraphics#fill}. */
	static void draw(GuiGraphics g, float x, float y, float w, float h, float r, int color) {
		if ((color >>> 24) == 0 || w <= 0 || h <= 0) {
			return;
		}
		int x0 = Math.round(x), y0 = Math.round(y), x1 = Math.round(x + w), y1 = Math.round(y + h);
		int c = Math.round(Math.min(r, Math.min(x1 - x0, y1 - y0) / 2f));
		if (c <= 0 || !ensureTextures()) {
			g.fill(x0, y0, x1, y1, color);
			return;
		}
		int i = sizeIndex(c);
		Identifier id = IDS[i];
		int q = SIZES[i], t = 2 * q;
		g.blit(RenderPipelines.GUI_TEXTURED, id, x0, y0, 0, 0, c, c, q, q, t, t, color);
		g.blit(RenderPipelines.GUI_TEXTURED, id, x1 - c, y0, q, 0, c, c, q, q, t, t, color);
		g.blit(RenderPipelines.GUI_TEXTURED, id, x0, y1 - c, 0, q, c, c, q, q, t, t, color);
		g.blit(RenderPipelines.GUI_TEXTURED, id, x1 - c, y1 - c, q, q, c, c, q, q, t, t, color);
		if (x1 - c > x0 + c) {
			g.fill(x0 + c, y0, x1 - c, y1, color);
		}
		if (y1 - c > y0 + c) {
			g.fill(x0, y0 + c, x0 + c, y1 - c, color);
			g.fill(x1 - c, y0 + c, x1, y1 - c, color);
		}
	}

	static int sizeIndex(int radius) {
		for (int i = 0; i < SIZES.length; i++) {
			if (SIZES[i] >= radius) {
				return i;
			}
		}
		return SIZES.length - 1;
	}

	/** How much of the pixel at (px, py) a disc of the given diameter covers, 0..1, from 4×4 samples. */
	static float coverage(int px, int py, int diameter) {
		float c = diameter / 2f, rr = c * c;
		int inside = 0;
		for (int sy = 0; sy < 4; sy++) {
			for (int sx = 0; sx < 4; sx++) {
				float dx = px + (sx + 0.5f) / 4 - c, dy = py + (sy + 0.5f) / 4 - c;
				if (dx * dx + dy * dy <= rr) {
					inside++;
				}
			}
		}
		return inside / 16f;
	}

	private static boolean ensureTextures() {
		if (ready) {
			return true;
		}
		if (!RenderSystem.isOnRenderThread()) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		for (int i = 0; i < SIZES.length; i++) {
			int d = 2 * SIZES[i];
			NativeImage image = new NativeImage(d, d, false);
			for (int py = 0; py < d; py++) {
				for (int px = 0; px < d; px++) {
					image.setPixel(px, py, Math.round(coverage(px, py, d) * 255) << 24 | 0xFFFFFF);
				}
			}
			IDS[i] = Identifier.fromNamespaceAndPath("skylyrics", "dynamic/round_" + d);
			mc.getTextureManager().register(IDS[i], new DynamicTexture(() -> "SkyLyrics round corner", image) {
				{
					sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
				}
			});
		}
		ready = true;
		return true;
	}
}
