package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.config.SkyLyricsConfig.Direction;
import dev.beli.skylyrics.config.SkyLyricsConfig.SkyEffect;
import dev.beli.skylyrics.media.Track;
import java.util.EnumSet;
import java.util.Set;

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

	@Test
	void shootingStarsAreGentleWhenCalm() {
		assertEquals(SkyEffects.METEOR_RATE, SkyEffects.meteorRate(0), 1e-6f);
		assertEquals(SkyEffects.METEOR_RATE, SkyEffects.meteorRate(8), 1e-6f);
		assertEquals(SkyEffects.METEOR_RATE, SkyEffects.meteorRate(5), 1e-6f);
		assertTrue(SkyEffects.METEOR_RATE < 0.1f, "about one every ten seconds or less");
	}

	@Test
	void shootingStarsTripleWhenLinesRushBy() {
		assertEquals(3 * SkyEffects.METEOR_RATE, SkyEffects.meteorRate(2), 1e-6f);
		assertEquals(3 * SkyEffects.METEOR_RATE, SkyEffects.meteorRate(0.5f), 1e-6f);
		float mid = SkyEffects.meteorRate(3.5f);
		assertTrue(mid > 1.9f * SkyEffects.METEOR_RATE && mid < 2.1f * SkyEffects.METEOR_RATE, "halfway " + mid);
	}

	@Test
	void shootingStarFadesInAndOut() {
		assertEquals(0, SkyEffects.meteorEnvelope(0, 1), 1e-6f);
		assertEquals(1, SkyEffects.meteorEnvelope(0.5f, 1), 1e-6f);
		assertEquals(0, SkyEffects.meteorEnvelope(1, 1), 1e-6f);
		assertTrue(SkyEffects.meteorEnvelope(0.1f, 1) > SkyEffects.meteorEnvelope(0.9f, 1), "quick in, slow out");
	}

	@Test
	void glintIsMostlyDimWithAShortPeak() {
		assertEquals(1, SkyEffects.glint(1), 1e-6f);
		assertEquals(0, SkyEffects.glint(-1), 1e-6f);
		assertEquals(0.125f, SkyEffects.glint(0), 1e-6f);
	}

	@Test
	void randomSkyHoldsForASongAndVariesAcrossSongs() {
		assertEquals(SkyEffect.ECLIPSE, SkyEffects.pick(SkyEffect.ECLIPSE, new Track("Song 1", "Band", "", 0, "", "")));
		Set<SkyEffect> seen = EnumSet.noneOf(SkyEffect.class);
		for (int n = 0; n < 60; n++) {
			Track song = new Track("Song " + n, "Band", "", 0, "", "");
			SkyEffect sky = SkyEffects.pick(SkyEffect.RANDOM, song);
			assertEquals(sky, SkyEffects.pick(SkyEffect.RANDOM, new Track("Song " + n, "Band", "Album", 1, "x", "y")));
			assertNotEquals(SkyEffect.RANDOM, sky);
			assertNotEquals(SkyEffect.OFF, sky);
			seen.add(sky);
		}
		assertEquals(SkyEffects.RANDOM_SKIES.length, seen.size());
		assertNotEquals(SkyEffect.RANDOM, SkyEffects.pick(SkyEffect.RANDOM, null));
	}
}
