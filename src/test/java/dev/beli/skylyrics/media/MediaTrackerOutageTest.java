package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class MediaTrackerOutageTest {
	/** Like the Spotify poller: fails on an attempt, then answers with its last snapshot while backing off. */
	private static final class BackingOff implements MediaSource {
		volatile Throwable failure;
		volatile boolean asking;
		volatile CountDownLatch barrier = new CountDownLatch(1);

		@Override
		public Snapshot poll() throws Exception {
			if (asking && failure != null) {
				throw (Exception) failure;
			}
			return Snapshot.NOTHING;
		}

		@Override
		public Throwable failing() {
			return failure;
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
			return "Fake";
		}
	}

	/** Runs the polls queued so far to the end (a control queued after them runs after them). */
	private static void settle(MediaTracker tracker, BackingOff source) throws InterruptedException {
		source.barrier = new CountDownLatch(1);
		tracker.playPause();
		assertTrue(source.barrier.await(30, TimeUnit.SECONDS));
	}

	@Test
	void anOutageLogsOnceAndStaysFailingBetweenRetries() throws Exception {
		BackingOff source = new BackingOff();
		MediaTracker tracker = new MediaTracker(source, () -> false);
		try {
			source.failure = new UnknownHostException();
			source.asking = true;
			tracker.refresh(); // the attempt that throws
			settle(tracker, source);
			source.asking = false;
			for (int i = 0; i < 5; i++) {
				tracker.refresh(); // backing off: answers without asking
			}
			settle(tracker, source);
			source.asking = true;
			tracker.refresh(); // the next attempt fails too
			settle(tracker, source);
			source.asking = false;
			tracker.refresh();
			settle(tracker, source);
			assertEquals(1, tracker.outageWarnings);
			assertFalse(tracker.connected());
			assertFalse(tracker.error().isEmpty());
		} finally {
			tracker.shutdown();
		}
	}

	@Test
	void statusNeverFlickersToConnectedWhileBackingOff() throws Exception {
		BackingOff source = new BackingOff();
		MediaTracker tracker = new MediaTracker(source, () -> false);
		try {
			source.failure = new IOException("boom");
			source.asking = true;
			tracker.refresh();
			settle(tracker, source);
			source.asking = false;
			tracker.refresh();
			settle(tracker, source);
			assertFalse(tracker.connected());
			assertEquals("boom", tracker.error());
		} finally {
			tracker.shutdown();
		}
	}

	@Test
	void recoveryLogsOnceAndClearsTheError() throws Exception {
		BackingOff source = new BackingOff();
		MediaTracker tracker = new MediaTracker(source, () -> false);
		try {
			source.failure = new IOException("boom");
			source.asking = true;
			tracker.refresh();
			settle(tracker, source);
			source.asking = false;
			tracker.refresh();
			settle(tracker, source);
			source.failure = null; // the next attempt works
			tracker.refresh();
			tracker.refresh();
			settle(tracker, source);
			assertEquals(1, tracker.outageWarnings);
			assertEquals(1, tracker.recoveries);
			assertTrue(tracker.connected());
			assertEquals("", tracker.error());
		} finally {
			tracker.shutdown();
		}
	}

	@Test
	void aNetworkFailureBlamesTheInternetNotThePlayer() {
		assertEquals("Can't reach the internet or Spotify", MediaTracker.failureText(new UnknownHostException()));
		assertEquals("Can't reach the internet or Spotify", MediaTracker.failureText(new java.net.ConnectException()));
		assertEquals("Can't reach the internet or Spotify", MediaTracker.failureText(new IOException()));
		assertEquals("Can't reach the internet or Spotify", MediaTracker.failureText(new IOException("wrapped", new UnknownHostException("api.spotify.com"))));
		assertEquals("Spotify answered with error 500", MediaTracker.failureText(new IOException("Spotify answered with error 500")));
		assertEquals("Allow Minecraft", MediaTracker.failureText(new IllegalStateException("Allow Minecraft")));
	}
}
