package probe;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;

public class ProbeClient implements ClientModInitializer {
	static final Class<?>[] SEEN = {
		Minecraft.class, GuiGraphics.class, Font.class, PoseStack.class, RenderPipelines.class,
		HudElementRegistry.class, WorldRenderEvents.class,
	};

	@Override
	public void onInitializeClient() {
		for (Class<?> c : SEEN) System.out.println("probe sees " + c.getName());
	}
}
