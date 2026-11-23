package dev.beli.skylyrics.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
 * A player who goes back to v0.8.0 after this version saved the settings: v0.8.0's own reader ({@link Config080}, copied
 * from the release) must keep every setting and find every song timing it saved, also for titles whose saved key today's
 * title cleaning writes differently (direction marks, an emoji's style selector).
 */
class BackTo080SettingsTest {
	@TempDir
	Path dir;

	private static String resource(String name) throws Exception {
		try (InputStream in = BackTo080SettingsTest.class.getResourceAsStream("/upgrade/" + name)) {
			assertNotNull(in, name);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private Path file() {
		return dir.resolve("skylyrics.json");
	}

	private SkyLyricsConfig open() {
		return SkyLyricsConfig.load(file(), SafeFile::readText);
	}

	/** What v0.8.0 makes of the file as it is on disk now. */
	private Config080 in080() throws Exception {
		Config080.Parsed parsed = Config080.parse(Files.readString(file()));
		assertFalse(parsed.damaged(), "v0.8.0 would reset settings: " + parsed.describe());
		return parsed.config();
	}

	/** A song as today's music sources hand it over: through the title cleaning. */
	private static Track playing(String title, String artist) {
		return TitleCleaner.clean(new Track(title, artist, "", 0, "", "spotify", ""));
	}

	/** The first start of this version saves once (the one-time key move), with nothing else changed. */
	private void firstStart() {
		SkyLyricsConfig c = open();
		c.keysMigrated = true;
		c.save(file());
	}

	@Test
	void every080SettingAndTimingIsStillThereFor080AfterThisVersionSaved() throws Exception {
		String text = resource("skylyrics-0.8.0.json");
		Files.writeString(file(), text);
		firstStart();
		Config080 back = in080();
		JsonObject old = JsonParser.parseString(text).getAsJsonObject();
		JsonObject now = new Gson().toJsonTree(back).getAsJsonObject();
		for (var e : old.entrySet()) {
			JsonElement is = now.get(e.getKey());
			if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isNumber()) {
				assertEquals(e.getValue().getAsFloat(), is.getAsFloat(), 1e-6f, e.getKey());
			} else {
				assertEquals(e.getValue(), is, e.getKey());
			}
		}
		assertEquals(900, back.songOffset("‏שיר הלילה‎", "להקת הים"), "the Hebrew song with its direction marks, as the player sends it");
		assertEquals(350, back.songOffset("Midnight Signal", "Aurora Lane"));
	}

	@Test
	void aTimingForATitleWithAnEmojiStyleSelectorIsFoundBy080AfterASave() throws Exception {
		String heart = "Love Me ❤️"; // a heart with its colour-style selector, as Spotify sends it
		Config080 before = new Config080();
		before.setSongOffset(heart, "Nova Ray", 750);
		Files.writeString(file(), before.toJson());
		assertEquals(750, open().songOffset(playing(heart, "Nova Ray")), "found after the update");
		firstStart();
		assertEquals(750, in080().songOffset(heart, "Nova Ray"), Files.readString(file()));
		assertEquals(750, open().songOffset(playing(heart, "Nova Ray")));
	}

	@Test
	void aTimingChangedInThisVersionIsTheOne080FindsToo() throws Exception {
		Files.writeString(file(), resource("skylyrics-0.8.0.json"));
		SkyLyricsConfig c = open();
		c.setSongOffset(playing("‏שיר הלילה‎", "להקת הים"), 1200);
		c.save(file());
		assertEquals(1200, in080().songOffset("‏שיר הלילה‎", "להקת הים"), Files.readString(file()));
		SkyLyricsConfig again = open();
		assertEquals(1200, again.songOffset(playing("‏שיר הלילה‎", "להקת הים")));
		assertEquals(4, again.songOffsets.size(), "one timing for the song, not two");
	}

	@Test
	void aTimingResetInThisVersionStaysResetIn080() throws Exception {
		Files.writeString(file(), resource("skylyrics-0.8.0.json"));
		SkyLyricsConfig c = open();
		c.setSongOffset(playing("‏שיר הלילה‎", "להקת הים"), 0);
		c.save(file());
		assertEquals(0, in080().songOffset("‏שיר הלילה‎", "להקת הים"), Files.readString(file()));
		assertEquals(0, open().songOffset(playing("‏שיר הלילה‎", "להקת הים")));
		assertEquals(350, in080().songOffset("Midnight Signal", "Aurora Lane"));
	}

	@Test
	void settingsChangedInThisVersionAndThen080ComeBackWhole() throws Exception {
		Files.writeString(file(), resource("skylyrics-0.8.0.json"));
		SkyLyricsConfig c = open();
		c.keysMigrated = true;
		c.glow = 0.8f;
		c.hat = SkyLyricsConfig.Hat.NONE;
		c.save(file());
		Config080 back = in080();
		assertEquals(0.8f, back.glow, 1e-6f);
		assertEquals(Config080.Hat.NONE, back.hat);
		// v0.8.0 saves (it knows no keysMigrated); this version reads every setting back
		back.opacity = 0.9f;
		Files.writeString(file(), back.toJson());
		SkyLyricsConfig after = open();
		assertEquals(0.8f, after.glow, 1e-6f);
		assertEquals(0.9f, after.opacity, 1e-6f);
		assertEquals(SkyLyricsConfig.Hat.NONE, after.hat);
		assertEquals(900, after.songOffset(playing("‏שיר הלילה‎", "להקת הים")));
		JsonObject a = new Gson().toJsonTree(after).getAsJsonObject();
		JsonObject b = new Gson().toJsonTree(back).getAsJsonObject();
		for (var e : b.entrySet()) {
			if (!e.getKey().equals("songOffsets")) {
				assertEquals(e.getValue(), a.get(e.getKey()), e.getKey());
			}
		}
	}
}
