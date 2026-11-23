package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Something that isn't a file where the Spotify sign-in goes: a pipe (made by mistake, or by a tool that streams files)
 * never ends, so reading it held up the game's start for ever. Only a plain file is read now; a sign-in shared between
 * game folders through a link to one still is.
 */
class SpotifyLoginPipeTest {
	private static final String ID = "0123456789abcdef0123456789abcdef";

	/** Makes a pipe at {@code at}, or skips the test where there is no mkfifo (Windows). */
	static void pipe(Path at) throws IOException, InterruptedException {
		assumeFalse(System.getProperty("os.name", "").startsWith("Windows"), "no pipes in the file system on Windows");
		Process p;
		try {
			p = new ProcessBuilder("mkfifo", at.toString()).redirectErrorStream(true).start();
		} catch (IOException e) {
			assumeTrue(false, "no mkfifo here");
			return;
		}
		assumeTrue(p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0, "mkfifo failed");
		assumeTrue(Files.exists(at) && !Files.isRegularFile(at), "not a pipe");
	}

	/**
	 * Runs the job on a thread of its own and says whether it was done within 10 s. A job still waiting on the pipe then
	 * is let go: each time something opens the pipe for writing and closes it, a read waiting on it ends with nothing.
	 */
	static boolean finishes(ThrowingRunnable job, Path pipe) throws InterruptedException {
		AtomicReference<Throwable> failed = new AtomicReference<>();
		Thread t = new Thread(() -> {
			try {
				job.run();
			} catch (Throwable e) {
				failed.set(e);
			}
		}, "sign-in pipe test");
		t.setDaemon(true);
		t.start();
		t.join(10_000);
		boolean done = !t.isAlive();
		for (int i = 0; i < 100 && t.isAlive(); i++) {
			try (FileChannel c = FileChannel.open(pipe, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
				// opened and closed: the waiting read sees the end
			} catch (IOException ignored) {
				// gone already
			}
			t.join(100);
		}
		if (done && failed.get() instanceof Error e) {
			throw e;
		}
		return done;
	}

	@FunctionalInterface
	interface ThrowingRunnable {
		void run() throws Exception;
	}

	@Test
	void aPipeWhereTheSignInGoesDoesntHoldUpTheStart(@TempDir Path dir) throws Exception {
		Path login = dir.resolve("spotify-login.json");
		pipe(login);
		AtomicReference<SpotifyWebSource> made = new AtomicReference<>();
		assertTrue(finishes(() -> made.set(new SpotifyWebSource(() -> ID, login)), login), "the start waits on the pipe for ever");
		assertEquals(SpotifyWebSource.Account.SIGNED_OUT, made.get().account());
		assertFalse(Files.isRegularFile(login), "the pipe is left as it is");
		assertFalse(Files.exists(dir.resolve("spotify-login.json.bad")), "no copy is made of a pipe (that would wait on it too)");
	}

	@Test
	void aSignInSharedThroughALinkStillReads(@TempDir Path dir) throws Exception {
		Path shared = dir.resolve("shared-login.json");
		Files.writeString(shared, "{\"clientId\":\"" + ID + "\",\"refreshToken\":\"r\",\"scopes\":\"\"}");
		Path login = dir.resolve("spotify-login.json");
		try {
			Files.createSymbolicLink(login, shared);
		} catch (IOException | UnsupportedOperationException e) {
			assumeTrue(false, "no links here: " + e);
		}
		assertEquals(SpotifyWebSource.Account.SIGNED_IN, new SpotifyWebSource(() -> ID, login).account());
	}
}
