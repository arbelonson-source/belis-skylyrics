package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A player who shares the Recently heard list or the Spotify sign-in between game folders links the file to one copy. A save
 * goes into that copy and leaves the link a link: else the other games keep an old list, or a sign-in whose token Spotify has
 * already replaced, so they are signed out.
 */
class LinkedFilesTest {
	private static final String ID = "0123456789abcdef0123456789abcdef";

	@TempDir
	Path dir;

	private static void link(Path link, Path target) throws IOException {
		Files.createDirectories(link.getParent());
		try {
			Files.createSymbolicLink(link, target);
		} catch (IOException | UnsupportedOperationException e) {
			Assumptions.abort("no symbolic links here: " + e);
		}
	}

	@Test
	void theHeardListIsSavedIntoTheSharedFile() throws Exception {
		Path shared = dir.resolve("shared").resolve("heard.json");
		Files.createDirectories(shared.getParent());
		assertTrue(HeardLibrary.write(shared, List.of()));
		Path mine = dir.resolve("instance").resolve(".cache").resolve("skylyrics").resolve("heard.json");
		link(mine, shared);

		assertTrue(HeardLibrary.write(mine, List.of(new HeardLibrary.Entry("Song", "Artist", "", 1000, "", "uri:song", "mpv"))));

		assertTrue(Files.isSymbolicLink(mine), "the link is still a link");
		assertEquals(List.of("Song"), HeardLibrary.read(shared).stream().map(HeardLibrary.Entry::title).toList());
	}

	@Test
	void theSpotifySignInIsSavedIntoTheSharedFile() throws Exception {
		Path shared = dir.resolve("shared").resolve("spotify-login.json");
		Files.createDirectories(shared.getParent());
		Files.writeString(shared, "{\"clientId\":\"" + ID + "\",\"refreshToken\":\"r1\",\"scopes\":\"\"}");
		Path mine = dir.resolve("instance").resolve(".cache").resolve("skylyrics").resolve("spotify-login.json");
		link(mine, shared);

		SpotifyWebSource source = new SpotifyWebSource(() -> ID, mine);
		com.google.gson.JsonObject next = new com.google.gson.JsonObject();
		next.addProperty("access_token", "a");
		next.addProperty("refresh_token", "r2");
		source.acceptAndSave(ID, next);

		assertTrue(Files.isSymbolicLink(mine), "the link is still a link");
		assertTrue(Files.readString(shared).contains("\"r2\""), "the shared sign-in has the new token: " + Files.readString(shared));
	}
}
