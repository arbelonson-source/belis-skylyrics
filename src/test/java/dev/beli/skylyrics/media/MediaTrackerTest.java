package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class MediaTrackerTest {
	private static final class FakeSource implements MediaSource {
		private final Deque<Snapshot> queue = new ArrayDeque<>();
		private volatile Snapshot last = Snapshot.NOTHING;
		final AtomicInteger polls = new AtomicInteger();

		static final Snapshot FAIL = new Snapshot(Track.NONE, -1, false, 0);

		synchronized void then(Snapshot s) {
			queue.add(s);
		}

		synchronized void then(Track t) {
			queue.add(new Snapshot(t, 1000, true, System.nanoTime()));
		}

		@Override
		public synchronized Snapshot poll() {
			if (!queue.isEmpty()) {
				last = queue.poll();
			}
			polls.incrementAndGet();
			if (last == FAIL) {
				throw new IllegalStateException("player did not answer");
			}
			return last;
		}

		@Override
		public void playPause() {
		}

		@Override
		public void next() {
		}

		@Override
		public void previous() {
		}

		@Override
		public String displayName() {
			return "Fake";
		}
	}

	private static List<Track> run(Track... steps) throws InterruptedException {
		FakeSource source = new FakeSource();
		MediaTracker tracker = new MediaTracker(source, () -> false);
		List<Track> changes = new CopyOnWriteArrayList<>();
		tracker.onTrackChange(changes::add);
		try {
			for (Track t : steps) {
				source.then(t);
				tracker.refresh();
			}
			tracker.refresh();
			long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
			while (source.polls.get() < steps.length + 1) {
				assertTrue(System.nanoTime() < until, "timed out");
				Thread.sleep(5);
			}
			// the last poll was a no-op, so every earlier poll has finished
		} finally {
			tracker.shutdown();
		}
		return changes;
	}

	private static Track song(long lengthMs, String art) {
		return new Track("Made Up Song", "Made Up Band", "", lengthMs, art, "firefox");
	}

	@Test
	void lengthThatArrivesLateIsPassedOn() throws InterruptedException {
		List<Track> changes = run(song(0, ""), song(0, ""), song(215_000, ""));
		assertEquals(2, changes.size(), changes.toString());
		assertEquals(215_000, changes.get(1).lengthMs());
	}

	@Test
	void coverThatArrivesLateIsPassedOn() throws InterruptedException {
		List<Track> changes = run(song(215_000, ""), song(215_000, "file:///tmp/cover.png"));
		assertEquals(2, changes.size(), changes.toString());
		assertEquals("file:///tmp/cover.png", changes.get(1).artUrl());
	}

	@Test
	void sameSongDoesNotFireAgain() throws InterruptedException {
		List<Track> changes = run(song(215_000, "file:///a.png"), song(215_000, "file:///a.png"), song(215_000, "file:///b.png"));
		assertEquals(1, changes.size(), changes.toString());
	}

	private static Track song(String artist) {
		return new Track("Made Up Song", artist, "", 215_000, "", "firefox");
	}

	@Test
	void artistThatArrivesAfterTheTitleIsOneSong() throws InterruptedException {
		List<Track> changes = run(song(""), song("Made Up Band"), song("Made Up Band"));
		assertEquals(1, changes.size(), changes.toString());
		assertEquals("Made Up Band", changes.get(0).artist());
	}

	@Test
	void aSongThatReallyHasNoArtistIsAnnouncedOnceItStays() throws InterruptedException {
		List<Track> changes = run(song(""), song(""), song(""));
		assertEquals(1, changes.size(), changes.toString());
		assertEquals("", changes.get(0).artist());
	}

	@Test
	void albumThatArrivesLateIsNotANewSong() throws InterruptedException {
		Track bare = new Track("Made Up Song", "Made Up Band", "", 215_000, "", "firefox");
		Track full = new Track("Made Up Song", "Made Up Band", "Made Up Album", 215_000, "", "firefox");
		List<Track> changes = run(bare, full, full);
		assertEquals(1, changes.size(), changes.toString());
	}

	@Test
	void oneEmptyReadDoesNotWipeTheSong() throws InterruptedException {
		List<Track> changes = run(song(215_000, ""), Track.NONE, song(215_000, ""));
		assertEquals(1, changes.size(), changes.toString());
		assertEquals("Made Up Song", changes.get(0).title());
	}

	@Test
	void aRealStopClearsAfterAFewReads() throws InterruptedException {
		List<Track> changes = run(song(215_000, ""), Track.NONE, Track.NONE, Track.NONE, Track.NONE);
		assertEquals(2, changes.size(), changes.toString());
		assertTrue(changes.get(1).isNone());
	}

	@Test
	void fasterPlaybackMovesTheClockFaster() throws InterruptedException {
		FakeSource source = new FakeSource();
		MediaTracker tracker = new MediaTracker(source, () -> false);
		try {
			long readAt = System.nanoTime() - 1_000_000_000L;
			source.then(new Snapshot(song(215_000, ""), 10_000, true, readAt, 2.0));
			tracker.refresh();
			long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
			while (source.polls.get() < 1) {
				assertTrue(System.nanoTime() < until, "timed out");
				Thread.sleep(5);
			}
			Thread.sleep(50);
			// Twice as fast as the clock since the read, however slow this machine is (1x would be about 1 s short)
			long before = System.nanoTime();
			long pos = tracker.positionMs();
			long after = System.nanoTime();
			long lo = 10_000 + 2 * (before - readAt) / 1_000_000 - 50;
			long hi = 10_000 + 2 * (after - readAt) / 1_000_000 + 50;
			assertTrue(pos >= lo && pos <= hi, "position " + pos + " not in " + lo + ".." + hi);
		} finally {
			tracker.shutdown();
		}
	}

	@Test
	void aFailedPollDoesNotWipeTheSong() throws InterruptedException {
		FakeSource source = new FakeSource();
		MediaTracker tracker = new MediaTracker(source, () -> false);
		List<Track> changes = new CopyOnWriteArrayList<>();
		tracker.onTrackChange(changes::add);
		try {
			source.then(song(215_000, ""));
			tracker.refresh();
			source.then(FakeSource.FAIL);
			tracker.refresh();
			source.then(song(215_000, ""));
			tracker.refresh();
			tracker.refresh();
			long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
			while (source.polls.get() < 4) {
				assertTrue(System.nanoTime() < until, "timed out");
				Thread.sleep(5);
			}
		} finally {
			tracker.shutdown();
		}
		assertEquals(1, changes.size(), changes.toString());
	}

	@Test
	void aListenerThatThrowsDoesNotStopPolling() throws InterruptedException {
		FakeSource source = new FakeSource();
		MediaTracker tracker = new MediaTracker(source, () -> false);
		List<Track> seen = new CopyOnWriteArrayList<>();
		tracker.onTrackChange(t -> {
			seen.add(t);
			if (seen.size() == 1) {
				throw new IllegalStateException("listener broke");
			}
		});
		source.then(song(215_000, ""));
		source.then(new Track("Other Song", "Made Up Band", "", 200_000, "", "firefox"));
		tracker.start(20);
		try {
			long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
			while (seen.size() < 2 && System.nanoTime() < until) {
				Thread.sleep(10);
			}
		} finally {
			tracker.shutdown();
		}
		assertEquals(2, seen.size(), seen.toString());
		assertEquals("Other Song", seen.get(1).title());
	}
}
