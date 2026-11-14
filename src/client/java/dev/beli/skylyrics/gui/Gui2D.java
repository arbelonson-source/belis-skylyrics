package dev.beli.skylyrics.gui;

//? if <1.21.6 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;

// Minecraft 1.21.5 and older have a 3D pose stack in the GUI; these give it the 2D calls newer versions have.
public final class Gui2D {
	private Gui2D() {
	}

	public static void translate(GuiGraphics g, float x, float y) {
		PoseStack pose = g.pose();
		pose.translate(x, y, 0);
	}

	public static void scale(GuiGraphics g, float x, float y) {
		PoseStack pose = g.pose();
		pose.scale(x, y, 1);
	}

	public static void rotate(GuiGraphics g, float radians) {
		PoseStack pose = g.pose();
		pose.mulPose(Axis.ZP.rotation(radians));
	}

	// Older versions have no layers: whatever is drawn later is already on top.
	public static void nextLayer(GuiGraphics g) {
	}
}
*///?}
