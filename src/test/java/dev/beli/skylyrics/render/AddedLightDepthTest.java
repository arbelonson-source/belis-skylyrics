package dev.beli.skylyrics.render;

import static dev.beli.skylyrics.render.PaintedSkyCloudsTest.leavesDepth;
import static org.junit.jupiter.api.Assertions.assertFalse;
//? if >=26.3 {
/*import static org.junit.jupiter.api.Assertions.assertNotNull;
*///?}
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The sky's added light (the aurora, the nebula, the eclipse's glow and milky way, the galaxy, the comet, the
 * constellations on the vanilla sky, the sparkles and the shooting stars) goes out through vanilla's dragon rays type,
 * which marks the depth of everything it draws from 26.2 on (up to 26.1 it marks none). Vanilla draws the lyrics after
 * the sky, and its clouds after that, each hidden wherever something nearer has left its depth:
 * <ul>
 * <li>The aurora hangs at 0.45 of the render distance (at most 110 blocks), its sparkles and shooting stars a little
 * nearer still, while the lyrics in the Sky keep the Distance setting (64 by default) up to just short of the painted
 * skies. At render distance 6, as a new world starts, the curtains are 37 to 43 blocks away and the lyrics 50 (at the
 * default Distance they hang behind the sparkles and shooting stars up to render distance 11, and with Distance 160
 * behind the whole aurora at every render distance): with See through blocks off every letter a curtain, a nebula cloud,
 * a sparkle or a shooting star crossed was lost.
 * <li>The clouds float 128 blocks above a player at y 64, farther than any curtain or nebula cloud in front of them:
 * every cloud behind the default Aurora was hidden, in the curtains' shape.
 * </ul>
 */
class AddedLightDepthTest {
	private static final int[] RENDER_DISTANCES = {2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 16, 24, 32};

	/** 1.21.1's render types reach the item and block registries as they load. */
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void theLyricsBehindTheAuroraAreNotHiddenByIt() throws Exception {
		boolean behind = false;
		for (int rd : RENDER_DISTANCES) {
			for (float setting : new float[] {64, 160}) {
				float lyrics = lyricsDistance(setting, rd);
				float light = nearestLight(rd);
				if (light < lyrics) {
					behind = true;
					assertFalse(leavesDepth(SkyEffects.lightType(false)), "at render distance " + rd + " and Distance " + setting
							+ " the lyrics hang " + lyrics + " blocks away, behind the aurora (its nearest shapes " + light
							+ " blocks away), whose depth hides every letter it crosses");
				}
			}
		}
		// The rule above has something to check: the default lyrics do hang behind the aurora at a low render distance
		assertTrue(behind, "the lyrics never hang behind the aurora");
		assertTrue(nearestLight(6) < lyricsDistance(64, 6));
	}

	@Test
	void theAddedLightLeavesNoDepthForTheCloudsToHideBehind() throws Exception {
		// The clouds are drawn after the sky and lie behind it: 128 blocks above you, the aurora at most 110 away
		assertFalse(leavesDepth(SkyEffects.lightType(false)), "the added light marks its depth, hiding every cloud behind it");
	}

	@Test
	void theVanillaTypeIsKeptWithShaders() {
		// The type Iris has a program for: what the added light went out through before, and still does with a shader pack
		assertSame(RenderTypes.dragonRays(), SkyEffects.lightType(true));
	}

	//? if >=26.3 {
	/*@Test
	void improvedTransparencyHasPipelinesForIt() throws Exception {
		// With Improved Transparency on, 26.3 draws every blended type through its order independent pipelines, and
		// stops with an error on a type that has none
		assertNotNull(PaintedSkyCloudsTest.field(PaintedSkyCloudsTest.field(SkyEffects.lightType(false), "state"), "oitPipelineSet"));
	}
	*///?}

	/** The nearest any added light comes at this render distance: the shooting stars (on 26.2 and later). */
	private static float nearestLight(int rd) {
		return SkyEffects.radius(rd) * Math.min(Math.min(SkyEffects.METEOR_FRONT, SkyEffects.SPARKLE_FRONT), SkyEffects.nearestLayer());
	}

	/** Where the renderer hangs the stacked lines in the Sky: Size 1, field of view 70, a 16:9 window. */
	private static float lyricsDistance(float setting, int rd) {
		float angular = LyricsRenderer.rowAngle(true, 1);
		double tanHalf = LyricsRenderer.tanHalfWide(70, 1920, 1080);
		float wrap = LyricsRenderer.rowWidth(230, angular, tanHalf);
		return LyricsRenderer.skyDistance(setting, rd, LyricsRenderer.reach(LyricsRenderer.halfWide(false, wrap, angular, tanHalf), angular));
	}
}
