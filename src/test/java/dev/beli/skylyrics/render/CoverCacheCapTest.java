package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoverCacheCapTest {
	@Test
	void savingPastTheCapDuringASessionTrimsTheFolder(@TempDir Path dir) throws Exception {
		for (int i = 0; i < 100; i++) {
			Path f = dir.resolve(String.format("%040x.img", i));
			CoverCache.store(dir, f, new byte[] {1, 2, 3}, Files::write, 20);
			f.toFile().setLastModified(1_000_000L + i * 1000L);
		}
		String[] left = dir.toFile().list();
		assertTrue(left.length <= 22, "kept " + left.length);
		assertTrue(left.length >= 20, "kept " + left.length);
		assertTrue(Files.exists(dir.resolve(String.format("%040x.img", 99))));
	}

	@Test
	void aFolderUnderTheCapIsLeftAlone(@TempDir Path dir) throws Exception {
		for (int i = 0; i < 15; i++) {
			CoverCache.store(dir, dir.resolve(String.format("%040x.img", i)), new byte[] {1}, Files::write, 20);
		}
		assertEquals(15, dir.toFile().list().length);
	}
}
