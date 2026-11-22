package dev.beli.skylyrics.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.media.TitleCleaner;
import dev.beli.skylyrics.media.Track;
import dev.beli.skylyrics.util.SafeFile;

/**
 * Settings files from the last releases, as a player upgrading has them. The files in {@code upgrade/} were written by the
 * releases' own code: their SkyLyricsConfig, Gson and defaults, with a non-default choice for every enum and most other
 * settings (Floating, Lines, Moonlight, a propeller cap, timings for four songs, Spotify Web with a Client ID, Tell me for
 * updates). {@code skylyrics-0.8.0-defaults.json} is 0.8.0's untouched defaults; the 0.7.8 file has the same choices but
 * lacks the four settings 0.8.0 added.
 */
class OldSettingsFileTest {
	@TempDir
	Path dir;

	/** Timings are compared song by song below: their keys are cleaned on load. */
	private static final Set<String> NOT_COMPARED = Set.of("songOffsets");

	private static String resource(String name) throws Exception {
		try (InputStream in = OldSettingsFileTest.class.getResourceAsStream("/upgrade/" + name)) {
			assertNotNull(in, name);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private SkyLyricsConfig load(String text) throws Exception {
		Path file = dir.resolve("skylyrics.json");
		Files.writeString(file, text);
		return SkyLyricsConfig.load(file, SafeFile::readText);
	}

	/** Every setting the old file has comes back with the same value (floats as floats, the rest exactly). */
	private static void assertKept(JsonObject old, SkyLyricsConfig loaded, String when) {
		JsonObject now = new Gson().toJsonTree(loaded).getAsJsonObject();
		for (var e : old.entrySet()) {
			if (NOT_COMPARED.contains(e.getKey())) {
				continue;
			}
			JsonElement was = e.getValue();
			JsonElement is = now.get(e.getKey());
			assertNotNull(is, when + ": " + e.getKey() + " is gone");
			if (was.isJsonPrimitive() && was.getAsJsonPrimitive().isNumber() && is.isJsonPrimitive() && is.getAsJsonPrimitive().isNumber()) {
				assertEquals(was.getAsFloat(), is.getAsFloat(), 1e-6f, when + ": " + e.getKey());
			} else {
				assertEquals(was, is, when + ": " + e.getKey());
			}
		}
	}

	/** A song as the music sources hand it over: through the title cleaning. */
	private static Track playing(String title, String artist) {
		return TitleCleaner.clean(new Track(title, artist, "", 0, "", "spotify", ""));
	}

	@Test
	void everyChoiceInA080FileComesBackAndSurvivesASave() throws Exception {
		String text = resource("skylyrics-0.8.0.json");
		JsonObject old = JsonParser.parseString(text).getAsJsonObject();
		SkyLyricsConfig c = load(text);
		assertKept(old, c, "loaded");
		assertTrue(c.introSeen, "the tour isn't shown again");
		assertFalse(c.keysMigrated, "the one-time library key move still gets its one run, as 0.8.0 ran it at every start");
		assertEquals(350, c.songOffset(new Track("Midnight Signal", "Aurora Lane", "Night Drive", 201_000, "", "spotify", "")));
		assertEquals(-1250, c.songOffset(playing("Café Lights (feat. Mira Sol)", "Zoë Hart")));
		assertEquals(900, c.songOffset(playing("‏שיר הלילה‎", "להקת הים")), "saved by 0.8.0 with its direction marks");

		c.keysMigrated = true; // what the first start does
		c.save(dir.resolve("skylyrics.json"));
		SkyLyricsConfig again = SkyLyricsConfig.load(dir.resolve("skylyrics.json"), SafeFile::readText);
		assertKept(old, again, "after a save");
		assertTrue(again.keysMigrated);
		assertEquals(900, again.songOffset(playing("שיר הלילה", "להקת הים")));
	}

	@Test
	void untouched080DefaultsLoadAsTodaysDefaults() throws Exception {
		String text = resource("skylyrics-0.8.0-defaults.json");
		SkyLyricsConfig c = load(text);
		assertKept(JsonParser.parseString(text).getAsJsonObject(), c, "0.8.0 defaults");
		SkyLyricsConfig fresh = new SkyLyricsConfig();
		JsonObject now = new Gson().toJsonTree(c).getAsJsonObject();
		JsonObject defaults = new Gson().toJsonTree(fresh).getAsJsonObject();
		assertEquals(defaults, now, "nothing a 0.8.0 player left alone is set differently now");
	}

	@Test
	void everyChoiceInA078FileComesBackAndNewSettingsGetTheirDefaults() throws Exception {
		String text = resource("skylyrics-0.7.8.json");
		SkyLyricsConfig c = load(text);
		assertKept(JsonParser.parseString(text).getAsJsonObject(), c, "0.7.8");
		SkyLyricsConfig d = new SkyLyricsConfig();
		assertEquals(d.viewLock, c.viewLock);
		assertEquals(d.showUnderground, c.showUnderground);
		assertEquals(d.cardLines, c.cardLines);
		assertEquals(d.recentSongs, c.recentSongs);
		assertFalse(c.keysMigrated);
		assertEquals(-1250, c.songOffset(playing("Café Lights (feat. Mira Sol)", "Zoë Hart")));
	}
}
