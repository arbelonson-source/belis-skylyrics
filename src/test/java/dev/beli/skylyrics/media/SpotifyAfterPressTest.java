package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Right after a press (Play, a seek, a song started from the library) Spotify often still reports the old state for a
 * moment: paused, the last song, or nothing. The poller must then keep asking every second for a little while instead of
 * going back to its 4 s idle pace, or the lyrics stand still (or the bar stays empty) for up to 4 s while the music plays.
 * And play/pause must go by what plays now, not by a reading taken before the song was paused or resumed elsewhere.
 */
class SpotifyAfterPressTest {
	private static final String ID = "0123456789abcdef0123456789abcdef";
	private static final long SEC = 1_000_000_000L;
	private static final String PAUSED = "{\"is_playing\": false, \"progress_ms\": 60000, \"currently_playing_type\": \"track\","
			+ " \"item\": {\"name\": \"Made Up Song\", \"uri\": \"spotify:track:1\", \"duration_ms\": 200000,"
			+ " \"artists\": [{\"name\": \"Made Up Band\"}], \"album\": {\"name\": \"Album\"}}}";
	private static final LibraryItem SONG = new LibraryItem("a", "spotify:track:a", "A", "", "", "", 0, "");

	private record Reply(int status, String body) {
	}

	/** Answers each Web API request ("GET /v1/me/player/currently-playing", "PUT /v1/me/player/play") from {@code api}. */
	private static final class Net extends HttpClient {
		final List<String> requests = new CopyOnWriteArrayList<>();
		final Function<String, Reply> api;

		Net(Function<String, Reply> api) {
			this.api = api;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
			boolean token = request.uri().getHost().startsWith("accounts");
			Reply reply;
			if (token) {
				reply = new Reply(200, "{\"access_token\":\"fresh\",\"expires_in\":3600}");
			} else {
				String what = request.method() + " " + request.uri().getPath();
				requests.add(what);
				reply = api.apply(what);
			}
			Reply r = reply;
			return (HttpResponse<T>) new HttpResponse<String>() {
				public int statusCode() { return r.status; }
				public HttpRequest request() { return request; }
				public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
				public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
				public String body() { return r.body; }
				public Optional<SSLSession> sslSession() { return Optional.empty(); }
				public URI uri() { return request.uri(); }
				public Version version() { return Version.HTTP_1_1; }
			};
		}

		@Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
		@Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
		@Override public Redirect followRedirects() { return Redirect.NEVER; }
		@Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
		@Override public SSLContext sslContext() { throw new UnsupportedOperationException(); }
		@Override public SSLParameters sslParameters() { return new SSLParameters(); }
		@Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
		@Override public Version version() { return Version.HTTP_1_1; }
		@Override public Optional<Executor> executor() { return Optional.empty(); }

