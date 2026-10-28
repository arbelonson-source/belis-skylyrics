package dev.beli.skylyrics.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class FadeFromTest {
	@Test
	void anOldHandPickedLingerIsKeptButTheOldDefaultGivesWay() {
		JsonObject picked = new JsonObject();
		picked.addProperty("worldLinger", 4.5f);
		assertEquals(4.5f, SkyLyricsConfig.fadeFrom(picked, 1.2f), 1e-6f);

		JsonObject oldDefault = new JsonObject();
		oldDefault.addProperty("worldLinger", 2.2f);
		assertEquals(1.2f, SkyLyricsConfig.fadeFrom(oldDefault, 1.2f), 1e-6f);

		assertEquals(1.2f, SkyLyricsConfig.fadeFrom(new JsonObject(), 1.2f), 1e-6f);
		JsonObject odd = new JsonObject();
		odd.addProperty("worldLinger", "slow");
		assertEquals(1.2f, SkyLyricsConfig.fadeFrom(odd, 1.2f), 1e-6f);
	}
}
