package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.LogCapture;

/**
 * A reader's own sentence can name a file in the game folder, like the Windows music reader's "Couldn't prepare the
 * Windows music reader (C:\Users\<your name>\...)". The game log, which players paste in public, gets it with the home
 * folder cut to "~", like the rest of the line.
 */
class MediaTrackerLogTest {
	private static final class Failing implements MediaSource {
		volatile Exception failure;
		volatile CountDownLatch barrier = new CountDownLatch(1);

		@Override
		public Snapshot poll() throws Exception {
			throw failure;
		}

		@Override
		public void playPause() {
			barrier.countDown();
		}

		@Override
		public void next() {
		}

		@Override
		public void previous() {
		}

		@Override
		public String displayName() {
			return "Fake";
		}
	}

	@Test
	void aFailedReadThatNamesAFileInTheHomeFolderIsLoggedWithoutIt() throws Exception {
		String home = System.getProperty("user.home");
		assumeTrue(home != null && home.length() > 3, "a real home folder");
		String covers = home + "\\AppData\\Roaming\\.minecraft\\.cache\\skylyrics\\windows\\covers";
		Failing source = new Failing();
		source.failure = new IOException("Couldn't prepare the Windows music reader (" + covers + ")", new AccessDeniedException(covers));
		MediaTracker tracker = new MediaTracker(source, () -> false);
		try (LogCapture log = new LogCapture()) {
			tracker.refresh();
			tracker.playPause(); // runs after the poll queued before it
			assertTrue(source.barrier.await(30, TimeUnit.SECONDS));
			assertTrue(log.lines().stream().anyMatch(l -> l.contains("Couldn't read the music player")), "the line was written: " + log.lines());
			for (String line : log.lines()) {
				assertFalse(line.contains(home), "the home folder is in the log: " + line);
			}
		} finally {
			tracker.shutdown();
		}
	}
}
