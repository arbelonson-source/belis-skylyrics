package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.media.Track;

/**
 * A song change at the very moment the last cover's download fails must not lock up the game. The failing download
 * holds the cover lock while it books its retry (the real retry timer, which locks the album art), and the song change
 * must not hold the album art while it waits for the cover lock.
 */
class AlbumArtLockOrderTest {
	/** A failure that waits, the first time its cause is asked for, inside the retry check (which runs under the cover lock). */
	private static final class SlowFailure extends java.net.ConnectException {
		final CountDownLatch inLock = new CountDownLatch(1);
		final CountDownLatch release = new CountDownLatch(1);
		private volatile boolean asked;

		SlowFailure() {
			super("offline");
		}

		@Override
		public synchronized Throwable getCause() {
			if (!asked) {
				asked = true;
				inLock.countDown();
				try {
					release.await(20, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			return null;
		}
	}

	@Test
	void aSongChangeWhileAFailedCoverBooksItsRetryDoesNotLockUp() throws Exception {
		ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "cover worker (test)");
			t.setDaemon(true);
			return t;
		});
		AlbumArt art = new AlbumArt(worker, null, () -> true); // null: the real retry timer
		art.toImage = image -> BlankImages.blank(); // no native memory in unit tests
		SlowFailure failure = new SlowFailure();
		art.fetcher = url -> {
			if (url.endsWith("a.jpg")) {
				throw failure;
			}
			return new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
		};
		try {
			art.load(new Track("T", "Nova Lane", "", 180_000, "https://img.example/a.jpg", "spotify"));
			assertTrue(failure.inLock.await(20, TimeUnit.SECONDS), "the download failed and holds the cover lock");
			Thread change = new Thread(() -> art.load(new Track("U", "Nova Lane", "", 180_000, "https://img.example/b.jpg", "spotify")),
					"song change (test)");
			change.setDaemon(true);
			change.start();
			long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
			while (change.getState() != Thread.State.BLOCKED) { // waiting for the cover lock
				assertTrue(System.nanoTime() < until && change.isAlive(), "the song change reached the cover lock");
				Thread.sleep(1);
			}
			failure.release.countDown(); // the failed download goes on to book its retry
			assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
				change.join();
				worker.submit(() -> { }).get(); // and the worker is free again
			}, "the song change and the cover worker locked each other up");
		} finally {
			failure.release.countDown();
			art.close();
		}
	}
}
