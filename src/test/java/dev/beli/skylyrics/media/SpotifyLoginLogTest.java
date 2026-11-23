package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.LogCapture;

/**
 * Players paste the game log in public when they ask for help. When the Spotify sign-in file can't be read, saved or
 * removed, the log says so, but with the home folder (on Windows, C:\Users\<your name>) cut to "~".
 */
class SpotifyLoginLogTest {
	private static final String ID = "0123456789abcdef0123456789abcdef";

	private static void assertNoHome(List<String> lines, String home, String expected) {
		assertTrue(lines.stream().anyMatch(l -> l.contains(expected)), "the line was written: " + lines);
		for (String line : lines) {
			assertFalse(line.contains(home), "the home folder is in the log: " + line);
		}
	}

	/** Runs {@code body} with the home folder set to {@code home}, as the log's redaction reads it. */
	private static void withHome(Path home, ThrowingRunnable body) throws Exception {
		String old = System.getProperty("user.home");
		System.setProperty("user.home", home.toString());
		try {
			body.run();
		} finally {
			System.setProperty("user.home", old);
		}
	}

	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	@Test
	void aSignInFileWindowsHoldsIsLoggedWithoutTheHomeFolder(@TempDir Path dir) throws Exception {
		String home = System.getProperty("user.home");
		assumeTrue(home != null && home.length() > 3, "a real home folder");
		Path login = dir.resolve("spotify-login.json");
		Files.writeString(login, "{}");
		String where = home + "/AppData/Roaming/.minecraft/.cache/skylyrics/spotify-login.json";
		try (LogCapture log = new LogCapture()) {
			new SpotifyWebSource(() -> ID, login, () -> true, f -> {
				throw new FileSystemException(where, null, "The process cannot access the file because it is being used by another process");
			});
			assertNoHome(log.lines(), home, "Couldn't read the Spotify sign-in");
		}
	}

	@Test
	void aSignInThatCanNotBeSavedIsLoggedWithoutTheHomeFolder(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("blocked"), "a file where the folder should be");
		Path login = dir.resolve("blocked").resolve("spotify-login.json");
		withHome(dir, () -> {
			try (LogCapture log = new LogCapture()) {
				SpotifyWebSource source = new SpotifyWebSource(() -> ID, login, () -> true, Files::readString);
				com.google.gson.JsonObject token = new com.google.gson.JsonObject();
				token.addProperty("access_token", "a");
				token.addProperty("refresh_token", "r");
				source.acceptAndSave(ID, token);
				assertNoHome(log.lines(), dir.toString(), "Couldn't save the Spotify sign-in");
			}
		});
	}

	@Test
	void aSignInThatCanNotBeRemovedIsLoggedWithoutTheHomeFolder(@TempDir Path dir) throws Exception {
		Path login = dir.resolve("spotify-login.json");
		Files.createDirectories(login.resolve("inside")); // a folder with something in it can't be deleted like a file
		withHome(dir, () -> {
			try (LogCapture log = new LogCapture()) {
				SpotifyWebSource source = new SpotifyWebSource(() -> ID, login, () -> true, f -> "");
				source.signOut();
				assertNoHome(log.lines(), dir.toString(), "Couldn't remove the Spotify sign-in");
			}
		});
	}
}
