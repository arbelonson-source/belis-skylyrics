package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Browsing a big library for an evening: every cover loads, shows and later scrolls out of the cache. Minecraft 1.21.4
 * and 1.21.5 keep a menu render type for every texture name ever drawn, for as long as the game runs, so a fresh name
 * per cover would add one for every cover seen. The names of covers that left the cache go to the next ones instead.
 */
class CoverTextureNamesTest {
	@Test
	void coversThatLeftTheCacheHandTheirTextureNamesOn(@TempDir Path dir) throws Exception {
		Set<Identifier> named = new HashSet<>();
		Set<Identifier> live = new HashSet<>();
		List<String> problems = new ArrayList<>();
		CoverCache cache = new CoverCache(dir, url -> new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB));
		cache.toImage = image -> BlankImages.blank(); // no native memory in unit tests
		cache.textures = new CoverCache.Textures() {
			@Override
			public void register(Identifier id, NativeImage image) {
				if (!live.add(id)) {
					problems.add("a second cover was put under " + id + " while the first was still on the GPU");
				}
				named.add(id);
			}

			@Override
			public void release(Identifier id) {
				if (!live.remove(id)) {
					problems.add(id + " was let go of while not on the GPU");
				}
			}
		};
		int covers = 600;
		try {
			for (int i = 0; i < covers; i++) {
				String url = "https://example.test/cover/" + i;
				long end = System.nanoTime() + 10_000_000_000L;
				// The library asks every frame, and moves the covers that finished loading to the GPU first
				while (cache.get(url) == null && System.nanoTime() < end) {
					Thread.sleep(1);
					cache.upload();
				}
				assertNotNull(cache.get(url), "cover " + i + " never got on the GPU");
			}
		} finally {
			cache.close();
		}
		assertEquals(List.of(), problems);
		assertTrue(live.size() <= 96, "covers on the GPU at the end: " + live.size());
		assertTrue(named.size() <= 128, covers + " covers used " + named.size() + " texture names");
	}
}
