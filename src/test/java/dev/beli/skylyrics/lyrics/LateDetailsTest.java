package dev.beli.skylyrics.lyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.media.DemoSource;
import dev.beli.skylyrics.media.Track;

/**
 * A browser or a Linux player often sends the song's length (or album) a moment after its title and artist. The song is
 * then asked about again with it, but it is still the same song: the lyrics on show stay while that runs, the same
 * lyrics again are kept as they are (the line on show doesn't start over), and other lyrics replace them cleanly.
 */
class LateDetailsTest {
	private static final String ONE = "[00:01.00]A made-up line\n[00:05.00]Another made-up line";
	private static final String TWO = "[00:02.00]A made-up line, other version\n[00:06.00]Another one";
	private static final LrclibClient.Result FIRST = new LrclibClient.Result(ONE, null, false);
	private static final LrclibClient.Result OTHER = new LrclibClient.Result(TWO, null, false);

	@TempDir
	Path cache;

	private LyricsService service;

	@AfterEach
	void stop() throws InterruptedException {
		if (service != null) {
			// Wait for the worker: it may still be saving the lyrics into the temp folder, and on Windows and macOS a
			// file being written makes the folder's clean-up fail the test
			assertTrue(service.shutdownAndWait(10, TimeUnit.SECONDS));
		}
	}

	private static Track song(String album, long lengthMs) {
		return new Track("Made Up Song", "Made Up Band", album, lengthMs, "", "firefox");
	}

	private static Track song(long lengthMs) {
		return song("", lengthMs);
	}

	private static void waitFor(BooleanSupplier done) throws InterruptedException {
		long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		while (!done.getAsBoolean()) {
			assertTrue(System.nanoTime() < until, "timed out");
			Thread.sleep(5);
		}
	}

	/** Waits until the worker has finished everything it was given (the publish after the last hook too). */
	private void settle() throws InterruptedException {
		assertTrue(service.shutdownAndWait(10, TimeUnit.SECONDS));
	}

	/** A source that answers {@code withLength} once the length is known, held until {@code go} opens. */
	private static LyricsSource source(LrclibClient.Result withoutLength, LrclibClient.Result withLength, CountDownLatch asked,
			CountDownLatch go) {
		return t -> {
			if (t.lengthMs() <= 0) {
				return withoutLength;
			}
			asked.countDown();
			go.await();
			return withLength;
		};
	}

	@Test
	void aLateLengthKeepsTheLyricsOnShowWhileTheSongIsAskedAboutAgain() throws Exception {
		CountDownLatch asked = new CountDownLatch(1);
		CountDownLatch go = new CountDownLatch(1);
		service = new LyricsService(source(FIRST, FIRST, asked, go), cache);
		service.load(song(0));
		waitFor(() -> service.state() == LyricsService.State.READY);
		Lyrics shown = service.lyrics();

		service.load(song(215_000));
		assertSame(shown, service.lyrics(), "the lyrics on show went away while the song was asked about again");
		assertEquals(LyricsService.State.READY, service.state(), "the bar said it was finding lyrics again");
		assertTrue(asked.await(10, TimeUnit.SECONDS), "the song wasn't asked about again with its length");
		assertSame(shown, service.lyrics());
		assertEquals(LyricsService.State.READY, service.state());
		assertEquals(215_000, service.track().lengthMs());
		go.countDown();
	}

	@Test
	void theSameLyricsAgainAreKeptAsTheyAre() throws Exception {
		AtomicInteger published = new AtomicInteger();
		service = new LyricsService(t -> FIRST, cache);
		service.beforePublish = published::incrementAndGet;
		service.load(song(0));
		waitFor(() -> service.state() == LyricsService.State.READY);
		Lyrics shown = service.lyrics();

		service.load(song(215_000));
		waitFor(() -> published.get() == 2);
		settle();
		assertSame(shown, service.lyrics(), "the same lyrics were shown anew, so the line on show started over");
		assertEquals(LyricsService.State.READY, service.state());
	}

