package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.util.SafeFile;

/** Files that are far too big or unreadable JSON are kept as .bad and start fresh, never an out-of-memory. */
class DurableLoadTest {
	@TempDir
	Path dir;

	private static String spaces(int n) {
		return " ".repeat(n);
	}

	@Test
	void anOversizedHeardFileIsKeptAsBadAndStartsEmpty() throws Exception {
		Path file = dir.resolve("heard.json");
		Files.writeString(file, "{\"songs\": []}" + spaces(3_000_000));
		boolean[] read = {false};
		HeardLibrary.Loaded loaded = HeardLibrary.load(file, f -> {
			read[0] = true;
			return Files.readString(f);
		}, 1, 0, p -> true);
		assertTrue(loaded.songs().isEmpty());
		assertTrue(!loaded.failed());
		assertTrue(!read[0], "the big file was read");
		assertTrue(Files.exists(dir.resolve("heard.json.bad")));
	}

	@Test
	void anOversizedSignInFileIsKeptAsBadAndSignsOut() throws Exception {
		Path file = dir.resolve("login.json");
		Files.writeString(file, "{\"clientId\":\"0123456789abcdef0123456789abcdef\",\"refreshToken\":\"r\"}" + spaces(3_000_000));
		SpotifyWebSource source = new SpotifyWebSource(() -> "0123456789abcdef0123456789abcdef", file);
		assertEquals(SpotifyWebSource.Account.SIGNED_OUT, source.account());
		assertTrue(Files.exists(dir.resolve("login.json.bad")));
	}

	@Test
	void aDamagedSignInFileIsKeptAsBad() throws Exception {
		Path file = dir.resolve("login.json");
		Files.writeString(file, "{\"refreshToken\": \"abc");
		SpotifyWebSource source = new SpotifyWebSource(() -> "0123456789abcdef0123456789abcdef", file);
		assertEquals(SpotifyWebSource.Account.SIGNED_OUT, source.account());
		assertTrue(Files.exists(dir.resolve("login.json.bad")));
	}

	@Test
	void anEmptySignInFileMakesNoCopy() throws Exception {
		Path file = dir.resolve("login.json");
		Files.writeString(file, "");
		new SpotifyWebSource(() -> "0123456789abcdef0123456789abcdef", file);
		assertTrue(!Files.exists(dir.resolve("login.json.bad")));
	}
}
