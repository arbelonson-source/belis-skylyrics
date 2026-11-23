package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A number in a Spotify answer that no whole number can hold (30 digits, or 1e999999999, which newer Gson refuses
 * outright) reads as missing, like a number that isn't there: the song, page or sign-in around it still counts.
 */
class SpotifyOddNumbersTest {
	private static final String HUGE = "9".repeat(30);
	private static final String ID = "0123456789abcdef0123456789abcdef";

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

	/** Polls a signed-in source three times with this expires_in in the token answer; how many tokens that took. */
	private static int tokenCallsFor(Path dir, String expiresIn) throws Exception {
		Path login = dir.resolve("login.json");
		Files.writeString(login, "{\"clientId\":\"" + ID + "\",\"refreshToken\":\"r\",\"scopes\":\"\"}");
		SpotifyWebSource source = new SpotifyWebSource(() -> ID, login);
		SpotifyTokenCacheTest.Net net = new SpotifyTokenCacheTest.Net(expiresIn);
		source.useHttp(net);
		for (int i = 0; i < 3; i++) {
			source.settle(System.nanoTime() - 10_000_000_000L);
			source.poll();
		}
		return net.tokenCalls;
	}

	@Test
	void aTokenLifetimeOfNullOrTooBigANumberIsStillReused(@TempDir Path dir) throws Exception {
		// null is no lifetime, as a missing one (an hour). 9.3e9 s in nanoseconds is past what a long holds: the token looked
		// stale at once and was asked for again at every poll; so it was with 30 digits (wrapped round) and 1e999999999
		for (String odd : List.of("null", "9300000000", HUGE, "1e999999999")) {
			assertEquals(1, tokenCallsFor(dir, odd), "expires_in " + odd);
		}
	}

	@Test
	void aTokenLifetimeThatIsNoNumberIsAFailedReadThatBacksOff(@TempDir Path dir) throws Exception {
		// Like any sign-in answer that can't be read, the Music tab says "Couldn't read your music player" (see
		// MediaTrackerOutageTest); and the polling backs off as for a failed read, instead of asking for a token every time
		for (String odd : List.of("\"soon\"", "{}", "[]", "true")) {
			Path login = dir.resolve("login.json");
			Files.writeString(login, "{\"clientId\":\"" + ID + "\",\"refreshToken\":\"r\",\"scopes\":\"\"}");
			SpotifyWebSource source = new SpotifyWebSource(() -> ID, login);
			SpotifyTokenCacheTest.Net net = new SpotifyTokenCacheTest.Net(odd);
			source.useHttp(net);
			long asked = 0; // when the last poll began: "not due yet" is checked against it, so lost seconds can't make it due
			for (int i = 0; i < 2; i++) {
				source.settle(System.nanoTime() - 60_000_000_000L);
				asked = System.nanoTime();
				IOException e = assertThrows(IOException.class, source::poll, "expires_in " + odd);
				assertEquals("Couldn't read your music player", MediaTracker.failureText(e), "expires_in " + odd);
			}
			assertNotNull(source.failing(), "expires_in " + odd);
			// Two failures in a row wait 8 s, longer than the 4 s an idle poll waits
			assertFalse(source.pollDue(asked + 5_000_000_000L, true), "expires_in " + odd);
			assertEquals(2, net.tokenCalls, "expires_in " + odd);
		}
	}
}
