package dev.beli.skylyrics.gui;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;

/**
 * Opens a clip box (a scissor) only when something drawn in it could show. Minecraft 26.2 and newer stop the game
 * ("Scissor size must be >0") when a clip box ends up empty in the window's pixels: the game drops whatever is drawn in a
 * box that lies off the GUI, but a box that is on the GUI and past the window's right or bottom edge is cut to nothing at
 * the edge and then refused. That happens when the GUI is a frame behind the window (made smaller, the GUI not laid out
 * again yet). Older versions draw nothing in such a box, so skipping it changes nothing they show.
 */
public final class ClipBox {
	private ClipBox() {
	}

	/**
	 * Opens the clip from {@code left, top} to {@code right, bottom} (as {@code GuiGraphics.enableScissor} takes it) and
	 * returns true; or returns false without opening it when nothing drawn in it could show: the box is empty, off the GUI,
	 * or past the window's edge. Draw what it clips, and close it with {@code g.disableScissor()}, only after true.
	 */
	public static boolean open(GuiGraphics g, int left, int top, int right, int bottom) {
		if (right <= left || bottom <= top) {
			return false;
		}
		// Where the game puts the box: through the pose, as its enableScissor does (1.21.1's ignores the pose)
		ScreenRectangle box = new ScreenRectangle(left, top, right - left, bottom - top);
		//? if >=1.21.6 {
		box = box.transformAxisAligned(g.pose());
		//?} else if >=1.21.2 {
		/*box = box.transformAxisAligned(g.pose().last().pose());
		*///?}
		Window window = Minecraft.getInstance().getWindow();
		if (!opens(box.left(), box.top(), box.right(), box.bottom(), g.guiWidth(), g.guiHeight(), window.getWidth(),
				window.getHeight(), (int) window.getGuiScale())) {
			return false;
		}
		g.enableScissor(left, top, right, bottom);
		return true;
	}

	/**
	 * Whether a clip box (in GUI units, where the game puts it) shows any of the window. The GUI's size and the window's
	 * (in pixels) are both asked for: they disagree for a frame when the window changed size and the GUI didn't follow yet,
	 * and the game cuts the box at the window's edge, not the GUI's.
	 */
	static boolean opens(int left, int top, int right, int bottom, int guiWidth, int guiHeight, int windowWidth, int windowHeight,
			int guiScale) {
		// The part on the GUI; the game drops what is drawn in a box wholly off it
		int l = Math.max(left, 0), t = Math.max(top, 0), r = Math.min(right, guiWidth), b = Math.min(bottom, guiHeight);
		if (l >= r || t >= b) {
			return false;
		}
		// The part in the window, in its pixels
		return (long) l * guiScale < Math.min((long) r * guiScale, windowWidth)
				&& (long) t * guiScale < Math.min((long) b * guiScale, windowHeight);
	}
}