	@Test
	void otherLyricsForTheLengthReplaceTheOnesOnShow() throws Exception {
		CountDownLatch asked = new CountDownLatch(1);
		CountDownLatch go = new CountDownLatch(1);
		service = new LyricsService(source(FIRST, OTHER, asked, go), cache);
		service.load(song(0));
		waitFor(() -> service.state() == LyricsService.State.READY);

		service.load(song(215_000));
		assertTrue(asked.await(10, TimeUnit.SECONDS));
		go.countDown();
		waitFor(() -> !service.lyrics().isEmpty() && service.lyrics().lines().get(0).text().contains("other version"));
		assertEquals(LyricsService.State.READY, service.state());
	}

	@Test
	void lyricsTheLengthRulesOutGoAway() throws Exception {
		// The first answer was picked without a length; with it, the site has no copy close enough in length, so the
		// first one was most likely another recording's: none is better than its timing
		CountDownLatch asked = new CountDownLatch(1);
		CountDownLatch go = new CountDownLatch(1);
		service = new LyricsService(source(FIRST, LrclibClient.Result.NOT_FOUND, asked, go), cache);
		service.load(song(0));
		waitFor(() -> service.state() == LyricsService.State.READY);

		service.load(song(215_000));
		assertTrue(asked.await(10, TimeUnit.SECONDS));
		go.countDown();
		waitFor(() -> service.state() == LyricsService.State.NOT_FOUND);
		assertTrue(service.lyrics().isEmpty());
	}

	@Test
	void aLateAlbumWithTheLengthKeepsTheLyricsOnShowToo() throws Exception {
		CountDownLatch asked = new CountDownLatch(1);
		CountDownLatch go = new CountDownLatch(1);
		service = new LyricsService(source(FIRST, FIRST, asked, go), cache);
		service.load(song(0));
		waitFor(() -> service.state() == LyricsService.State.READY);
		Lyrics shown = service.lyrics();

		service.load(song("Made Up Album", 215_000));
		assertSame(shown, service.lyrics(), "the lyrics on show went away when the album and length came in");
		assertEquals(LyricsService.State.READY, service.state());
		assertTrue(asked.await(10, TimeUnit.SECONDS));
		go.countDown();
	}

	@Test
	void anotherAlbumIsAnotherRecordingAndStartsBlank() throws Exception {
		CountDownLatch asked = new CountDownLatch(1);
		CountDownLatch go = new CountDownLatch(1);
		service = new LyricsService(t -> {
			if (t.album().equals("Live Album")) {
				asked.countDown();
				go.await();
			}
			return FIRST;
		}, cache);
		service.load(song("Studio Album", 215_000));
		waitFor(() -> service.state() == LyricsService.State.READY);

		service.load(song("Live Album", 215_000));
		assertTrue(service.lyrics().isEmpty(), "another album's song showed the last one's lyrics");
		assertEquals(LyricsService.State.LOADING, service.state());
		assertTrue(asked.await(10, TimeUnit.SECONDS));
		go.countDown();
	}

	@Test
	void aLateArtistStartsBlank() throws Exception {
		// Found by the title alone, the lyrics may be another artist's song of that name: they don't stay for the artist's
		CountDownLatch asked = new CountDownLatch(1);
		CountDownLatch go = new CountDownLatch(1);
		service = new LyricsService(t -> {
			if (!t.artist().isEmpty()) {
				asked.countDown();
				go.await();
			}
			return FIRST;
		}, cache);
		service.load(new Track("Made Up Song", "", "", 215_000, "", "firefox"));
		waitFor(() -> service.state() == LyricsService.State.READY);

		service.load(song(215_000));
		assertTrue(service.lyrics().isEmpty());
		assertEquals(LyricsService.State.LOADING, service.state());
		assertTrue(asked.await(10, TimeUnit.SECONDS));
		go.countDown();
	}

