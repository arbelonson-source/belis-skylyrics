package dev.beli.skylyrics.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.config.SkyLyricsConfig.Updates;

/** The checksum guard on a downloaded jar, and that "tell me" mode never downloads. */
class UpdaterChecksTest {
	private static final String NAME = "belis-skylyrics-0.2.0+1.21.11.jar";
	private static final String URL = "https://github.com/arbelonson-source/belis-skylyrics/releases/download/v0.2.0/" + NAME;

	@TempDir
	Path dir;

	private static byte[] jarBytes() throws IOException {
		java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		try (ZipOutputStream out = new ZipOutputStream(bytes)) {
			out.putNextEntry(new ZipEntry("fabric.mod.json"));
			out.write("{\"id\":\"skylyrics\",\"version\":\"0.2.0+1.21.11\"}".getBytes(StandardCharsets.UTF_8));
			out.closeEntry();
		}
		return bytes.toByteArray();
	}

	private static String sha(byte[] data) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
	}

	/** A network that answers every request with the same 200 body and counts the requests. */
	private static final class Net extends HttpClient {
		final byte[] body;
		final List<String> requests = new java.util.ArrayList<>();

		Net(byte[] body) {
			this.body = body;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
			requests.add(request.uri().toString());
			return (HttpResponse<T>) new HttpResponse<InputStream>() {
				public int statusCode() { return 200; }
				public HttpRequest request() { return request; }
				public Optional<HttpResponse<InputStream>> previousResponse() { return Optional.empty(); }
				public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
				public InputStream body() { return new ByteArrayInputStream(body); }
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

	@Test
	void validRejectsAChecksumThatDoesNotMatchAndAcceptsOneThatDoes() throws Exception {
		byte[] data = jarBytes();
		Path jar = Files.write(dir.resolve("a.jar"), data);
		assertTrue(Updater.valid(jar, sha(data), "0.2.0+1.21.11"));
		assertFalse(Updater.valid(jar, sha(new byte[] {1, 2, 3}), "0.2.0+1.21.11"));
		assertFalse(Updater.valid(jar, "0".repeat(64), "0.2.0+1.21.11"));
		assertTrue(Updater.valid(jar, null, "0.2.0+1.21.11"), "no published checksum: the mod id and version check is what is left");
	}

	@Test
	void aDownloadWithTheWrongChecksumIsDiscardedAndTheOldJarStays() throws Exception {
		Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"));
		byte[] data = jarBytes();
		Path old = Files.createFile(dir.resolve("belis-skylyrics-0.1.0+1.21.11.jar"));
		Net net = new Net(data);
		var asset = new UpdateCheck.Asset(NAME, URL, sha(new byte[] {9, 9, 9}));
		assertFalse(Updater.install(net, old, asset));
		assertEquals(1, net.requests.size());
		assertTrue(Files.exists(old));
		assertFalse(Files.exists(dir.resolve(NAME)));
		assertFalse(Files.exists(dir.resolve(NAME + ".part")), "the unchecked download is deleted");
	}

	@Test
	void aDownloadWithTheRightChecksumReplacesTheOldJar() throws Exception {
		Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"));
		byte[] data = jarBytes();
		Path old = Files.createFile(dir.resolve("belis-skylyrics-0.1.0+1.21.11.jar"));
		Net net = new Net(data);
		var asset = new UpdateCheck.Asset(NAME, URL, sha(data));
		assertTrue(Updater.install(net, old, asset));
		assertFalse(Files.exists(old));
		assertTrue(Files.exists(dir.resolve(NAME)));
		assertFalse(Files.exists(dir.resolve(NAME + ".part")));
	}

	@Test
	void notifyModeMakesNoDownloadRequestAndOnlyTellsThePlayer() throws Exception {
		Path old = Files.createFile(dir.resolve("belis-skylyrics-0.1.0+1.21.11.jar"));
		Net net = new Net(jarBytes());
		var asset = new UpdateCheck.Asset(NAME, URL, null);
		var release = new UpdateCheck.Release("0.2.0", List.of(asset));
		Updater.Notice notice = Updater.update(Updates.NOTIFY, net, old, release, asset);
		assertEquals(0, net.requests.size());
		assertEquals("0.2.0", notice.version());
		assertFalse(notice.downloaded());
		assertTrue(Files.exists(old));
		assertFalse(Files.exists(dir.resolve(NAME)));
	}

	@Test
	void installModeDoesDownloadSoTheNotifyTestAboveMeansSomething() throws Exception {
		Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"));
		byte[] data = jarBytes();
		Path old = Files.createFile(dir.resolve("belis-skylyrics-0.1.0+1.21.11.jar"));
		Net net = new Net(data);
		var asset = new UpdateCheck.Asset(NAME, URL, sha(data));
		var release = new UpdateCheck.Release("0.2.0", List.of(asset));
		Updater.Notice notice = Updater.update(Updates.INSTALL, net, old, release, asset);
		assertEquals(1, net.requests.size());
		assertTrue(notice.downloaded());
	}
}
