package dev.beli.skylyrics.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.Fuzz;
import dev.beli.skylyrics.util.SafeFile;

/**
 * The settings file can be hand-edited, cut off by a crash or replaced by anything: every value of the wrong type or out
 * of range, random JSON or random bytes must load as usable settings (defaults where needed) that save and load again.
 */
class ConfigFuzzTest {
	private static final Duration BOUND = Duration.ofSeconds(30);
	private static final Gson GSON = new GsonBuilder().create();

	/** What the rest of the mod relies on in loaded settings. */
	private static void checkUsable(SkyLyricsConfig c, String from) throws Exception {
		for (Field field : SkyLyricsConfig.class.getDeclaredFields()) {
			int mods = field.getModifiers();
			if (Modifier.isStatic(mods) || Modifier.isTransient(mods) || field.isSynthetic()) {
				continue;
			}
			field.setAccessible(true);
			Object v = field.get(c);
			if (!field.getType().isPrimitive()) {
				assertNotNull(v, field.getName() + " is null after " + Fuzz.show(from));
			}
			if (v instanceof Float x) {
				assertTrue(Float.isFinite(x), field.getName() + " is " + x);
			}
		}
		assertTrue(c.glow >= 0 && c.glow <= 1 && c.opacity >= 0.2f && c.opacity <= 1 && c.size >= 0.3f && c.size <= 3);
		assertTrue(c.worldWords >= 1 && c.worldWords <= 10 && (c.cardLines == 1 || c.cardLines == 3));
		assertTrue(c.syncOffsetMs >= -5000 && c.syncOffsetMs <= 5000);
		assertTrue(c.songOffsets.size() <= 500);
		for (Map.Entry<String, Integer> e : c.songOffsets.entrySet()) {
			assertNotNull(e.getKey());
			assertTrue(e.getValue() != null && e.getValue() != 0 && Math.abs(e.getValue()) <= 5000);
		}
		assertTrue(c.recentSearches.size() <= SkyLyricsConfig.MAX_RECENT_SEARCHES);
		c.recentSearches.forEach(s -> assertTrue(s != null && !s.isBlank() && s.length() <= 100));
		assertTrue((c.color & ~0xFFFFFF) == 0 && (c.glowColor & ~0xFFFFFF) == 0 && (c.toneColor & ~0xFFFFFF) == 0 && (c.barAccentColor & ~0xFFFFFF) == 0);
		// What is saved reads back clean
		SkyLyricsConfig.Parsed again = SkyLyricsConfig.parse(GSON.toJson(c));
		assertFalse(again.damaged(), "saved settings read back damaged: " + again.describe());
	}

	private static String template() {
		SkyLyricsConfig c = new SkyLyricsConfig();
		c.songOffsets.put("made up band - a made-up song", 250);
		c.recentSearches.add("made up");
		return GSON.toJson(c);
	}

	@Test
	void anySettingsTextLoadsAsUsableSettings() {
		String template = template();
		assertTimeoutPreemptively(BOUND, () -> Fuzz.cases(40, 800, (f, i) -> {
			String text = f.random.nextInt(8) == 0 ? f.nasty(200) : f.damagedText(template, 0.15);
			SkyLyricsConfig.Parsed p;
			try {
				p = SkyLyricsConfig.parse(text);
			} catch (RuntimeException e) {
				throw new AssertionError(e + " for " + Fuzz.show(text), e);
			}
			checkUsable(p.config(), text);
		}));
	}

	@Test
	void anySettingsFileLoadsAsUsableSettings(@TempDir Path dir) {
		String template = template();
		assertTimeoutPreemptively(BOUND, () -> Fuzz.cases(41, 200, (f, i) -> {
			Path file = dir.resolve("skylyrics" + i + ".json");
			byte[] bytes = switch (f.random.nextInt(5)) {
				case 0 -> f.bytes(3000);
				case 1 -> f.mutate(template.getBytes(StandardCharsets.UTF_8));
				case 2 -> template.getBytes(StandardCharsets.UTF_16);
				case 3 -> ("﻿" + template).getBytes(StandardCharsets.UTF_8);
				default -> f.damagedText(template, 0.2).getBytes(StandardCharsets.UTF_8);
			};
			Files.write(file, bytes);
			checkUsable(SkyLyricsConfig.load(file, SafeFile::readText, 1, 0), new String(bytes, StandardCharsets.ISO_8859_1));
		}));
	}
}
