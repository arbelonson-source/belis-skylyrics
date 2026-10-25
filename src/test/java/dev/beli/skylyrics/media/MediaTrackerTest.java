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

		synchronized void then(Track t) {
			queue.add(new Snapshot(t, 1000, true, System.nanoTime()));
		}

		@Override
		public synchronized Snapshot poll() {
			if (!queue.isEmpty()) {
				last = queue.poll();
			}
			polls.incrementAndGet();
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
}
