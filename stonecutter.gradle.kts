plugins {
	id("dev.kikugie.stonecutter")
}

// The version the code in src/ is currently written for
stonecutter active "1.21.11"

stonecutter parameters {
	// Names Minecraft changed: written the 1.21.11 way in src/, swapped when building newer versions
	replacements {
		string(current.parsed >= "26.2") {
			replace("GuiGraphics", "GuiGraphicsExtractor")
			replace(".drawString(", ".text(")
			replace(".drawCenteredString(", ".centeredText(")
		}
		// 26.3 moved the low-level graphics classes into com.mojang.renderpearl
		string(current.parsed >= "26.3") {
			replace("com.mojang.blaze3d.textures.", "com.mojang.renderpearl.api.textures.")
			replace("com.mojang.blaze3d.buffers.GpuBufferSlice", "com.mojang.renderpearl.api.buffers.GpuBufferSlice")
			replace("com.mojang.blaze3d.pipeline.RenderPipeline", "com.mojang.renderpearl.api.pipeline.RenderPipeline")
			replace("Lcom/mojang/blaze3d/pipeline/RenderPipeline;", "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;")
			replace("pose.mulPose(", "pose.rotate(")
			replace("RenderTypes.textBackground()", "RenderTypes.debugQuads()")
		}
	}
}
