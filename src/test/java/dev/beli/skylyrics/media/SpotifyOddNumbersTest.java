package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * A number in a Spotify answer that no whole number can hold (30 digits, or 1e999999999, which newer Gson refuses
 * outright) reads as missing, like a number that isn't there: the song or page around it still counts.
 */
class SpotifyOddNumbersTest {
	private static final String HUGE = "9".repeat(30);

	@Test
	void aPlayingAnswerWithAnUnholdableNumberStillGivesTheSong() {
		for (String odd : List.of(HUGE, "-" + HUGE, "1e999999999")) {
			String body = "{\"is_playing\":true,\"progress_ms\":" + odd + ",\"item\":{\"name\":\"A Made-Up Song\",\"uri\":\"spotify:track:abc\","
					+ "\"duration_ms\":200000,\"artists\":[{\"name\":\"Made Up Band\"}],\"album\":{\"name\":\"Made Up Album\","
					+ "\"images\":[{\"url\":\"https://x/a\",\"width\":" + odd + "}]}}}";
			Snapshot s = SpotifyWebSource.parse(body, 0);
			assertEquals("A Made-Up Song", s.track().title(), odd);
			assertEquals(200_000, s.track().lengthMs(), odd);
			assertEquals(0, s.positionMs(), odd);
			assertEquals("https://x/a", s.track().artUrl(), odd);
		}
	}

	@Test
	void aLibraryPageWithAnUnholdableNumberKeepsItsSongs() {
		String body = "{\"tracks\":{\"items\":[{\"id\":\"abc\",\"uri\":\"spotify:track:abc\",\"name\":\"A Made-Up Song\",\"duration_ms\":" + HUGE
				+ ",\"artists\":[{\"name\":\"Made Up Band\"}]}],\"total\":" + HUGE + ",\"offset\":0}}";
		SpotifyLibrary.Page<LibraryItem> page = SpotifyLibrary.parseSearch(body);
		assertEquals(1, page.items().size());
		assertEquals(0, page.items().get(0).lengthMs());
	}
}
