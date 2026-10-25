package probe;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

// 26.x names: GuiGraphics -> GuiGraphicsExtractor, WorldRenderEvents -> level.LevelRenderEvents
public class ProbeClient implements ClientModInitializer {
	static final Class<?>[] SEEN = {
		Minecraft.class, GuiGraphicsExtractor.class, Font.class, PoseStack.class, RenderPipelines.class,
		HudElementRegistry.class, LevelRenderEvents.class,
	};

	@Override
	public void onInitializeClient() {
		for (Class<?> c : SEEN) System.out.println("probe sees " + c.getName());
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("probe", "hud"),
				(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker delta) -> g.text(Minecraft.getInstance().font, "probe", 2, 2, 0xFFFFFFFF));
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			PoseStack pose = context.poseStack();
			pose.pushPose();
			pose.popPose();
		});
	}
}
