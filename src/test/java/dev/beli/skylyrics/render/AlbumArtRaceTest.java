package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;

import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.media.Track;

class AlbumArtRaceTest {
	/** Never runs anything; keeps every Future so a test can see what load() did to it. */
	private static final class RecordingExecutor extends AbstractExecutorService {
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
			// parked: the test decides
		}

		@Override
		public void shutdown() {
		}

		@Override
		public java.util.List<Runnable> shutdownNow() {
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

	private static Track song(String art) {
		return song("T", art);
	}

	private static Track song(String title, String art) {
		return new Track(title, "Nova Lane", "", 180_000, art, "spotify");
	}

	@Test
	void theUploadedCoverIsOnlyCurrentForItsOwnSong() {
		assertTrue(AlbumArt.isCurrent(3, 3));
		assertFalse(AlbumArt.isCurrent(3, 4));
		assertFalse(AlbumArt.isCurrent(-1, 0));
	}

	/** The render thread was mid-upload of A's cover when load(B) ran: the cover must not count as B's. */
	@Test
	void aCoverUploadedAfterTheNextSongLoadedIsNotShownAsItsCover() {
		AlbumArt art = new AlbumArt(new RecordingExecutor());
		art.load(song("A", "file:///a.png"));
		long genA = 1; // the first load
		assertFalse(art.hasTexture());
		art.load(song("B", "file:///b.png"));
		art.markUploaded(genA); // upload() finishing A's image after load(B)
		assertFalse(art.hasTexture(), "A's cover is not B's");
		art.markUploaded(2); // B's own cover arrives
		assertTrue(art.hasTexture());
		art.load(song("C", "file:///c.png"));
		assertFalse(art.hasTexture());
	}

	@Test
	void loadingTheNextSongCancelsTheStuckDownload() {
		RecordingExecutor executor = new RecordingExecutor();
		AlbumArt art = new AlbumArt(executor);
		art.load(song("https://example.invalid/a.jpg"));
		assertEquals(1, executor.tasks.size());
		assertFalse(executor.tasks.get(0).isCancelled());
		art.load(song("https://example.invalid/b.jpg"));
		assertEquals(2, executor.tasks.size());
		assertTrue(executor.tasks.get(0).isCancelled(), "A's download is cancelled");
		assertFalse(executor.tasks.get(1).isCancelled());
	}

	@Test
	void theColourFallsBackAsSoonAsTheCoverChanges() {
		AlbumArt art = new AlbumArt(new RecordingExecutor());
		art.load(song("A", "https://example.invalid/a.jpg"));
		art.color = 0x00FF00; // A's colour, picked from its cover
		art.load(song("B", "https://example.invalid/b.jpg"));
		assertEquals(AlbumArt.FALLBACK_COLOR, art.color(), "A's colour must not glow on B while B's cover loads");
	}

	@Test
	void aCoverChangeOfTheSameSongKeepsTheColour() {
		AlbumArt art = new AlbumArt(new RecordingExecutor());
		art.load(song("A", "https://example.invalid/a-640.jpg"));
		art.color = 0x00FF00;
		art.load(song("A", "https://example.invalid/a-300.jpg")); // another image size, or the album arriving late
		assertEquals(0x00FF00, art.color(), "no flash to the fallback for the same song");
		art.load(song("A", ""));
		assertEquals(AlbumArt.FALLBACK_COLOR, art.color(), "no cover at all falls back");
	}

	/** The real upload() starts with this step: a cover from a song we already left is closed and never uploaded. */
	@Test
	void aStaleCoverIsClosedAndNotTaken() {
		AlbumArt art = new AlbumArt(new RecordingExecutor());
		art.load(song("A", "file:///a.png"));
		art.load(song("B", "file:///b.png"));
		List<NativeImage> closed = new ArrayList<>();
		art.closeStale = image -> {
			closed.add(image);
			image.close();
		};
		NativeImage late = new NativeImage(2, 2, false);
		art.offer(late, 1); // a worker handing A's cover over after load(B)
		assertNull(art.takeIfCurrent());
		assertEquals(List.of(late), closed, "the stale image is closed");
		assertFalse(art.hasTexture());
		NativeImage mine = new NativeImage(2, 2, false);
		art.offer(mine, 2);
		AlbumArt.Pending taken = art.takeIfCurrent();
		assertNotNull(taken);
		assertEquals(2, taken.gen());
		mine.close();
	}

	/** An album-mate with the same cover keeps the colour, but a later song named like the first one with another cover doesn't. */
	@Test
	void aSongWithTheSameNameAsBeforeButAnotherCoverFallsBack() {
		AlbumArt art = new AlbumArt(new RecordingExecutor());
		art.load(song("A", "https://example.invalid/album.jpg"));
		art.color = 0x00FF00;
		art.load(song("B", "https://example.invalid/album.jpg")); // same album, same cover: the colour stays
		assertEquals(0x00FF00, art.color());
		art.load(song("A", "https://example.invalid/single.jpg")); // A again, from a single: a new song to the colour
		assertEquals(AlbumArt.FALLBACK_COLOR, art.color());
	}
}
