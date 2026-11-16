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
}
