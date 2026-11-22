package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/** Quitting the game while the music player is being read: the read the quit cuts short is nobody's business. */
class MediaTrackerShutdownTest {
	/**
	 * A player that takes its time to answer, as osascript on a Mac or the Windows helper does for a moment every read.
	 * Quitting interrupts the wait; {@code answersAnyway} makes it answer with a new song all the same (a read that
	 * finished just as the game closed).
	 */
	private static final class SlowPlayer implements MediaSource {
		final CountDownLatch reading = new CountDownLatch(1);
		final AtomicInteger closes = new AtomicInteger();
		final boolean answersAnyway;
		volatile Thread thread;

		SlowPlayer(boolean answersAnyway) {
			this.answersAnyway = answersAnyway;
		}

		@Override
		public Snapshot poll() throws Exception {
			thread = Thread.currentThread();
			reading.countDown();
			try {
				new CountDownLatch(1).await(30, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				if (!answersAnyway) {
					throw e;
				}
			}
			return new Snapshot(new Track("Late Song", "Someone", "", 200_000, "", "slow"), 1000, true, System.nanoTime());
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
			return "Slow";
		}

		@Override
		public void close() {
			closes.incrementAndGet();
		}
	}

	/** Quits while {@code source} is being read, and waits until the tracker's thread has finished with that read. */
	private static MediaTracker quitDuringRead(SlowPlayer source, AtomicInteger songs) throws InterruptedException {
		MediaTracker tracker = new MediaTracker(source, () -> false);
		tracker.onTrackChange(t -> songs.incrementAndGet());
		tracker.refresh();
		assertTrue(source.reading.await(30, TimeUnit.SECONDS));
		tracker.shutdown();
		Thread reader = source.thread;
		reader.join(30_000); // the tracker's only thread ends once the read it was doing is over
		assertFalse(reader.isAlive());
		return tracker;
	}

	@Test
	void aReadCutShortByQuittingIsNotLoggedAsAFailingPlayer() throws Exception {
		SlowPlayer source = new SlowPlayer(false);
		AtomicInteger songs = new AtomicInteger();
		MediaTracker tracker = quitDuringRead(source, songs);
		// The old code logged "Couldn't read the music player: ... (java.lang.InterruptedException)" at every such quit
		assertEquals(0, tracker.outageWarnings, "the quit's own interruption was logged as a failed read");
		assertEquals(1, source.closes.get());
	}

	@Test
	void aSongReadAsTheGameQuitsIsNotPassedOn() throws Exception {
		SlowPlayer source = new SlowPlayer(true);
		AtomicInteger songs = new AtomicInteger();
		quitDuringRead(source, songs);
		// The rest of the shutdown is already stopping the lyrics, the cover and the Recently heard writer: a new song
		// passed on now only logs their refusals ("Track change step 'lyrics' failed")
		assertEquals(0, songs.get(), "a song read after the quit began was passed on");
	}
}
