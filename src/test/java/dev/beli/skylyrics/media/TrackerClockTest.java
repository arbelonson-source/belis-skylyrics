package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/** The song clock the lyrics follow: pause, seek, jitter and the song's end, with no waiting on real time. */
class TrackerClockTest {
	private static final class Scripted implements MediaSource {
		private final Deque<Snapshot> queue = new ArrayDeque<>();
		private Snapshot last = Snapshot.NOTHING;
		final CountDownLatch barrier = new CountDownLatch(1);

		@Override
		public synchronized Snapshot poll() {
			if (!queue.isEmpty()) {
				last = queue.poll();
			}
			return last;
		}

		@Override
		public void playPause() {
			barrier.countDown();
		}

		@Override
		public void next() {
		}

		@Override
		public void previous() {
		}

		@Override
		public String displayName() {
			return "Scripted";
		}
	}

	private static Track song(long lengthMs) {
		return new Track("Made Up Song", "Made Up Band", "", lengthMs, "", "firefox");
	}

	/** Gives the tracker one reading per poll and waits until every one has been taken in (the control runs after them). */
	private static MediaTracker feed(Snapshot... readings) throws InterruptedException {
		Scripted source = new Scripted();
		MediaTracker tracker = new MediaTracker(source, () -> false);
		for (Snapshot r : readings) {
			synchronized (source) {
				source.queue.add(r);
			}
			tracker.refresh();
		}
		tracker.playPause();
		assertTrue(source.barrier.await(30, TimeUnit.SECONDS), "timed out");
		return tracker;
	}

	private static Snapshot paused(long positionMs) {
		return new Snapshot(song(215_000), positionMs, false, System.nanoTime() - 60_000_000_000L);
	}

	@Test
	void aPausedSongStaysWhereItWasHoweverLongAgoItWasRead() throws InterruptedException {
		MediaTracker tracker = feed(paused(42_000));
		try {
			assertEquals(42_000, tracker.positionMs());
		} finally {
			tracker.shutdown();
		}
	}

	@Test
	void theClockNeverRunsPastTheEndOfTheSong() throws InterruptedException {
		long readLongAgo = System.nanoTime() - 100_000_000_000L;
		MediaTracker tracker = feed(new Snapshot(song(215_000), 214_000, true, readLongAgo));
		try {
			assertEquals(215_000, tracker.positionMs());
		} finally {
			tracker.shutdown();
		}
	}

	@Test
	void aSongWithNoLengthKeepsRunning() throws InterruptedException {
		long readLongAgo = System.nanoTime() - 100_000_000_000L;
		MediaTracker tracker = feed(new Snapshot(song(0), 1_000, true, readLongAgo));
		try {
			assertTrue(tracker.positionMs() >= 100_000, "position " + tracker.positionMs());
		} finally {
			tracker.shutdown();
		}
	}

	@Test
	void aSmallWobbleInThePlayersPositionIsNotASeek() throws InterruptedException {
		MediaTracker tracker = feed(paused(10_000), paused(10_350));
		try {
			assertEquals(10_000, tracker.positionMs());
		} finally {
			tracker.shutdown();
		}
	}

	@Test
	void aJumpOfMoreThanTheWobbleLimitIsASeek() throws InterruptedException {
		MediaTracker tracker = feed(paused(10_000), paused(10_351));
		try {
			assertEquals(10_351, tracker.positionMs());
		} finally {
			tracker.shutdown();
		}
	}

	@Test
	void seekingBackAlsoMovesTheClock() throws InterruptedException {
		MediaTracker tracker = feed(paused(120_000), paused(3_000));
		try {
			assertEquals(3_000, tracker.positionMs());
		} finally {
			tracker.shutdown();
		}
	}

	@Test
	void pausingFreezesTheClockAtThePlayersPosition() throws InterruptedException {
		long now = System.nanoTime();
		MediaTracker tracker = feed(new Snapshot(song(215_000), 10_000, true, now),
				new Snapshot(song(215_000), 10_100, false, now));
		try {
			assertEquals(10_100, tracker.positionMs());
			assertFalse(tracker.playing());
		} finally {
			tracker.shutdown();
		}
	}

	@Test
	void playingAgainRunsTheClockFromWhereItWasPaused() throws InterruptedException {
		long before = System.nanoTime();
		MediaTracker tracker = feed(paused(12_000), new Snapshot(song(215_000), 12_000, true, before));
		try {
			long pos = tracker.positionMs();
			long after = System.nanoTime();
			assertTrue(tracker.playing());
			assertTrue(pos >= 12_000 && pos <= 12_000 + (after - before) / 1_000_000 + 50, "position " + pos);
		} finally {
			tracker.shutdown();
		}
	}

	private static final long SEC = 1_000_000_000L;

	/**
	 * A player that never says where the song is (it reads 0 every time) is followed on the clock from when the song
	 * appeared. Such a player reads 0 when paused too: that is not where the song stopped.
	 */
	@Test
	void aPlayerThatNeverSaysWhereItIsPausesWhereTheClockGotTo() throws InterruptedException {
		long now = System.nanoTime();
		Snapshot zero = new Snapshot(song(0), 0, true, now - 12 * SEC);
		MediaTracker tracker = feed(new Snapshot(song(0), 0, true, now - 20 * SEC), zero, zero, zero, zero,
				new Snapshot(song(0), 0, false, now - 10 * SEC));
		try {
			assertFalse(tracker.playing());
			assertEquals(10_000, tracker.positionMs(), "paused 10 s after the song appeared, not back at the top");
		} finally {
			tracker.shutdown();
		}
	}

	@Test
	void aPlayerThatNeverSaysWhereItIsGoesOnFromThePauseWhenPlayedAgain() throws InterruptedException {
		long now = System.nanoTime();
		Snapshot zero = new Snapshot(song(0), 0, true, now - 25 * SEC);
		Snapshot paused = new Snapshot(song(0), 0, false, now - 20 * SEC); // 10 s into the song
		Snapshot again = new Snapshot(song(0), 0, true, now - 5 * SEC); // played again 15 s later
		MediaTracker tracker = feed(new Snapshot(song(0), 0, true, now - 30 * SEC), zero, zero, zero, zero,
				paused, paused, again, again, again, again, again);
		try {
			long pos = tracker.positionMs();
			// 10 s before the pause and 5 s since, plus however long this machine took since now
			long hi = 15_000 + (System.nanoTime() - now) / 1_000_000 + 50;
			assertTrue(tracker.playing());
			assertTrue(pos >= 14_950 && pos <= hi, "position " + pos + " not in 14950.." + hi);
		} finally {
			tracker.shutdown();
		}
	}
}
