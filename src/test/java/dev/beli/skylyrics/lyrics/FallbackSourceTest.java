package dev.beli.skylyrics.lyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.media.Track;

class FallbackSourceTest {
	private static final Track TRACK = new Track("A Made-Up Song", "Made Up Band", "Made Up Album", 200_000, "", "spotify");
	private static final LrclibClient.Result MAIN = new LrclibClient.Result("[00:01.00]From the main site", null, false);
	private static final LrclibClient.Result BACKUP = new LrclibClient.Result("[00:01.00]From the backup", null, false);

	private final AtomicInteger mainAsked = new AtomicInteger();
	private final AtomicInteger backupAsked = new AtomicInteger();
	private final AtomicLong now = new AtomicLong(1_000_000);

	private LyricsSource counting(AtomicInteger counter, LyricsSource source) {
		return track -> {
			counter.incrementAndGet();
			return source.find(track);
		};
	}

	private FallbackSource fallback(LyricsSource main, LyricsSource backup) {
		return new FallbackSource(counting(mainAsked, main), counting(backupAsked, backup), now::get);
	}

	private static LyricsSource down(int status) {
		return track -> {
			throw new LrclibClient.StatusException(status);
		};
	}

	@Test
	void mainLyricsWinWithoutAskingTheBackup() throws Exception {
		assertSame(MAIN, fallback(t -> MAIN, t -> BACKUP).find(TRACK));
		assertEquals(0, backupAsked.get());
	}

	@Test
	void backupFillsInWhenMainHasNothing() throws Exception {
		LrclibClient.Result r = fallback(t -> LrclibClient.Result.NOT_FOUND, t -> BACKUP).find(TRACK);
		assertEquals(BACKUP.synced(), r.synced());
		assertTrue(r.fromBackup());
		assertFalse(r.stopgap()); // LRCLIB is up and just lacks the song, so this is kept as usual
	}

	private static final LrclibClient.Result PLAIN_MAIN = new LrclibClient.Result(null, "Only plain words", false);
	private static final LrclibClient.Result PLAIN_BACKUP = new LrclibClient.Result(null, "Other plain words", false);

	@Test
	void plainMainLyricsGiveWayToSyncedBackupLyrics() throws Exception {
		LrclibClient.Result r = fallback(t -> PLAIN_MAIN, t -> BACKUP).find(TRACK);
		assertEquals(BACKUP.synced(), r.synced());
		assertTrue(r.fromBackup());
		assertFalse(r.stopgap());
	}

	@Test
	void plainMainLyricsStayWhenTheBackupHasOnlyPlainOrNothingOrFails() throws Exception {
		assertSame(PLAIN_MAIN, fallback(t -> PLAIN_MAIN, t -> PLAIN_BACKUP).find(TRACK));
		assertSame(PLAIN_MAIN, fallback(t -> PLAIN_MAIN, t -> LrclibClient.Result.NOT_FOUND).find(TRACK));
		assertSame(PLAIN_MAIN, fallback(t -> PLAIN_MAIN, t -> {
			throw new IOException("backup down");
		}).find(TRACK));
	}

	@Test
	void syncedMainLyricsStillSkipTheBackup() throws Exception {
		fallback(t -> MAIN, t -> BACKUP).find(TRACK);
		assertEquals(0, backupAsked.get());
	}

	@Test
	void backupFillsInWhenMainIsDown() throws Exception {
		LrclibClient.Result r = fallback(down(502), t -> BACKUP).find(TRACK);
		assertEquals(BACKUP.synced(), r.synced());
		assertTrue(r.stopgap());
	}

	@Test
	void mainErrorIsKeptWhenBackupHasNothingSoTheSongIsRetried() {
		LrclibClient.StatusException e = assertThrows(LrclibClient.StatusException.class,
				() -> fallback(down(502), t -> LrclibClient.Result.NOT_FOUND).find(TRACK));
		assertEquals(502, e.status());
		assertEquals(0, e.getSuppressed().length);
	}

	@Test
	void bothDownThrowsTheMainError() {
		IOException e = assertThrows(IOException.class, () -> fallback(down(503), track -> {
			throw new IOException("backup down too");
		}).find(TRACK));
		assertEquals("backup down too", e.getSuppressed()[0].getMessage());
	}

	@Test
	void backupDownWhileMainHasNothingIsStillNotFound() throws Exception {
		LrclibClient.Result result = fallback(t -> LrclibClient.Result.NOT_FOUND, track -> {
			throw new IOException("backup down");
		}).find(TRACK);
		assertFalse(result.found());
		assertTrue(result.stopgap()); // a backup that timed out says nothing about the song, so it isn't kept
	}

	@Test
	void backupTimeoutIsNotAKeptNotFound() throws Exception {
		LrclibClient.Result result = fallback(t -> LrclibClient.Result.NOT_FOUND, track -> {
			throw new java.net.http.HttpTimeoutException("timed out");
		}).find(TRACK);
		assertTrue(result.stopgap());
	}

	@Test
	void realNotFoundFromBothIsStillKept() throws Exception {
		assertFalse(fallback(t -> LrclibClient.Result.NOT_FOUND, t -> LrclibClient.Result.NOT_FOUND).find(TRACK).stopgap());
	}

	@Test
	void brokenMainAnswerCountsAsDown() throws Exception {
		assertEquals(BACKUP.synced(), fallback(track -> {
			throw new com.google.gson.JsonParseException("not JSON");
		}, t -> BACKUP).find(TRACK).synced());
	}

