package dev.beli.skylyrics.util;

import net.minecraft.client.Minecraft;

/**
 * Which glyph set the game draws with. Force Unicode Font and Japanese glyph variants change glyph widths in place
 * (the Options callback calls Minecraft.updateFontOptions, then FontManager.updateOptions): no resource reload, and the
 * same Language and Font objects, so a cache of widths or cuts has to watch this value too.
 */
public final class GlyphMode {
	private GlyphMode() {
	}

	/** A small number that changes whenever either option does: 1 for Force Unicode Font, 2 for Japanese glyph variants. */
	public static int current() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.options == null) {
			return 0;
		}
		return (mc.options.forceUnicodeFont().get() ? 1 : 0) | (mc.options.japaneseGlyphVariants().get() ? 2 : 0);
	}
}
