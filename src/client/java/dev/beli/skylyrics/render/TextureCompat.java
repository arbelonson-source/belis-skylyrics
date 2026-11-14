package dev.beli.skylyrics.render;

//? if <1.21.2 {
/*import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;

// Minecraft 1.21.1's textures: smooth scaling with clamped edges has to be set on the GL texture after every upload,
// and pixels are written as ABGR.
public final class TextureCompat {
	private TextureCompat() {
	}

	// Smooth scaling and clamped edges, for the texture bound after this call
	public static void smooth(AbstractTexture texture) {
		texture.bind();
		GlStateManager._texParameter(3553, 10241, 9729);
		GlStateManager._texParameter(3553, 10240, 9729);
		GlStateManager._texParameter(3553, 10242, 33071);
		GlStateManager._texParameter(3553, 10243, 33071);
	}

	// A picture's pixel as 0xAARRGGBB, like newer versions' setPixel
	public static void setPixel(NativeImage image, int x, int y, int argb) {
		image.setPixelRGBA(x, y, argb & 0xFF00FF00 | (argb >> 16 & 0xFF) | (argb & 0xFF) << 16);
	}

	// A dynamic texture that keeps its smooth look when its pixels are uploaded again (an upload resets the filter)
	public static class Smooth extends DynamicTexture {
		public Smooth(NativeImage image) {
			super(image);
		}

		@Override
		public void upload() {
			super.upload();
			smooth(this);
		}
	}
}
*///?}
