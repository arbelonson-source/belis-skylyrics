package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.config.SkyLyricsConfig.Direction;

class SkyEffectsTest {
	@Test
	void visibilityNeedsDarkClearSkyAndStrength() {
		assertEquals(0.7f, SkyEffects.visibility(0.5f, 0, 0.7f), 1e-6f);
		assertEquals(0, SkyEffects.visibility(0, 0, 1));
		assertEquals(0, SkyEffects.visibility(1, 1, 1));
		assertEquals(0, SkyEffects.visibility(1, 0, 0));
		assertEquals(0.25f, SkyEffects.visibility(0.25f, 0.5f, 1), 1e-6f);
	}

	@Test
	void visibilityClampsOddInputs() {
		assertEquals(1, SkyEffects.visibility(5, -2, 9), 1e-6f);
		assertEquals(0, SkyEffects.visibility(-1, 0, 1));
	}

	@Test
	void presenceFadesOverOneAndAHalfSeconds() {
		float p = 0;
		for (int i = 0; i < 74; i++) {
			p = SkyEffects.step(p, true, 0.02f);
		}
		assertTrue(p > 0.95f && p < 1, "nearly in " + p);
		p = SkyEffects.step(p, true, 0.1f);
		assertEquals(1, p, 1e-6f);
		for (int i = 0; i < 80; i++) {
			p = SkyEffects.step(p, false, 0.02f);
		}
		assertEquals(0, p, 1e-6f);
	}

	@Test
	void smoothEasesBetweenEnds() {
		assertEquals(0, SkyEffects.smooth(-1));
		assertEquals(1, SkyEffects.smooth(2));
		assertEquals(0.5f, SkyEffects.smooth(0.5f), 1e-6f);
		assertTrue(SkyEffects.smooth(0.25f) < 0.25f);
	}

	@Test
	void radiusStaysInsideFog() {
		assertEquals(14.4f, SkyEffects.radius(2), 1e-4f);
		assertEquals(110, SkyEffects.radius(32), 1e-4f);
		assertEquals(86.4f, SkyEffects.radius(12), 1e-4f);
	}

	@Test
	void ribbonEndsFadeOut() {
		assertEquals(0, SkyEffects.endFade(0), 1e-6f);
		assertEquals(0, SkyEffects.endFade(1), 1e-6f);
		assertEquals(1, SkyEffects.endFade(0.5f), 1e-6f);
	}

	@Test
	void mixBlendsChannels() {
		assertEquals(0x000000, SkyEffects.mix(0x000000, 0xFFFFFF, 0));
		assertEquals(0xFFFFFF, SkyEffects.mix(0x000000, 0xFFFFFF, 1));
		assertEquals(0x808080, SkyEffects.mix(0x000000, 0xFFFFFF, 0.5f));
		assertEquals(0xFF0000, SkyEffects.mix(0xFF0000, 0x0000FF, -3));
	}

	@Test
	void farShapesStayInsideTheFog() {
		for (int chunks = 2; chunks <= 32; chunks++) {
			float blocks = chunks * 16;
			assertTrue(SkyEffects.farRadius(chunks) < blocks - Math.min(64, Math.max(4, blocks / 10)));
			assertTrue(SkyEffects.farRadius(chunks) > SkyEffects.radius(chunks));
		}
	}

	@Test
	void sunsFaceTheLyricsDirection() {
		assertEquals(0, SkyEffects.baseYaw(Direction.SOUTH));
		assertEquals(SkyEffects.FOLLOW_YAW, SkyEffects.baseYaw(Direction.FOLLOW));
	}
}
