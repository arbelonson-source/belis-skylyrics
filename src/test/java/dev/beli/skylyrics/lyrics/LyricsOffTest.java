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
	private LyricsService service;

	private final LyricsSource source = track -> {
		asked.add(track.key());
		if (failures.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
			throw new IOException("offline");
		}
		return new LrclibClient.Result(SYNCED, null, false);
	};

	@AfterEach
	void stop() throws InterruptedException {
		if (service != null) {
			service.shutdownAndWait(5, TimeUnit.SECONDS);
		}
	}

	private static void waitFor(java.util.function.BooleanSupplier done, String what) throws InterruptedException {
		long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (!done.getAsBoolean()) {
			assertTrue(System.nanoTime() < until, "timed out waiting for " + what);
			Thread.sleep(5);
		}
	}

	@Test
	void aSongThatStartsWhileLyricsAreOffIsNotLookedUp() throws InterruptedException {
		service = new LyricsService(source, cache, attempt -> 30, on::get);
		service.load(SONG);
		Thread.sleep(400); // a lookup starts within milliseconds
		assertEquals(List.of(), asked, "nothing went to the lyrics sites");
		assertEquals(SONG, service.track(), "the song is still known, for when Lyrics are turned on");
		assertEquals(LyricsService.State.IDLE, service.state());
	}

	@Test
	void turningLyricsOnLooksUpTheSongPlayingThen() throws InterruptedException {
		service = new LyricsService(source, cache, attempt -> 30, on::get);
		service.load(SONG);
		service.wake(); // still off: nothing
		Thread.sleep(200);
		assertEquals(List.of(), asked);
		on.set(true);
		service.wake();
		waitFor(() -> service.state() == LyricsService.State.READY, "the lyrics");
		assertEquals(List.of(SONG.key()), asked);
		assertFalse(service.lyrics().isEmpty());
		service.wake(); // found already: not asked again
		Thread.sleep(200);
		assertEquals(1, asked.size());
	}

	@Test
	void retriesStopWhenLyricsAreTurnedOffAndGoOnWhenTurnedOn() throws InterruptedException {
		on.set(true);
		failures.set(1_000);
		service = new LyricsService(source, cache, attempt -> 30, on::get);
		service.load(SONG);
		waitFor(() -> asked.size() >= 2, "a retry");
		on.set(false);
		int before = asked.size();
		Thread.sleep(400); // retries every 30 ms while on
		assertTrue(asked.size() <= before + 1, "at most the try already on its way, then none: " + asked.size() + " after " + before);
		int parked = asked.size();
		Thread.sleep(200);
		assertEquals(parked, asked.size(), "no more tries while Lyrics are off");
		failures.set(0);
		on.set(true);
		service.wake();
		waitFor(() -> service.state() == LyricsService.State.READY, "the lyrics");
		assertFalse(service.lyrics().isEmpty());
	}
}
