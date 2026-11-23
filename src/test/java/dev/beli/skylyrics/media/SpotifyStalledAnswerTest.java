package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Spotify's answer stops half way: hotel Wi-Fi drops for a moment, a phone hotspot changes its address, a proxy stalls.
 * The request's own time limit ends when the headers are in, so without a limit on the rest the poll would wait on the
 * silent connection until the server gave up (or for ever): no song change and no button press gets through meanwhile.
 * Runs Java's real HttpClient against a server on this computer; nothing is sent to Spotify.
 */
class SpotifyStalledAnswerTest {
	private static final String ID = "0123456789abcdef0123456789abcdef";

	private HttpServer server;
	private final CountDownLatch end = new CountDownLatch(1);

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(Executors.newCachedThreadPool());
		server.createContext("/api/token", ex -> reply(ex, "{\"access_token\":\"fresh\",\"expires_in\":3600}"));
		server.createContext("/v1/me/player/currently-playing", ex -> {
			// The headers and the start of the song come, then nothing more
			ex.sendResponseHeaders(200, 5000);
			OutputStream out = ex.getResponseBody();
			out.write("{\"is_playing\": true, \"item\": {\"name\": \"Made Up".getBytes(StandardCharsets.UTF_8));
			out.flush();
			try {
				end.await(60, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			ex.close();
		});
		server.start();
	}

	@AfterEach
	void stop() {
		end.countDown();
		server.stop(0);
	}

	private static void reply(HttpExchange ex, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().add("Content-Type", "application/json");
		ex.sendResponseHeaders(200, bytes.length);
		try (OutputStream out = ex.getResponseBody()) {
			out.write(bytes);
		}
	}

	@Test
	void anAnswerThatStopsHalfWayEndsThePollAsANetworkProblem(@TempDir Path dir) throws IOException {
		Path login = dir.resolve("login.json");
		Files.writeString(login, "{\"clientId\":\"" + ID + "\",\"refreshToken\":\"r\",\"scopes\":\"\"}");
		SpotifyWebSource source = new SpotifyWebSource(() -> ID, login);
		source.useHttp(new ToLocal(server.getAddress().getPort()));
		source.bodyLimit = Duration.ofMillis(500);

		IOException failure = assertTimeoutPreemptively(Duration.ofSeconds(15), () -> assertThrows(IOException.class, source::poll),
				"the poll waits on the silent connection");
		assertTrue(MediaTracker.networkFailure(failure), "the Status line says the internet is the problem: " + failure);
	}

	/** Sends Spotify's requests to the server here instead, through a real HttpClient, so Java's own reading of the answer runs. */
	private static final class ToLocal extends HttpClient {
		private final HttpClient real = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
		private final int port;

		ToLocal(int port) {
			this.port = port;
		}

		private HttpRequest local(HttpRequest request) {
			URI u = request.uri();
			URI to = URI.create("http://127.0.0.1:" + port + u.getRawPath() + (u.getRawQuery() == null ? "" : "?" + u.getRawQuery()));
			return HttpRequest.newBuilder(request, (name, value) -> true).uri(to).build();
		}

		@Override
		public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException, InterruptedException {
			return real.send(local(request), handler);
		}

		@Override
		public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
			return real.sendAsync(local(request), handler);
		}

		@Override
		public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler,
				HttpResponse.PushPromiseHandler<T> push) {
			return real.sendAsync(local(request), handler, push);
		}

		@Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
		@Override public Optional<Duration> connectTimeout() { return real.connectTimeout(); }
		@Override public Redirect followRedirects() { return real.followRedirects(); }
		@Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
		@Override public SSLContext sslContext() { return real.sslContext(); }
		@Override public SSLParameters sslParameters() { return real.sslParameters(); }
		@Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
		@Override public Version version() { return real.version(); }
		@Override public Optional<Executor> executor() { return Optional.empty(); }
	}
}
