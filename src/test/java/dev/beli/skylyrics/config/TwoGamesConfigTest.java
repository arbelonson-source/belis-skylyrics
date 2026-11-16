package dev.beli.skylyrics.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.util.SafeFile;

/** Two games share config/skylyrics.json: a save keeps what the other game saved meanwhile. */
class TwoGamesConfigTest {
	@TempDir
	Path dir;

	private SkyLyricsConfig open(Path file) {
		return SkyLyricsConfig.load(file, SafeFile::readText);
	}

	@Test
	void aSettingFromTheOtherGameSurvivesMySave() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		new SkyLyricsConfig().save(file);
		SkyLyricsConfig a = open(file);
		SkyLyricsConfig b = open(file);
		a.glow = 0.3f;
		a.save(file);
		b.syncOffsetMs = 400;
		b.save(file);
		SkyLyricsConfig disk = open(file);
		assertEquals(0.3f, disk.glow, 1e-6f);
		assertEquals(400, disk.syncOffsetMs);
		// B now also sees A's change in memory
		assertEquals(0.3f, b.glow, 1e-6f);
		assertEquals(400, b.syncOffsetMs);
	}

	@Test
	void songTimingsMergePerSong() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		SkyLyricsConfig seed = new SkyLyricsConfig();
		seed.songOffsets.put("old|song", 100);
		seed.songOffsets.put("gone|song", 50);
		seed.save(file);
		SkyLyricsConfig a = open(file);
		SkyLyricsConfig b = open(file);
		a.songOffsets.put("a|song", 200);
		a.songOffsets.put("old|song", 150);
		a.save(file);
		b.songOffsets.put("b|song", 300);
		b.songOffsets.remove("gone|song");
		b.save(file);
		SkyLyricsConfig disk = open(file);
		assertEquals(200, disk.songOffsets.get("a|song"));
		assertEquals(300, disk.songOffsets.get("b|song"));
		assertEquals(150, disk.songOffsets.get("old|song"));
		assertFalse(disk.songOffsets.containsKey("gone|song"));
	}

	@Test
	void aFieldOnlyANewerModKnowsStaysOnDisk() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		new SkyLyricsConfig().save(file);
		SkyLyricsConfig b = open(file);
		String text = Files.readString(file).replaceFirst("\\{", "{\n  \"futureThing\": 7,");
		Files.writeString(file, text);
		b.glow = 0.2f;
		b.save(file);
		String after = Files.readString(file);
		assertTrue(after.contains("\"futureThing\": 7"), after);
		assertEquals(0.2f, open(file).glow, 1e-6f);
	}

	@Test
	void withNoOtherGameTheFileIsJustMySettings() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		SkyLyricsConfig a = open(file);
		a.glow = 0.5f;
		a.save(file);
		a.songOffsets.put("x|y", 10);
		a.save(file);
		assertEquals(new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(a), Files.readString(file));
		SkyLyricsConfig disk = open(file);
		assertEquals(0.5f, disk.glow, 1e-6f);
		assertEquals(10, disk.songOffsets.get("x|y"));
	}

	@Test
	void whenTheOtherGameChangedTheSameFieldMineWins() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		new SkyLyricsConfig().save(file);
		SkyLyricsConfig a = open(file);
		SkyLyricsConfig b = open(file);
		a.glow = 0.3f;
		a.save(file);
		b.glow = 0.6f;
		b.save(file);
		assertEquals(0.6f, open(file).glow, 1e-6f);
	}

	@Test
	void savingLeavesAnotherGamesTempFileAlone() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		Path other = dir.resolve("skylyrics.json.tmp"); // the other game is writing its own copy right now
		Files.writeString(other, "half");
		SkyLyricsConfig a = open(file);
		a.glow = 0.5f;
		a.save(file);
		assertEquals("half", Files.readString(other));
		try (var files = Files.list(dir)) {
			assertEquals(java.util.Set.of("skylyrics.json", "skylyrics.json.tmp"), files.map(f -> f.getFileName().toString()).collect(java.util.stream.Collectors.toSet()));
		}
	}

	@Test
	void aSettingTheFileDoesNotKnowKeepsMyValue() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		new SkyLyricsConfig().save(file);
		SkyLyricsConfig a = open(file);
		a.worldWords = 5; // not the default; this game set it before and has not changed it since
		a.save(file);
		SkyLyricsConfig mine = open(file);
		// An older game, which has no worldWords setting, rewrites the file and a different setting changes in this game
		com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		json.remove("worldWords");
		json.addProperty("glow", 0.2f);
		Files.writeString(file, json.toString());
		mine.syncOffsetMs = 123;
		mine.save(file);
		assertEquals(5, mine.worldWords, "in memory");
		assertEquals(5, open(file).worldWords, "on disk");
		assertEquals(0.2f, open(file).glow, 1e-6f, "the other game's change is still kept");
		assertEquals(123, open(file).syncOffsetMs);
	}

	@Test
	void oldTempFilesOfOurNameAreRemovedAtLoad() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		java.nio.file.attribute.FileTime old = java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() - 2 * 24L * 3600 * 1000);
		Path stale = Files.writeString(dir.resolve("skylyrics.json.8812345.tmp"), "x");
		Path fresh = Files.writeString(dir.resolve("skylyrics.json.555.tmp"), "y");
		Path odd = Files.writeString(dir.resolve("skylyrics.json.bad.tmp"), "z");
		Path unrelated = Files.writeString(dir.resolve("other.json.99.tmp"), "w");
		for (Path p : java.util.List.of(stale, odd, unrelated)) {
			Files.setLastModifiedTime(p, old);
		}
		open(file);
		assertFalse(Files.exists(stale));
		assertTrue(Files.exists(fresh), "another game may be saving it");
		assertTrue(Files.exists(odd));
		assertTrue(Files.exists(unrelated));
	}
}
