package dev.beli.skylyrics.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.media.Track;
import dev.beli.skylyrics.util.SafeFile;

/** A file written by a newer or older version keeps its settings and song timings across loads and saves. */
class ConfigUpgradeTest {
	@TempDir
	Path dir;

	private SkyLyricsConfig open(Path file) {
		return SkyLyricsConfig.load(file, SafeFile::readText);
	}

	private static Track track(String title, String artist) {
		return new Track(title, artist, "", 0, "", "", "");
	}

	@Test
	void settingsOnlyANewerVersionKnowsSurviveAnUnchangedFileSave() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		new SkyLyricsConfig().save(file);
		String text = Files.readString(file).replaceFirst("\\{", "{\n  \"futureThing\": 7,");
		text = text.replaceFirst("\"hat\": \"[A-Z_]+\"", "\"hat\": \"FUTURE_HAT\"");
		Files.writeString(file, text);
		SkyLyricsConfig c = open(file); // reads the file once, then nothing else touches it
		c.glow = 0.2f;
		c.save(file);
		String after = Files.readString(file);
		assertTrue(after.contains("\"futureThing\": 7"), after);
		assertTrue(after.contains("\"hat\": \"FUTURE_HAT\""), after);
		assertEquals(0.2f, open(file).glow, 1e-6f);
	}

	@Test
	void timingKeysWithDirectionMarksAreFound() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		new SkyLyricsConfig().save(file);
		String text = Files.readString(file).replace("\"songOffsets\": {}",
				"\"songOffsets\": {\"‏shir‎ - ‏שיר‎\": 250}");
		assertTrue(text.contains("250"), text);
		Files.writeString(file, text);
		SkyLyricsConfig c = open(file);
		assertEquals(250, c.songOffset(track("שיר", "Shir")));
	}

	@Test
	void aCleanKeyStaysTheSame() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		SkyLyricsConfig seed = new SkyLyricsConfig();
		seed.songOffsets.put("first - one", 5);
		seed.songOffsets.put("artist - song (live)", 100);
		seed.songOffsets.put("zed - last", 7);
		seed.save(file);
		SkyLyricsConfig c = open(file);
		assertEquals(List.of("first - one", "artist - song (live)", "zed - last"), List.copyOf(c.songOffsets.keySet()));
		assertEquals(100, c.songOffsets.get("artist - song (live)"));
	}

	@Test
	void onACollisionTheLaterEntryWins() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		new SkyLyricsConfig().save(file);
		String text = Files.readString(file).replace("\"songOffsets\": {}",
				"\"songOffsets\": {\"a‏ - b\": 100, \"mid - x\": 1, \"a - b\": 200}");
		Files.writeString(file, text);
		SkyLyricsConfig c = open(file);
		assertEquals(200, c.songOffsets.get("a - b"));
		assertEquals(List.of("mid - x", "a - b"), List.copyOf(c.songOffsets.keySet()));
	}
}
