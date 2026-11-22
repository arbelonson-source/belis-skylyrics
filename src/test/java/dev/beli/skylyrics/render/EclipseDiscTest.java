package dev.beli.skylyrics.render;

import static dev.beli.skylyrics.render.PaintedSkyCloudsTest.leavesDepth;
import static org.junit.jupiter.api.Assertions.assertFalse;
//? if <26.3 {
import static org.junit.jupiter.api.Assertions.assertSame;
//?}

//? if <26.3 {
import net.minecraft.client.renderer.rendertype.RenderTypes;
//?}
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The eclipse's black disc, in front of the vanilla sky and behind the clouds.
 */
class EclipseDiscTest {
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
}
