package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.beli.skylyrics.lyrics.LrcParser;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A hostile lyric line without spaces, as parsed, must not freeze the render thread when it is wrapped. */
class WrapLongLineTest {
	@Test
	void parsedLongLineWrapsFast() {
		String text = LrcParser.parseSynced("[00:01.00]" + "a".repeat(200_000)).lines().get(0).text();
		List<String> rows = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> LyricsRenderer.wrap(s -> s.length() * 6.0, text, 300f));
		assertFalse(rows.isEmpty());
	}

	/**
	 * The parser caps lines at 500, so this feeds {@code wrap} a line far longer than the parser allows, to test wrap
	 * itself. It counts the characters handed to the width measure instead of reading a clock, so a slow machine can't
	 * make it flake: measuring a growing prefix of the line at every step (a real freeze with a real font) blows far
	 * past the limit, while cutting the line row by row stays well under it.
	 */
	@Test
	void rawVeryLongLineIsNotMeasuredPrefixByPrefix() {
		int n = 50_000;
		String text = "a".repeat(n);
		long[] measured = {0};
		List<String> rows = assertTimeoutPreemptively(Duration.ofSeconds(20), () -> LyricsRenderer.wrap(s -> {
			measured[0] += s.length();
			return s.length() * 6.0;
		}, text, 300f));
		assertEquals(text, String.join("", rows));
		assertTrue(measured[0] < 150_000_000L, "the width measure was asked about " + measured[0] + " characters for a line of " + n);
	}
}