	@Test
	void mainIsSkippedForAWhileAfterItFails() throws Exception {
		FallbackSource source = fallback(down(502), t -> BACKUP);
		source.find(TRACK);
		source.find(TRACK);
		source.find(TRACK);
		assertEquals(1, mainAsked.get());
		assertEquals(3, backupAsked.get());

		now.addAndGet(FallbackSource.SKIP_MAIN_MS + 1);
		source.find(TRACK);
		assertEquals(2, mainAsked.get());
	}

	@Test
	void songStaysRetryableWhileMainIsSkipped() throws Exception {
		FallbackSource source = fallback(down(502), t -> LrclibClient.Result.NOT_FOUND);
		assertThrows(IOException.class, () -> source.find(TRACK));
		assertThrows(IOException.class, () -> source.find(TRACK));
		assertEquals(2, mainAsked.get()); // while skipping, main is asked once more for a song the backup lacks
	}

	@Test
	void oddRefusalDoesNotSkipMain() throws Exception {
		FallbackSource source = fallback(down(400), t -> BACKUP);
		source.find(TRACK);
		source.find(TRACK);
		assertEquals(2, mainAsked.get());
	}

	@Test
	void serviceUnavailableStillSkips() throws Exception {
		FallbackSource source = fallback(down(503), t -> BACKUP);
		source.find(TRACK);
		source.find(TRACK);
		assertEquals(1, mainAsked.get());
	}

	@Test
	void tooManyRequestsStillSkips() throws Exception {
		FallbackSource source = fallback(down(429), t -> BACKUP);
		source.find(TRACK);
		source.find(TRACK);
		assertEquals(1, mainAsked.get());
	}

	@Test
	void duringSkipASongOnlyMainHasIsAskedFromMain() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		FallbackSource source = fallback(track -> {
			if (calls.incrementAndGet() == 1) {
				throw new LrclibClient.StatusException(503);
			}
			return MAIN;
		}, t -> LrclibClient.Result.NOT_FOUND);
		assertThrows(IOException.class, () -> source.find(TRACK)); // starts the skip
		assertSame(MAIN, source.find(TRACK)); // skipping, backup has nothing: main answers
		assertSame(MAIN, source.find(TRACK)); // main is up again
		assertEquals(3, mainAsked.get());
	}

	@Test
	void duringSkipMainStillDownKeepsSkipping() throws Exception {
		FallbackSource source = fallback(down(503), t -> BACKUP);
		source.find(TRACK);
		assertEquals(1, mainAsked.get());
		source.find(TRACK); // backup has it: main not asked
		assertEquals(1, mainAsked.get());
	}

	@Test
	void mainIsAskedWhenTheClockReadsNegative() throws Exception {
		// System.nanoTime() may be negative, and a main source that never failed must not look skipped
		now.set(-5_000_000);
		fallback(t -> MAIN, t -> BACKUP).find(TRACK);
		assertEquals(1, mainAsked.get());
	}

	@Test
	void skipEndsOnANegativeClockToo() throws Exception {
		now.set(-5_000_000);
		FallbackSource source = fallback(down(502), t -> BACKUP);
		source.find(TRACK);
		source.find(TRACK);
		assertEquals(1, mainAsked.get());
		now.addAndGet(FallbackSource.SKIP_MAIN_MS + 1);
		source.find(TRACK);
		assertEquals(2, mainAsked.get());
	}

	@Test
	void interruptedFailureDoesNotSkipMain() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		FallbackSource source = fallback(track -> {
			if (calls.incrementAndGet() == 1) {
				Thread.currentThread().interrupt();
				throw new IOException("closed while reading");
			}
			return MAIN;
		}, t -> BACKUP);
		try {
			assertThrows(InterruptedException.class, () -> source.find(TRACK));
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
		assertSame(MAIN, source.find(TRACK));
		assertEquals(0, backupAsked.get());
	}

	@Test
	void interruptCauseDoesNotSkipMain() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		FallbackSource source = fallback(track -> {
			if (calls.incrementAndGet() == 1) {
				throw new IOException("wrapped", new java.nio.channels.ClosedByInterruptException());
			}
			return MAIN;
		}, t -> BACKUP);
		try {
			assertThrows(InterruptedException.class, () -> source.find(TRACK));
		} finally {
			Thread.interrupted();
		}
		assertEquals(0, backupAsked.get());
		assertSame(MAIN, source.find(TRACK)); // not skipped: main is asked again
		assertEquals(2, mainAsked.get());
	}

	@Test
	void failureWithoutInterruptStillSkips() throws Exception {
		FallbackSource source = fallback(track -> {
			throw new IOException("down");
		}, t -> BACKUP);
		source.find(TRACK);
		source.find(TRACK);
		assertEquals(1, mainAsked.get());
	}

	@Test
	void socketTimeoutStillSkips() throws Exception {
		FallbackSource source = fallback(track -> {
			throw new java.net.SocketTimeoutException("read timed out");
		}, t -> BACKUP);
		source.find(TRACK);
		source.find(TRACK);
		assertEquals(1, mainAsked.get());
	}

	@Test
	void httpTimeoutStillSkips() throws Exception {
		FallbackSource source = fallback(track -> {
			throw new java.net.http.HttpTimeoutException("timed out");
		}, t -> BACKUP);
		source.find(TRACK);
		source.find(TRACK);
		assertEquals(1, mainAsked.get());
	}
}
