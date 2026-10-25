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
