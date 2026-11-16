package dev.beli.skylyrics.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

/** What a saved or hand-edited settings file is made into on load: timing, card size, colours and empty entries. */
class ConfigLoadFixTest {
	private static SkyLyricsConfig load(String json) {
		return SkyLyricsConfig.parse("{" + json + "}").config();
	}

	@Test
	void theGlobalTimingIsHeldBetweenMinusAndPlusFiveSeconds() {
		assertEquals(-5000, load("\"syncOffsetMs\": -99999").syncOffsetMs);
		assertEquals(5000, load("\"syncOffsetMs\": 5001").syncOffsetMs);
		assertEquals(-5000, load("\"syncOffsetMs\": -5000").syncOffsetMs);
		assertEquals(1234, load("\"syncOffsetMs\": 1234").syncOffsetMs);
	}

	@Test
	void eachSongsTimingIsHeldBetweenMinusAndPlusFiveSeconds() {
		SkyLyricsConfig c = load("\"songOffsets\": {\"a - late\": 99999, \"a - early\": -99999, \"a - fine\": 250}");
		assertEquals(5000, c.songOffsets.get("a - late"));
		assertEquals(-5000, c.songOffsets.get("a - early"));
		assertEquals(250, c.songOffsets.get("a - fine"));
	}

	@Test
	void aSongWithNoTimingOrAnEmptyOneIsForgotten() {
		SkyLyricsConfig c = load("\"songOffsets\": {\"a - zero\": 0, \"a - empty\": null, \"a - kept\": 100}");
		assertFalse(c.songOffsets.containsKey("a - zero"));
		assertFalse(c.songOffsets.containsKey("a - empty"));
		assertEquals(1, c.songOffsets.size());
	}

	@Test
	void theCardShowsOneLineOrThreeAndNothingElse() {
		assertEquals(1, load("\"cardLines\": 1").cardLines);
		assertEquals(1, load("\"cardLines\": 0").cardLines);
		assertEquals(1, load("\"cardLines\": 2").cardLines);
		assertEquals(3, load("\"cardLines\": 3").cardLines);
		assertEquals(3, load("\"cardLines\": 9").cardLines);
		assertEquals(1, load("\"cardLines\": -4").cardLines);
	}

	@Test
	void coloursKeepOnlyTheirThreeColourBytes() {
		SkyLyricsConfig c = load("\"color\": " + 0xFF123456 + ", \"glowColor\": " + 0x01ABCDEF + ", \"toneColor\": -1, \"barAccentColor\": " + 0x7F000001);
		assertEquals(0x123456, c.color);
		assertEquals(0xABCDEF, c.glowColor);
		assertEquals(0xFFFFFF, c.toneColor);
		assertEquals(0x000001, c.barAccentColor);
	}
}
