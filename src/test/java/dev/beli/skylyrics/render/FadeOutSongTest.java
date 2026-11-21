package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.beli.skylyrics.media.Snapshot;
import dev.beli.skylyrics.media.Track;
import org.junit.jupiter.api.Test;

/** The Record card and the top bar fading out after the music stopped keep the song they showed, never "Unknown song". */
class FadeOutSongTest {
	/** A made-up song. */
	private static final Track SONG = new Track("Paper Lanterns", "The Made Up Band", "Night Album", 200_000, "", "demo");
	private static final Snapshot PLAYING = new Snapshot(SONG, 30_000, true, 0);

	@Test
	void aCardFadingOutAfterTheMusicStoppedKeepsItsSong() {
		// Showing: the card draws what the music player says
		Snapshot drawn = TopBarLayout.keptWhileFading(true, null, PLAYING);
		assertSame(PLAYING, drawn);
		// The music stops: the player says nothing (no title, no length) and the card fades out over a few frames
		for (int frame = 0; frame < 20; frame++) {
			drawn = TopBarLayout.keptWhileFading(false, drawn, Snapshot.NOTHING);
			assertEquals("Paper Lanterns", drawn.track().title());
			assertEquals("The Made Up Band", drawn.track().artist());
			assertEquals(200_000, drawn.track().lengthMs());
		}
	}

	@Test
	void aBarFadingOutKeepsWhatItShows() {
		String shown = TopBarLayout.keptWhileFading(true, null, "Paper Lanterns");
		assertEquals("Paper Lanterns", TopBarLayout.keptWhileFading(false, shown, "Unknown song"));
	}

	@Test
	void aShowingCardFollowsTheMusic() {
		Snapshot next = new Snapshot(new Track("Glass River", "The Made Up Band", "", 180_000, "", "demo"), 0, true, 0);
		assertSame(next, TopBarLayout.keptWhileFading(true, PLAYING, next));
		// Gone from the screen and back: nothing is kept from before
		assertSame(next, TopBarLayout.keptWhileFading(false, null, next));
	}
}
