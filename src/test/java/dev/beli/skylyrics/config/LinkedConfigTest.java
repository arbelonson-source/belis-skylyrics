package dev.beli.skylyrics.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.util.SafeFile;

/**
 * Players who share one settings file between game folders (Prism or MultiMC instances) link config/skylyrics.json to it.
 * A save goes into the shared file and leaves the link a link; else every other instance stops seeing the changes.
 */
class LinkedConfigTest {
	@TempDir
	Path dir;

	/** A link at {@code link} to {@code target}; skipped where this system won't make one (Windows without the right). */
	static void link(Path link, Path target) throws IOException {
		Files.createDirectories(link.getParent());
		try {
			Files.createSymbolicLink(link, target);
		} catch (IOException | UnsupportedOperationException e) {
			Assumptions.abort("no symbolic links here: " + e);
		}
	}

	@Test
	void aSaveWritesTheSharedFileAndKeepsTheLink() throws Exception {
		Path shared = dir.resolve("shared").resolve("skylyrics.json");
		Files.createDirectories(shared.getParent());
		new SkyLyricsConfig().save(shared);
		Path mine = dir.resolve("instance").resolve("config").resolve("skylyrics.json");
		link(mine, shared);

		SkyLyricsConfig cfg = SkyLyricsConfig.load(mine, SafeFile::readText);
		cfg.syncOffsetMs = 400;
		cfg.save(mine);

		assertTrue(Files.isSymbolicLink(mine), "the link is still a link");
		assertEquals(400, SkyLyricsConfig.load(shared, SafeFile::readText).syncOffsetMs, "the shared file has the change");
	}

	@Test
	void aLinkGivenAsARelativePathStillReachesTheSharedFile() throws Exception {
		Path shared = dir.resolve("shared").resolve("skylyrics.json");
		Files.createDirectories(shared.getParent());
		new SkyLyricsConfig().save(shared);
		Path mine = dir.resolve("instance").resolve("config").resolve("skylyrics.json");
		link(mine, Path.of("..", "..", "shared", "skylyrics.json"));

		SkyLyricsConfig cfg = SkyLyricsConfig.load(mine, SafeFile::readText);
		cfg.glow = 0.25f;
		cfg.save(mine);

		assertTrue(Files.isSymbolicLink(mine));
		assertEquals(0.25f, SkyLyricsConfig.load(shared, SafeFile::readText).glow, 1e-6f);
	}
}
