package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The Moonlight and Synthwave stars, and the Synthwave sun's stripes, stand still where the lyrics face, yet each frame
 * aimed at every star again (two sines and two cosines) and pushed its corners out onto the sky (six square roots). A
 * whole frame of either sky must now work out no piece afresh, and hand on exactly what the old drawing did.
 */
class PaintedSkyKeepTest {
	private interface Sky {
		void draw(SkyCanvas c, float radius, float baseYaw, float level, double time, float pixel, SkyView view);
	}

	private static void check(String name, Sky now, Sky before) {
		int frames = 240, fresh = 0, old = 0;
		float radius = 80, baseYaw = 20, pixel = 0.0011f;
		double time = 40;
		for (int f = 0; f < frames; f++) {
			// The field of view widens (sprinting), the direction setting changes, then the render distance
			if (f == 100) {
				pixel = 0.0013f;
			}
			if (f == 150) {
				baseYaw = -90;
			}
			if (f == 200) {
				radius = 120;
			}
			float level = f < 50 ? 1 : 0.8f + 0.2f * (float) Math.sin(f * 0.2);
			SkyView view = f % 4 == 0 ? SkyView.ALL : SkyRecorder.looking(baseYaw + f * 5, f % 3 * 25 - 5);
			SkyRecorder kept = new SkyRecorder(), drawn = new SkyRecorder();
			now.draw(kept, radius, baseYaw, level, time, pixel, view);
			before.draw(drawn, radius, baseYaw, level, time, pixel, view);
			SkyRecorder.assertSame(drawn, kept, name + " frame " + f);
			fresh += kept.freshQuads();
			old += drawn.freshQuads();
			time += 1 / 60.0;
		}
		System.out.printf("%s over %d frames: pieces worked out afresh before %d (%.0f a frame), now %d%n", name, frames, old,
				old / (float) frames, fresh);
		assertTrue(old > frames * 1000, name + ": the old drawing works out every piece afresh");
		assertEquals(0, fresh, name + ": a frame must work out no piece afresh, the stars and the sun's stripes included");
	}

	@Test
	void aMoonlightFrameWorksOutNothingAfreshAndLooksTheSame() {
		MoonlitSky sky = new MoonlitSky();
		check("Moonlight", sky::draw, (c, radius, baseYaw, level, time, pixel, view) -> OldSkyDraw.moonlit(sky, c, radius, baseYaw, level, time, pixel, view));
	}

	@Test
	void aSynthwaveFrameWorksOutNothingAfreshAndLooksTheSame() {
		SynthwaveSky sky = new SynthwaveSky();
		check("Synthwave", sky::draw, OldSkyDraw::synthwave);
	}
}
