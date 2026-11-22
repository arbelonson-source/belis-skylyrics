package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Windows, an antivirus or OneDrive can hold the sign-in file for a moment just as the game starts. */
class SpotifyLoginHeldTest {
	private static final String ID = "0123456789abcdef0123456789abcdef";

	@Test
	void aSignInFileHeldForAMomentAtStartStillSignsIn(@TempDir Path dir) throws Exception {
		Path login = dir.resolve("spotify-login.json");
		Files.writeString(login, "{\"clientId\":\"" + ID + "\",\"refreshToken\":\"r\",\"scopes\":\"\"}");
		int[] reads = {0};
		SpotifyWebSource source = new SpotifyWebSource(() -> ID, login, () -> true, f -> {
			if (reads[0]++ == 0) {
				throw new FileSystemException(f.toString(), null, "The process cannot access the file because it is being used by another process");
			}
			return Files.readString(f);
		});
		assertEquals(SpotifyWebSource.Account.SIGNED_IN, source.account());
	}

	@Test
	void aSignInFileThatStaysHeldSignsOutAndIsLeftAlone(@TempDir Path dir) throws Exception {
		Path login = dir.resolve("spotify-login.json");
		String text = "{\"clientId\":\"" + ID + "\",\"refreshToken\":\"r\",\"scopes\":\"\"}";
		Files.writeString(login, text);
		SpotifyWebSource source = new SpotifyWebSource(() -> ID, login, () -> true, f -> {
			throw new FileSystemException(f.toString(), null, "The process cannot access the file because it is being used by another process");
		});
		assertEquals(SpotifyWebSource.Account.SIGNED_OUT, source.account());
		assertEquals(text, Files.readString(login));
		assertFalse(Files.exists(dir.resolve("spotify-login.json.bad")), "a held file isn't damaged");
	}
}
