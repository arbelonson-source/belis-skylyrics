package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SynthwaveSkyTest {
	@Test
	void stripesStartAboveTheMiddleAndThickenDownward() {
		assertFalse(SynthwaveSky.inGap(0.1f));
		assertFalse(SynthwaveSky.inGap(SynthwaveSky.GAP_START - 0.01f));
		assertTrue(SynthwaveSky.inGap(SynthwaveSky.GAP_START + 0.005f));
		for (int g = 1; g < 7; g++) {
			assertTrue(SynthwaveSky.gapWidth(g) > SynthwaveSky.gapWidth(g - 1));
			assertTrue(SynthwaveSky.gapTop(g) > SynthwaveSky.gapTop(g - 1) + SynthwaveSky.gapWidth(g - 1));
		}
	}

	@Test
	void faceRowsCoverTheDiscInOrder() {
		float[] t = SynthwaveSky.FACE_T;
		assertEquals(0, t[0], 1e-6f);
		assertEquals(1, t[t.length - 1], 1e-6f);
		for (int i = 1; i < t.length; i++) {
			assertTrue(t[i] > t[i - 1]);
		}
		int gaps = 0;
		for (int i = 0; i < SynthwaveSky.FACE_FILL.length; i++) {
			if (!SynthwaveSky.FACE_FILL[i]) {
				gaps++;
				assertTrue(t[i] >= SynthwaveSky.GAP_START - 1e-4f);
			}
		}
		// Seven or more gaps, as in the reference
		assertTrue(gaps >= 7, "gaps: " + gaps);
	}

	@Test
	void swirlIsDarkestInTheMiddleAndFadesOut() {
		assertTrue(SynthwaveSky.swirl(0) > SynthwaveSky.swirl(20));
		assertEquals(0, SynthwaveSky.swirl(40), 1e-6f);
		for (float r = 0; r < 45; r += 0.5f) {
			assertTrue(SynthwaveSky.swirl(r) >= 0 && SynthwaveSky.swirl(r) <= 1);
		}
	}

	@Test
	void skyGoesFromIndigoToMagentaAtTheHorizon() {
		assertEquals(0xB140BF, SynthwaveSky.skyColor(0));
		int top = SynthwaveSky.skyColor(60);
		assertTrue((top >> 16 & 255) < 0x40, "dark red channel overhead");
		assertTrue((top & 255) > (top >> 16 & 255), "bluish overhead");
	}

	@Test
	void backgroundIsPlainGradientAwayFromSwirlAndSun() {
		// Behind the lyrics' direction, low: neither the swirl nor the sun reach there
		SkyCanvas.SkyFrame f = new SkyCanvas.SkyFrame().aim(180, 20);
		assertEquals(SynthwaveSky.skyColor(20), SynthwaveSky.background(f.dx, f.dy, f.dz));
		// Near the sun the glow takes over
		SkyCanvas.SkyFrame sun = new SkyCanvas.SkyFrame().aim(SynthwaveSky.SUN_YAW, SynthwaveSky.SUN_ELEV);
		assertEquals(0, SynthwaveSky.sunDistance(sun.dx, sun.dy, sun.dz), 1e-3f);
		int glow = SynthwaveSky.background(sun.dx, sun.dy, sun.dz);
		assertTrue((glow >> 16 & 255) > 0xE0, "hot pink behind the sun");
	}

	@Test
	void glowAndHaloFadeSmoothlyToNothing() {
		assertEquals(1, SynthwaveSky.glow(1), 1e-6f);
		assertEquals(0.5f, SynthwaveSky.glow(1.32f), 0.01f);
		assertTrue(SynthwaveSky.glow(1.79f) < 0.02f);
		assertTrue(SynthwaveSky.halo(1) > SynthwaveSky.halo(2));
		assertTrue(SynthwaveSky.halo(3.15f) < 0.005f);
		assertEquals(0, SynthwaveSky.halo(3.2f), 1e-6f);
	}

	@Test
	void theSwirlDarkensTheSkyWhereItSitsAndTheHaloLightensItAroundTheSun() {
		// Straight into the middle of the swirl, high up: darker than the plain gradient there
		SkyCanvas.SkyFrame swirl = new SkyCanvas.SkyFrame().aim(0, 62);
		int plain = SynthwaveSky.skyColor(62);
		int inSwirl = SynthwaveSky.background(swirl.dx, swirl.dy, swirl.dz);
		assertTrue(SkyColors.luma(inSwirl) < SkyColors.luma(plain) * 0.6f, Integer.toHexString(inSwirl) + " vs " + Integer.toHexString(plain));
		// Two sun radii out from the sun: in the halo, a pink tint over the plain gradient at that height
		SkyCanvas.SkyFrame sun = new SkyCanvas.SkyFrame().aim(SynthwaveSky.SUN_YAW, SynthwaveSky.SUN_ELEV);
		float k = 2 * (float) Math.tan(Math.toRadians(SynthwaveSky.SUN_RADIUS));
		float x = sun.dx + k * sun.ux, y = sun.dy + k * sun.uy, z = sun.dz + k * sun.uz;
		float len = (float) Math.sqrt(x * x + y * y + z * z);
		assertEquals(2, SynthwaveSky.sunDistance(x / len, y / len, z / len), 0.05f);
		float elev = (float) Math.toDegrees(Math.asin(y / len));
		int halo = SynthwaveSky.background(x / len, y / len, z / len);
		assertTrue((halo >> 16 & 255) > (SynthwaveSky.skyColor(elev) >> 16 & 255) + 20,
				"pink over the gradient: " + Integer.toHexString(halo) + " vs " + Integer.toHexString(SynthwaveSky.skyColor(elev)));
	}

	/** Every corner a full draw hands over, with its colour. */
	private static java.util.List<float[]> drawCorners(java.util.List<Integer> colours) {
		java.util.List<float[]> corners = new java.util.ArrayList<>();
		SkyCanvas c = new SkyCanvas() {
			@Override
			protected void put(float x, float y, float z, int argb) {
				corners.add(new float[] {x, y, z});
				colours.add(argb);
			}
		};
		new SynthwaveSky().draw(c, 80, 90, 1, 0, 0.001f);
		return corners;
	}

	@Test
	void theSkyFadesOutBelowTheHorizonAndStaysSolidAboveIt() {
		java.util.List<Integer> colours = new java.util.ArrayList<>();
		java.util.List<float[]> corners = drawCorners(colours);
		int below = 0, above = 0;
		for (int i = 0; i < corners.size(); i++) {
			float[] p = corners.get(i);
			float elev = (float) Math.toDegrees(Math.asin(p[1] / Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2])));
			int alpha = colours.get(i) >>> 24;
			if (elev <= -SynthwaveSky.HORIZON_FADE + 0.01f) {
				below++;
				assertEquals(0, alpha, "corner at " + elev + " degrees");
			} else if (elev >= 0) {
				above++;
				assertEquals(255, alpha, "corner at " + elev + " degrees");
			}
		}
		assertTrue(below > 0 && above > 0, "below " + below + " above " + above);
	}

	@Test
	void theSunFaceIsDrawnWhenInView() {
		java.util.List<Integer> colours = new java.util.ArrayList<>();
		drawCorners(colours);
		assertTrue(colours.contains(0xFFFFD677), "the top row of the sun face, #FFD677");
	}

	@Test
	void drawsWholeQuadsOfFiniteCorners() {
		int[] count = new int[1];
		SkyCanvas c = new SkyCanvas() {
			@Override
			protected void put(float x, float y, float z, int argb) {
				assertTrue(Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z));
				count[0]++;
			}
		};
		new SynthwaveSky().draw(c, 80, 90, 1, 0, 0.001f);
		assertEquals(0, count[0] % 4);
		assertTrue(count[0] > 1000);
	}
}
