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

		/** Counted down by a control sent through the tracker, which runs after every poll asked before it. */
		volatile java.util.concurrent.CountDownLatch barrier = new java.util.concurrent.CountDownLatch(1);

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

		final AtomicInteger standBys = new AtomicInteger();

		/** No @Override: the test also builds against a MediaSource without it. */
		public void standBy() {
			standBys.incrementAndGet();
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
		tracker.onDetailsChange(changes::add); // a late length, cover or artist is passed on too (as the same song)
		try {
			for (Track t : steps) {
				source.then(t);
				tracker.refresh();
			}
			tracker.refresh();
			long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
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
	void aLateLengthIsPassedOnAsTheSameSongNotANewOne() throws InterruptedException {
		FakeSource source = new FakeSource();
		MediaTracker tracker = new MediaTracker(source, () -> false);
		List<Track> songs = new CopyOnWriteArrayList<>();
		List<Track> details = new CopyOnWriteArrayList<>();
		tracker.onTrackChange(songs::add);
		tracker.onDetailsChange(details::add);
		try {
			for (Track t : new Track[] {song(0, ""), song(0, ""), song(215_000, "")}) {
				source.then(t);
				tracker.refresh();
			}
			tracker.refresh();
			long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
			while (source.polls.get() < 4) {
				assertTrue(System.nanoTime() < until, "timed out");
				Thread.sleep(5);
			}
		} finally {
			tracker.shutdown();
		}
		assertEquals(1, songs.size(), songs.toString());
		assertEquals(1, details.size(), details.toString());
		assertEquals(215_000, details.get(0).lengthMs());
	}

	@Test
	void coverThatArrivesLateIsPassedOn() throws InterruptedException {
		List<Track> changes = run(song(215_000, ""), song(215_000, "file:///tmp/cover.png"));
		assertEquals(2, changes.size(), changes.toString());
		assertEquals("file:///tmp/cover.png", changes.get(1).artUrl());
	}

	/** Runs the steps and returns the track changes and the cover changes. */
	private static List<List<Track>> runWithCovers(Track... steps) throws InterruptedException {
		FakeSource source = new FakeSource();
		MediaTracker tracker = new MediaTracker(source, () -> false);
		List<Track> changes = new CopyOnWriteArrayList<>();
		List<Track> covers = new CopyOnWriteArrayList<>();
		tracker.onTrackChange(changes::add);
		tracker.onDetailsChange(changes::add);
		tracker.onCoverChange(covers::add);
		try {
			for (Track t : steps) {
				source.then(t);
				tracker.refresh();
			}
			tracker.refresh();
			long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
			while (source.polls.get() < steps.length + 1) {
				assertTrue(System.nanoTime() < until, "timed out");
				Thread.sleep(5);
			}
			assertEquals(steps[steps.length - 1].artUrl(), tracker.track().artUrl(), "the tracker keeps the newest cover");
		} finally {
			tracker.shutdown();
		}
		return List.of(changes, covers);
	}

	private static Track other(String art) {
		return new Track("Another Song", "Made Up Band", "", 215_000, art, "firefox");
	}

	@Test
	void correctedCoverIsPassedOnAsACoverChangeOnly() throws InterruptedException {
		List<List<Track>> r = runWithCovers(song(215_000, "file:///a.img"), song(215_000, "file:///b.img"));
		assertEquals(1, r.get(0).size(), r.toString());
		assertEquals(1, r.get(1).size(), r.toString());
		assertEquals("file:///b.img", r.get(1).get(0).artUrl());
	}

	@Test
	void coverChangesPerSongAreLimitedAndANewSongStartsOver() throws InterruptedException {
		List<List<Track>> r = runWithCovers(song(215_000, "file:///a.img"), song(215_000, "file:///b.img"),
				song(215_000, "file:///c.img"), song(215_000, "file:///d.img"), song(215_000, "file:///e.img"),
				other("file:///f.img"), other("file:///g.img"));
		assertEquals(2, r.get(0).size(), "two songs: " + r);
		assertEquals(List.of("file:///b.img", "file:///c.img", "file:///g.img"), r.get(1).stream().map(Track::artUrl).toList());
	}

	@Test
	void coverThatArrivesLateIsNotAlsoACoverChange() throws InterruptedException {
		List<List<Track>> r = runWithCovers(song(215_000, ""), song(215_000, "file:///a.img"));
		assertEquals(2, r.get(0).size(), r.toString());
		assertEquals(0, r.get(1).size(), r.toString());
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

	private static Track withUri(String uri) {
		return new Track("Interlude", "Made Up Band", "Made Up Album", 60_000, "", "Spotify", uri);
	}

	@Test
	void twoSongsWithTheSameNamesButOtherAddressesAreTwoSongs() throws InterruptedException {
		List<Track> changes = run(withUri("spotify:track:1"), withUri("spotify:track:2"), withUri("spotify:track:2"));
		assertEquals(2, changes.size(), changes.toString());
	}

	@Test
	void aBrowserPageAddressThatChangesMidSongIsStillTheSameSong() throws InterruptedException {
		Track a = new Track("Made Up Song", "Made Up Band", "", 215_000, "", "firefox", "https://music.example/watch?v=1&list=a&index=1");
		Track b = new Track("Made Up Song", "Made Up Band", "", 215_000, "", "firefox", "https://music.example/watch?v=1&list=a&index=2");
		assertEquals(1, run(a, b, b).size());
	}

	@Test
	void anAddressThatAppearsLateOrIsMissingIsStillTheSameSong() throws InterruptedException {
		assertEquals(1, run(withUri(""), withUri("spotify:track:1"), withUri("spotify:track:1")).size());
		assertEquals(1, run(withUri("spotify:track:1"), withUri(""), withUri("")).size());
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
			// Wait until the snapshot is really in (the poll counter moves before it is stored), not for a guessed time
			long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
			while (tracker.positionMs() < 10_000) {
				assertTrue(System.nanoTime() < until, "timed out");
				Thread.sleep(5);
			}
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
			long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
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
			long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
			while (seen.size() < 2 && System.nanoTime() < until) {
				Thread.sleep(10);
			}
		} finally {
			tracker.shutdown();
		}
		assertEquals(2, seen.size(), seen.toString());
		assertEquals("Other Song", seen.get(1).title());
	}

	/** Feeds the readings one poll each and waits until every one has been taken in. */
	private static MediaTracker feed(FakeSource source, Snapshot... readings) throws InterruptedException {
		MediaTracker tracker = new MediaTracker(source, () -> false);
		for (Snapshot r : readings) {
			source.then(r);
			tracker.refresh();
		}
		tracker.playPause();
		assertTrue(source.barrier.await(30, TimeUnit.SECONDS), "timed out");
		return tracker;
	}

	@Test
	void aPlayerThatNeverSaysWhereItIsStillMovesTheClock() throws InterruptedException {
		FakeSource source = new FakeSource();
		Track t = song(0, "");
		long appeared = System.nanoTime() - 10_000_000_000L;
		Snapshot zero = new Snapshot(t, 0, true, System.nanoTime());
		MediaTracker tracker = feed(source, new Snapshot(t, 0, true, appeared), zero, zero, zero, zero, zero);
		try {
			// The clock runs from when the song appeared (10 s ago here), not from the last poll
			assertTrue(tracker.positionMs() >= 9_500, "position " + tracker.positionMs());
		} finally {
			tracker.shutdown();
		}
	}

	@Test
	void aSeekToZeroThenALaterPositionStillWorks() throws InterruptedException {
		FakeSource source = new FakeSource();
		Track t = song(215_000, "");
		long now = System.nanoTime();
		long readAt = System.nanoTime();
		MediaTracker tracker = feed(source, new Snapshot(t, 50_000, true, now),
				new Snapshot(t, 0, true, now), new Snapshot(t, 0, true, now), new Snapshot(t, 0, true, now),
				new Snapshot(t, 0, true, now), new Snapshot(t, 0, true, now), new Snapshot(t, 3_000, true, readAt));
		try {
			long pos = tracker.positionMs();
			// 3 s plus however long this machine took since the last reading was made
			long hi = 3_000 + (System.nanoTime() - readAt) / 1_000_000 + 50;
			assertTrue(pos >= 2_900 && pos <= hi, "position " + pos + " not in 2900.." + hi);
		} finally {
			tracker.shutdown();
		}
	}

	@Test
	void aRepeatOneRestartMovesTheClockBackToTheStart() throws InterruptedException {
		FakeSource source = new FakeSource();
		Track t = song(215_000, "");
		long now = System.nanoTime();
		MediaTracker tracker = feed(source, new Snapshot(t, 214_000, true, now),
				new Snapshot(t, 0, true, now), new Snapshot(t, 500, true, now), new Snapshot(t, 1_000, true, now));
		try {
			long pos = tracker.positionMs();
			long hi = 1_000 + (System.nanoTime() - now) / 1_000_000 + 50;
			assertTrue(pos >= 900 && pos <= hi, "position " + pos + " not in 900.." + hi);
		} finally {
			tracker.shutdown();
		}
	}

	/** The renderer tells a seek the mod asked for from a position that merely snapped back, by when it was requested. */
	@Test
	void seekRecordsWhenItWasRequested() {
		MediaTracker tracker = new MediaTracker(new FakeSource(), () -> false);
		try {
			assertEquals(0, tracker.seekRequestedNanos(), "none yet");
			long before = System.nanoTime();
			tracker.seek(10_000);
			long after = System.nanoTime();
			long at = tracker.seekRequestedNanos();
			assertTrue(at - before >= 0 && after - at >= 0, "stamped between the two clock reads: " + at);
		} finally {
			tracker.shutdown();
		}
	}

	/**
	 * Turning the demo song on puts the player on stand-by, once, so the Windows reader's hidden PowerShell doesn't idle
	 * while the demo plays; turning it off reads the player again (which starts the helper again).
	 */
	@Test
	void theDemoSongPutsThePlayerOnStandBy() throws InterruptedException {
		FakeSource source = new FakeSource();
		source.then(song(215_000, ""));
		// What each poll finds the demo setting at: player, demo three times, player, demo, demo
		Deque<Boolean> demoAtPoll = new java.util.concurrent.ConcurrentLinkedDeque<>(List.of(false, true, true, true, false, true, true));
		AtomicInteger asked = new AtomicInteger();
		MediaTracker tracker = new MediaTracker(source, () -> {
			asked.incrementAndGet();
			return demoAtPoll.size() > 1 ? demoAtPoll.poll() : demoAtPoll.peek();
		});
		try {
			for (int i = 0; i < 8; i++) {
				tracker.refresh();
			}
			// The polls run one after another: once the 8th has asked the setting, the first 7 are over
			long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
			while (asked.get() < 8) {
				assertTrue(System.nanoTime() < until, "timed out");
				Thread.sleep(5);
			}
		} finally {
			tracker.shutdown();
		}
		assertEquals(2, source.polls.get(), "the player is read only while the demo is off");
		assertEquals(2, source.standBys.get(), "once each time the demo is turned on after the player was read");
		assertEquals(DemoSource.PLAYER, tracker.track().player());
	}
}
