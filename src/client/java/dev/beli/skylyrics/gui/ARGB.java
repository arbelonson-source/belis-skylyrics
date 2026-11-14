package dev.beli.skylyrics.gui;

//? if <1.21.2 {
/*import net.minecraft.util.Mth;

// Minecraft 1.21.1 has no net.minecraft.util.ARGB; this is the small part of it the mod uses.
public final class ARGB {
	private ARGB() {
	}

	public static int alpha(int argb) {
		return argb >>> 24;
	}

	public static int color(int alpha, int rgb) {
		return (alpha & 0xFF) << 24 | rgb & 0xFFFFFF;
	}

	public static int white(float alpha) {
		return color(Math.round(Mth.clamp(alpha, 0, 1) * 255), 0xFFFFFF);
	}

	public static int lerp(float t, int from, int to) {
		int a = Math.round(Mth.lerp(t, alpha(from), alpha(to)));
		int r = Math.round(Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF));
		int g = Math.round(Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF));
		int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
		return a << 24 | r << 16 | g << 8 | b;
	}
}
*///?}
