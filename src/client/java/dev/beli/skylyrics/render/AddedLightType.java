package dev.beli.skylyrics.render;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * The render type the sky's added light goes out through (the aurora, the nebula, the eclipse's glow and milky way,
 * the galaxy, the comet, the constellations on the vanilla sky, the sparkles and the shooting stars): vanilla's dragon
 * rays type, with its shader, its added light and its fog, hidden behind nearer blocks like it, but leaving the depth as
 * it was.
 * <p>
 * Up to 26.1 the dragon rays type marks no depth, and it is used as it is. From 26.2 it marks how near everything it
 * draws is, and the added light sits inside the world (the aurora at 0.45 of the render distance, at most 110 blocks).
 * Vanilla draws the lyrics after the sky, then its clouds, so every cloud behind a curtain, a nebula cloud, the corona or
 * the milky way was hidden in that shape, and with See through blocks off the lyrics in the Sky, farther away than the
 * aurora at a low render distance or a big Distance, lost every letter a curtain, a cloud, a sparkle or a shooting star
 * crossed. Nothing needs its depth: the blocks it stands behind are drawn before it, and light adds up in any order.
 * <p>
 * With a shader pack on, the vanilla type is kept: Iris draws that one with the pack's own program, and has none for
 * this one (as for {@link PaintedSkyType}). On 26.3 with Improved Transparency on, vanilla's own order independent
 * pipelines for the dragon rays draw it, as they draw the vanilla type.
 */
final class AddedLightType {
	private AddedLightType() {}

	/** The type to draw the added light with; {@code shaders} when a shader pack is on. */
	static RenderType get(boolean shaders) {
		//? if >=26.2 {
		/*return shaders ? RenderTypes.dragonRays() : TYPE;
		*///?} else {
		return RenderTypes.dragonRays();
		//?}
	}

	//? if >=26.3 {
	/*static final com.mojang.renderpearl.api.pipeline.RenderPipeline PIPELINE = com.mojang.renderpearl.api.pipeline.RenderPipeline.builder(
					net.minecraft.client.renderer.RenderPipelines.DRAGON_RAYS_SNIPPET)
			.withLocation(dev.beli.skylyrics.SkyLyricsClient.id("pipeline/added_light"))
			.withColorTargetState(new com.mojang.renderpearl.api.pipeline.ColorTargetState(com.mojang.renderpearl.api.pipeline.BlendFunction.LIGHTNING))
			.withDepthStencilState(new com.mojang.renderpearl.api.pipeline.DepthStencilState(
					com.mojang.renderpearl.api.pipeline.DepthStencilState.DEFAULT.depthTest(), false))
			.build();
	// Without its order independent pipelines 26.3 stops with an error when Improved Transparency is on
	private static final RenderType TYPE = RenderType.create("skylyrics_added_light",
			net.minecraft.client.renderer.rendertype.RenderSetup.builder(PIPELINE)
					.setOitPipelines(net.minecraft.client.renderer.RenderPipelines.OIT_DRAGON_RAYS).createRenderSetup());
	*///?} else if >=26.2 {
	/*static final com.mojang.blaze3d.pipeline.RenderPipeline PIPELINE = com.mojang.blaze3d.pipeline.RenderPipeline.builder(
					net.minecraft.client.renderer.RenderPipelines.MATRICES_FOG_SNIPPET)
			.withLocation(dev.beli.skylyrics.SkyLyricsClient.id("pipeline/added_light"))
			.withVertexShader("core/rendertype_lightning")
			.withFragmentShader("core/rendertype_lightning")
			.withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(com.mojang.blaze3d.pipeline.BlendFunction.LIGHTNING))
			.withVertexBinding(0, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR)
			.withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
			.withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(
					com.mojang.blaze3d.pipeline.DepthStencilState.DEFAULT.depthTest(), false))
			.build();
	private static final RenderType TYPE = RenderType.create("skylyrics_added_light",
			net.minecraft.client.renderer.rendertype.RenderSetup.builder(PIPELINE).createRenderSetup());
	*///?}
}
