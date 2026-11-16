package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.util.Leftovers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Temp files under the name 0.8.0 saved through ("heard" + a number + ".tmp") are cleaned at start like the new ones. */
class HeardOldTempTest {
	@TempDir
	Path dir;

	private Path aged(String name) throws IOException {
		Path p = dir.resolve(name);
		Files.writeString(p, "x");
		Files.setLastModifiedTime(p, FileTime.fromMillis(System.currentTimeMillis() - 2 * Leftovers.AGE_MS));
		return p;
	}

	@Test
	void anOldStyleTempOfAKilledGameIsCleanedAtStart() throws IOException {
		Path old = aged("heard8812734412.tmp");
		Path fresh = dir.resolve("heard5531.tmp");
		Files.writeString(fresh, "x");
		HeardLibrary.read(dir.resolve("heard.json"));
		assertFalse(Files.exists(old));
		assertTrue(Files.exists(fresh), "a fresh one may be another game's save in progress");
	}

	@Test
	void onlyTheExactOldNameIsTouched() throws IOException {
		Path notes = aged("heardnotes.tmp");
		Path other = aged("heard12.tmp.bak");
		Path cover = aged("cover123.tmp");
		HeardLibrary.read(dir.resolve("heard.json"));
		assertTrue(Files.exists(notes));
		assertTrue(Files.exists(other));
		assertTrue(Files.exists(cover));
	}
}
