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
}
