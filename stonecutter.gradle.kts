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
				// 1.21.1's textures have no setClamp: a helper sets filter and edges together
				replace("sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);",
					if (current.parsed < "1.21.2") "dev.beli.skylyrics.render.TextureCompat.smooth(this);"
					else "setClamp(true); setFilter(true, false);")
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
		// 1.21.9 and 1.21.10 have no hasControlDownWithQuirk: there hasControlDown() is already Cmd on a Mac (1.21.11 split them)
		if (current.parsed >= "1.21.9" && current.parsed < "1.21.11") string(true) {
			replace("hasControlDownWithQuirk()", "hasControlDown()")
		}
		// 1.21.5 and older draw the GUI with a 3D PoseStack and RenderType functions instead of 2D matrices and pipelines
		if (current.parsed < "1.21.6") string(true) {
			replace("import net.minecraft.client.renderer.RenderPipelines;", "import net.minecraft.client.renderer.RenderType;")
			// (1.21.1 has no blit with a render type at all: its blits go through LegacyBlit, below)
			if (current.parsed >= "1.21.2") replace("RenderPipelines.GUI_TEXTURED", "RenderType::guiTextured")
			replace("g.pose().pushMatrix()", "g.pose().pushPose()")
			replace("g.pose().popMatrix()", "g.pose().popPose()")
			replace("g.pose().translate(", "dev.beli.skylyrics.gui.Gui2D.translate(g, ")
			replace("g.pose().scale(", "dev.beli.skylyrics.gui.Gui2D.scale(g, ")
			replace("g.pose().rotate(", "dev.beli.skylyrics.gui.Gui2D.rotate(g, ")
			replace("g.nextStratum()", "dev.beli.skylyrics.gui.Gui2D.nextLayer(g)")
			replace("camera.position()", "camera.getPosition()")
		}
		// 1.21.4 has no GpuTexture: textures are plain GL ids, so no sampler objects, no texture names, no scaled mouse helper
		if (current.parsed < "1.21.5") string(true) {
			replace("import com.mojang.blaze3d.textures.FilterMode;", "import java.lang.Object;")
			if (current.parsed >= "1.21.2") replace("new DynamicTexture(() -> \"SkyLyrics album art\", image)", "new DynamicTexture(image)")
			if (current.parsed >= "1.21.2") replace("new DynamicTexture(() -> \"SkyLyrics cover\", image)", "new DynamicTexture(image)")
			if (current.parsed >= "1.21.2") replace("new DynamicTexture(() -> \"SkyLyrics round corner\", image)", "new DynamicTexture(image)")
			replace("minecraft.mouseHandler.getScaledXPos(minecraft.getWindow())",
				"(minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth())")
			replace("minecraft.mouseHandler.getScaledYPos(minecraft.getWindow())",
				"(minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight())")
		}
		// 1.21.1 predates the 1.21.2 rendering rewrite: coloured blits, ARGB, the timer and a few names differ
		if (current.parsed < "1.21.2") string(true) {
			replace("g.blit(RenderPipelines.GUI_TEXTURED, ", "dev.beli.skylyrics.gui.LegacyBlit.blit(g, ")
			replace("import net.minecraft.util.ARGB;", "import dev.beli.skylyrics.gui.ARGB;")
			replace("image.setPixel(", "dev.beli.skylyrics.render.TextureCompat.setPixel(image, ")
			replace("out.setPixel(", "dev.beli.skylyrics.render.TextureCompat.setPixel(out, ")
			replace("new DynamicTexture(() -> \"SkyLyrics album art\", image)", "new dev.beli.skylyrics.render.TextureCompat.Smooth(image)")
			replace("new DynamicTexture(() -> \"SkyLyrics cover\", image)", "new dev.beli.skylyrics.render.TextureCompat.Smooth(image)")
			replace("new DynamicTexture(() -> \"SkyLyrics round corner\", image)", "new dev.beli.skylyrics.render.TextureCompat.Smooth(image)")
			replace("client.getToastManager()", "client.getToasts()")
			replace("mc.getDeltaTracker()", "mc.getTimer()")
			replace("mc.getFramerateLimitTracker().getFramerateLimit()", "(mc.level == null && (mc.screen != null || mc.getOverlay() != null) ? 60 : mc.getWindow().getFramerateLimit())")
			replace("DimensionSpecialEffects.SkyType.OVERWORLD", "DimensionSpecialEffects.SkyType.NORMAL")
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
