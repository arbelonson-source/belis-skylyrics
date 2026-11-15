package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The access token is reused until a minute before it expires; a short-lived one is renewed at every ask. */
class SpotifyTokenCacheTest {
	private static final String ID = "0123456789abcdef0123456789abcdef";
	private static final long SEC = 1_000_000_000L;

	/** Answers the token call with the given expires_in (or none) and every Web API call with 204. */
	private static final class Net extends HttpClient {
		final String expiresIn;
		int tokenCalls;
		int apiCalls;

		Net(String expiresIn) {
			this.expiresIn = expiresIn;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
			boolean token = request.uri().getHost().startsWith("accounts");
			if (token) {
				tokenCalls++;
			} else {
				apiCalls++;
			}
			int status = token ? 200 : 204;
			String body = token ? "{\"access_token\":\"t" + tokenCalls + "\"" + (expiresIn == null ? "" : ",\"expires_in\":" + expiresIn) + "}" : "";
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

	/** Polls {@code times} times, each one due, and says how many token calls that took. */
	private static int tokenCallsFor(Path dir, String expiresIn, int times) throws Exception {
		Path login = dir.resolve("login.json");
		Files.writeString(login, "{\"clientId\":\"" + ID + "\",\"refreshToken\":\"r\",\"scopes\":\"\"}");
		SpotifyWebSource source = new SpotifyWebSource(() -> ID, login);
		Net net = new Net(expiresIn);
		source.useHttp(net);
		for (int i = 0; i < times; i++) {
			source.settle(System.nanoTime() - 10 * SEC);
			try {
				source.poll();
			} catch (IOException e) {
				throw new AssertionError("poll failed: " + e, e);
			}
		}
		assertEquals(times, net.apiCalls, "every poll asked the Web API");
		return net.tokenCalls;
	}

	@Test
	void anHourTokenIsReusedAtEveryPoll(@TempDir Path dir) throws Exception {
		assertEquals(1, tokenCallsFor(dir, "3600", 4));
	}

	@Test
	void aMissingExpiresInCountsAsAnHour(@TempDir Path dir) throws Exception {
		assertEquals(1, tokenCallsFor(dir, null, 3));
	}

	@Test
	void aTokenGoodForTwoMinutesIsStillReused(@TempDir Path dir) throws Exception {
		// 120 s minus the 60 s early margin leaves a minute of use
		assertEquals(1, tokenCallsFor(dir, "120", 3));
	}

	@Test
	void aTokenGoodForThirtySecondsIsRenewedEveryTimeBecauseTheMarginIsLonger(@TempDir Path dir) throws Exception {
		assertEquals(3, tokenCallsFor(dir, "30", 3));
	}

	@Test
	void aTokenGoodForExactlyTheMarginIsAlreadyStale(@TempDir Path dir) throws Exception {
		assertEquals(2, tokenCallsFor(dir, "60", 2));
	}
}
