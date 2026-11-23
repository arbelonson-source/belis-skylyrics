package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/** close() runs on the shutdown thread while the media thread may still be inside poll(). */
class MprisSourceCloseTest {
	private static final String SLOW = "org.mpris.MediaPlayer2.slowapp";
	private static final String GOOD = "org.mpris.MediaPlayer2.mpv";

	@Test
	void closingDuringAPollDoesNotBreakThatPollAndIsQuick() throws Exception {
		CountDownLatch slowAsked = new CountDownLatch(1);
		CountDownLatch unstick = new CountDownLatch(1);
		MprisSource source = new MprisSource(() -> "", new MprisSource.Reader() {
			@Override
			public List<String> names() {
				return List.of(SLOW, GOOD);
			}

			@Override
			public MprisSource.Reading read(String busName) throws Exception {
				if (busName.equals(SLOW)) {
					slowAsked.countDown();
					unstick.await(60, TimeUnit.SECONDS);
				}
				return new MprisSource.Reading(busName, new Track("Song", "Band", "", 200_000, "", "x"), 0, true, "/id");
			}
		});
		AtomicReference<Throwable> failure = new AtomicReference<>();
		Thread media = new Thread(() -> {
			try {
				source.poll();
			} catch (Throwable e) {
				failure.set(e);
			}
		});
		media.start();
		try {
			assertTrue(slowAsked.await(20, TimeUnit.SECONDS));
			long start = System.nanoTime();
			source.close();
			long tookMs = (System.nanoTime() - start) / 1_000_000;
			assertTrue(tookMs < 15_000, "close() held the shutdown for " + tookMs + " ms");
		} finally {
			unstick.countDown();
			media.join(20_000);
		}
		assertNull(failure.get(), "the poll that was running when close() came broke: " + failure.get());
	}

	/**
	 * close() (the game's shutdown, or a reconnect) left the source closed for good: every later poll threw away the reads
	 * still running when it ended, so a player slower to answer than one poll waits never showed again.
	 */
	@Test
	void aSlowPlayerIsStillReadAfterAClose() throws Exception {
		long answerWaitMs = 100; // a poll's wait, shorter than the game's: only being slower than it counts here
		MprisSource source = new MprisSource(() -> "", new MprisSource.Reader() {
			@Override
			public List<String> names() {
				return List.of(SLOW);
			}

			@Override
			public MprisSource.Reading read(String busName) throws Exception {
				Thread.sleep(answerWaitMs + 500); // answers, but after a poll has stopped waiting
				return new MprisSource.Reading(busName, new Track("Song", "Band", "", 200_000, "", "x"), 0, true, "/id");
			}
		});
		source.answerWaitMs = answerWaitMs;
		source.close();
		long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		Snapshot s = Snapshot.NOTHING;
		while (s.track().isNone()) {
			assertTrue(System.nanoTime() < until, "the slow player never showed after close()");
			s = source.poll();
			Thread.sleep(50);
		}
		assertEquals("Song", s.track().title());
	}
}
