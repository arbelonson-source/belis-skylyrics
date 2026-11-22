package dev.beli.skylyrics.render;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * The render type the painted skies (Synthwave, Moonlight) go out through: vanilla's text background type, with its
 * shader, its blending and its fog, hidden behind nearer blocks like it, but leaving the depth as it was.
 * <p>
 * The text background type itself marks how near everything it draws is, and vanilla draws its clouds after the sky
 * (1.21.1 to 26.2), so every cloud farther away than the painted sky, which sits just short of the render-distance fog,
 * was hidden: at render distance 12 the clouds ended in a hard circle about 55 degrees up, and at 6 none showed at all.
 * Nothing needs the painted sky's depth: the blocks it stands behind are drawn before it, and the lyrics and the rest of
 * the sky effects after it (see {@link SkyEffects}). On 26.3, which has no text background type, the debug quads stand in
 * for it, and they already leave no depth.
 * <p>
 * With a shader pack on, the painted sky keeps the text background type: Iris draws that one with the pack's own
 * program, and has none for this one.
 */
final class PaintedSkyType {
	private PaintedSkyType() {}

	/** The type to draw the painted sky with; {@code shaders} when a shader pack is on. */
	static RenderType get(boolean shaders) {
		//? if >=26.3 {
		/*return RenderTypes.textBackground();
		*///?} else {
		return shaders ? RenderTypes.textBackground() : TYPE;
		//?}
	}

	//? if >=26.3 {
	/*// Nothing of its own: the debug quads that stand in for the text background type already leave no depth
	*///?} else if >=26.2 {
	/*static final com.mojang.blaze3d.pipeline.RenderPipeline PIPELINE = com.mojang.blaze3d.pipeline.RenderPipeline.builder(
					net.minecraft.client.renderer.RenderPipelines.GLOBALS_SNIPPET)
			.withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.MATRICES_PROJECTION)
			.withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.FOG)
			.withLocation(dev.beli.skylyrics.SkyLyricsClient.id("pipeline/painted_sky"))
			.withVertexShader("core/text_background")
			.withFragmentShader("core/text_background")
			.withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(
					com.mojang.blaze3d.pipeline.DepthStencilState.DEFAULT.depthTest(), false))
			.withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER2)
			.withVertexBinding(0, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_LIGHTMAP)
			.withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.QUADS)
			.build();
	private static final RenderType TYPE = RenderType.create("skylyrics_painted_sky",
			net.minecraft.client.renderer.rendertype.RenderSetup.builder(PIPELINE).useLightmap().sortOnUpload().createRenderSetup());
	*///?} else if >=26.1 {
	/*static final com.mojang.blaze3d.pipeline.RenderPipeline PIPELINE = com.mojang.blaze3d.pipeline.RenderPipeline.builder(
					net.minecraft.client.renderer.RenderPipelines.TEXT_SNIPPET, net.minecraft.client.renderer.RenderPipelines.FOG_SNIPPET)
			.withLocation(dev.beli.skylyrics.SkyLyricsClient.id("pipeline/painted_sky"))
			.withVertexShader("core/rendertype_text_background")
			.withFragmentShader("core/rendertype_text_background")
			.withSampler("Sampler2")
			.withVertexFormat(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_LIGHTMAP, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
			.withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(
					com.mojang.blaze3d.pipeline.DepthStencilState.DEFAULT.depthTest(), false))
			.build();
	private static final RenderType TYPE = RenderType.create("skylyrics_painted_sky",
			net.minecraft.client.renderer.rendertype.RenderSetup.builder(PIPELINE).useLightmap().sortOnUpload().createRenderSetup());
	*///?} else if >=1.21.5 {
	static final com.mojang.blaze3d.pipeline.RenderPipeline PIPELINE = com.mojang.blaze3d.pipeline.RenderPipeline.builder(
					net.minecraft.client.renderer.RenderPipelines.TEXT_SNIPPET, net.minecraft.client.renderer.RenderPipelines.FOG_SNIPPET)
			.withLocation(dev.beli.skylyrics.SkyLyricsClient.id("pipeline/painted_sky"))
			.withVertexShader("core/rendertype_text_background")
			.withFragmentShader("core/rendertype_text_background")
			.withSampler("Sampler2")
			.withVertexFormat(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_LIGHTMAP, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
			.withDepthWrite(false)
			.build();
	//? if >=1.21.11 {
	private static final RenderType TYPE = RenderType.create("skylyrics_painted_sky",
			net.minecraft.client.renderer.rendertype.RenderSetup.builder(PIPELINE).useLightmap().sortOnUpload().createRenderSetup());
	//?} else {
	/*private static final RenderType TYPE = RenderType.create("skylyrics_painted_sky", 1536, false, true, PIPELINE,
			RenderType.CompositeState.builder().setTextureState(RenderType.NO_TEXTURE).setLightmapState(RenderType.LIGHTMAP)
					.createCompositeState(false));
	*///?}
	//?} else {
	/*private static final RenderType TYPE = RenderType.create("skylyrics_painted_sky", com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_LIGHTMAP,
			com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 1536, false, true,
			RenderType.CompositeState.builder()
					.setShaderState(RenderType.RENDERTYPE_TEXT_BACKGROUND_SHADER)
					.setTextureState(RenderType.NO_TEXTURE)
					.setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
					.setLightmapState(RenderType.LIGHTMAP)
					.setWriteMaskState(RenderType.COLOR_WRITE)
					.createCompositeState(false));
	*///?}
}
