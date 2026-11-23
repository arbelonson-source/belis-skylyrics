package dev.beli.skylyrics.lyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.media.Track;

/** A lyrics file exactly as 0.8.0 saved it: its own file name for the song and its own JSON (no "backup", version 2). */
class OldLyricsFileTest {
	private static final String SYNCED = "[00:01.00]A made-up line\n[00:05.00]Another made-up line";

	@TempDir
	Path cache;

	/** 0.8.0's name for a song's lyrics file. */
	private Path oldFile(Track t) throws Exception {
		String id = (t.artist() + "\n" + t.title() + "\n" + t.album() + "\n" + Math.round(t.lengthMs() / 2000.0)).toLowerCase(Locale.ROOT);
		byte[] hash = MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8));
		return cache.resolve(HexFormat.of().formatHex(hash, 0, 12) + ".json");
	}

	private static void waitFor(BooleanSupplier done) throws InterruptedException {
		long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
		while (!done.getAsBoolean()) {
			assertTrue(System.nanoTime() < until, "timed out");
			Thread.sleep(10);
		}
	}

	@Test
	void lyricsSavedBy080ShowAtOnceAndAreCheckedOnce() throws Exception {
		Track song = new Track("Midnight Signal", "Aurora Lane", "Night Drive", 201_000, "", "spotify");
		Path file = oldFile(song);
		Files.writeString(file, "{\"synced\":\"" + SYNCED.replace("\n", "\\n") + "\",\"instrumental\":false,\"savedAt\":1731650000000,\"v\":2}");
		CountDownLatch answer = new CountDownLatch(1);
		AtomicInteger asks = new AtomicInteger();
		LyricsService service = new LyricsService(t -> {
			asks.incrementAndGet();
			answer.await();
			return new LrclibClient.Result(SYNCED, null, false);
		}, cache);
		try {
			service.load(song);
			// shown from the file while the site hasn't answered
			waitFor(() -> service.state() == LyricsService.State.READY);
			assertEquals("A made-up line", service.lyrics().lines().get(0).text());
			answer.countDown();
			// asked once more, the same lyrics: the file is kept, now stamped as checked
			waitFor(() -> {
				try {
					return Files.readString(file).contains("\"v\":3");
				} catch (Exception e) {
					return false;
				}
			});
			assertEquals(1, asks.get());
			assertTrue(Files.readString(file).contains("A made-up line"));
			assertEquals("A made-up line", service.lyrics().lines().get(0).text());
		} finally {
			answer.countDown();
			// Waits for the worker: a cache file it still had open would keep Windows from deleting the test's folder
			service.shutdownAndWait(10, TimeUnit.SECONDS);
		}
	}
}
