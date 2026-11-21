package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** With Pulse with the lyrics on, the sky's resting brightness moves smoothly when synced lyrics come or go. */
class SkyPulseRestTest {
	private static final float FRAME = 1 / 60f;

	/** The sky brightness the pulse gives, frame by frame, the way the sky works it out. */
	private static final class Sky {
		float rest = Float.NaN;

		float frame(float since, boolean syncedShown) {
			rest = SkyEffects.easeRest(rest, SkyEffects.restTarget(since, syncedShown), FRAME);
			return SkyEffects.beatAt(since, rest);
		}
	}

	@Test
	void aSongChangeDoesNotMakeTheSkyJumpBrighterWhileTheNewLyricsLoad() {
		Sky sky = new Sky();
		// Between two lines of a song with synced lyrics: the sky rests a little dimmer
		float since = 4;
		float last = sky.frame(since, true);
		assertEquals(SkyEffects.BEAT_REST, last, 1e-6f);
		float biggest = 0;
		for (int frame = 1; frame < 240; frame++) {
			since += FRAME;
			// The next song starts at frame 60: no lyrics for it yet until frame 90, then its synced lyrics arrive
			boolean synced = frame < 60 || frame >= 90;
			float now = sky.frame(since, synced);
			biggest = Math.max(biggest, Math.abs(now - last));
			last = now;
		}
		assertTrue(biggest < 0.01f, "the sky's brightness must not jump from one frame to the next: it moved " + biggest);
		assertEquals(SkyEffects.BEAT_REST, last, 1e-6f, "back at rest with the new song's lyrics");
	}

	@Test
	void hiddenLyricsBringTheSkyUpToFullOverAboutASecond() {
		Sky sky = new Sky();
		sky.frame(4, true);
		float after = 0;
		for (int frame = 1; frame <= 30; frame++) {
			after = sky.frame(4 + frame * FRAME, false);
		}
		assertTrue(after > SkyEffects.BEAT_REST && after < 1, "half a second on, still on its way up: " + after);
		for (int frame = 31; frame <= 75; frame++) {
			after = sky.frame(4 + frame * FRAME, false);
		}
		assertEquals(1, after, 1e-6f, "with no synced lyrics on show the sky ends at full strength, as with the pulse off");
	}

	@Test
	void nothingToPulseWithRestsAtFullAndTheFirstFrameSnaps() {
		assertEquals(1, SkyEffects.restTarget(Float.NaN, true), "pulse off, or Hide Lightning Flashes on: full strength");
		assertEquals(1, SkyEffects.restTarget(4, false), "no synced lyrics on show: full strength");
		assertEquals(SkyEffects.BEAT_REST, SkyEffects.restTarget(4, true));
		assertEquals(SkyEffects.BEAT_REST, SkyEffects.easeRest(Float.NaN, SkyEffects.BEAT_REST, FRAME),
				"a sky that was hidden comes back at its resting brightness at once");
		assertTrue(SkyEffects.beatAt(0.1f, SkyEffects.BEAT_REST) > SkyEffects.BEAT_REST, "a new line still lifts it");
		assertTrue(SkyEffects.beatAt(0.1f, SkyEffects.BEAT_REST) <= 1);
	}
}
