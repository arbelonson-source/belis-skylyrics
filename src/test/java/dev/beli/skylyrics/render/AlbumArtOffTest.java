package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.media.Track;

/**
 * With Lyrics turned off nothing shows a cover, so none is downloaded: a cover address can name any web server, which
 * then learns the player's address and what they listen to. Turning Lyrics back on loads the cover of the song playing.
 */
class AlbumArtOffTest {
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

	private final AtomicBoolean on = new AtomicBoolean(false);
	private final List<String> fetched = new ArrayList<>();
	private final List<FutureTask<?>> scheduled = new ArrayList<>();
	private int failuresLeft;

	private AlbumArt art() {
		AlbumArt art = new AlbumArt(new Inline(), (task, delayMs) -> {
			FutureTask<Void> f = new FutureTask<>(task, null);
			scheduled.add(f);
			return f;
		}, on::get);
		art.toImage = image -> BlankImages.blank(); // no native memory in unit tests
		art.fetcher = url -> {
			fetched.add(url);
			if (failuresLeft > 0) {
				failuresLeft--;
				throw new java.net.ConnectException("offline");
			}
			return new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
		};
		return art;
	}

	private static Track song(String title, String art) {
		return new Track(title, "Nova Lane", "", 180_000, art, "spotify");
	}

	@Test
	void noCoverIsDownloadedWhileLyricsAreOff() {
		AlbumArt art = art();
		art.load(song("T", "https://img.example/a.jpg"));
		art.load(song("U", "https://img.example/b.jpg"));
		art.wake(); // still off
		assertEquals(List.of(), fetched);
		assertNull(art.takeIfCurrent());
	}

	@Test
	void turningLyricsOnLoadsTheCoverOfTheSongPlayingThen() {
		AlbumArt art = art();
		art.load(song("T", "https://img.example/a.jpg"));
		art.load(song("U", "https://img.example/b.jpg"));
		on.set(true);
		art.wake();
		assertEquals(List.of("https://img.example/b.jpg"), fetched, "only the song playing now");
		assertNotNull(art.takeIfCurrent());
		art.wake();
		assertEquals(1, fetched.size(), "a wake with nothing waiting asks for nothing");
	}

	@Test
	void aRetryDueWhileLyricsAreOffWaitsForThemToBeOn() {
		on.set(true);
		failuresLeft = 1;
		AlbumArt art = art();
		art.load(song("T", "https://img.example/a.jpg"));
		assertEquals(1, fetched.size());
		assertEquals(1, scheduled.size(), "a retry is waiting");
		on.set(false);
		scheduled.get(0).run();
		assertEquals(1, fetched.size(), "the retry asks nothing while Lyrics are off");
		on.set(true);
		art.wake();
		assertEquals(2, fetched.size());
		assertNotNull(art.takeIfCurrent());
	}
}