		@Override
		public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler,
				HttpResponse.PushPromiseHandler<T> push) {
			throw new UnsupportedOperationException();
		}
	}

	private static SpotifyWebSource signedIn(Path dir, Net net) throws IOException {
		Path login = dir.resolve("login.json");
		Files.writeString(login, "{\"clientId\":\"" + ID + "\",\"refreshToken\":\"r\",\"scopes\":\"\"}");
		SpotifyWebSource source = new SpotifyWebSource(() -> ID, login);
		source.useHttp(net);
		return source;
	}

	/** Asks Spotify once more, as the next due poll would. */
	private static Snapshot askAgain(SpotifyWebSource source) throws Exception {
		source.settle(System.nanoTime() - 10 * SEC);
		return source.poll();
	}

	/** The first reading, with the access token in hand (fetching it asks for one more reading at once, as after a sign-in). */
	private static Snapshot settled(SpotifyWebSource source) throws Exception {
		source.poll();
		return askAgain(source);
	}

	@Test
	void afterPlayIsPressedWhilePausedSpotifyIsAskedEverySecondUntilItSaysSo(@TempDir Path dir) throws Exception {
		Net net = new Net(what -> what.startsWith("GET") ? new Reply(200, PAUSED) : new Reply(204, ""));
		SpotifyWebSource source = signedIn(dir, net);
		// "Not due yet" is checked against a time taken before the ask, so a slow machine's lost seconds can't make it due
		long asked = System.nanoTime();
		assertFalse(settled(source).playing());
		assertFalse(source.pollDue(asked + 2 * SEC, true), "paused: asked every 4 s");

		source.playPause();
		assertTrue(net.requests.contains("PUT /v1/me/player/play"), net.requests.toString());
		assertTrue(source.pollDue(System.nanoTime(), true), "asked again at once");
		assertFalse(source.poll().playing(), "Spotify's first answer after the press still says paused");
		assertTrue(source.pollDue(System.nanoTime() + 3 * SEC / 2, true), "so it is asked again a second later, not 4 s later");

		// A few quick asks in all; if it really stays paused, the idle pace comes back
		askAgain(source);
		asked = System.nanoTime();
		askAgain(source);
		assertFalse(source.pollDue(asked + 3 * SEC / 2, true), "back to every 4 s");
		assertTrue(source.pollDue(System.nanoTime() + 5 * SEC, true));
	}

	@Test
	void aPressSpotifyRefusesChangesNothingSoTheIdlePaceStays(@TempDir Path dir) throws Exception {
		Net net = new Net(what -> what.startsWith("GET") ? new Reply(200, PAUSED) : new Reply(403, ""));
		SpotifyWebSource source = signedIn(dir, net);
		settled(source);
		source.playPause(); // a device Spotify won't let us control
		assertTrue(source.pollDue(System.nanoTime(), true), "the result is still read at once");
		long asked = System.nanoTime();
		source.poll();
		assertFalse(source.pollDue(asked + 3 * SEC / 2, true));
	}

	@Test
	void aSongStartedFromTheLibraryWhileNothingPlaysIsAskedForAtOnce(@TempDir Path dir) throws Exception {
		Net net = new Net(what -> new Reply(204, ""));
		SpotifyWebSource source = signedIn(dir, net);
		long asked = System.nanoTime();
		assertTrue(settled(source).track().isNone());
		assertFalse(source.pollDue(asked + SEC, true), "nothing playing: asked every 4 s");

		assertEquals("", new SpotifyLibrary(source).play(SONG, List.of()).get(10, TimeUnit.SECONDS));
		assertTrue(net.requests.contains("PUT /v1/me/player/play"), net.requests.toString());
		// The library then calls the tracker's refresh, which only reads what the source already has unless it is due
		assertTrue(source.pollDue(System.nanoTime(), true), "the new song is asked for at once");
		assertTrue(source.poll().track().isNone(), "Spotify hasn't started it yet");
		assertTrue(source.pollDue(System.nanoTime() + 3 * SEC / 2, true), "so it is asked again a second later");
	}

	@Test
	void aSongTheAppOnThisComputerStartsIsAskedForAtOnceToo(@TempDir Path dir) throws Exception {
		// Spotify's servers know no device, so the library hands the song to the Spotify app here instead
		Net net = new Net(what -> what.startsWith("PUT") ? new Reply(404, "") : new Reply(204, ""));
		SpotifyWebSource source = signedIn(dir, net);
		long asked = System.nanoTime();
		settled(source);
		assertFalse(source.pollDue(asked, true));
		assertEquals("", new SpotifyLibrary(source, uri -> true).play(SONG, List.of()).get(10, TimeUnit.SECONDS));
		assertTrue(source.pollDue(System.nanoTime(), true));
	}

	private static final String PLAYING = PAUSED.replace("\"is_playing\": false", "\"is_playing\": true");

	@Test
	void playPauseGoesByWhatPlaysNowWhenTheLastReadingIsOld(@TempDir Path dir) throws Exception {
		// Read while playing; then paused on the phone. With the lyrics off the next reading is up to 15 s away (4 s while
		// paused), and a press going by the old reading sent "pause" to a paused song: nothing happened
		boolean[] paused = {false};
		Net net = new Net(what -> what.startsWith("GET") ? new Reply(200, paused[0] ? PAUSED : PLAYING) : new Reply(204, ""));
		SpotifyWebSource source = signedIn(dir, net);
		assertTrue(settled(source).playing());
		paused[0] = true;
		source.settle(System.nanoTime() - 10 * SEC); // the reading is 10 s old
		net.requests.clear();
		source.playPause();
		assertEquals(List.of("GET /v1/me/player/currently-playing", "PUT /v1/me/player/play"), net.requests);
	}

	@Test
	void playPauseGoesByAFreshReadingWithoutAskingAgain(@TempDir Path dir) throws Exception {
		Net net = new Net(what -> what.startsWith("GET") ? new Reply(200, PLAYING) : new Reply(204, ""));
		SpotifyWebSource source = signedIn(dir, net);
		assertTrue(settled(source).playing());
		net.requests.clear();
		source.playPause();
		assertEquals(List.of("PUT /v1/me/player/pause"), net.requests);
	}

	@Test
	void playPauseStillWorksWhenTheCheckFails(@TempDir Path dir) throws Exception {
		boolean[] down = {false};
		Net net = new Net(what -> what.startsWith("GET") ? (down[0] ? new Reply(502, "") : new Reply(200, PLAYING)) : new Reply(204, ""));
		SpotifyWebSource source = signedIn(dir, net);
		assertTrue(settled(source).playing());
		down[0] = true;
		source.settle(System.nanoTime() - 10 * SEC);
		net.requests.clear();
		source.playPause();
		assertEquals("PUT /v1/me/player/pause", net.requests.getLast(), "goes by the last reading it has");
	}
}
