package dev.beli.skylyrics.lyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
		assertSame(BACKUP, fallback(t -> LrclibClient.Result.NOT_FOUND, t -> BACKUP).find(TRACK));
	}

	@Test
	void backupFillsInWhenMainIsDown() throws Exception {
		assertSame(BACKUP, fallback(down(502), t -> BACKUP).find(TRACK));
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
	}

	@Test
	void brokenMainAnswerCountsAsDown() throws Exception {
		assertSame(BACKUP, fallback(track -> {
			throw new com.google.gson.JsonParseException("not JSON");
		}, t -> BACKUP).find(TRACK));
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
		assertEquals(1, mainAsked.get());
	}
}
