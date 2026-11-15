package dev.beli.skylyrics.lyrics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.media.Track;

class PendingFetchTest {
	@TempDir
	Path cache;

	private static Track song(String title) {
		return new Track(title, "Nova Lane", "", 180_000, "", "spotify");
	}

	/**
	 * Song A fails with a temporary error. While it works out its retry delay (after its generation check), song B is
	 * loaded. A's retry must not take over {@code pendingFetch}: when song C then skips B, it has to cancel B's fetch
	 * (which is waiting on the network), not the stale retry.
	 */
	@Test
	void aLateRetryDoesNotTakeOverTheNextSongsFetch() throws Exception {
		CountDownLatch bRunning = new CountDownLatch(1);
		CountDownLatch bInterrupted = new CountDownLatch(1);
		CountDownLatch holdB = new CountDownLatch(1);
		AtomicReference<LyricsService> service = new AtomicReference<>();
		LyricsSource source = t -> {
			if (t.title().equals("A")) {
				throw new IOException("offline");
			}
			if (t.title().equals("B")) {
				bRunning.countDown();
				try {
					holdB.await(10, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					bInterrupted.countDown();
					throw e;
				}
			}
			return LrclibClient.Result.NOT_FOUND;
		};
		boolean[] done = {false};
		// Called by A's fetch after its generation check and before it schedules the retry
		LyricsService s = new LyricsService(source, cache, attempt -> {
			if (!done[0]) {
				done[0] = true;
				CountDownLatch loaded = new CountDownLatch(1);
				new Thread(() -> {
					service.get().load(song("B"));
					loaded.countDown();
				}).start();
				while (true) {
					try {
						loaded.await();
						break;
					} catch (InterruptedException ignored) {
						// load(B) interrupts this very fetch; keep waiting for it
					}
				}
			}
			return 60_000; // far away: the retry stays pending
		});
		service.set(s);
		try {
			s.load(song("A"));
			assertTrue(bRunning.await(10, TimeUnit.SECONDS));
			s.load(song("C"));
			assertTrue(bInterrupted.await(5, TimeUnit.SECONDS), "skipping B should cancel B's fetch");
		} finally {
			holdB.countDown();
			s.shutdown();
		}
	}
}
