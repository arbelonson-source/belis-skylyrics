package dev.beli.skylyrics.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.beli.skylyrics.util.SafeFile;

/** A wrong-typed field is repaired by the next save, and one bad song timing doesn't cost the others. */
class ConfigRepairTest {
	@TempDir
	Path dir;

	@Test
	void aWrongTypedFieldIsRepairedAfterOneSaveAndAnUnknownOneIsKept() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		Files.writeString(file, "{\"size\":\"big\",\"futureThing\":7}");
		SkyLyricsConfig config = SkyLyricsConfig.load(file, SafeFile::readText);
		config.glow = 0.3f; // any other change, so the save has something to write
		config.save(file);
		JsonObject disk = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		assertTrue(disk.get("size").getAsJsonPrimitive().isNumber(), "size still holds the bad value: " + disk.get("size"));
		assertEquals(7, disk.get("futureThing").getAsInt());
		assertFalse(SkyLyricsConfig.parse(Files.readString(file)).damaged());
	}

	@Test
	void oneBadSongTimingKeepsTheRest() {
		var parsed = SkyLyricsConfig.parse("{\"songOffsets\":{\"a|b\":\"x\",\"c|d\":250}}");
		assertEquals(250, parsed.config().songOffsets.get("c|d"));
		assertFalse(parsed.config().songOffsets.containsKey("a|b"));
	}
}
