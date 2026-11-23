package dev.beli.skylyrics.lyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.media.Track;

/**
 * With Lyrics turned off the mod shows nothing, so no song's title, artist, album or length goes to the lyrics sites
 * (LRCLIB, and NetEase in China as the backup). Turning Lyrics back on looks up the song that is playing then.
 */
class LyricsOffTest {
	private static final String SYNCED = "[00:01.00]A made-up line\n[00:05.00]Another made-up line";
	private static final Track SONG = new Track("Paper Lanterns", "Nova Lane", "Night Roads", 180_000, "", "spotify");

	@TempDir
	Path cache;

	private final AtomicBoolean on = new AtomicBoolean(false);
	private final List<String> asked = new CopyOnWriteArrayList<>();
	/** The next this many lookups fail like a site that can't be reached. */
	private final AtomicInteger failures = new AtomicInteger();
	/** Whether the lyrics sites have nothing for the song (yet). */
	private volatile boolean none;
	private LyricsService service;

	private final LyricsSource source = track -> {
		asked.add(track.key());
		if (failures.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
			throw new IOException("offline");
		}
		return new LrclibClient.Result(none ? null : SYNCED, null, false);
	};

	@AfterEach
	void stop() throws InterruptedException {
		if (service != null) {
			service.shutdownAndWait(5, TimeUnit.SECONDS);
		}
	}

	private static void waitFor(java.util.function.BooleanSupplier done, String what) throws InterruptedException {
		long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		while (!done.getAsBoolean()) {
			assertTrue(System.nanoTime() < until, "timed out waiting for " + what);
			Thread.sleep(5);
		}
	}

	/**
	 * Lets everything the worker has running, queued or scheduled run out, so a check that nothing was asked needs no
	 * guess at how long a lookup takes to start. A worker that never goes idle (retries) shows in the checks that follow.
	 */
	private void settle() throws Exception {
		service.awaitIdle(10, TimeUnit.SECONDS);
	}

	@Test
	void aSongThatStartsWhileLyricsAreOffIsNotLookedUp() throws Exception {
		service = new LyricsService(source, cache, attempt -> 30, on::get);
		service.load(SONG);
		settle(); // a lookup queued on the worker has asked by the time it is idle
		assertEquals(List.of(), asked, "nothing went to the lyrics sites");
		assertEquals(SONG, service.track(), "the song is still known, for when Lyrics are turned on");
		assertEquals(LyricsService.State.IDLE, service.state());
	}

	@Test
	void turningLyricsOnLooksUpTheSongPlayingThen() throws Exception {
		service = new LyricsService(source, cache, attempt -> 30, on::get);
		service.load(SONG);
		service.wake(); // still off: nothing
		settle();
		assertEquals(List.of(), asked);
		on.set(true);
		service.wake();
		waitFor(() -> service.state() == LyricsService.State.READY, "the lyrics");
		assertEquals(List.of(SONG.key()), asked);
		assertFalse(service.lyrics().isEmpty());
		service.wake(); // found already: not asked again
		settle();
		assertEquals(1, asked.size());
	}

	@Test
	void retriesStopWhenLyricsAreTurnedOffAndGoOnWhenTurnedOn() throws Exception {
		on.set(true);
		failures.set(1_000);
		service = new LyricsService(source, cache, attempt -> 30, on::get);
		service.load(SONG);
		waitFor(() -> asked.size() >= 2, "a retry");
		on.set(false);
		int before = asked.size();
		// The next try finds Lyrics off and waits for them (IDLE), instead of a guess at how long that takes here
		waitFor(() -> service.state() == LyricsService.State.IDLE, "the retries to stop");
		int parked = asked.size();
		assertTrue(parked <= before + 1, "at most the try already on its way, then none: " + parked + " after " + before);
		settle(); // retries every 30 ms while on, and the worker isn't idle while one waits
		assertEquals(parked, asked.size(), "no more tries while Lyrics are off");
		failures.set(0);
		on.set(true);
		service.wake();
		waitFor(() -> service.state() == LyricsService.State.READY, "the lyrics");
		assertFalse(service.lyrics().isEmpty());
	}

	@Test
	void retryPressedWhileLyricsAreOffWaitsForThemAndThenAsksPastTheSavedAnswer() throws Exception {
		on.set(true);
		none = true;
		service = new LyricsService(source, cache, attempt -> 30, on::get);
		service.load(SONG);
		waitFor(() -> service.state() == LyricsService.State.NOT_FOUND, "no lyrics");
		assertEquals(1, asked.size());
		on.set(false);
		service.retry(); // the Music tab's Retry, still on offer for the song that had none
		settle();
		assertEquals(1, asked.size(), "nothing asked while Lyrics are off");
		// The Lyrics row says "Lyrics off" and the Retry button greys out, instead of a Retry that does nothing
		assertEquals(LyricsService.State.IDLE, service.state());
		none = false; // the lyrics site has them now
		on.set(true);
		service.wake();
		waitFor(() -> asked.size() == 2, "the Retry's lookup once Lyrics are on");
		waitFor(() -> service.state() != LyricsService.State.LOADING, "its answer");
		assertEquals(LyricsService.State.READY, service.state(), "asked again, not the saved 'no lyrics'");
	}

	@Test
	void aRetryWaitingOutAFailureWhenLyricsGoOffStillAsksPastTheSavedAnswer() throws InterruptedException {
		on.set(true);
		none = true;
		service = new LyricsService(source, cache, attempt -> 30, on::get);
		service.load(SONG);
		waitFor(() -> service.state() == LyricsService.State.NOT_FOUND, "no lyrics");
		failures.set(1_000);
		service.retry(); // pressed while the internet is down: it tries again every 30 ms
		waitFor(() -> asked.size() >= 3, "the Retry's tries");
		on.set(false);
		waitFor(() -> service.state() == LyricsService.State.IDLE, "the Retry to wait for Lyrics");
		failures.set(0);
		none = false;
		on.set(true);
		service.wake();
		waitFor(() -> service.state() == LyricsService.State.READY || service.state() == LyricsService.State.NOT_FOUND, "an answer");
		assertEquals(LyricsService.State.READY, service.state(), "the Retry still forgets the saved 'no lyrics'");
		assertFalse(service.lyrics().isEmpty());
	}

	@Test
	void aNewSongDoesNotInheritARetryThatWaitedForLyrics() throws InterruptedException {
		on.set(true);
		none = true;
		service = new LyricsService(source, cache, attempt -> 30, on::get);
		Track other = new Track("Glass Harbour", "Nova Lane", "Night Roads", 200_000, "", "spotify");
		service.load(other);
		waitFor(() -> service.state() == LyricsService.State.NOT_FOUND, "no lyrics for the other song");
		service.load(SONG);
		waitFor(() -> service.state() == LyricsService.State.NOT_FOUND, "no lyrics");
		on.set(false);
		service.retry();
		service.load(other); // another song while Lyrics are off
		none = false;
		on.set(true);
		service.wake();
		waitFor(() -> service.state() != LyricsService.State.LOADING && service.state() != LyricsService.State.IDLE, "an answer");
		assertEquals(LyricsService.State.NOT_FOUND, service.state(), "its saved answer, as for any song that starts");
		assertEquals(2, asked.size());
	}
}
