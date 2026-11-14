package dev.beli.skylyrics.render;

//? if <1.21.5 {
/*// Minecraft 1.21.4 gives a font page no name, so the mixins can't tell ours from the others by it. The font set
// that is adding a letter says whether it is one of ours just before it makes a page, and the page reads it here.
public final class FontPageFlag {
	// True while the font set about to create a page is one of ours. Only touched on the render thread.
	public static boolean ours;

	private FontPageFlag() {
	}
}
*///?}
