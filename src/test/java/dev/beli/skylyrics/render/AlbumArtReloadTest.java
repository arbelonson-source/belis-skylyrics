package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.media.Track;

/** A corrected cover for the same song replaces the current one only when it is in; a failure leaves the old one. */
class AlbumArtReloadTest {
	private static final class ManualExecutor extends AbstractExecutorService {
		final List<FutureTask<?>> tasks = new ArrayList<>();

		@Override
		protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
			FutureTask<T> task = new FutureTask<>(runnable, value);
			tasks.add(task);
			return task;
		}

		@Override
		protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
			FutureTask<T> task = new FutureTask<>(callable);
			tasks.add(task);
			return task;
		}

		@Override
		public void execute(Runnable command) {
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

		void runLast() {
			tasks.get(tasks.size() - 1).run();
		}
	}

	private static Track song(String title, String art) {
		return new Track(title, "Nova Lane", "", 180_000, art, "spotify");
	}

	private static BufferedImage red() {
		BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 8; x++) {
				image.setRGB(x, y, 0xFF2020);
			}
		}
		return image;
	}

	/** First cover loaded and on the GPU, colour picked. */
	private static AlbumArt withCover(ManualExecutor executor) {
		AlbumArt art = new AlbumArt(executor);
		art.fetcher = url -> red();
		art.load(song("A", "https://example.invalid/first.jpg"));
		executor.runLast();
		AlbumArt.Pending taken = art.takeIfCurrent();
		assertNotNull(taken);
		art.markUploaded(taken.gen());
		taken.image().close();
		assertTrue(art.hasTexture());
		return art;
	}

	@Test
	void theOldCoverStaysWhileTheCorrectedOneLoads() {
		ManualExecutor executor = new ManualExecutor();
		AlbumArt art = withCover(executor);
		int colour = art.color();
		assertTrue(colour != AlbumArt.FALLBACK_COLOR);
		art.load(song("A", "https://example.invalid/second.jpg")); // pending: nothing has run
		assertTrue(art.hasTexture(), "no placeholder flicker");
		assertEquals(colour, art.color());
	}

	@Test
	void aFailedCorrectedCoverKeepsTheOldCoverAndColour() {
		ManualExecutor executor = new ManualExecutor();
		AlbumArt art = withCover(executor);
		int colour = art.color();
		art.fetcher = url -> null; // 404, undecodable, deleted file
		art.load(song("A", "https://example.invalid/second.jpg"));
		executor.runLast();
		assertTrue(art.hasTexture());
		assertEquals(colour, art.color());
		art.fetcher = url -> {
			throw new java.io.FileNotFoundException("gone");
		};
		art.load(song("A", "https://example.invalid/third.jpg"));
		executor.runLast();
		assertTrue(art.hasTexture());
		assertEquals(colour, art.color());
	}

	@Test
	void aCorrectedCoverThatArrivesReplacesTheOldOne() {
		ManualExecutor executor = new ManualExecutor();
		AlbumArt art = withCover(executor);
		art.fetcher = url -> {
			BufferedImage blue = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
			for (int y = 0; y < 8; y++) {
				for (int x = 0; x < 8; x++) {
					blue.setRGB(x, y, 0x2020FF);
				}
			}
			return blue;
		};
		int before = art.color();
		art.load(song("A", "https://example.invalid/second.jpg"));
		executor.runLast();
		assertTrue(art.color() != before, "the new cover's colour");
		AlbumArt.Pending taken = art.takeIfCurrent();
		assertNotNull(taken, "the new image is waiting for upload");
		art.markUploaded(taken.gen());
		taken.image().close();
		assertTrue(art.hasTexture());
	}

	@Test
	void aDifferentSongDropsTheOldCoverAtOnce() {
		ManualExecutor executor = new ManualExecutor();
		AlbumArt art = withCover(executor);
		art.load(song("B", "https://example.invalid/other.jpg"));
		assertFalse(art.hasTexture());
		assertEquals(AlbumArt.FALLBACK_COLOR, art.color());
		art.fetcher = url -> null;
		executor.runLast();
		assertFalse(art.hasTexture(), "a failed cover of a new song leaves nothing behind");
	}

	@Test
	void aCoverThatIsRemovedForTheSameSongIsDropped() {
		ManualExecutor executor = new ManualExecutor();
		AlbumArt art = withCover(executor);
		art.load(song("A", ""));
		assertFalse(art.hasTexture());
		assertEquals(AlbumArt.FALLBACK_COLOR, art.color());
	}
}
