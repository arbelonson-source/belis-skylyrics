plugins {
	id("dev.kikugie.stonecutter")
}

// The version the code in src/ is currently written for
stonecutter active "1.21.11"

stonecutter parameters {
	// Names Minecraft changed: written the 1.21.11 way in src/, swapped when building newer versions
	replacements {
		// Only forwards: run backwards for older versions it would also turn lyric lines' text() into drawString()
		if (current.parsed >= "1.21.11") string(current.parsed >= "26.1") {
			replace("GuiGraphics", "GuiGraphicsExtractor")
			replace(".drawString(", ".text(")
			replace(".drawCenteredString(", ".centeredText(")
		}
		// 1.21.10 and older use the earlier names. Only forwards, so newer versions never get them backwards.
		if (current.parsed < "1.21.11") {
			regex(true) {
				replace("\\bIdentifier\\b", "ResourceLocation", "\\bResourceLocation\\b", "Identifier")
			}
			regex(true) {
				replace("\\bRenderTypes\\b", "RenderType", "\\bRenderType\\b", "RenderTypes")
			}
			string(true) {
				replace("net.minecraft.client.renderer.rendertype.", "net.minecraft.client.renderer.")
				replace("net.minecraft.client.model.player.PlayerModel", "net.minecraft.client.model.PlayerModel")
				replace("camera.yRot()", "camera.getYRot()")
				replace("camera.xRot()", "camera.getXRot()")
				replace("camera.blockPosition()", "camera.getBlockPosition()")
				replace("ARGB.srgbLerp(", "ARGB.lerp(")
				replace("sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);",
					"setClamp(true); setFilter(true, false);")
			}
		}
		// 1.21.8 and older have no input event classes: use the small stand-ins in LegacyInput. Only forwards.
		if (current.parsed < "1.21.9") string(true) {
			replace("FontDescription", "net.minecraft.resources.ResourceLocation")
			// No deferred drawing before 1.21.9: LegacyCollector draws straight into the world's buffers
			replace("import net.minecraft.client.renderer.OrderedSubmitNodeCollector;", "import dev.beli.skylyrics.render.LegacyCollector;")
			replace("import net.minecraft.client.renderer.SubmitNodeCollector;", "import dev.beli.skylyrics.render.LegacyCollector;")
			replace("OrderedSubmitNodeCollector", "LegacyCollector")
			replace("SubmitNodeCollector", "LegacyCollector")
			replace("rendering.v1.world.WorldRender", "rendering.v1.WorldRender")
			replace("context.commandQueue()", "new dev.beli.skylyrics.render.LegacyCollector(context.consumers())")
			replace("context.matrices()", "context.matrixStack()")
			replace("AvatarRenderState", "PlayerRenderState")
			replace("AvatarRenderer", "PlayerRenderer")
			replace("import net.minecraft.client.input.KeyEvent;", "import dev.beli.skylyrics.gui.LegacyInput.KeyEvent;")
			replace("import net.minecraft.client.input.MouseButtonEvent;", "import dev.beli.skylyrics.gui.LegacyInput.MouseButtonEvent;")
			replace("import net.minecraft.client.input.CharacterEvent;", "import dev.beli.skylyrics.gui.LegacyInput.CharacterEvent;")
			replace("super.mouseClicked(event, doubleClick)", "super.mouseClicked(event.x(), event.y(), event.button())")
			replace("super.keyPressed(event)", "super.keyPressed(event.key(), event.scancode(), event.modifiers())")
			replace("super.mouseDragged(event, dx, dy)", "super.mouseDragged(event.x(), event.y(), event.button(), dx, dy)")
			replace("super.mouseReleased(event)", "super.mouseReleased(event.x(), event.y(), event.button())")
			replace("open.matches(event)", "open.matches(event.key(), event.scancode())")
			replace("key.matches(event)", "key.matches(event.key(), event.scancode())")
		}
		// 26.3 moved the low-level graphics classes into com.mojang.renderpearl
		if (current.parsed >= "26.3") string(true) {
			replace("com.mojang.blaze3d.textures.", "com.mojang.renderpearl.api.textures.")
			replace("com.mojang.blaze3d.buffers.GpuBufferSlice", "com.mojang.renderpearl.api.buffers.GpuBufferSlice")
			replace("com.mojang.blaze3d.pipeline.RenderPipeline", "com.mojang.renderpearl.api.pipeline.RenderPipeline")
			replace("Lcom/mojang/blaze3d/pipeline/RenderPipeline;", "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;")
			replace("pose.mulPose(", "pose.rotate(")
			replace("RenderTypes.textBackground()", "RenderTypes.debugQuads()")
		}
	}
}
