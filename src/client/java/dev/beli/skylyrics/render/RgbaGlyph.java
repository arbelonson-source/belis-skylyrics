package dev.beli.skylyrics.render;

/**
 * A TrueType letter that can be stored as a full picture instead of a see-through mask. Some shader packs (AstraLex)
 * draw mask letters in the world as solid black boxes, while full pictures, like Minecraft's own letters, draw right
 * everywhere. Only letters of our own fonts are switched, by the font page they are added to.
 */
public interface RgbaGlyph {
	void skylyrics$asPicture();
}
