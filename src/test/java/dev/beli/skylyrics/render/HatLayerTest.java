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
}
