package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.util.SafeFile;

/** The game waits for the Recently heard list's last write as it closes, but a write that never ends must not keep it open. */
class HeardShutdownTest {
	@TempDir
	Path dir;

	private static Track song(String title) {
		return new Track(title, "Nova Lane", "Album", 200_000, "https://example.test/cover.jpg", "mpv", "file:///music/" + title + ".mp3");
	}

	@Test
	void aWriteThatHangsDoesNotKeepTheGameFromClosing() throws Exception {
		Path file = dir.resolve("heard.json");
		HeardLibrary.write(file, java.util.List.of());
		String before = Files.readString(file);
		CountDownLatch answers = new CountDownLatch(1);
		// A network drive that stopped answering: the move over the real file never comes back
		HeardLibrary lib = new HeardLibrary(file, () -> true, HeardLibrary.NO_OPENER, SafeFile::readText, (from, to, options) -> {
			try {
				answers.await();
			} catch (InterruptedException e) {
				throw new IOException(e);
			}
			Files.move(from, to, options);
		});
		try {
			lib.record(song("Paper Moon"));
			long start = System.nanoTime();
			boolean done = assertTimeoutPreemptively(Duration.ofSeconds(20), () -> lib.flush(2_000),
					"the game waits for ever for the Recently heard write");
			long waitedMs = (System.nanoTime() - start) / 1_000_000L;
			assertFalse(done);
			assertTrue(waitedMs >= 1_900 && waitedMs < 10_000, "waited " + waitedMs + " ms");
			assertEquals(before, Files.readString(file), "the file is as it was, never half written");
		} finally {
			answers.countDown();
			lib.flush(); // let the late write finish before the folder is cleaned up
		}
	}

	@Test
	void aNormalLastWriteIsStillOnDiskWhenTheGameCloses() throws Exception {
		Path file = dir.resolve("heard.json");
		// A slow disk, or an antivirus looking at the file: a moment, not for ever
		HeardLibrary lib = new HeardLibrary(file, () -> true, HeardLibrary.NO_OPENER, SafeFile::readText, (from, to, options) -> {
			try {
				Thread.sleep(300);
			} catch (InterruptedException e) {
				throw new IOException(e);
			}
			Files.move(from, to, options);
		});
		lib.record(song("Paper Moon"));
		lib.record(song("Glass Harbour"));
		assertTrue(lib.flush(2_000));
		HeardLibrary again = new HeardLibrary(file, () -> true, HeardLibrary.NO_OPENER);
		assertEquals(java.util.List.of("Glass Harbour", "Paper Moon"),
				again.liked(0).join().items().stream().map(LibraryItem::title).toList());
	}
}
