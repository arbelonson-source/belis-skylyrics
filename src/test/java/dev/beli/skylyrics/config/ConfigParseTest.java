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

	@Test
	void recentSearchesAreCleanedOnLoad() {
		String json = "{\"recentSearches\": [\"a\", null, \"  \", \"  b  \", \"A\", \"c\", \"d\", \"e\", \"f\", \"g\"]}";
		assertEquals(java.util.List.of("a", "b", "c", "d", "e", "f"), SkyLyricsConfig.parse(json).config().recentSearches);
		String longOne = "x".repeat(99) + "\uD83C\uDFB5" + "tail";
		String capped = SkyLyricsConfig.parse("{\"recentSearches\": [\"" + longOne + "\"]}").config().recentSearches.get(0);
		assertEquals("x".repeat(99), capped);
		StringBuilder many = new StringBuilder("{\"recentSearches\": [");
		for (int i = 0; i < 1000; i++) many.append(i == 0 ? "" : ",").append('"').append(i).append('"');
		assertEquals(SkyLyricsConfig.MAX_RECENT_SEARCHES, SkyLyricsConfig.parse(many.append("]}").toString()).config().recentSearches.size());
	}

	@Test
	void playerAndClientIdAreTidied() {
		SkyLyricsConfig c = SkyLyricsConfig.parse("{\"preferredPlayer\": \" Spotify-Web \", \"spotifyClientId\": \"  abcd \"}").config();
		assertEquals("spotify-web", c.preferredPlayer);
		assertEquals("abcd", c.spotifyClientId);
	}

	@Test
	void hugeSongOffsetMapKeepsTheLastOnes() {
		StringBuilder sb = new StringBuilder("{\"songOffsets\": {");
		for (int i = 0; i < 700; i++) sb.append(i == 0 ? "" : ",").append("\"k").append(i).append("\": 100");
		var map = SkyLyricsConfig.parse(sb.append("}}").toString()).config().songOffsets;
		assertEquals(500, map.size());
		assertFalse(map.containsKey("k199"));
		assertTrue(map.containsKey("k200") && map.containsKey("k699"));
	}

	@Test
	void unreadableFileIsRetriedThenLeftAloneInsteadOfOverwrittenWithDefaults() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		String real = "{\"glow\": 0.4, \"introSeen\": true}";
		Files.writeString(file, real);
		int[] calls = {0};
		SkyLyricsConfig c = SkyLyricsConfig.load(file, f -> {
			calls[0]++;
			throw new java.io.IOException("locked");
		}, 3, 0);
		assertEquals(3, calls[0]);
		assertEquals(new SkyLyricsConfig().glow, c.glow, 1e-6f);
		c.save(file);
		assertEquals(real, Files.readString(file));
		assertFalse(Files.exists(dir.resolve("skylyrics.json.bad")));
		// "Reset all" must not unblock it
		c.resetToDefaults();
		c.save(file);
		assertEquals(real, Files.readString(file));
	}

	@Test
	void aLockThatLetsGoOnTheSecondTryLoadsTheRealSettings() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		Files.writeString(file, "{\"glow\": 0.4}");
		int[] calls = {0};
		SkyLyricsConfig c = SkyLyricsConfig.load(file, f -> {
			if (++calls[0] < 2) throw new java.io.IOException("locked");
			return Files.readString(f);
		}, 3, 0);
		assertEquals(0.4f, c.glow, 1e-6f);
		c.glow = 0.5f;
		c.save(file);
		assertTrue(Files.readString(file).contains("0.5"));
	}

	@Test
	void anEmptyDamagedFileDoesNotReplaceABetterBadCopy() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		Path bad = dir.resolve("skylyrics.json.bad");
		Files.writeString(bad, "{\"glow\": 0.4, \"songOffsets\": {");
		Files.writeString(file, "");
		SkyLyricsConfig.keepCopy(file);
		assertEquals("{\"glow\": 0.4, \"songOffsets\": {", Files.readString(bad));
		Files.writeString(file, "{");
		SkyLyricsConfig.keepCopy(file);
		assertEquals("{\"glow\": 0.4, \"songOffsets\": {", Files.readString(bad));
	}

	@Test
	void bytesThatAreNotTextCountAsDamageNotALock() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		Files.write(file, new byte[] {(byte) 0xC3, 0x28});
		SkyLyricsConfig c = SkyLyricsConfig.load(file, dev.beli.skylyrics.util.SafeFile::readText, 3, 0);
		assertFalse(c.saveBlocked);
		assertTrue(Files.exists(dir.resolve("skylyrics.json.bad")));
		c.glow = 0.5f;
		c.save(file);
		assertTrue(Files.readString(file).contains("0.5"));
	}

	@Test
	void aFileThatVanishesBetweenCheckAndReadIsJustNoFile() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		Files.writeString(file, "{}");
		SkyLyricsConfig c = SkyLyricsConfig.load(file, f -> {
			throw new java.nio.file.NoSuchFileException(f.toString());
		}, 3, 0);
		assertFalse(c.saveBlocked);
	}

	@Test
	void whenABlockedFileReadsAgainTheFileWinsAndIsNotOverwrittenWithDefaults() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		String real = "{\"glow\": 0.4, \"songOffsets\": {\"a|b\": 250}}";
		Files.writeString(file, real);
		boolean[] locked = {true};
		SkyLyricsConfig c = SkyLyricsConfig.load(file, f -> {
			if (locked[0]) throw new java.io.IOException("locked");
			return Files.readString(f);
		}, 3, 0);
		assertTrue(c.saveBlocked);
		c.save(file);
		assertEquals(real, Files.readString(file));
		locked[0] = false;
		c.save(file);
		assertFalse(c.saveBlocked);
		assertEquals(0.4f, c.glow, 1e-6f);
		assertEquals(250, c.songOffsets.get("a|b"));
		assertEquals(real, Files.readString(file));
		c.glow = 0.7f;
		c.save(file);
		assertTrue(Files.readString(file).contains("0.7"));
		assertTrue(Files.readString(file).contains("250"));
	}

	@Test
	void aBlockedFileIsReadAgainSoonAfterStartOnTheMainThread() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		Files.writeString(file, "{\"glow\": 0.4}");
		boolean[] locked = {true};
		SkyLyricsConfig c = SkyLyricsConfig.load(file, f -> {
			if (locked[0]) throw new java.io.IOException("locked");
			return Files.readString(f);
		}, 1, 0);
		var oldMain = SkyLyricsConfig.mainThread;
		int[] handed = {0};
		SkyLyricsConfig.mainThread = r -> {
			handed[0]++;
			r.run();
		};
		try {
			locked[0] = false;
			SkyLyricsConfig.rereadSoon(c, file, new long[] {10});
			for (int i = 0; i < 200 && c.saveBlocked; i++) Thread.sleep(10);
		} finally {
			SkyLyricsConfig.mainThread = oldMain;
		}
		assertFalse(c.saveBlocked);
		assertEquals(1, handed[0]);
		assertEquals(0.4f, c.glow, 1e-6f);
	}

	@Test
	void aFileThatIsNeverReadableByPermissionsIsMovedAsideAndSavingStarts() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		Path bad = dir.resolve("skylyrics.json.bad");
		Files.writeString(file, "{\"glow\": 0.4}");
		Files.writeString(bad, "{\"glow\": 0.3, \"older\": 1}");
		SkyLyricsConfig c = SkyLyricsConfig.load(file, f -> {
			throw new java.nio.file.AccessDeniedException(f.toString());
		}, 1, 0);
		assertTrue(c.saveBlocked);
		var old = SkyLyricsConfig.readable;
		SkyLyricsConfig.readable = p -> false;
		try {
			SkyLyricsConfig.rereadSoon(c, file, new long[] {1, 1}).join();
		} finally {
			SkyLyricsConfig.readable = old;
		}
		assertFalse(c.saveBlocked);
		assertEquals("{\"glow\": 0.4}", Files.readString(dir.resolve("skylyrics.json.bad.1")));
		assertEquals("{\"glow\": 0.3, \"older\": 1}", Files.readString(bad));
		assertFalse(Files.exists(file));
		c.glow = 0.7f;
		c.save(file);
		assertTrue(Files.readString(file).contains("0.7"));
	}

	@Test
	void aSharingLockThatNeverLetsGoLeavesTheFileAndStaysBlocked() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		Files.writeString(file, "{\"glow\": 0.4}");
		SkyLyricsConfig c = SkyLyricsConfig.load(file, f -> {
			throw new java.io.IOException("locked");
		}, 1, 0);
		var old = SkyLyricsConfig.readable;
		SkyLyricsConfig.readable = p -> true;
		try {
			SkyLyricsConfig.rereadSoon(c, file, new long[] {1, 1}).join();
		} finally {
			SkyLyricsConfig.readable = old;
		}
		assertTrue(c.saveBlocked);
		assertEquals("{\"glow\": 0.4}", Files.readString(file));
		assertFalse(Files.exists(dir.resolve("skylyrics.json.bad")));
	}
}
