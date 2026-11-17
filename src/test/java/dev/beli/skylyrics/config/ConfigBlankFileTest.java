package dev.beli.skylyrics.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import dev.beli.skylyrics.util.SafeFile;

/** A settings file with nothing in it is a fresh start, not damage. */
class ConfigBlankFileTest {
	@TempDir Path dir;

	private SkyLyricsConfig load(String text) throws Exception {
		Path f = dir.resolve("skylyrics.json");
		Files.writeString(f, text);
		return SkyLyricsConfig.load(f, SafeFile::readText);
	}

	@Test
	void anEmptyOrBlankFileIsNoSettingsYetNotADamagedOne() throws Exception {
		for (String text : new String[] {"", "  \n\t ", "﻿"}) {
			SkyLyricsConfig c = load(text);
			assertEquals(new SkyLyricsConfig().size, c.size, 1e-6f);
			assertFalse(Files.exists(dir.resolve("skylyrics.json.bad")), "no .bad for " + text.length() + " chars");
		}
	}

	@Test
	void aFileWithBrokenContentStillGetsItsCopy() throws Exception {
		load("{\"size\": ");
		assertTrue(Files.exists(dir.resolve("skylyrics.json.bad")));
	}
}
