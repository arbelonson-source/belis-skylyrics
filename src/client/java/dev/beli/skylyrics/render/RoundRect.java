package dev.beli.skylyrics.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Rounded rectangles drawn as four textured corners plus at most three flat fills (a pill, an upright bar or a disc in
 * fewer pieces still: corners that meet are drawn together). Drawing the curves row by
 * row costs hundreds of GUI elements per shape, and Minecraft compares every new element against the ones
 * before it, so the top bar alone used to eat a tenth of the frame.
 */
final class RoundRect {
	/** Quarter-disc sizes; the smallest one at least as big as the radius is used, so it is never shrunk more than 2×. */
	private static final int[] SIZES = {4, 8, 16, 32, 64, 128};
	private static final @Nullable Identifier[] IDS = new Identifier[SIZES.length];

	private RoundRect() {}

	/** Draws in the current pose's units; corners are snapped to whole units like {@link GuiGraphics#fill}. */
	static void draw(GuiGraphics g, float x, float y, float w, float h, float r, int color) {
		if ((color >>> 24) == 0 || w <= 0 || h <= 0) {
			return;
		}
		int x0 = Math.round(x), y0 = Math.round(y), x1 = Math.round(x + w), y1 = Math.round(y + h);
		int c = corner(x1 - x0, y1 - y0, r);
		int i = sizeIndex(c);
		Identifier id = c <= 0 ? null : texture(i);
		if (id == null) {
			g.fill(x0, y0, x1, y1, color);
			return;
		}
		ToGui out = TO_GUI;
		out.g = g;
		out.id = id;
		out.t = 2 * SIZES[i];
		out.color = color;
		try {
			pieces(x0, y0, x1, y1, c, SIZES[i], out);
		} finally {
			out.g = null;
		}
	}

	/** Where the pieces of a rounded rectangle go: parts of the disc texture, and flat fills. */
	interface Pieces {
		/** The part of the disc texture ({@code 2q} across) at {@code u, v}, {@code uw} by {@code vh}, drawn at {@code x, y}, {@code w} by {@code h}. */
		void disc(int x, int y, int w, int h, int u, int v, int uw, int vh);

		void fill(int x0, int y0, int x1, int y1);
	}

	/** Sends the pieces to the game; one reused instance, set up by {@link #draw} (render thread only). */
	private static final class ToGui implements Pieces {
		GuiGraphics g;
		Identifier id;
		int t, color;

		@Override
		public void disc(int x, int y, int w, int h, int u, int v, int uw, int vh) {
			g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, uw, vh, t, t, color);
		}

		@Override
		public void fill(int x0, int y0, int x1, int y1) {
			g.fill(x0, y0, x1, y1, color);
		}
	}

	private static final ToGui TO_GUI = new ToGui();

	/**
	 * The pieces of the box from (x0, y0) to (x1, y1) with corners {@code c} across, from the texture with quarters
	 * {@code q} across: four corners and the fills between them. Where two corners meet (a pill as tall as its corners, an
	 * upright bar as wide, a disc) they are one stretch of the texture, drawn as one piece: each piece is a GUI element the
	 * game builds and checks against the elements before it, every frame.
	 */
	static void pieces(int x0, int y0, int x1, int y1, int c, int q, Pieces out) {
		// Corners never overlap (see corner): the left and right ones meet, or leave a gap that a fill closes
		boolean meetX = x1 - c == x0 + c, meetY = y1 - c == y0 + c;
		if (meetX && meetY) {
			out.disc(x0, y0, 2 * c, 2 * c, 0, 0, 2 * q, 2 * q);
			return;
		}
		if (meetY) {
			// A pill lying down: a half disc at each end, the middle between them
			out.disc(x0, y0, c, 2 * c, 0, 0, q, 2 * q);
			out.disc(x1 - c, y0, c, 2 * c, q, 0, q, 2 * q);
			out.fill(x0 + c, y0, x1 - c, y1);
			return;
		}
		if (meetX) {
			// An upright bar: a half disc at the top and the bottom, the middle between them
			out.disc(x0, y0, 2 * c, c, 0, 0, 2 * q, q);
			out.disc(x0, y1 - c, 2 * c, c, 0, q, 2 * q, q);
			out.fill(x0, y0 + c, x1, y1 - c);
			return;
		}
		out.disc(x0, y0, c, c, 0, 0, q, q);
		out.disc(x1 - c, y0, c, c, q, 0, q, q);
		out.disc(x0, y1 - c, c, c, 0, q, q, q);
		out.disc(x1 - c, y1 - c, c, c, q, q, q, q);
		if (x1 - c > x0 + c) {
			out.fill(x0 + c, y0, x1 - c, y1);
		}
		if (y1 - c > y0 + c) {
			out.fill(x0, y0 + c, x0 + c, y1 - c);
			out.fill(x1 - c, y0 + c, x1, y1 - c);
		}
	}

	/**
	 * The corner size for a w by h box with radius r (pixels): r rounded, at most half the box's width and height rounded
	 * down, so two corners never share a row or column (an odd-sized see-through pill would get a darker line there).
	 */
	static int corner(int w, int h, float r) {
		return Math.min(Math.round(r), Math.min(w, h) / 2);
	}

	/** The quarter size of the texture a corner {@code c} across is drawn from. */
	static int quarter(int c) {
		return SIZES[sizeIndex(c)];
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

	/** The ARGB pixels of the corner texture for size index {@code i}, row by row: white with the disc coverage as alpha. */
	static int[] cornerPixels(int i) {
		int d = 2 * SIZES[i];
		int[] pixels = new int[d * d];
		for (int py = 0; py < d; py++) {
			for (int px = 0; px < d; px++) {
				pixels[py * d + px] = Math.round(coverage(px, py, d) * 255) << 24 | 0xFFFFFF;
			}
		}
		return pixels;
	}

	/** Size count, for tests that check every texture. */
	static int sizeCount() {
		return SIZES.length;
	}

	/** The texture for size index {@code i}, built and registered the first time that size is drawn; null off the render thread. */
	private static @Nullable Identifier texture(int i) {
		Identifier id = IDS[i];
		if (id != null) {
			return id;
		}
		if (!RenderSystem.isOnRenderThread()) {
			return null;
		}
		int d = 2 * SIZES[i];
		int[] pixels = cornerPixels(i);
		NativeImage image = new NativeImage(d, d, false);
		for (int py = 0; py < d; py++) {
			for (int px = 0; px < d; px++) {
				image.setPixel(px, py, pixels[py * d + px]);
			}
		}
		id = Identifier.fromNamespaceAndPath("skylyrics", "dynamic/round_" + d);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "SkyLyrics round corner", image) {
			{
				sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
			}
		});
		IDS[i] = id;
		return id;
	}
}
