package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.config.SkyLyricsConfig.Direction;
import dev.beli.skylyrics.config.SkyLyricsConfig.Placement;
import dev.beli.skylyrics.lyrics.LyricLine;
import dev.beli.skylyrics.lyrics.Lyrics;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArcFollowTest {
	private static final Lyrics SONG = new Lyrics(List.of(
			new LyricLine(1_000, "First"),
			new LyricLine(4_000, "Second"),
			new LyricLine(60_000, "Last")), true, false);

	@Test
	void anyChangedArcSettingMakesANewKey() {
		float[] lock = {10f, -5f};
		var base = LyricsRenderer.ArcKey.of(Placement.SKY, Direction.FOLLOW, 22f, null);
		assertEquals(base, LyricsRenderer.ArcKey.of(Placement.SKY, Direction.FOLLOW, 22f, null));
		assertNotEquals(base, LyricsRenderer.ArcKey.of(Placement.SKY, Direction.FOLLOW, 30f, null));
		assertNotEquals(base, LyricsRenderer.ArcKey.of(Placement.FLOATING, Direction.FOLLOW, 22f, null));
		assertNotEquals(base, LyricsRenderer.ArcKey.of(Placement.SKY, Direction.NORTH, 22f, null));
		assertNotEquals(base, LyricsRenderer.ArcKey.of(Placement.SKY, Direction.FOLLOW, 22f, lock));
		assertEquals(LyricsRenderer.ArcKey.of(Placement.SKY, Direction.FOLLOW, 22f, lock),
				LyricsRenderer.ArcKey.of(Placement.SKY, Direction.FOLLOW, 22f, new float[] {10f, -5f}));
	}

	@Test
	void faintGlowIsLeftOutBelowTheLowestAlphaTheFontKeeps() {
		assertFalse(LyricsRenderer.glowVisible(2));
		assertFalse(LyricsRenderer.glowVisible(3));
		assertTrue(LyricsRenderer.glowVisible(4));
		assertTrue(LyricsRenderer.glowVisible(255));
	}

	@Test
	void aLineOnlyFadesOnceItHasBeenOverForAWhile() {
		long end = SONG.lineEnd(0); // the next line's start
		assertEquals(1f, LyricsRenderer.staleFade(SONG, 0, 2_000), 0f);
		assertEquals(1f, LyricsRenderer.staleFade(SONG, 0, end + LyricsRenderer.STALE_AFTER_MS), 0f);
		float half = LyricsRenderer.staleFade(SONG, 1, SONG.lineEnd(1) + LyricsRenderer.STALE_AFTER_MS + LyricsRenderer.STALE_FADE_MS / 2);
		assertTrue(half > 0.4f && half < 0.6f, "half way: " + half);
		assertEquals(0f, LyricsRenderer.staleFade(SONG, 1, SONG.lineEnd(1) + LyricsRenderer.STALE_AFTER_MS + LyricsRenderer.STALE_FADE_MS), 0f);
	}

	@Test
	void theLastLineGoesAfterItsFiveSecondsAndALongLineAfterTwelve() {
		assertEquals(65_000, SONG.lineEnd(2));
		assertEquals(1f, LyricsRenderer.staleFade(SONG, 2, 66_000), 0f);
		assertEquals(0f, LyricsRenderer.staleFade(SONG, 2, 80_000), 0f);
		// The second line has 56 s to the last one, capped at 12 s
		assertEquals(16_000, SONG.lineEnd(1));
		assertEquals(0f, LyricsRenderer.staleFade(SONG, 1, 30_000), 0f);
	}

	@Test
	void pausesUnsyncedLyricsAndTheTimeBeforeTheFirstLineAreNotFaded() {
		assertEquals(1f, LyricsRenderer.staleFade(SONG, -1, 0), 0f);
		assertEquals(1f, LyricsRenderer.staleFade(SONG, 9, 999_999), 0f);
		Lyrics gap = new Lyrics(List.of(new LyricLine(1_000, "A"), new LyricLine(2_000, "")), true, false);
		assertTrue(gap.get(1).isBreak());
		assertEquals(1f, LyricsRenderer.staleFade(gap, 1, 99_000), 0f);
		Lyrics plain = new Lyrics(List.of(new LyricLine(1_000, "A")), false, false);
		assertEquals(1f, LyricsRenderer.staleFade(plain, 0, 99_000), 0f);
	}

	@Test
	void theFloatingDistanceSliderMovesTheLyricsOnEveryStep() {
		assertEquals(4f, LyricsRenderer.floatingDistance(8f), 1e-4);
		assertEquals(16f, LyricsRenderer.floatingDistance(160f), 1e-4);
		assertEquals(64f / 6f, LyricsRenderer.floatingDistance(64f), 1e-3); // the default stays where it was
		float last = 0;
		for (int d = 8; d <= 160; d++) {
			float now = LyricsRenderer.floatingDistance(d);
			assertTrue(now > last, "step " + d);
			last = now;
		}
	}
}
