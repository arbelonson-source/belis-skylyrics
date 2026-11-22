package dev.beli.skylyrics.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.util.SafeFile;

/**
 * Windows, an antivirus or OneDrive can hold the settings file past the tries at start. The settings in use until it reads
 * again are the defaults, not the player's: the game must not take them for a first run (the tour) or for a file the
 * one-time library key move hasn't seen yet (it would move a key the player chose).
 */
class HeldAtStartTest {
	@TempDir
	Path dir;

	private static SkyLyricsConfig loadHeld(Path file) {
		return SkyLyricsConfig.load(file, f -> {
			throw new FileSystemException(f.toString(), null, "The process cannot access the file because it is being used by another process");
		}, 3, 0);
	}

	@Test
	void aSettingsFileHeldAtStartDoesNotOpenTheTourAgain() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		Files.writeString(file, "{\"glow\": 0.4, \"introSeen\": true, \"keysMigrated\": true}");
		SkyLyricsConfig c = loadHeld(file);
		assertTrue(c.saveBlocked);
		assertTrue(c.introSeen, "the first-run tour opens for a player who finished it long ago");
	}

	@Test
	void aSettingsFileHeldAtStartDoesNotMoveTheLibraryKeyAgain() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		String real = "{\"glow\": 0.4, \"introSeen\": true, \"keysMigrated\": true}";
		Files.writeString(file, real);
		SkyLyricsConfig c = loadHeld(file);
		assertFalse(KeyMigration.due(c.keysMigrated), "the one-time move off Iris's key runs again on settings that weren't read");
		// The save the move makes leaves the real file alone
		c.keysMigrated = true;
		c.save(file);
		assertEquals(real, Files.readString(file));
	}

	@Test
	void aNewPlayerStillGetsTheTourAndTheKeyCheck() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		SkyLyricsConfig none = SkyLyricsConfig.load(file, SafeFile::readText, 3, 0);
		assertFalse(none.introSeen);
		assertTrue(KeyMigration.due(none.keysMigrated));
		Files.writeString(file, "");
		SkyLyricsConfig empty = SkyLyricsConfig.load(file, SafeFile::readText, 3, 0);
		assertFalse(empty.introSeen);
		assertTrue(KeyMigration.due(empty.keysMigrated));
	}

	@Test
	void onceTheHeldFileReadsItsOwnAnswersCount() throws Exception {
		// A player who closed the game during the tour last time: the tour is still due once the file can be read
		Path file = dir.resolve("skylyrics.json");
		Files.writeString(file, "{\"glow\": 0.4, \"introSeen\": false, \"keysMigrated\": false}");
		boolean[] held = {true};
		SkyLyricsConfig c = SkyLyricsConfig.load(file, f -> {
			if (held[0]) {
				throw new FileSystemException(f.toString(), null, "The process cannot access the file because it is being used by another process");
			}
			return Files.readString(f);
		}, 3, 0);
		var oldMain = SkyLyricsConfig.mainThread;
		SkyLyricsConfig.mainThread = Runnable::run;
		try {
			held[0] = false;
			SkyLyricsConfig.rereadSoon(c, file, new long[] {1}).join(30_000);
		} finally {
			SkyLyricsConfig.mainThread = oldMain;
		}
		assertFalse(c.saveBlocked);
		assertEquals(0.4f, c.glow, 1e-6f);
		assertFalse(c.introSeen);
		assertTrue(KeyMigration.due(c.keysMigrated));
	}
}
