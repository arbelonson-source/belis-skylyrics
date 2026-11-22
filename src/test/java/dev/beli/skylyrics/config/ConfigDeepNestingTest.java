package dev.beli.skylyrics.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.util.SafeFile;

/**
 * A settings file nested tens of thousands of lists deep (damaged, or made to break things) is a damaged file, or a
 * damaged setting. On Minecraft 1.21.1 to 1.21.10, whose Gson reads any depth but writes an element out by recursion,
 * the error text for it was a StackOverflowError: the game crashed at every start, or could no longer save its settings.
 * Newer Gson refuses such a file as a whole, which reads as damaged too.
 */
class ConfigDeepNestingTest {
	private static final String DEEP = "[".repeat(20_000) + "]".repeat(20_000);

	@Test
	void aDeeplyNestedOnOffValueGivesTheDefaultInsteadOfCrashing() {
		SkyLyricsConfig.Parsed p = SkyLyricsConfig.parse("{\"enabled\": " + DEEP + ", \"glow\": 0.4}");
		assertEquals(new SkyLyricsConfig().enabled, p.config().enabled);
		assertTrue(p.damaged());
	}

	@Test
	void aFileThatIsOneDeepListIsDamagedInsteadOfCrashing() {
		assertTrue(SkyLyricsConfig.parse(DEEP).unreadable());
	}

	@Test
	void savingOverADeeplyNestedFileStillWritesTheSettings(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("skylyrics.json");
		SkyLyricsConfig c = SkyLyricsConfig.load(file, SafeFile::readText, 1, 0);
		Files.writeString(file, DEEP); // replaced after the load (another program, a damaged disk)
		c.glow = 0.4f;
		c.save(file);
		assertEquals(0.4f, SkyLyricsConfig.parse(Files.readString(file)).config().glow, 1e-6f);
	}
}
