package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SpotifyPaceTest {
	private static final long S = 1_000_000_000L;

	@Test
	void retryAfterIsSecondsClampedToOneAndThreeHundred() {
		assertEquals(7 * S, SpotifyPace.retryAfterNs("7"));
		assertEquals(7 * S, SpotifyPace.retryAfterNs(" 7 "));
		assertEquals(S, SpotifyPace.retryAfterNs("0"));
		assertEquals(S, SpotifyPace.retryAfterNs("-5"));
		assertEquals(300 * S, SpotifyPace.retryAfterNs("99999"));
	}

	@Test
	void retryAfterThatIsADateOrJunkFallsBackToTenSeconds() {
		assertEquals(10 * S, SpotifyPace.retryAfterNs(null));
		assertEquals(10 * S, SpotifyPace.retryAfterNs("Wed, 21 Oct 2026 07:28:00 GMT"));
		assertEquals(10 * S, SpotifyPace.retryAfterNs(""));
		assertEquals(10 * S, SpotifyPace.retryAfterNs("1.5"));
	}
}
