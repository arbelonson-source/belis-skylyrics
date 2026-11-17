package dev.beli.skylyrics.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
	static final class Net extends HttpClient {
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
	void anOldJarAnotherGameAlreadyRemovedDoesNotCostTheNewJarOrAnotherGamesDownload() throws Exception {
		Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"));
		byte[] data = jarBytes();
		Path old = dir.resolve("belis-skylyrics-0.1.0+1.21.11.jar"); // the other game updated first and deleted it
		Path otherPart = Files.writeString(dir.resolve(NAME + ".part"), "half"); // and its download is still running
		Net net = new Net(data);
		var asset = new UpdateCheck.Asset(NAME, URL, sha(data));
		assertTrue(Updater.install(net, old, asset));
		assertTrue(Files.exists(dir.resolve(NAME)), "the new jar stays");
		assertEquals("half", Files.readString(otherPart));
		try (var files = Files.list(dir)) {
			assertEquals(java.util.Set.of(NAME, NAME + ".part"), files.map(f -> f.getFileName().toString()).collect(java.util.stream.Collectors.toSet()));
		}
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

	private static final String LATEST_JSON = "{\"tag_name\":\"v0.2.0\",\"assets\":[{\"name\":\"" + NAME
			+ "\",\"browser_download_url\":\"" + URL + "\"}]}";

	@Test
	void anInstalledJarKeepsTheOldJarsPermissions() throws Exception {
		Assumptions.assumeTrue(dir.getFileSystem().supportedFileAttributeViews().contains("posix"), "needs file permissions");
		byte[] data = jarBytes();
		Path old = Files.createFile(dir.resolve("belis-skylyrics-0.1.0+1.21.11.jar"));
		Files.setPosixFilePermissions(old, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
		var asset = new UpdateCheck.Asset(NAME, URL, sha(data));
		Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"));
		assertTrue(Updater.install(new Net(data), old, asset));
		assertEquals("rw-r--r--", java.nio.file.attribute.PosixFilePermissions.toString(
				Files.getPosixFilePermissions(dir.resolve(NAME))));
	}

	@Test
	void withoutAPlainJarNotifyAndInstallBothTellAndInstallDownloadsNothing() throws Exception {
		for (Updates mode : new Updates[] {Updates.NOTIFY, Updates.INSTALL}) {
			Net net = new Net(jarBytes());
			Updater.Notice n = Updater.look(mode, () -> LATEST_JSON, net, null, "0.1.0+1.21.11", "1.21.11", null, id -> null);
			assertNotNull(n, mode.toString());
			assertEquals("0.2.0", n.version());
			assertFalse(n.downloaded());
			assertEquals(0, net.requests.size());
		}
	}

	@Test
	void offAsksForNothing() throws Exception {
		Net net = new Net(jarBytes());
		assertNull(Updater.look(Updates.OFF, () -> {
			throw new AssertionError("no request when off");
		}, net, null, "0.1.0+1.21.11", "1.21.11", null, id -> null));
		assertEquals(0, net.requests.size());
	}

	/** A jar whose fabric.mod.json is right but padded with a few MB of spaces: a tiny file that inflates. */
	private static byte[] bloatedJar(int spaces) throws IOException {
		java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		try (ZipOutputStream out = new ZipOutputStream(bytes)) {
			out.putNextEntry(new ZipEntry("fabric.mod.json"));
			out.write("{\"id\":\"skylyrics\",\"version\":\"0.2.0+1.21.11\"}".getBytes(StandardCharsets.UTF_8));
			out.write(" ".repeat(spaces).getBytes(StandardCharsets.UTF_8));
			out.closeEntry();
		}
		return bytes.toByteArray();
	}

	@Test
	void aModJsonThatInflatesPastTheCapIsNotValid() throws Exception {
		Path jar = Files.write(dir.resolve("bloat.jar"), bloatedJar(3 * 1024 * 1024));
		assertTrue(Files.size(jar) < 20_000, "the file itself is small");
		assertFalse(Updater.valid(jar, null, "0.2.0+1.21.11"));
		assertFalse(Updater.valid(jar, sha(Files.readAllBytes(jar)), "0.2.0+1.21.11"));
		assertThrows(IOException.class, () -> Updater.dependsOf(jar));
		Path fine = Files.write(dir.resolve("fine.jar"), bloatedJar(1000));
		assertTrue(Updater.valid(fine, null, "0.2.0+1.21.11"));
	}

	@Test
	void theChecksumIsCheckedBeforeAnythingIsParsed() throws Exception {
		Path jar = Files.write(dir.resolve("bad.jar"), bloatedJar(10));
		// Not a zip at all would throw if it were opened; a wrong checksum has to answer false first
		Path junk = Files.write(dir.resolve("junk.jar"), new byte[] {1, 2, 3});
		assertFalse(Updater.valid(junk, "0".repeat(64), null));
		assertFalse(Updater.valid(jar, "0".repeat(64), null));
	}

	@Test
	void aSwapThatFailsAtTheSecondStepKeepsTheOldJarAndLeavesNoSecondOne() throws Exception {
		Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"));
		byte[] data = jarBytes();
		Path old = Files.writeString(dir.resolve("belis-skylyrics-0.1.0+1.21.11.jar"), "old");
		int[] calls = {0};
		dev.beli.skylyrics.util.SafeFile.Mover mover = (from, to, options) -> {
			if (++calls[0] == 2) {
				throw new IOException("disk full");
			}
			Files.move(from, to, options);
		};
		var asset = new UpdateCheck.Asset(NAME, URL, sha(data));
		assertThrows(IOException.class, () -> Updater.install(new Net(data), old, asset, id -> null, mover));
		assertEquals("old", Files.readString(old), "the old jar is back");
		try (var files = Files.list(dir)) {
			assertEquals(List.of(old.getFileName().toString()), files.map(f -> f.getFileName().toString()).toList());
		}
	}

	@Test
	void anotherGameInstallingTheSameUpdateFirstLeavesOnlyTheNewJar() throws Exception {
		Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"));
		byte[] data = jarBytes();
		Path old = Files.writeString(dir.resolve("belis-skylyrics-0.1.0+1.21.11.jar"), "old");
		Path target = dir.resolve(NAME);
		int[] calls = {0};
		dev.beli.skylyrics.util.SafeFile.Mover mover = (from, to, options) -> {
			if (++calls[0] == 2) {
				// The other game's install lands between our two moves (its own set-aside found nothing to move)
				Files.writeString(target, "theirs");
			}
			Files.move(from, to, options);
		};
		assertFalse(Updater.install(new Net(data), old, new UpdateCheck.Asset(NAME, URL, sha(data)), id -> null, mover));
		assertEquals("theirs", Files.readString(target));
		try (var files = Files.list(dir)) {
			assertEquals(List.of(NAME), files.map(f -> f.getFileName().toString()).toList(), "no old jar next to the new one");
		}
	}

	@Test
	void theSwapRefusesPathsPowerShellCannotOpen() {
		Path root = dir.toAbsolutePath();
		String oldName = "belis-skylyrics-0.1.0+1.21.11.jar";
		// a folder name that brings the staged name (target + .19 digits + .new) to exactly the limit, and one character over
		int room = Updater.MAX_SWAP_PATH - 1 - (root.toString().length() + 1 + NAME.length() + 24 + 1);
		Path fits = root.resolve("d".repeat(room));
		Path over = root.resolve("d".repeat(room + 1));
		assertTrue(Updater.swapPathsSafe(fits.resolve(oldName), fits.resolve(NAME)));
		assertFalse(Updater.swapPathsSafe(over.resolve(oldName), over.resolve(NAME)));
		assertFalse(Updater.swapPathsSafe(root.resolve("x".repeat(300)).resolve(oldName), root.resolve("x".repeat(300)).resolve(NAME)));
		assertTrue(Updater.swapPathsSafe(root.resolve(oldName), root.resolve(NAME)));
	}

	@Test
	void theOldJarIsSetAsideBeforeTheNewOneTakesItsName() throws Exception {
		Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"));
		byte[] data = jarBytes();
		Path old = Files.writeString(dir.resolve("belis-skylyrics-0.1.0+1.21.11.jar"), "old");
		List<String> seen = new java.util.ArrayList<>();
		dev.beli.skylyrics.util.SafeFile.Mover mover = (from, to, options) -> {
			seen.add(to.getFileName().toString());
			Files.move(from, to, options);
		};
		assertTrue(Updater.install(new Net(data), old, new UpdateCheck.Asset(NAME, URL, sha(data)), id -> null, mover));
		assertEquals(List.of(old.getFileName() + ".skylyrics-old", NAME), seen);
		try (var files = Files.list(dir)) {
			assertEquals(List.of(NAME), files.map(f -> f.getFileName().toString()).toList());
		}
	}

	@Test
	void aReleaseReplyOverOneMegabyteIsIgnored() throws Exception {
		assertNull(Updater.readReply(new ByteArrayInputStream(new byte[1024 * 1024 + 1])));
		assertEquals("{}", Updater.readReply(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8))));
		assertEquals(1024 * 1024, Updater.readReply(new ByteArrayInputStream(" ".repeat(1024 * 1024).getBytes(StandardCharsets.UTF_8))).length());
	}

	@Test
	void aTagNameShownToThePlayerHasNoControlCharactersOrSectionSigns() {
		var release = UpdateCheck.parse("{\"tag_name\":\"v0.2.0\\u00a7c\\n\\u001b[0mX\"}");
		assertEquals("0.2.0cX", release.version().replace("[0m", ""));
		assertFalse(release.version().contains("\u00a7"));
		assertFalse(release.version().chars().anyMatch(Character::isISOControl));
	}
}
