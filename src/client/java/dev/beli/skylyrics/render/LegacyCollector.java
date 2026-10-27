package dev.beli.skylyrics.render;

//? if <1.21.9 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FormattedCharSequence;

// Stand-in for the submit collectors that only exist from Minecraft 1.21.9 on: older versions have no
// deferred drawing, so everything goes straight into the world's buffer source.
public final class LegacyCollector {
	public interface CustomGeometryRenderer {
		void render(PoseStack.Pose pose, VertexConsumer consumer);
	}

	private final MultiBufferSource buffers;
	private final boolean early;

	public LegacyCollector(MultiBufferSource buffers) {
		this(buffers, false);
	}

	private LegacyCollector(MultiBufferSource buffers, boolean early) {
		this.buffers = buffers;
		this.early = early;
	}

	// A negative batch is drawn at once, before the text and everything else still waiting in the buffers.
	public LegacyCollector order(int batch) {
		return new LegacyCollector(buffers, batch < 0);
	}

	public void submitCustomGeometry(PoseStack pose, RenderType type, CustomGeometryRenderer renderer) {
		renderer.render(pose.last(), buffers.getBuffer(type));
		if (early && buffers instanceof MultiBufferSource.BufferSource source) {
			source.endBatch(type);
		}
	}

	public void submitText(PoseStack pose, float x, float y, FormattedCharSequence text, boolean shadow,
			Font.DisplayMode mode, int light, int argb, int background, int outline) {
		Minecraft.getInstance().font.drawInBatch(text, x, y, argb, shadow, pose.last().pose(), buffers, mode, background, light);
	}
}
*///?}
