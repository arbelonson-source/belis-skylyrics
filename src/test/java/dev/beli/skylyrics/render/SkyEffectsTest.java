package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
	void skyPulseBrightensOnNewLines() {
		assertEquals(1, SkyEffects.beat(Float.NaN, true), 1e-6f, "pulse off: full strength");
		assertEquals(SkyEffects.BEAT_REST, SkyEffects.beat(5, true), 1e-6f, "dimmer between lines");
		assertTrue(SkyEffects.beat(0.1f, true) > SkyEffects.beat(0.6f, true), "brightest just after a new line");
		assertTrue(SkyEffects.beat(0.1f, true) <= 1, "never brighter than the strength setting");
	}

	@Test
	void skyPulseStaysFullWithNoLineBeingSung() {
		assertEquals(1, SkyEffects.beat(5, false), 1e-6f, "unsynced, instrumental or hidden lyrics: as with the pulse off");
		assertEquals(1, SkyEffects.beat(0.1f, false), 1e-6f);
	}

	@Test
	void starsEaseOverHalfASecondAfterATimeJump() {
		float s = 0;
		int frames = 0;
		while (s < 0.5f && frames < 200) {
			s = SkyEffects.easeStars(s, 0.5f, 0.02f);
			frames++;
		}
		assertEquals(25, frames, 1, "0 -> 0.5 takes about half a second");
		s = 0.5f;
		for (int i = 0; i < 12; i++) {
			s = SkyEffects.easeStars(s, 0, 0.02f);
		}
		assertTrue(s > 0 && s < 0.5f, "day falls away over about half a second, not in one frame: " + s);
		assertEquals(0.5f, SkyEffects.easeStars(Float.NaN, 0.5f, 0.02f), "nothing shown yet: snaps");
		// dusk is slower than the ease, so it is followed exactly
		assertEquals(0.3f - 0.0001f, SkyEffects.easeStars(0.3f, 0.3f - 0.0001f, 0.02f), 1e-7f);
	}

	@Test
	void sparklesComingBackSnapTheirAnchor() {
		assertTrue(SkyEffects.sparklesStarting(0, 0.02f));
		assertFalse(SkyEffects.sparklesStarting(0.5f, 0.52f));
		assertFalse(SkyEffects.sparklesStarting(0, 0));
		assertFalse(SkyEffects.sparklesStarting(0.02f, 0));
	}

	@Test
	void rainIsNotAppliedTwiceWhereTheStarValueAlreadyHasIt() {
		// 1.21.11 and newer: the star value is already 0.5 of full in half rain, so visibility must not halve it again
		assertEquals(0.5f, SkyEffects.visibility(0.25f, 0.5f, 1, true), 1e-6f);
		assertEquals(0.25f, SkyEffects.visibility(0.25f, 0.5f, 1, false), 1e-6f);
		assertEquals(0.5f, SkyEffects.visibility(0.25f, 0.5f, 1, SkyEffects.STARS_DIM_WITH_RAIN) * (SkyEffects.STARS_DIM_WITH_RAIN ? 1 : 2), 1e-6f);
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

	@Test
	void newSongsRandomSkyWaitsForTheOldOneToFadeThenComesIn() {
		SkyEffect shown = SkyEffect.AURORA, next = SkyEffect.MOONLIGHT;
		float presence = 1;
		for (int frame = 0; frame < 400 && shown != next; frame++) {
			boolean swapping = SkyEffects.waitsForFade(shown, next, presence);
			presence = SkyEffects.step(presence, !swapping, 0.02f);
			if (!swapping || presence <= 0) {
				shown = next;
			}
		}
		assertEquals(next, shown);
		assertEquals(0f, presence);
		for (int frame = 0; frame < 200; frame++) {
			presence = SkyEffects.step(presence, !SkyEffects.waitsForFade(shown, next, presence), 0.02f);
		}
		assertEquals(1f, presence);
		assertTrue(!SkyEffects.waitsForFade(SkyEffect.ECLIPSE, SkyEffect.ECLIPSE, 1));
	}

	@Test
	void changingTheSkySettingFadesOutBeforeTheNewOneComesIn() {
		SkyEffect shown = SkyEffect.AURORA, next = SkyEffect.SYNTHWAVE;
		float presence = 1;
		float atSwap = -1;
		for (int frame = 0; frame < 400 && shown != next; frame++) {
			boolean swapping = SkyEffects.waitsForFade(shown, next, presence);
			presence = SkyEffects.step(presence, !swapping, 0.02f);
			if (!swapping || presence <= 0) {
				atSwap = presence;
				shown = next;
			}
		}
		assertEquals(next, shown);
		assertEquals(0f, atSwap, "the old sky is fully faded before the swap");
		assertTrue(!SkyEffects.waitsForFade(SkyEffect.AURORA, SkyEffect.SYNTHWAVE, 0), "nothing shown: swaps at once");
	}

	@Test
	void sparklesSitInFrontOfEveryCloudAndCurtain() {
		assertTrue(SkyEffects.SPARKLE_FRONT < SkyEffects.nearestLayer());
		assertTrue(SkyEffects.METEOR_FRONT < SkyEffects.nearestLayer());
		assertTrue(SkyEffects.METEOR_FRONT <= SkyEffects.SPARKLE_FRONT);
		assertTrue(SkyEffects.blobFraction(0) > SkyEffects.blobFraction(1));
		assertTrue(SkyEffects.ribbonFraction(0) > SkyEffects.ribbonFraction(1));
	}

	@Test
	void presenceTargetIsOffInTheNetherAndUnderwater() {
		assertTrue(SkyEffects.presenceTarget(true, false, true));
		assertFalse(SkyEffects.presenceTarget(true, false, false), "no open sky: presence must drop so it fades in again");
		assertFalse(SkyEffects.presenceTarget(true, true, true));
		assertFalse(SkyEffects.presenceTarget(false, false, true));
	}
}
