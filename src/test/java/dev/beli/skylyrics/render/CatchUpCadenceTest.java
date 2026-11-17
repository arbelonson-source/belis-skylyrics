package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A fast line at a low frame rate: how many pieces the catch-up limit lets through, and that the line's end is reached. */
class CatchUpCadenceTest {
	/** Pieces spawned for a line of {@code pieces} sung evenly over {@code lineMs}, drawn every {@code frameMs}; also checks nothing lags. */
	private static int spawned(int pieces, long lineMs, long frameMs) {
		int spawned = 0, count = 0;
		for (long t = 0; t <= lineMs + frameMs; t += frameMs) {
			int due = (int) Math.min(pieces, t * pieces / lineMs);
			int from = WorldWords.skipTo(spawned, due, false, false);
			count += due - from;
			spawned = due;
			assertEquals(due, spawned, "never behind the due pieces");
		}
		assertEquals(pieces, spawned, "the end of the line is reached");
		return count;
	}

	@Test
	void aRapLineAtTenFramesASecondSpawnsEveryPiece() {
		// 12 pieces a second is 1.2 a frame at 10 fps: well inside the catch-up
		assertEquals(24, spawned(24, 2000, 100));
		// 20 pieces a second is 2 a frame, exactly CATCH_UP: still all of them
		assertEquals(40, spawned(40, 2000, 100));
	}

	@Test
	void onlyAHitchBiggerThanTheCatchUpDropsPiecesAndTheLineStillEnds() {
		int count = spawned(50, 2000, 100);
		assertTrue(count < 50, "pieces past the catch-up are skipped, not queued: " + count);
	}
}
