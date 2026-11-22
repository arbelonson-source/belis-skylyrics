package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The painted skies' meshes (the Moonlight and Synthwave domes, the moon and its glow, the clouds, the swirl and the sun's
 * glow) used to turn every corner to the lyrics' direction again each frame and have the canvas work out each quad's
 * facing and fade afresh, though those only change with the direction and the render distance. They must hand on the
 * very same quads.
 */
class SkyMeshKeepTest {
	private static final float DEG = (float) (Math.PI / 180);

	private static int paint(float x, float y, float z) {
		return (int) (x * 60 + 70) << 16 | (int) (y * 60 + 70) << 8 | (int) (z * 60 + 70);
	}

	private static void check(String name, SkyMesh mesh) {
		int frames = 160, now = 0, before = 0;
		int layouts = mesh.layouts();
		float yaw = 20, r = 90;
		for (int f = 0; f < frames; f++) {
			// The direction setting changes, then the render distance, then a shape is drawn once without the horizon fade
			if (f == 60) {
				yaw = -75;
			}
			if (f == 100) {
				r = 64;
			}
			float fade = f >= 120 && f < 125 ? 0 : 3;
			float level = f < 30 ? 1 : 0.75f + 0.25f * (float) Math.sin(f * 0.3);
			SkyView view = f % 3 == 0 ? SkyView.ALL : SkyRecorder.looking(f * 7, f % 5 * 20 - 30);
			if (f % 10 == 5) {
				// A repaint, as the drifting clouds have
				for (int k = 0; k < mesh.rgb.length; k += 7) {
					mesh.rgb[k] = mesh.rgb[k] + 0x010203 * f & 0xFFFFFF;
				}
			}
			float cos = (float) Math.cos(yaw * DEG), sin = (float) Math.sin(yaw * DEG);
			SkyRecorder kept = new SkyRecorder(), old = new SkyRecorder();
			kept.fadeBelowHorizon(fade);
			old.fadeBelowHorizon(fade);
			mesh.draw(kept, cos, sin, r, level, view);
			OldSkyDraw.mesh(mesh, old, cos, sin, r, level, view);
			SkyRecorder.assertSame(old, kept, name + " frame " + f);
			now += kept.freshQuads();
			before += old.freshQuads();
		}
		System.out.printf("%s over %d frames: quads worked out afresh before %d (%.0f a frame), now %d; turned and kept %d times%n",
				name, frames, before, before / (float) frames, now, mesh.layouts() - layouts);
		assertTrue(before > frames * 100, name + ": the old drawing works out every quad afresh");
		assertEquals(0, now, name + ": a frame must work out no quad afresh");
		// Once at the start, after the direction and the distance changed, and on and off the frames without the fade
		assertEquals(5, mesh.layouts() - layouts, name + ": turned and kept only when the direction, distance or fade changed");
	}

	@Test
	void aDomeHandsOnTheSameQuadsWithoutWorkingThemOutAgain() {
		// Reaching below the horizon, where it fades
		check("dome", SkyMesh.dome(new float[] {-3, 0, 4, 10, 25, 50, 90}, 36, SkyMeshKeepTest::paint));
	}

	@Test
	void aRoundPatchHandsOnTheSameQuadsWithoutWorkingThemOutAgain() {
		// Low enough that its lower part is below the horizon
		float[] rings = new float[24];
		for (int i = 0; i < rings.length; i++) {
			rings[i] = (float) Math.tan(i * 1.5f * DEG);
		}
		check("round patch", SkyMesh.polar(new SkyCanvas.SkyFrame().aim(30, 8), rings, 48, SkyMeshKeepTest::paint));
	}
}
