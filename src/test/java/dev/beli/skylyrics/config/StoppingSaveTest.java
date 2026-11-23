package dev.beli.skylyrics.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.util.SafeFile;

/**
 * Minecraft sends its stopping event (where the mod waits for queued saves) before it closes the open screen, so the
 * settings screen saves what was changed in it after that wait. Nothing waits for that save later: the game exits and
 * the writer thread goes with it.
 */
class StoppingSaveTest {
	@TempDir
	Path dir;

	private Consumer<Runnable> oldWriter;
	private Consumer<Runnable> oldMain;
	/** Until this opens the disk doesn't answer, so a save can't be written however slow this machine is. */
	private final CountDownLatch diskAnswers = new CountDownLatch(1);

	/**
	 * How long the game is given to wait for the writer here: far over the slow disk's moment, so a busy test machine can't
	 * run out of it (the game's own 2 s are not what these tests are about).
	 */
	private static final long WAIT_MS = 60_000;

	@BeforeEach
	void slowDisk() {
		oldWriter = SkyLyricsConfig.writerExec;
		oldMain = SkyLyricsConfig.mainThread;
		// The writer gets to each save a moment later, on its own thread, as on a slow disk or with an antivirus scanning
		// (100 ms: the file is looked at right after the call that must wait, far sooner than that)
		SkyLyricsConfig.writerExec = task -> {
			Thread t = new Thread(() -> {
				try {
					diskAnswers.await();
					Thread.sleep(100);
				} catch (InterruptedException e) {
					return;
				}
				task.run();
			});
			t.setDaemon(true);
			t.start();
		};
		SkyLyricsConfig.mainThread = task -> { };
	}

	@AfterEach
	void restore() {
		SkyLyricsConfig.writerExec = oldWriter;
		SkyLyricsConfig.mainThread = oldMain;
	}

	@Test
	void aSettingSavedAfterTheGameStartedStoppingIsOnDiskBeforeTheGameGoesOn() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		SkyLyricsConfig c = SkyLyricsConfig.load(file, SafeFile::readText);
		diskAnswers.countDown(); // slowly
		assertTrue(c.close(WAIT_MS)); // the stopping event: nothing was waiting yet
		c.glow = 0.3f; // changed in the settings screen, which Minecraft closes next
		c.saveLater(file);
		// The game exits right after closing the screen
		assertTrue(Files.exists(file), "the change was still waiting for the writer when the game exited");
		assertEquals(0.3f, SkyLyricsConfig.load(file, SafeFile::readText).glow, 1e-6f);
	}

	@Test
	void savesBeforeTheGameStopsStillGoToTheWriter() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		SkyLyricsConfig c = SkyLyricsConfig.load(file, SafeFile::readText);
		c.glow = 0.3f;
		c.saveLater(file);
		assertFalse(Files.exists(file), "a save while playing must not wait for the disk");
		diskAnswers.countDown(); // slowly
		assertTrue(c.close(WAIT_MS));
		assertEquals(0.3f, SkyLyricsConfig.load(file, SafeFile::readText).glow, 1e-6f);
	}
}
