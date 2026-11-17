package dev.beli.skylyrics.lyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LyricsCacheCapTest {
	@Test
	void savingPastTheCapDuringASessionTrimsTheFolder(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("spotify.json"), "login"); // not ours: must survive
		for (int i = 0; i < 100; i++) {
			Path f = dir.resolve(String.format("%024x.json", i));
			LyricsService.writeCache(f, new LyricsService.CacheEntry("[00:01.00]a", null, false, 1L, 3), 20);
			f.toFile().setLastModified(1_000_000L + i * 1000L);
		}
		File[] left = dir.toFile().listFiles((d, n) -> n.matches("[0-9a-f]{24}\\.json"));
		assertTrue(left.length <= 22, "kept " + left.length);
		assertTrue(left.length >= 20, "kept " + left.length);
		assertTrue(Files.exists(dir.resolve("spotify.json")));
		assertTrue(Files.exists(dir.resolve(String.format("%024x.json", 99)))); // newest stays
	}

	@Test
	void aFolderUnderTheCapIsLeftAlone(@TempDir Path dir) throws Exception {
		for (int i = 0; i < 15; i++) {
			LyricsService.writeCache(dir.resolve(String.format("%024x.json", i)),
					new LyricsService.CacheEntry("[00:01.00]a", null, false, 1L, 3), 20);
		}
		assertEquals(15, dir.toFile().listFiles().length);
	}
}