	@Test
	void retryWithTheSameLyricsKeepsThemAsTheyAre() throws Exception {
		AtomicInteger published = new AtomicInteger();
		service = new LyricsService(t -> FIRST, cache);
		service.beforePublish = published::incrementAndGet;
		service.load(song(215_000));
		waitFor(() -> service.state() == LyricsService.State.READY);
		Lyrics shown = service.lyrics();

		service.retry();
		waitFor(() -> published.get() == 2);
		settle();
		assertSame(shown, service.lyrics(), "Retry showed the same lyrics anew, so the line on show started over");
		assertEquals(LyricsService.State.READY, service.state());
	}

	@Test
	void anAnswerForTheSongWithoutItsLengthIsDroppedOnceTheLengthIsIn() throws Exception {
		CountDownLatch firstAsked = new CountDownLatch(1);
		CountDownLatch firstGo = new CountDownLatch(1);
		CountDownLatch secondAsked = new CountDownLatch(1);
		CountDownLatch secondGo = new CountDownLatch(1);
		service = new LyricsService(t -> {
			if (t.lengthMs() <= 0) {
				firstAsked.countDown();
				while (true) { // an answer already on its way: the cancel's interrupt doesn't stop it
					try {
						firstGo.await();
						return FIRST;
					} catch (InterruptedException ignored) {
						// keep waiting
					}
				}
			}
			secondAsked.countDown();
			secondGo.await();
			return OTHER;
		}, cache);
		service.load(song(0));
		assertTrue(firstAsked.await(10, TimeUnit.SECONDS));
		service.load(song(215_000));
		assertEquals(LyricsService.State.LOADING, service.state());
		firstGo.countDown();
		// One worker: the second lookup starts only once the first has finished and published (or not)
		assertTrue(secondAsked.await(10, TimeUnit.SECONDS));
		assertTrue(service.lyrics().isEmpty(), "the answer for the song without its length was shown");
		assertEquals(LyricsService.State.LOADING, service.state());
		secondGo.countDown();
		waitFor(() -> service.state() == LyricsService.State.READY);
		assertTrue(service.lyrics().lines().get(0).text().contains("other version"));
	}

	@Test
	void aSongChangeWhileTheSongIsAskedAboutAgainDropsThatAnswer() throws Exception {
		CountDownLatch asked = new CountDownLatch(1);
		CountDownLatch go = new CountDownLatch(1);
		service = new LyricsService(t -> {
			if (t.title().equals("Next Song")) {
				return LrclibClient.Result.NOT_FOUND;
			}
			if (t.lengthMs() <= 0) {
				return FIRST;
			}
			asked.countDown();
			while (true) {
				try {
					go.await();
					return OTHER;
				} catch (InterruptedException ignored) {
					// keep waiting
				}
			}
		}, cache);
		service.load(song(0));
		waitFor(() -> service.state() == LyricsService.State.READY);
		service.load(song(215_000));
		assertTrue(asked.await(10, TimeUnit.SECONDS));

		Track next = new Track("Next Song", "Made Up Band", "", 180_000, "", "firefox");
		service.load(next);
		assertTrue(service.lyrics().isEmpty(), "the next song showed the last one's lyrics");
		go.countDown();
		waitFor(() -> service.state() == LyricsService.State.NOT_FOUND);
		settle();
		assertEquals(next, service.track());
		assertTrue(service.lyrics().isEmpty(), "the last song's late answer was shown on the next one");
		assertFalse(service.state() == LyricsService.State.READY);
	}

	@Test
	void theDemoSongKeepsItsLyricsWhenItsLengthComesIn() {
		service = new LyricsService(t -> LrclibClient.Result.NOT_FOUND, cache);
		Track demo = DemoSource.TRACK;
		service.load(new Track(demo.title(), demo.artist(), demo.album(), 0, demo.artUrl(), demo.player()));
		Lyrics shown = service.lyrics();
		assertFalse(shown.isEmpty());
		service.load(demo);
		assertSame(shown, service.lyrics(), "the demo's lyrics were shown anew, so the line on show started over");
		assertEquals(LyricsService.State.READY, service.state());
	}
}
