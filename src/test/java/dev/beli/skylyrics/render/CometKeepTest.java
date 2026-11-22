package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The comet stands still where the lyrics face, yet each frame pushed every corner of its head and both tails out onto
 * the sky again (a square root each), had each piece's facing and fade worked out afresh and rounded every corner's alpha
 * anew. Only the gas tail's streamers and the strength change. It must hand on exactly what it did.
 */
class CometKeepTest {
	@Test
	void aCometFrameWorksOutNothingAfreshAndLooksTheSame() {
		CometSky sky = new CometSky();
		int frames = 240, fresh = 0, old = 0;
		float radius = 80, baseYaw = 20;
		double time = 40;
		for (int f = 0; f < frames; f++) {
			// The direction setting changes, then the render distance; a while of the sky pulse in between
			if (f == 150) {
				baseYaw = -90;
			}
			if (f == 200) {
				radius = 120;
			}
			float level = f >= 60 && f < 100 ? 0.8f + 0.2f * (float) Math.sin(f * 0.2) : 1;
			SkyView view = f % 4 == 0 ? SkyView.ALL : SkyRecorder.looking(baseYaw + CometSky.YAW + f % 7 * 10 - 30, CometSky.ELEV + f % 3 * 15 - 15);
			SkyRecorder kept = new SkyRecorder(), drawn = new SkyRecorder();
			sky.draw(kept, radius, baseYaw, level, time, view);
			OldSkyDraw.comet(sky, drawn, radius, baseYaw, level, view);
			SkyRecorder.assertSame(drawn, kept, "comet frame " + f);
			fresh += kept.freshQuads();
			old += drawn.freshQuads();
			time += 1 / 60.0;
		}
		System.out.printf("Comet over %d frames: pieces worked out afresh before %d (%.0f a frame), now %d%n", frames, old, old / (float) frames,
				fresh);
		assertTrue(old > frames * 1000, "the old drawing works out every piece afresh");
		assertEquals(0, fresh, "a frame must work out no piece afresh");
	}
}
