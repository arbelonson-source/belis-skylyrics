package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.Fuzz;

/**
 * Spotify's Web API answers (what's playing, library pages, devices, errors, sign-in tokens), its headers and the
 * browser's sign-in callback, damaged every way JSON can be: none of it may throw anything but the IOException that
 * means "failed, try later" out of the reader, or give a song the rest of the mod can't show.
 */
class SpotifyFuzzTest {
	private static final Duration BOUND = Duration.ofSeconds(20);
	private static final String ID = "0123456789abcdef0123456789abcdef";

	static final String PLAYING = "{\"is_playing\":true,\"progress_ms\":12000,\"currently_playing_type\":\"track\",\"item\":{"
			+ "\"name\":\"A Made-Up Song\",\"uri\":\"spotify:track:abc123\",\"id\":\"abc123\",\"duration_ms\":200000,"
			+ "\"artists\":[{\"name\":\"Made Up Band\"},{\"name\":\"Someone\"}],\"album\":{\"name\":\"Made Up Album\","
			+ "\"images\":[{\"url\":\"https://i.scdn.co/image/a\",\"width\":640},{\"url\":\"https://i.scdn.co/image/b\",\"width\":300}]}}}";
	static final String EPISODE = "{\"is_playing\":false,\"progress_ms\":500,\"currently_playing_type\":\"episode\",\"item\":{"
			+ "\"name\":\"An Episode\",\"uri\":\"spotify:episode:e1\",\"duration_ms\":3600000,\"images\":[{\"url\":\"https://x/e\",\"width\":64}],"
			+ "\"show\":{\"name\":\"A Show\",\"publisher\":\"Someone\",\"images\":[]}}}";
	static final String TRACK = "{\"id\":\"abc123\",\"uri\":\"spotify:track:abc123\",\"name\":\"A Made-Up Song\",\"duration_ms\":200000,"
			+ "\"artists\":[{\"name\":\"Made Up Band\"}],\"album\":{\"name\":\"Made Up Album\",\"images\":[{\"url\":\"https://x/a\",\"width\":300}]}}";
	static final String SEARCH = "{\"tracks\":{\"items\":[" + TRACK + "," + TRACK.replace("abc123", "def456") + "],\"total\":2,\"offset\":0}}";
	static final String SAVED = "{\"items\":[{\"track\":" + TRACK + "},{\"track\":null}],\"total\":2,\"offset\":0}";
	static final String PLAYLIST_ITEMS = "{\"items\":[{\"item\":" + TRACK + "},{\"track\":" + TRACK + "}],\"total\":2,\"offset\":0}";
	static final String PLAYLISTS = "{\"items\":[{\"id\":\"p1\",\"uri\":\"spotify:playlist:p1\",\"name\":\"Mine\",\"owner\":{\"id\":\"me\"},"
			+ "\"collaborative\":false,\"items\":{\"total\":3},\"images\":[{\"url\":\"https://x/p\",\"width\":300}]},null],\"total\":2,\"offset\":0}";
	static final String DEVICES = "{\"devices\":[{\"id\":\"d1\",\"is_active\":false,\"is_restricted\":false,\"type\":\"Computer\",\"name\":\"PC\"},"
			+ "{\"id\":\"d2\",\"is_active\":true,\"is_restricted\":false,\"type\":\"Smartphone\"}]}";
	static final String ERROR = "{\"error\":{\"status\":403,\"message\":\"Player command failed: Premium required\",\"reason\":\"PREMIUM_REQUIRED\"}}";
	static final String TOKEN = "{\"access_token\":\"tok\",\"token_type\":\"Bearer\",\"expires_in\":3600,\"refresh_token\":\"r2\",\"scope\":\"user-read-playback-state\"}";

	private static void checkTrack(Track t) {
		for (String s : List.of(t.title(), t.artist(), t.album())) {
			assertTrue(s.length() <= Track.MAX_TEXT, "field of " + s.length());
			assertTrue(s.chars().noneMatch(c -> Character.getType(c) == Character.CONTROL), "control char in " + Fuzz.show(s));
			assertFalse(s.indexOf('§') >= 0, "formatting code in " + Fuzz.show(s));
		}
	}

