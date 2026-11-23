package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

	/** A cover of one colour, so the colour picked from it is not the one used without a cover. */
	private static BufferedImage red() {
		BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++) {
				image.setRGB(x, y, 0xE02020);
			}
		}
		return image;
	}

	/** Loads a song's cover with Lyrics on and puts it on show, as the render thread's upload does. */
	private static void shown(AlbumArt art, Track track) {
		art.load(track);
		AlbumArt.Pending cover = art.takeIfCurrent();
		assertNotNull(cover);
		art.markUploaded(cover.gen());
		assertTrue(art.hasTexture());
	}

	@Test
	void theLastSongsCoverIsNotShownBesideASongThatStartsWhileLyricsAreOff() {
		on.set(true);
		AlbumArt art = art();
		art.fetcher = url -> {
			fetched.add(url);
			return red();
		};
		shown(art, song("T", "https://img.example/a.jpg"));
		assertNotEquals(AlbumArt.FALLBACK_COLOR, art.color());
		on.set(false);
		art.load(song("U", "https://img.example/b.jpg"));
		// The Music tab's Now playing card shows U's title: with T's cover beside it (and T's colour on its dot and the
		// Album colour row) it would name the wrong album
		assertFalse(art.hasTexture(), "no cover rather than the last song's");
		assertEquals(AlbumArt.FALLBACK_COLOR, art.color());
		assertEquals(1, fetched.size(), "and still nothing asked");
		on.set(true);
		art.wake();
		assertEquals(List.of("https://img.example/a.jpg", "https://img.example/b.jpg"), fetched);
		shown(art, song("U", "https://img.example/b.jpg"));
	}

	@Test
	void theDemoSongWhileLyricsAreOffShowsNoCoverEither() {
		on.set(true);
		AlbumArt art = art();
		shown(art, song("T", "https://img.example/a.jpg"));
		on.set(false);
		art.load(dev.beli.skylyrics.media.DemoSource.TRACK);
		assertFalse(art.hasTexture(), "the demo song has no cover");
	}

	@Test
	void anAlbumMateOrTheSameSongKeepsItsCoverWhileLyricsAreOff() {
		on.set(true);
		AlbumArt art = art();
		shown(art, song("T", "https://img.example/a.jpg"));
		on.set(false);
		art.load(song("T2", "https://img.example/a.jpg"));
		assertTrue(art.hasTexture(), "the same cover is still the right one");
		art.load(song("T2", "https://img.example/a-large.jpg"));
		assertTrue(art.hasTexture(), "a corrected cover of the same song: the one on show stays until it comes");
		on.set(true);
		art.wake();
		assertEquals(List.of("https://img.example/a.jpg", "https://img.example/a-large.jpg"), fetched);
	}

	@Test
	void aSongThatComesBackWhileLyricsAreOffGetsItsCoverAgainWhenTheyAreOn() {
		on.set(true);
		AlbumArt art = art();
		shown(art, song("T", "https://img.example/a.jpg"));
		on.set(false);
		art.load(song("U", "https://img.example/b.jpg"));
		art.load(song("T", "https://img.example/a.jpg"));
		on.set(true);
		art.wake();
		AlbumArt.Pending cover = art.takeIfCurrent(); // the render thread's upload
		if (cover != null) {
			art.markUploaded(cover.gen());
		}
		assertTrue(art.hasTexture(), "T's cover is on show again");
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
