package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Client ID and the account are changed on the settings screen (the game's thread) while the music reader goes on
 * reading Spotify on its own thread. Its next read must be about the new setting: not the answer, or the wait after a
 * failure (4 s, doubling up to 30 s), that belonged to the old one. Otherwise the Status line on the Music tab keeps
 * saying "Paste your Client ID first" for up to half a minute after the Client ID was pasted.
 */
class SpotifySetupChangeTest {
	private static final String ID = "0123456789abcdef0123456789abcdef";
	private static final long SEC = 1_000_000_000L;

	/** Spotify with a token for anyone and nothing playing. */
	private static final class Net extends HttpClient {
		@Override
		@SuppressWarnings("unchecked")
		public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
			boolean token = request.uri().getHost().startsWith("accounts");
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

	/** What the Status line on the Music tab says about the reader right now, as the tracker words it. */
	private static String statusLine(SpotifyWebSource source) {
		Throwable failing = source.failing();
		assertNotNull(failing, "the reader reports no problem, so the Status line shows no step to do");
		return MediaTracker.failureText(failing);
	}

	/** One read, as the tracker makes every half second; a read that fails is reported through {@code failing()}. */
	private static void read(SpotifyWebSource source) {
		try {
			source.poll();
		} catch (Exception e) {
			// the tracker shows it from failing()
		}
	}

	@Test
	void aClientIdPastedWhileTheLastFailureWaitsIsReadAtOnce(@TempDir Path dir) {
		AtomicReference<String> clientId = new AtomicReference<>("");
		SpotifyWebSource source = new SpotifyWebSource(clientId::get, dir.resolve("login.json"));
		assertThrows(IOException.class, source::poll);
		assertEquals(SpotifyWebSource.notSetUp("").getMessage(), statusLine(source));

		// The player pastes the Client ID on the settings screen; the tracker reads again half a second later
		clientId.set(ID);
		read(source);
		assertEquals(SpotifyWebSource.notSetUp(ID).getMessage(), statusLine(source),
				"still telling the player to paste the Client ID they just pasted");
	}

	@Test
	void aClientIdPastedAfterAMinuteOfWaitingIsReadAtOnceToo(@TempDir Path dir) {
		AtomicReference<String> clientId = new AtomicReference<>("");
		SpotifyWebSource source = new SpotifyWebSource(clientId::get, dir.resolve("login.json"));
		// A minute on the Spotify dashboard making the app: the waits after the failure have grown to 30 s
		for (int i = 0; i < 6; i++) {
			source.settle(System.nanoTime() - 60 * SEC);
			read(source);
		}
		clientId.set(ID);
		read(source);
		assertEquals(SpotifyWebSource.notSetUp(ID).getMessage(), statusLine(source));
	}

	@Test
	void signingOutIsReadAtOnce(@TempDir Path dir) throws Exception {
		Path login = dir.resolve("login.json");
		Files.writeString(login, "{\"clientId\":\"" + ID + "\",\"refreshToken\":\"r\",\"scopes\":\"\"}");
		SpotifyWebSource source = new SpotifyWebSource(() -> ID, login);
		source.useHttp(new Net());
		source.poll(); // fetching the access token asks for one more reading at once, as after a sign-in
		source.settle(System.nanoTime() - 10 * SEC);
		source.poll(); // nothing playing, read just now
		source.signOut(); // Sign out on the settings screen
		read(source);
		assertEquals(SpotifyWebSource.notSetUp(ID).getMessage(), statusLine(source),
				"the Status line doesn't say to sign in until the next read is due");
		// The file goes on the sign-out's own thread: wait for it, or on Windows the test folder can't be removed while it's open
		assertTrue(source.awaitSaved(30_000));
	}
}