	private static void checkItems(SpotifyLibrary.Page<LibraryItem> page) {
		for (LibraryItem item : page.items()) {
			for (String s : List.of(item.title(), item.artists(), item.album())) {
				assertTrue(s.length() <= Track.MAX_TEXT, "field of " + s.length());
				assertFalse(s.indexOf('§') >= 0, "formatting code in " + Fuzz.show(s));
			}
		}
	}

	@Test
	void anyPlayingAnswerIsASongOrNothing() {
		assertTimeoutPreemptively(BOUND, () -> Fuzz.cases(20, 2000, (f, i) -> {
			String body = f.damagedText(f.random.nextBoolean() ? PLAYING : EPISODE, 0.2);
			Snapshot s;
			try {
				s = SpotifyWebSource.parse(body, 0);
			} catch (RuntimeException e) {
				throw new AssertionError(e + " for " + Fuzz.show(body), e);
			}
			checkTrack(s.track());
			assertTrue(s.positionMs() >= 0, "position " + s.positionMs());
			assertTrue(s.track().lengthMs() <= 0 || s.positionMs() <= s.track().lengthMs(), "past the end");
		}));
	}

	@Test
	void anyLibraryAnswerIsAPageOrNothing() {
		assertTimeoutPreemptively(BOUND, () -> Fuzz.cases(21, 2000, (f, i) -> {
			String which = new String[] {SEARCH, SAVED, PLAYLIST_ITEMS, PLAYLISTS, DEVICES, ERROR}[i % 6];
			String body = f.damagedText(which, 0.2);
			try {
				switch (i % 6) {
					case 0 -> checkItems(SpotifyLibrary.parseSearch(body));
					case 1 -> checkItems(SpotifyLibrary.parseSaved(body));
					case 2 -> checkItems(SpotifyLibrary.parsePlaylistItems(body, "spotify:playlist:p1"));
					case 3 -> SpotifyLibrary.parsePlaylists(body, f.random.nextBoolean() ? "me" : "");
					case 4 -> SpotifyLibrary.firstDevice(body);
					default -> {
						SpotifyWebSource.ownerNeedsPremium(body);
						SpotifyWebSource.forbiddenText(body);
						SpotifyLibrary.outcome(new int[] {200, 403, 404, 429, 500, 0, -1}[f.random.nextInt(7)], body, "spotify:track:x", u -> f.random.nextBoolean());
					}
				}
			} catch (RuntimeException e) {
				throw new AssertionError(e + " for " + Fuzz.show(body), e);
			}
		}));
	}

	@Test
	void anyRetryAfterIsABoundedWait() {
		assertTimeoutPreemptively(BOUND, () -> Fuzz.cases(22, 2000, (f, i) -> {
			String h = switch (f.random.nextInt(4)) {
				case 0 -> null;
				case 1 -> Long.toString(f.random.nextLong());
				case 2 -> f.mutate("Wed, 21 Oct 2026 07:28:00 GMT");
				default -> f.nasty(12);
			};
			long ns = SpotifyPace.retryAfterNs(h);
			assertTrue(ns >= 1_000_000_000L && ns <= 300_000_000_000L, h + " -> " + ns);
			assertTrue(SpotifyLibrary.waitSeconds(h) >= 0, h + " -> negative");
		}));
	}

