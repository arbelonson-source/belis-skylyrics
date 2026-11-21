package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Turning sky settings on or off at night (the world stays in view behind the settings) fades instead of cutting. */
class SkyTurnOffFadeTest {
	private static final float FRAME = 1 / 60f;

	@Test
	void constellationsFadeInAndOutWhenTurnedOnOrOff() {
		float show = SkyEffects.patternsStep(0, true, FRAME);
		assertTrue(show > 0 && show < 0.05f, "a frame after turning them on they have only begun to show: " + show);
		int frames = 1;
		while (show < 1 && frames < 300) {
			show = SkyEffects.patternsStep(show, true, FRAME);
			frames++;
		}
		assertEquals(90, frames, 2, "in over the sky's usual one and a half seconds");
		show = SkyEffects.patternsStep(show, false, FRAME);
		assertTrue(show > 0.95f && show < 1, "a frame after turning them off they still show: " + show);
		assertEquals(1, SkyEffects.patternsStep(Float.NaN, true, FRAME), "coming back after the sky was hidden: as set, at once");
		assertEquals(0, SkyEffects.patternsStep(Float.NaN, false, FRAME));
	}

	@Test
	void turningTheLastSkyOptionOffFadesTheSkyOut() {
		// Night sky Off (or Constellations off with Night sky already Off) and nothing else on: nothing is wanted any
		// more, so presence heads down, and what shows must fade out with it instead of vanishing in one frame
		float presence = 1;
		int frames = 0;
		do {
			presence = SkyEffects.step(presence, SkyEffects.presenceTarget(false, false, true), FRAME);
			frames++;
		} while (!SkyEffects.skyGone(presence, false, true) && frames < 300);
		assertEquals(90, frames, 2, "the sky is skipped only once it has faded out, after one and a half seconds");
	}

	@Test
	void noOpenSkyStillHidesItAtOnce() {
		assertTrue(SkyEffects.skyGone(1, true, false), "the Nether, the End, water, Blindness: hidden at once, as vanilla");
		assertTrue(SkyEffects.skyGone(0, true, true), "faded out");
		assertFalse(SkyEffects.skyGone(0.5f, true, true));
	}
}
