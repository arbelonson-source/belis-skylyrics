package dev.beli.skylyrics.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import dev.beli.skylyrics.media.Track;
import org.junit.jupiter.api.Test;

class LongSongKeyTest {
	@Test
	void aTimingSavedUnderALongKeyComesBackUnderTheSameKey() {
		Track t = new Track("t".repeat(300), "a".repeat(300), "", 0, "", "x");
		String key = SkyLyricsConfig.makeKey(t);
		assertEquals(603, key.length());
		JsonObject json = new JsonObject();
		JsonObject offsets = new JsonObject();
		offsets.addProperty(key, 700);
		json.add("songOffsets", offsets);
		SkyLyricsConfig loaded = SkyLyricsConfig.parse(json.toString()).config();
		assertEquals(700, loaded.songOffset(t));
	}
}
