package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HatLayerTest {
	@Test
	void theHatFlaresWhenALineStartsAndSettlesAfter() {
		float rest = HatLayer.brightness(10f);
		assertEquals(HatLayer.REST, rest, 1e-4);
		assertTrue(HatLayer.brightness(0.1f) > rest, "a new line brightens the hat");
		assertTrue(HatLayer.brightness(0.1f) <= HatLayer.REST + HatLayer.FLARE + 1e-4f, "never brighter than a full flare");
	}

	@Test
	void withoutPulsingTheHatStaysSteady() {
		assertEquals(HatLayer.brightness(Float.NaN), HatLayer.brightness(Float.NaN));
		assertTrue(HatLayer.brightness(Float.NaN) > HatLayer.REST);
	}

	@Test
	void theTopHatSitsOnTheHeadAndItsBrimClearsTheCorners() {
		assertTrue(HatLayer.TOP_HAT_BRIM_Y < -0.53f, "above the head's outer layer");
		assertTrue(HatLayer.TOP_HAT_BRIM > 0.375f, "wider than the corners of the head's outer layer");
		assertTrue(HatLayer.TOP_HAT_RADIUS < HatLayer.TOP_HAT_BRIM);
	}

	@Test
	void theConicalHatIsWidestAtItsBrimAndComesToAPoint() {
		assertEquals(HatLayer.CONE_BRIM, HatLayer.coneRadius(HatLayer.CONE_BASE), 1e-5);
		assertEquals(0, HatLayer.coneRadius(HatLayer.CONE_APEX), 1e-5);
		float mid = HatLayer.coneRadius((HatLayer.CONE_BASE + HatLayer.CONE_APEX) / 2);
		assertTrue(mid > 0 && mid < HatLayer.CONE_BRIM);
		assertTrue(HatLayer.coneRadius(-0.53f) > 0.375f, "the brim clears the corners of the head's outer layer");
	}

	@Test
	void theHeadbandRunsFromEarToEarOverTheTop() {
		float[] left = HatLayer.bandPoint(0, 0.3f, 0.35f), top = HatLayer.bandPoint((float) Math.PI / 2, 0.3f, 0.35f);
		float[] right = HatLayer.bandPoint((float) Math.PI, 0.3f, 0.35f);
		assertEquals(0.3f, left[0], 1e-3);
		assertEquals(-0.3f, right[0], 1e-3);
		assertEquals(left[1], right[1], 1e-3);
		assertTrue(top[1] < -0.53f, "over the head's outer layer");
		assertEquals(-1, top[3], 1e-3, "the band faces up at the top");
		assertEquals(1, left[2], 1e-3, "and outwards at the ears");
	}

	@Test
	void catEarsAreMirroredAndPointUpAndOut() {
		float[] left = HatLayer.ear(-1), right = HatLayer.ear(1);
		assertEquals(-left[2], right[2], 1e-6);
		assertTrue(right[2] > (right[0] + right[1]) / 2);
		assertTrue(right[3] < -0.5f);
		assertTrue(left[1] < right[0]);
	}

	@Test
	void thePumpkinGrinIsMirroredCurvesUpAndHasTeeth() {
		float[] left = HatLayer.grinColumn(0), middle = HatLayer.grinColumn(2), right = HatLayer.grinColumn(4);
		assertEquals(-left[0], right[1], 1e-5f);
		assertEquals(left[2], right[3], 1e-5f);
		assertTrue(left[2] < middle[2], "the corners sit higher than the middle");
		for (int k = 0; k < 5; k++) {
			float[] g = HatLayer.grinColumn(k);
			assertTrue(g[4] - g[2] > 0.02f && g[5] - g[3] > 0.02f, "every slice stays open, teeth and all");
			if (k > 0) {
				assertEquals(HatLayer.grinColumn(k - 1)[1], g[0], 1e-5f);
			}
		}
		assertTrue(HatLayer.grinColumn(1)[2] > HatLayer.grinColumn(0)[3], "a tooth hangs down from the top");
		assertTrue(middle[4] < HatLayer.grinColumn(1)[5], "a tooth stands up in the middle");
	}

	@Test
	void hornsGrowOutOfTheHeadAndCurlUpAndOut() {
		for (float side : new float[] {-1, 1}) {
			float[][] h = HatLayer.horn(side);
			float[] base = h[0], tip = h[HatLayer.HORN_STEPS];
			assertTrue(base[1] > -0.53f, "the base hides inside the head's outer layer");
			assertTrue(tip[1] < base[1] - 0.2f, "the tip stands well above the head");
			assertTrue(tip[0] * side > base[0] * side, "each horn leans out to its own side");
			assertTrue(tip[3] < base[3], "the horn narrows to its tip");
			assertTrue(tip[4] * side < 0, "the tip curls back in");
		}
	}
}
