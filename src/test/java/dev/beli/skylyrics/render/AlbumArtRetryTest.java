package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.media.Track;

class AlbumArtRetryTest {
	/** Runs everything at once on the calling thread. */
	private static final class Inline extends AbstractExecutorService {
		@Override
		public void execute(Runnable command) {
			command.run();
		}

		@Override
		public void shutdown() {
		}

		@Override
		public List<Runnable> shutdownNow() {
			return List.of();
		}

		@Override
		public boolean isShutdown() {
			return false;
		}

		@Override
		public boolean isTerminated() {
			return false;
		}

		@Override
		public boolean awaitTermination(long timeout, TimeUnit unit) {
			return true;
		}
	}

	private final List<Long> delays = new ArrayList<>();
	private final List<FutureTask<?>> scheduled = new ArrayList<>();
	private int fetches;

	private AlbumArt art() {
		return new AlbumArt(new Inline(), (task, delayMs) -> {
			delays.add(delayMs);
			FutureTask<Void> f = new FutureTask<>(task, null);
			scheduled.add(f);
			return f;
		});
	}

	private static Track song(String title, String art) {
		return new Track(title, "Nova Lane", "", 180_000, art, "spotify");
	}

	private static void runLast(List<FutureTask<?>> tasks) {
		tasks.get(tasks.size() - 1).run();
	}

	@Test
	void offlineAtSongStartIsRetriedUntilTheCoverArrives() throws Exception {
		AlbumArt art = art();
		art.fetcher = url -> {
			if (++fetches < 3) {
				throw new java.net.ConnectException("offline");
			}
			return new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
		};
		art.load(song("T", "https://img.example/a.jpg"));
		assertEquals(List.of(5_000L), delays);
		runLast(scheduled);
		assertEquals(List.of(5_000L, 15_000L), delays);
		runLast(scheduled);
		assertEquals(3, fetches);
		assertNotNull(art.takeIfCurrent());
	}

	@Test
	void aMissingCoverIsNotRetried() {
		AlbumArt art = art();
		art.fetcher = url -> null; // 404, not an image
		art.load(song("T", "https://img.example/a.jpg"));
		assertTrue(delays.isEmpty());
	}

	@Test
	void aSongChangeCancelsThePendingRetry() {
		AlbumArt art = art();
		art.fetcher = url -> {
			throw new CoverRetry.TemporaryFailure("503", null);
		};
		art.load(song("T", "https://img.example/a.jpg"));
		assertEquals(1, scheduled.size());
		art.load(song("U", ""));
		assertTrue(scheduled.get(0).isCancelled());
	}

	@Test
	void retriesKeepGrowingAndNeverPileUp() {
		AlbumArt art = art();
		art.fetcher = url -> {
			throw new java.net.http.HttpTimeoutException("slow");
		};
		art.load(song("T", "https://img.example/a.jpg"));
		for (int i = 0; i < 5; i++) {
			runLast(scheduled);
		}
		assertEquals(List.of(5_000L, 15_000L, 30_000L, 60_000L, 60_000L, 60_000L), delays);
		long pending = scheduled.stream().filter(f -> !f.isDone() && !f.isCancelled()).count();
		assertEquals(1, pending);
	}

	@Test
	void policyOnlyRetriesWhatMayPass() {
		assertTrue(CoverRetry.temporaryStatus(503));
		assertTrue(CoverRetry.temporaryStatus(429));
		assertFalse(CoverRetry.temporaryStatus(404));
		assertFalse(CoverRetry.temporaryStatus(403));
		assertFalse(CoverRetry.temporary(new java.io.IOException("Refused a redirect to nowhere")));
		assertTrue(CoverRetry.temporary(new java.net.UnknownHostException("img.example")));
	}
}
