package dev.beli.skylyrics.media;

import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;

import dev.beli.skylyrics.util.PrivateFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A damaged sign-in file is kept as a .bad copy, and that copy can still hold the token. */
class SpotifyLoginBadCopyTest {
	private static final String ID = "0123456789abcdef0123456789abcdef";

	@Test
	void theCopyOfADamagedSignInIsAsPrivateAsTheSignIn(@TempDir Path dir) throws IOException {
		Path login = dir.resolve("spotify-login.json");
		// Cut off halfway, with the token still in it
		Files.writeString(login, "{\"clientId\":\"" + ID + "\",\"refreshToken\":\"AQD-not-a-real-token");
		boolean posix = Files.getFileAttributeView(login, PosixFileAttributeView.class) != null;
		// Private the way the game saves it
		if (posix) {
			Files.setPosixFilePermissions(login, EnumSet.of(OWNER_READ, OWNER_WRITE));
		} else {
			assertTrue(PrivateFile.restrictToOwner(login));
		}

		SpotifyWebSource source = new SpotifyWebSource(() -> ID, login);
		assertEquals(SpotifyWebSource.Account.SIGNED_OUT, source.account());
		Path bad = dir.resolve("spotify-login.json.bad");
		assertTrue(Files.exists(bad), "the damaged file is kept");

		if (posix) {
			assertEquals(EnumSet.of(OWNER_READ, OWNER_WRITE), Files.getPosixFilePermissions(bad));
		} else {
			// Windows: a copy gets the folder's access list, which on a second drive (D:\Games) lets every account in
			UserPrincipal owner = Files.getOwner(bad);
			List<AclEntry> acl = Files.getFileAttributeView(bad, AclFileAttributeView.class).getAcl();
			assertFalse(acl.isEmpty());
			for (AclEntry e : acl) {
				assertTrue(e.principal().equals(owner) || e.principal().getName().endsWith("\\" + System.getProperty("user.name")),
						"only the player may read the copy, not " + e.principal().getName());
			}
		}
	}
}
