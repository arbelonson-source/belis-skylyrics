package dev.beli.skylyrics.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import dev.beli.skylyrics.util.SafeFile;

/** NaN and Infinity numbers in a hand-edited settings file. */
class ConfigOddFilesTest {
	@TempDir Path dir;

	private SkyLyricsConfig load(String text) throws Exception {
		Path f = dir.resolve("skylyrics.json");
		Files.writeString(f, text);
		return SkyLyricsConfig.load(f, SafeFile::readText);
	}

	@Test
	void aNumberThatIsNotFiniteGoesBackToTheDefault() throws Exception {
		SkyLyricsConfig c = load("{\"height\": NaN, \"distance\": Infinity, \"glow\": -Infinity, \"size\": 2.0, \"worldFade\": NaN, \"barSize\": Infinity}");
		SkyLyricsConfig d = new SkyLyricsConfig();
		assertEquals(d.height, c.height, 1e-6f);
		assertEquals(d.distance, c.distance, 1e-6f);
		assertEquals(d.glow, c.glow, 1e-6f);
		assertEquals(d.worldFade, c.worldFade, 1e-6f);
		assertEquals(d.barSize, c.barSize, 1e-6f);
		assertEquals(2.0f, c.size, 1e-6f);
	}
}
