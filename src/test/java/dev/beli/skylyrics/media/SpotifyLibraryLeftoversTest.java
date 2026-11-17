package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.Tr;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What the library does with odd replies, long rate limits and failed plays. */
class SpotifyLibraryLeftoversTest {
	private static final String ID = "0123456789abcdef0123456789abcdef";

	/** A reply: status, Retry-After header text (or null) and body. */
	private record Reply(int status, String retryAfter, String body) {
		static Reply ok(String body) {
			return new Reply(200, null, body);
		}
	}

	private static class Net extends HttpClient {
		final List<String> requests = new CopyOnWriteArrayList<>();
		final Function<String, Reply> api;

		Net(Function<String, Reply> api) {
			this.api = api;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
			if (request.uri().getHost().startsWith("accounts")) {
				return (HttpResponse<T>) response(request, Reply.ok("{\"access_token\":\"fresh\",\"expires_in\":3600}"));
			}
			String what = request.method() + " " + request.uri().getPath() + (request.uri().getRawQuery() == null ? "" : "?" + request.uri().getRawQuery());
			requests.add(what);
			return (HttpResponse<T>) response(request, api.apply(what));
		}

		private static HttpResponse<String> response(HttpRequest request, Reply r) {
			HttpHeaders headers = HttpHeaders.of(r.retryAfter == null ? Map.of() : Map.of("Retry-After", List.of(r.retryAfter)), (a, b) -> true);
			return new HttpResponse<String>() {
				public int statusCode() { return r.status; }
				public HttpRequest request() { return request; }
				public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
				public HttpHeaders headers() { return headers; }
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

	private static SpotifyLibrary library(Path dir, Net net) throws IOException {
		Path login = dir.resolve("login.json");
		Files.writeString(login, "{\"clientId\":\"" + ID + "\",\"refreshToken\":\"r\",\"scopes\":\"\"}");
		SpotifyWebSource source = new SpotifyWebSource(() -> ID, login);
		source.useHttp(net);
		return new SpotifyLibrary(source);
	}

	private static Throwable failure(CompletableFuture<?> future) {
		ExecutionException e = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
		return e.getCause();
	}

	private static final String SONG = "{\"id\":\"a\",\"uri\":\"spotify:track:a\",\"name\":\"A\",\"artists\":[],\"album\":{\"name\":\"B\",\"images\":[]}}";

	// 1
	@Test
	void aSuccessReplyThatIsNotJsonIsAnErrorNotAnEndOfTheList(@TempDir Path dir) throws Exception {
		for (String body : List.of("<html>", "")) {
			SpotifyLibrary lib = library(dir, new Net(what -> Reply.ok(body)));
			Throwable error = failure(lib.liked(0));
			assertInstanceOf(IOException.class, error);
			assertEquals(Tr.t("Spotify didn't answer, try again"), SpotifyLibrary.errorText(error));
			assertInstanceOf(IOException.class, failure(lib.search("x", 0)));
			assertInstanceOf(IOException.class, failure(lib.playlists(0)));
		}
	}

	// 3
	@Test
	void aLongRateLimitFailsAtOnce(@TempDir Path dir) throws Exception {
		Net net = new Net(what -> new Reply(429, "120", ""));
		SpotifyLibrary lib = library(dir, net);
		long start = System.nanoTime();
		Throwable error = failure(lib.liked(0));
		assertTrue(System.nanoTime() - start < 5_000_000_000L);
		assertEquals(Tr.t("Spotify didn't answer, try again"), SpotifyLibrary.errorText(error));
		assertEquals(1, net.requests.size());
	}

	@Test
	void aRetryAfterDateIsReadLikeSeconds() {
		String farAway = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
				.format(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).plusHours(1));
		assertTrue(SpotifyLibrary.waitSeconds(farAway) > 30);
		String soon = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
				.format(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(5));
		long wait = SpotifyLibrary.waitSeconds(soon);
		assertTrue(wait >= 1 && wait <= 6, "wait was " + wait);
		assertEquals(3, SpotifyLibrary.waitSeconds("3"));
		assertEquals(2, SpotifyLibrary.waitSeconds(null));
		assertEquals(2, SpotifyLibrary.waitSeconds("soon"));
	}

	@Test
	void aRetryAfterDateDoesNotBecomeSomethingWentWrong(@TempDir Path dir) throws Exception {
		String farAway = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
				.format(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).plusHours(1));
		SpotifyLibrary lib = library(dir, new Net(what -> new Reply(429, farAway, "")));
		assertEquals(Tr.t("Spotify didn't answer, try again"), SpotifyLibrary.errorText(failure(lib.liked(0))));
	}

	// 5
	@Test
	void pagingGoesByTheRequestedOffsetNotTheReplys(@TempDir Path dir) throws Exception {
		String body = "{\"total\":300,\"items\":[{\"track\":" + SONG + "}]}";
		SpotifyLibrary lib = library(dir, new Net(what -> Reply.ok(body)));
		SpotifyLibrary.Page<LibraryItem> page = lib.liked(50).get(5, TimeUnit.SECONDS);
		assertEquals(50, page.offset());
		assertEquals(51, page.next());
	}

	// 6
	@Test
	void searchStopsPagingAtSpotifysCap(@TempDir Path dir) throws Exception {
		StringBuilder items = new StringBuilder();
		for (int i = 0; i < 10; i++) {
			items.append(i == 0 ? "" : ",").append(SONG);
		}
		String body = "{\"tracks\":{\"total\":5000,\"items\":[" + items + "]}}";
		SpotifyLibrary lib = library(dir, new Net(what -> Reply.ok(body)));
		assertEquals(990, lib.search("x", 980).get(5, TimeUnit.SECONDS).next());
		assertEquals(-1, lib.search("x", 990).get(5, TimeUnit.SECONDS).next());
	}

	// 7
	@Test
	void playKeepsTheSpecificReasonWhenSignedOutOrOffline(@TempDir Path dir) throws Exception {
		LibraryItem song = new LibraryItem("a", "spotify:track:a", "A", "", "", "", 0, "");
		SpotifyWebSource signedOut = new SpotifyWebSource(() -> ID, dir.resolve("none.json"));
		String out = new SpotifyLibrary(signedOut).play(song, List.of()).get(5, TimeUnit.SECONDS);
		assertEquals(Tr.t("Sign in to Spotify on the Music tab to see your songs here"), out);

		Net down = new Net(what -> {
			throw new IllegalStateException();
		}) {
			@Override
			public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
				if (request.uri().getHost().startsWith("accounts")) {
					return super.send(request, handler);
				}
				throw new java.net.ConnectException();
			}
		};
		assertEquals(Tr.t("Can't reach the internet or Spotify"), library(dir, down).play(song, List.of()).get(5, TimeUnit.SECONDS));
	}

	// 2
	@Test
	void aSongThatComesRoundOnALaterPageIsNotShownTwice() {
		LibraryItem a = new LibraryItem("a", "spotify:track:a", "A", "", "", "", 0, "");
		LibraryItem b = new LibraryItem("b", "spotify:track:b", "B", "", "", "", 0, "");
		LibraryItem c = new LibraryItem("c", "spotify:track:c", "C", "", "", "", 0, "");
		List<Object> shown = new ArrayList<>(List.of(a, b));
		assertEquals(List.of(c), SpotifyLibrary.withoutRepeats(shown, List.of(b, c)));
		assertFalse(SpotifyLibrary.withoutRepeats(shown, List.of(a, b)).contains(a));
	}
}
