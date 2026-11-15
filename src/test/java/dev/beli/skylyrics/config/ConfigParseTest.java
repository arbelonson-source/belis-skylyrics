package dev.beli.skylyrics.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigParseTest {
	@TempDir
	Path dir;

	private static final String GOOD_PART = "\"glow\": 0.4, \"songOffsets\": {\"a|b\": 250}, \"recentSearches\": [\"x\"]";

	@Test
	void oneBadFieldOnlyResetsThatField() {
		for (String bad : new String[] {"\"syncOffsetMs\": 1e10", "\"syncOffsetMs\": 1.5", "\"syncOffsetMs\": \"abc\"",
				"\"color\": \"#fff\"", "\"enabled\": 1", "\"enabled\": \"yes\""}) {
			SkyLyricsConfig.Parsed p = SkyLyricsConfig.parse("{" + bad + ", " + GOOD_PART + "}");
			assertEquals(1, p.badFields().size(), bad);
			assertEquals(0.4f, p.config().glow, 1e-6f, bad);
			assertEquals(250, p.config().songOffsets.get("a|b"), bad);
			assertEquals(java.util.List.of("x"), p.config().recentSearches, bad);
			assertTrue(p.damaged());
		}
		SkyLyricsConfig d = new SkyLyricsConfig();
		SkyLyricsConfig c = SkyLyricsConfig.parse("{\"syncOffsetMs\": 1e10, " + GOOD_PART + "}").config();
		assertEquals(d.syncOffsetMs, c.syncOffsetMs);
	}

	@Test
	void songOffsetsAsAnArrayKeepsEverythingElse() {
		SkyLyricsConfig.Parsed p = SkyLyricsConfig.parse("{\"songOffsets\": [1,2], \"glow\": 0.3}");
		assertEquals(java.util.List.of("songOffsets"), p.badFields());
		assertEquals(0.3f, p.config().glow, 1e-6f);
		assertTrue(p.config().songOffsets.isEmpty());
	}

	@Test
	void goodValuesStillGetClamped() {
		SkyLyricsConfig c = SkyLyricsConfig.parse("{\"syncOffsetMs\": 99999, \"glow\": 7}").config();
		assertEquals(5000, c.syncOffsetMs);
		assertEquals(1f, c.glow, 1e-6f);
	}

	@Test
	void truncatedFileGivesDefaultsAndIsFlaggedUnreadable() {
		SkyLyricsConfig.Parsed p = SkyLyricsConfig.parse("{\"glow\": 0.4, \"songOffsets\": {\"a|b\": 25");
		assertTrue(p.unreadable());
		assertEquals(new SkyLyricsConfig().glow, p.config().glow, 1e-6f);
		assertTrue(SkyLyricsConfig.parse("[1,2]").unreadable());
	}

	@Test
	void damagedFileIsCopiedToBadAndAnOlderCopyIsReplaced() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		Path bad = dir.resolve("skylyrics.json.bad");
		Files.writeString(bad, "old");
		Files.writeString(file, "{\"glow\": 0.4, \"songOffsets\": {");
		SkyLyricsConfig.keepCopy(file);
		assertEquals("{\"glow\": 0.4, \"songOffsets\": {", Files.readString(bad));
	}

	@Test
	void unknownEnumNamesGiveDefaults() {
		SkyLyricsConfig.Parsed p = SkyLyricsConfig.parse("{\"layout\": \"NOPE\", \"sky\": \"???\", \"glow\": 0.4}");
		SkyLyricsConfig d = new SkyLyricsConfig();
		assertEquals(d.layout, p.config().layout);
		assertEquals(d.sky, p.config().sky);
		assertEquals(0.4f, p.config().glow, 1e-6f);
		assertFalse(p.unreadable());
	}
}
