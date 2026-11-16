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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The library's one background thread must not keep Play waiting behind searches, and dropped searches must not run. */
class SpotifyLibraryQueueTest {
	private static final String ID = "0123456789abcdef0123456789abcdef";

	/** A reply: status, Retry-After seconds (or -1) and body. */
	private record Reply(int status, int retryAfter, String body) {
		static Reply ok(String body) {
			return new Reply(200, -1, body);
		}
	}

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
			String what = request.method() + " " + request.uri().getPath() + (request.uri().getRawQuery() == null ? "" : "?" + request.uri().getRawQuery());
			Reply reply;
			if (token) {
				reply = Reply.ok("{\"access_token\":\"fresh\",\"expires_in\":3600}");
			} else {
				requests.add(what);
				reply = api.apply(what);
			}
			Reply r = reply;
			HttpHeaders headers = HttpHeaders.of(r.retryAfter < 0 ? Map.of() : Map.of("Retry-After", List.of(String.valueOf(r.retryAfter))), (a, b) -> true);
			return (HttpResponse<T>) new HttpResponse<String>() {
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

	private static SpotifyWebSource signedIn(Path dir, Net net) throws IOException {
		Path login = dir.resolve("login.json");
		Files.writeString(login, "{\"clientId\":\"" + ID + "\",\"refreshToken\":\"r\",\"scopes\":\"\"}");
		SpotifyWebSource source = new SpotifyWebSource(() -> ID, login);
		source.useHttp(net);
		return source;
	}

	private static final LibraryItem SONG = new LibraryItem("a", "spotify:track:a", "A", "", "", "", 0, "");

	@Test
	void playDoesNotWaitBehindARateLimitedSearch(@TempDir Path dir) throws Exception {
		Net net = new Net(what -> what.startsWith("GET") ? new Reply(429, 30, "") : new Reply(204, -1, ""));
		SpotifyLibrary library = new SpotifyLibrary(signedIn(dir, net));
		library.search("one", 0); // sleeps out its 30 s Retry-After on the search thread
		library.search("two", 0);
		CompletableFuture<String> play = library.play(SONG, List.of());
		assertEquals("", play.get(2, TimeUnit.SECONDS));
	}

	@Test
	void aSearchWhoseShelfWasDroppedMakesNoRequest(@TempDir Path dir) throws Exception {
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Net net = new Net(what -> {
			if (what.contains("q=first")) {
				started.countDown();
				try {
					release.await(10, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			return Reply.ok("{\"tracks\":{\"total\":0,\"offset\":0,\"items\":[]}}");
		});
		SpotifyLibrary library = new SpotifyLibrary(signedIn(dir, net));
		library.search("first", 0);
		assertTrue(started.await(5, TimeUnit.SECONDS));
		CompletableFuture<?> dropped = library.search("second", 0);
		CompletableFuture<?> kept = library.search("third", 0);
		dropped.cancel(true);
		release.countDown();
		kept.get(5, TimeUnit.SECONDS);
		assertTrue(net.requests.stream().anyMatch(r -> r.contains("q=third")));
		assertFalse(net.requests.stream().anyMatch(r -> r.contains("q=second")), net.requests.toString());
	}

	@Test
	void ifSpotifyCantListDevicesTheAppOnThisComputerIsStillTried(@TempDir Path dir) throws Exception {
		Net net = new Net(what -> what.startsWith("PUT") ? new Reply(404, -1, "") : new Reply(500, -1, ""));
		AtomicBoolean opened = new AtomicBoolean();
		SpotifyLibrary library = new SpotifyLibrary(signedIn(dir, net), uri -> {
			opened.set(true);
			return true;
		});
		assertEquals("", library.play(SONG, List.of()).get(5, TimeUnit.SECONDS));
		assertTrue(opened.get());
	}
}
