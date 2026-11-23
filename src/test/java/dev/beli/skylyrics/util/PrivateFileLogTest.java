package dev.beli.skylyrics.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.LogCapture;

/**
 * Players paste the game log in public when they ask for help. On Windows the Spotify sign-in file is made readable by
 * its owner only; when that fails (Windows, an antivirus or OneDrive holding the file), the line in the log names the
 * file, but not the home folder (C:\Users\<your name>) that Windows' own error carries.
 */
class PrivateFileLogTest {
	@Test
	void aFileThatCanNotBeMadePrivateIsLoggedWithoutTheHomeFolder(@TempDir Path dir) throws Exception {
		String home = System.getProperty("user.home");
		assumeTrue(home != null && home.length() > 3, "a real home folder");
		Path file = Files.writeString(dir.resolve("spotify-login.json.tmp"), "{}");
		String where = home + "/AppData/Roaming/.minecraft/.cache/skylyrics/spotify-login.json.tmp";
		AclFileAttributeView held = new AclFileAttributeView() {
			public String name() {
				return "acl";
			}

			public List<AclEntry> getAcl() {
				return List.of();
			}

			public void setAcl(List<AclEntry> acl) throws FileSystemException {
				throw new FileSystemException(where, null, "The process cannot access the file because it is being used by another process");
			}

			public UserPrincipal getOwner() {
				return null;
			}

			public void setOwner(UserPrincipal owner) {
			}
		};
		PrivateFile.warnAgain(); // it warns once a game, and another test may have had that warning
		try (LogCapture log = new LogCapture("skylyrics")) {
			assertFalse(PrivateFile.restrict(file, held, f -> true));
			List<String> lines = log.lines();
			assertTrue(lines.stream().anyMatch(l -> l.contains("Couldn't limit access to spotify-login.json.tmp")), "the line was written: " + lines);
			for (String line : lines) {
				assertFalse(line.contains(home), "the home folder is in the log: " + line);
			}
		}
	}
}