	@Test
	void anySignInCallbackOrPasteIsReadOrRefused() {
		assertTimeoutPreemptively(BOUND, () -> Fuzz.cases(23, 2000, (f, i) -> {
			String line = f.random.nextBoolean() ? f.mutate("GET /callback?code=abc%20d&state=xyz&error=access_denied HTTP/1.1") : f.nasty(80);
			SpotifyWebSource.callbackQuery(line);
			byte[] request = (line + "\r\n" + f.nasty(40) + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
			SpotifyWebSource.requestLine(new ByteArrayInputStream(f.random.nextBoolean() ? request : f.bytes(30_000)));
			String pasted = f.random.nextBoolean() ? f.mutate("Client ID " + ID + " ") : f.nasty(100);
			String id = SpotifyWebSource.pastedClientId(pasted);
			assertTrue(id.length() <= 64, "kept " + id.length());
			SpotifyWebSource.validClientId(id);
			SpotifyWebSource.covers(f.nasty(20), f.nasty(20));
		}));
	}

	/** Answers the sign-in server and the Web API with what the test says. */
	private static final class FakeSpotify extends HttpClient {
		volatile Function<HttpRequest, Object[]> answer; // {status, body}
		int tokenCalls;
		int apiCalls;

		@Override
		@SuppressWarnings("unchecked")
		public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
			if (request.uri().getHost().startsWith("accounts")) {
				tokenCalls++;
			} else {
				apiCalls++;
			}
			Object[] a = answer.apply(request);
			int status = (Integer) a[0];
			String body = (String) a[1];
			Map<String, List<String>> headers = a.length > 2 && a[2] != null ? Map.of("Retry-After", List.of((String) a[2])) : Map.of();
			return (HttpResponse<T>) new HttpResponse<String>() {
				public int statusCode() { return status; }
				public HttpRequest request() { return request; }
				public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
				public HttpHeaders headers() { return HttpHeaders.of(headers, (x, y) -> true); }
				public String body() { return body; }
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

	private static final long SEC = 1_000_000_000L;

	/**
	 * Polls a signed-in source a few times against damaged answers: each poll gives a song, nothing, or an IOException.
	 * Most cases save a new sign-in: the save is not forced onto the disk here (it is in the game), and each case has
	 * its own time limit, so a slow or stalling disk (twice on GitHub's Windows machines) can't run the test out of time.
	 */
	@Test
	void anySignInOrPlayingAnswerIsAReadingOrAFailedRead(@TempDir Path dir) {
		Fuzz.cases(24, 400, BOUND, (f, i) -> {
			Path login = dir.resolve("login" + i + ".json");
			Files.writeString(login, "{\"clientId\":\"" + ID + "\",\"refreshToken\":\"r\",\"scopes\":\"\"}");
			SpotifyWebSource source = new SpotifyWebSource(() -> ID, login);
			assertTrue(source.forceSaves, "the game's saves are forced onto the disk");
			source.forceSaves = false;
			FakeSpotify net = new FakeSpotify();
			String token = f.random.nextBoolean() ? TOKEN : f.damagedText(TOKEN, 0.3);
			int tokenStatus = f.random.nextInt(5) == 0 ? new int[] {400, 401, 429, 500}[f.random.nextInt(4)] : 200;
			net.answer = request -> request.uri().getHost().startsWith("accounts")
					? new Object[] {tokenStatus, token, f.random.nextBoolean() ? null : f.nasty(5).replaceAll("[^ -~]", "")}
					: new Object[] {f.random.nextInt(6) == 0 ? new int[] {204, 401, 403, 429, 500, 502}[f.random.nextInt(6)] : 200,
							f.damagedText(f.random.nextBoolean() ? PLAYING : EPISODE, 0.15),
							f.random.nextBoolean() ? null : Long.toString(f.random.nextLong())};
			source.useHttp(net);
			int polls = 3;
			for (int p = 0; p < polls; p++) {
				source.settle(System.nanoTime() - 400 * SEC); // due, whatever the last answer asked for
				try {
					Snapshot s = source.poll();
					checkTrack(s.track());
					assertTrue(s.positionMs() >= 0);
				} catch (IOException failedRead) {
					// said in words, and backed off
				} catch (RuntimeException e) {
					throw new AssertionError(e + " for token " + Fuzz.show(token), e);
				}
			}
			assertTrue(net.tokenCalls <= 2 * polls, "token asked " + net.tokenCalls + " times in " + polls + " polls");
		});
	}

	/** Each case has its own time limit: a slow or stalling disk can't run the test out of time, a case that hangs still fails. */
	@Test
	void aDamagedLoginFileIsSignedOutOrSignedIn(@TempDir Path dir) {
		String good = "{\"clientId\":\"" + ID + "\",\"refreshToken\":\"r\",\"scopes\":\"user-read-playback-state\"}";
		Fuzz.cases(25, 300, BOUND, (f, i) -> {
			Path login = dir.resolve("login" + i + ".json");
			Files.write(login, f.random.nextInt(4) == 0 ? f.bytes(500) : f.damagedText(good, 0.3).getBytes(StandardCharsets.UTF_8));
			SpotifyWebSource source = new SpotifyWebSource(() -> ID, login);
			source.account();
			source.canBrowse();
		});
	}
}
