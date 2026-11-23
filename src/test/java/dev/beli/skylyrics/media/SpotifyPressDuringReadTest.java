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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A song started from the song library (on the library's own thread) while the music reader's read of Spotify is already on
 * its way (on the reader's thread). That read was asked before the press, so its answer can't show the new song: it must
 * not count as one of the quick reads after the press, or the new song shows up to 4 s late instead of within a second.
 */
class SpotifyPressDuringReadTest {
	private static final String ID = "0123456789abcdef0123456789abcdef";
	private static final long SEC = 1_000_000_000L;
	private static final LibraryItem SONG = new LibraryItem("a", "spotify:track:a", "A", "", "", "", 0, "");

	/** Spotify with nothing playing; with {@code holdNext} set, the next read of what plays waits at a gate. */
	private static final class Net extends HttpClient {
		final AtomicBoolean holdNext = new AtomicBoolean();
		final CountDownLatch asking = new CountDownLatch(1);
		final CountDownLatch release = new CountDownLatch(1);

		@Override
		@SuppressWarnings("unchecked")
		public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
			boolean token = request.uri().getHost().startsWith("accounts");
			if (!token && request.method().equals("GET") && request.uri().getPath().endsWith("/currently-playing") && holdNext.getAndSet(false)) {
				asking.countDown();
				try {
					release.await(30, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					throw new IOException(e);
				}
			}
			int status = token ? 200 : 204;
			String body = token ? "{\"access_token\":\"fresh\",\"expires_in\":3600}" : "";
			return (HttpResponse<T>) new HttpResponse<String>() {
				public int statusCode() { return status; }
				public HttpRequest request() { return request; }
				public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
				public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
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

	/** Asks Spotify once more, as the next due poll would. */
	private static Snapshot askAgain(SpotifyWebSource source) throws Exception {
		source.settle(System.nanoTime() - 10 * SEC);
		return source.poll();
	}

	@Test
	void aReadOnItsWayWhenASongIsStartedIsNotOneOfTheQuickReadsAfterIt(@TempDir Path dir) throws Exception {
		Path login = dir.resolve("login.json");
		Files.writeString(login, "{\"clientId\":\"" + ID + "\",\"refreshToken\":\"r\",\"scopes\":\"\"}");
		Net net = new Net();
		SpotifyWebSource source = new SpotifyWebSource(() -> ID, login);
		source.useHttp(net);
		source.poll(); // fetching the access token asks for one more reading at once, as after a sign-in
		// "Not due yet" is checked against a time taken before the ask, so a slow machine's lost seconds can't make it due
		long asked = System.nanoTime();
		assertTrue(askAgain(source).track().isNone());
		assertFalse(source.pollDue(asked + SEC, true), "nothing playing: asked every 4 s");

		// The reader's next read goes out, and while it waits for Spotify the player starts a song in the song library
		net.holdNext.set(true);
		source.settle(System.nanoTime() - 10 * SEC);
		Thread reader = new Thread(() -> {
			try {
				source.poll();
			} catch (Exception ignored) {
				// shown through failing(); not expected here
			}
		}, "test media");
		reader.start();
		try {
			assertTrue(net.asking.await(30, TimeUnit.SECONDS));
			assertEquals("", new SpotifyLibrary(source).play(SONG, List.of()).get(10, TimeUnit.SECONDS));
		} finally {
			net.release.countDown(); // Spotify answers the read it was asked before the press: nothing playing yet
			reader.join(30_000);
		}
		assertFalse(reader.isAlive());

		// Three quick reads after the press, as for a press between two reads
		assertTrue(source.pollDue(System.nanoTime(), true), "the new song is asked for at once");
		assertTrue(source.poll().track().isNone(), "Spotify hasn't started it yet");
		assertTrue(source.pollDue(System.nanoTime() + 3 * SEC / 2, true), "asked again a second later");
		askAgain(source);
		assertTrue(source.pollDue(System.nanoTime() + 3 * SEC / 2, true),
				"and once more a second after that: the read sent before the press was counted as one after it");
		asked = System.nanoTime();
		askAgain(source);
		assertFalse(source.pollDue(asked + 3 * SEC / 2, true), "then back to every 4 s");
	}
}
