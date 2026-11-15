package dev.beli.skylyrics.lyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.media.Track;

/**
 * Song A's lookup has finished and is about to publish (after its work, before the write) when the player moves on
 * to song B. A's answer must be dropped, not shown on B. Deterministic: the worker is parked at the publish point.
 */
class LyricsRaceTest {
	private static final String SYNCED = "[00:01.00]A made-up line\n[00:05.00]Another made-up line";

	@TempDir
	Path cache;

	private final CountDownLatch atPublish = new CountDownLatch(1);
	private final CountDownLatch go = new CountDownLatch(1);
	private final AtomicBoolean first = new AtomicBoolean(true);
	private LyricsService service;
	/** 0: A is found; 1: A fails with a temporary error; 2: with a permanent one. */
	private volatile int failA;

	private static Track song(String title) {
		return new Track(title, "Nova Lane", "", 180_000, "", "spotify");
	}

	@BeforeEach
	void start() {
		LyricsSource source = t -> {
			if (t.title().equals("A")) {
				if (failA == 1) {
					throw new IOException("offline");
				}
				if (failA == 2) {
					throw new IllegalStateException("boom");
				}
				return new LrclibClient.Result(SYNCED, null, false);
			}
			new CountDownLatch(1).await(10, TimeUnit.SECONDS); // B never answers during the test; shutdown interrupts it
			return LrclibClient.Result.NOT_FOUND;
		};
		service = new LyricsService(source, cache, attempt -> 60_000);
		service.beforePublish = () -> {
			if (!first.getAndSet(false)) {
				return;
			}
			atPublish.countDown();
			while (true) {
				try {
					go.await();
					return;
				} catch (InterruptedException ignored) {
					// load(B) cancels this very fetch; keep waiting until the test lets it go on
				}
			}
		};
	}

	@AfterEach
	void stop() {
		service.shutdown();
	}

	/** Loads A, parks its worker at the publish point, loads B from this thread, then lets A's worker go on. */
	private void skipTo(Track b) throws Exception {
		service.load(song("A"));
		assertTrue(atPublish.await(10, TimeUnit.SECONDS), "A's worker should reach the publish point");
		service.load(b);
		go.countDown();
		assertTrue(service.shutdownAndWait(10, TimeUnit.SECONDS), "A's worker should finish");
	}

	@Test
	void aFinishedLookupForTheOldSongIsNotShownOnTheNextOne() throws Exception {
		skipTo(Track.NONE);
		assertSame(Lyrics.NONE, service.lyrics());
		assertEquals(LyricsService.State.IDLE, service.state());
		assertSame(Track.NONE, service.track());
	}

	@Test
	void aFinishedLookupForTheOldSongIsNotShownWhileTheNextOneLoads() throws Exception {
		skipTo(song("B"));
		assertTrue(service.lyrics().isEmpty());
		assertEquals(LyricsService.State.LOADING, service.state());
		assertEquals("B", service.track().title());
	}

	@Test
	void aPermanentErrorOfTheOldSongIsNotShownOnTheNextOne() throws Exception {
		failA = 2;
		skipTo(Track.NONE);
		assertEquals(LyricsService.State.IDLE, service.state());
	}

	@Test
	void aTemporaryErrorOfTheOldSongIsNotShownOnTheNextOne() throws Exception {
		failA = 1;
		skipTo(Track.NONE);
		assertEquals(LyricsService.State.IDLE, service.state());
	}
}
