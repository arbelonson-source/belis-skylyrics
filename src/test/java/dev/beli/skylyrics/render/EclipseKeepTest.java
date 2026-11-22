package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The eclipse stands still where the lyrics face, yet each frame pushed every corona corner out onto the sky again (a
 * square root each), worked out every corner of the milky way and each piece's facing and fade, and rounded every
 * corner's alpha anew. Only the corona's breathing and the strength change. It must hand on exactly what it did.
 */
class EclipseKeepTest {
	@Test
	void anEclipseFrameWorksOutNothingAfreshAndLooksTheSame() {
		EclipseSky sky = new EclipseSky();
		int frames = 240, fresh = 0, old = 0, tints = sky.bandTints, steadyTints = 0;
		float radius = 80, baseYaw = 20, level = 1;
		double time = 40;
		for (int f = 0; f < frames; f++) {
			// The direction setting changes, then the render distance; a while of the sky pulse in between
			if (f == 150) {
				baseYaw = -90;
			}
			if (f == 200) {
				radius = 120;
			}
			float before = level;
			level = f >= 60 && f < 100 ? 0.8f + 0.2f * (float) Math.sin(f * 0.2) : 1;
			SkyView view = f % 4 == 0 ? SkyView.ALL : SkyRecorder.looking(baseYaw + 180 + f * 3, f % 3 * 15);
			SkyRecorder kept = new SkyRecorder(), drawn = new SkyRecorder();
			int t = sky.bandTints;
			sky.draw(kept, radius, baseYaw, level, time, view);
			sky.drawDisc(kept, radius, baseYaw, level, view);
			OldSkyDraw.eclipse(drawn, radius, baseYaw, level, time, view);
			OldSkyDraw.eclipseDisc(drawn, radius, baseYaw, level, view);
			SkyRecorder.assertSame(drawn, kept, "eclipse frame " + f);
			fresh += kept.freshQuads();
			old += drawn.freshQuads();
			if (f > 0 && KeptQuads.same(level, before)) {
				steadyTints += sky.bandTints - t;
			}
			time += 1 / 60.0;
		}
		System.out.printf("Eclipse over %d frames: pieces worked out afresh before %d (%.0f a frame), now %d; milky way coloured %d times%n",
				frames, old, old / (float) frames, fresh, sky.bandTints - tints);
		assertTrue(old > frames * 1000, "the old drawing works out every piece afresh");
		assertEquals(0, fresh, "a frame must work out no piece afresh");
		assertEquals(0, steadyTints, "the milky way's colours are only worked out when the strength changes");
	}
}
