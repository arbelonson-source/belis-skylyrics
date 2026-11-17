package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
