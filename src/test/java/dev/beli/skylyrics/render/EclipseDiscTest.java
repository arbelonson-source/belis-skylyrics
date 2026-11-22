package dev.beli.skylyrics.render;

import static dev.beli.skylyrics.render.PaintedSkyCloudsTest.leavesDepth;
import static org.junit.jupiter.api.Assertions.assertFalse;
//? if <26.3 {
import static org.junit.jupiter.api.Assertions.assertSame;
//?}
import static org.junit.jupiter.api.Assertions.assertTrue;

//? if <26.3 {
import net.minecraft.client.renderer.rendertype.RenderTypes;
//?}
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The eclipse's black disc, its glow round it, and the sparkles and shooting stars that go over it.
 */
class EclipseDiscTest {
	/** The batch the world's own things go in, the lyrics among them. */
	private static final int WORLD_ORDER = 0;

	/** 1.21.1's render types reach the item and block registries as they load. */
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void theDiscLeavesNoDepthForTheCloudsToHideBehind() throws Exception {
		// The disc went out through the text background type, which marks its depth (up to 26.2), at 0.94 of the sky's
		// radius: the clouds, drawn after the sky and 570 blocks away along the line to a sun 13 degrees up (from y 64),
		// were hidden wherever they passed in front of it, so the black disc stayed on top of every cloud
		assertFalse(leavesDepth(SkyEffects.discType(false)), "the eclipse disc marks its depth, hiding every cloud in front of the sun");
	}

	//? if <26.3 {
	@Test
	void theTextBackgroundTypeIsKeptWithShaders() {
		// The type Iris has a program for, as for the painted skies (see PaintedSkyType)
		assertSame(RenderTypes.textBackground(), SkyEffects.discType(true));
	}
	//?}

	@Test
	void theSparklesAndShootingStarsHaveABatchAfterTheDisc() {
		// In one batch vanilla draws the render types in no set order from 1.21.9 on (a hash map of them, its order
		// changing from one game start to the next). The sparkles and shooting stars go out through the glow's type, so in
		// the sky's batch they came before the disc in about half of the starts, and the disc then blacked them out where
		// they crossed the sun (26.2 on: they cut holes in it instead). A batch of their own puts them after it.
		assertTrue(SkyEffects.FRONT_ORDER > SkyEffects.SKY_ORDER,
				"the eclipse's sparkles and shooting stars share the disc's batch (" + SkyEffects.SKY_ORDER + ")");
		assertTrue(SkyEffects.FRONT_ORDER < WORLD_ORDER, "the eclipse's sparkles and shooting stars are drawn with the lyrics' batch or after it");
		assertTrue(SkyEffects.PAINTED_ORDER < SkyEffects.SKY_ORDER, "the painted skies are drawn after the light over them");
	}
}
