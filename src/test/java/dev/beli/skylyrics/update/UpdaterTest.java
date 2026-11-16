package dev.beli.skylyrics.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.config.SkyLyricsConfig.Updates;

class UpdaterTest {
	@TempDir
	Path dir;

	private static Path zip(Path file, String modJson) throws IOException {
		try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
			out.putNextEntry(new ZipEntry("fabric.mod.json"));
			out.write(modJson.getBytes(StandardCharsets.UTF_8));
			out.closeEntry();
		}
		return file;
	}

	@Test
	void failedDownloadStillTellsThePlayer() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		// Promises a body and then cuts the connection
		server.createContext("/", ex -> {
			ex.sendResponseHeaders(200, 1000);
			ex.getResponseBody().write(new byte[10]);
			ex.close();
		});
		server.start();
		try {
			Path old = Files.createFile(dir.resolve("belis-skylyrics-0.1.0+1.21.11.jar"));
			var asset = new UpdateCheck.Asset("belis-skylyrics-0.2.0+1.21.11.jar",
					"http://127.0.0.1:" + server.getAddress().getPort() + "/a.jar", null);
			var release = new UpdateCheck.Release("0.2.0", List.of(asset));
			Updater.Notice n = Updater.update(Updates.INSTALL, HttpClient.newHttpClient(), old, release, asset);
			assertNotNull(n);
			assertEquals("0.2.0", n.version());
			assertFalse(n.downloaded());
			assertTrue(Files.exists(old));
		} finally {
			server.stop(0);
		}
	}

	@Test
	void nonAsciiPathsSkipTheBatchSwap() throws Exception {
		Path home = dir.resolve("Jos\u00e9").resolve("mods");
		Files.createDirectories(home);
		Path old = Files.createFile(home.resolve("belis-skylyrics-0.1.0+1.21.11.jar"));
		Path part = Files.createFile(home.resolve("belis-skylyrics-0.2.0+1.21.11.jar.part"));
		Path target = home.resolve("belis-skylyrics-0.2.0+1.21.11.jar");
		assertFalse(Updater.installLater(old, part, target));
		assertTrue(Files.exists(part), "nothing is moved when the swap is refused");
		assertFalse(Updater.batchSafe("C:\\Users\\Jos\u00e9\\mods"));
		assertFalse(Updater.batchSafe("C:\\a&b"));
		assertTrue(Updater.batchSafe("C:\\Users\\Jose\\mods\\a-1.jar"));
	}

	/** Checked before the download, so an update is not fetched at every launch only to be thrown away. */
	@Test
	void swapPlacesAreCheckedBeforeDownloading() {
		Path ok = Path.of("/Games/mods/belis-skylyrics-0.1.0+1.21.11.jar");
		assertTrue(Updater.swapPathsSafe(ok, Path.of("/Games/mods/belis-skylyrics-0.2.0+1.21.11.jar")));
		assertFalse(Updater.swapPathsSafe(Path.of("/Jos\u00e9/mods/a.jar"), Path.of("/Jos\u00e9/mods/b.jar")));
		assertFalse(Updater.swapPathsSafe(ok, Path.of("/Games/mods/b&c.jar")));
	}

	/** The script goes in the temp folder, out of sight, unless cmd couldn't read that path (accents); then beside the jar. */
	@Test
	void swapScriptGoesToTheTempFolderUnlessItsNameHasAccents() {
		Path mods = dir.resolve("mods");
		Path old = mods.resolve("belis-skylyrics-0.1.0+1.21.11.jar");
		Path plain = dir.resolve("Temp");
		Path accented = dir.resolve("Jos\u00e9").resolve("Temp");
		assertEquals(Updater.batchSafe(plain.toAbsolutePath().toString()) ? plain : mods, Updater.scriptFolder(plain, old));
		assertEquals(mods, Updater.scriptFolder(accented, old));
	}

	@Test
	void swapScriptIsWrittenWhereItsPathIsSafe() throws Exception {
		Path mods = Files.createDirectories(dir.resolve("mods"));
		Path old = Files.createFile(mods.resolve("belis-skylyrics-0.1.0+1.21.11.jar"));
		Path target = mods.resolve("belis-skylyrics-0.2.0+1.21.11.jar");
		Path waiting = mods.resolve(target.getFileName() + ".new");
		Path part = Files.createFile(mods.resolve(target.getFileName() + ".part"));
		Path bat = Updater.writeSwapScript(old, waiting, target, part);
		assertEquals(Updater.scriptFolder(Path.of(System.getProperty("java.io.tmpdir")), old), bat.getParent());
		String written = Files.readString(bat, java.nio.charset.StandardCharsets.US_ASCII);
		Files.deleteIfExists(bat);
		assertTrue(bat.getFileName().toString().endsWith(".bat"));
		assertTrue(Files.exists(waiting));
		assertFalse(Files.exists(part));
		assertEquals(Updater.swapScript(old, waiting, target), written);
		assertTrue(written.contains("move /y \"" + waiting + "\" \"" + target + "\""));
		assertTrue(written.contains("del /f /q \"" + old + "\""));
		assertTrue(written.endsWith("del \"%~f0\"\r\n"));
	}

	@Test
	void downloadedJarMustBeThisModAtTheExpectedVersion() throws Exception {
		Path good = zip(dir.resolve("good.jar"), "{\"id\":\"skylyrics\",\"version\":\"0.2.0+1.21.11\"}");
		Path other = zip(dir.resolve("other.jar"), "{\"id\":\"othermod\",\"version\":\"0.2.0+1.21.11\"}");
		Path old = zip(dir.resolve("old.jar"), "{\"id\":\"skylyrics\",\"version\":\"0.1.0+1.21.11\"}");
		assertTrue(Updater.valid(good, null, "0.2.0+1.21.11"));
		assertFalse(Updater.valid(other, null, "0.2.0+1.21.11"));
		assertFalse(Updater.valid(old, null, "0.2.0+1.21.11"));
		assertEquals("0.2.0+1.21.11", Updater.expectedVersion("belis-skylyrics-0.2.0+1.21.11.jar"));
		assertNull(Updater.expectedVersion("random.jar"));
	}

	@Test
	void jarInsideASymlinkedModsFolderIsStillOurs(@TempDir Path tmp) throws IOException {
		Path real = Files.createDirectory(tmp.resolve("real-mods"));
		Path jar = Files.writeString(real.resolve("skylyrics.jar"), "x");
		Path link = tmp.resolve("mods");
		try {
			Files.createSymbolicLink(link, real);
		} catch (UnsupportedOperationException | IOException | SecurityException e) {
			assumeTrue(false, "symlinks not available: " + e);
		}
		// the loader reports the real path while the game folder goes through the link, and the other way round
		assertEquals(jar.toRealPath(), Updater.ownJar(link, List.of(jar)));
		assertEquals(jar.toRealPath(), Updater.ownJar(real, List.of(link.resolve("skylyrics.jar"))));
		assertNull(Updater.ownJar(tmp.resolve("elsewhere"), List.of(jar)));
	}

	@Test
	void plainModsFolderStillWorks(@TempDir Path tmp) throws IOException {
		Path jar = Files.writeString(tmp.resolve("skylyrics.jar"), "x");
		assertEquals(jar.toRealPath(), Updater.ownJar(tmp, List.of(jar)));
		assertNull(Updater.ownJar(tmp, List.of(Files.writeString(tmp.resolve("a.txt"), "x"))));
		assertNull(Updater.ownJar(tmp.resolve("missing"), List.of(jar)));
	}

	private static final String OK = "https://github.com/arbelonson-source/belis-skylyrics/releases/download/v1/a.jar";

	@Test
	void onlyThisModsReleaseDownloadsAreAllowed() {
		assertTrue(Updater.allowedDownload(OK));
		assertFalse(Updater.allowedDownload("https://github.com/someone-else/belis-skylyrics/releases/download/v1/a.jar"));
		assertFalse(Updater.allowedDownload("http://github.com/arbelonson-source/belis-skylyrics/releases/download/v1/a.jar"));
		assertFalse(Updater.allowedDownload("https://github.com.evil.example/arbelonson-source/belis-skylyrics/releases/download/v1/a.jar"));
		assertFalse(Updater.allowedDownload("https://github.com@evil.example/"));
		assertFalse(Updater.allowedDownload("https://github.com@evil.example/arbelonson-source/belis-skylyrics/releases/download/a.jar"));
		assertFalse(Updater.allowedDownload("https://github.com/arbelonson-source/belis-skylyrics/releases/download/../../../x"));
		assertFalse(Updater.allowedDownload(null));
	}

	@Test
	void onlyGithubHostsAreAllowedAsRedirects() {
		assertTrue(Updater.allowedRedirect(java.net.URI.create("https://github.com/x")));
		assertTrue(Updater.allowedRedirect(java.net.URI.create("https://objects.githubusercontent.com/x")));
		assertTrue(Updater.allowedRedirect(java.net.URI.create("https://release-assets.githubusercontent.com/x")));
		assertFalse(Updater.allowedRedirect(java.net.URI.create("http://github.com/x")));
		assertFalse(Updater.allowedRedirect(java.net.URI.create("https://github.com.evil.example/")));
		assertFalse(Updater.allowedRedirect(java.net.URI.create("https://evilgithubusercontent.com/x")));
		assertFalse(Updater.allowedRedirect(java.net.URI.create("https://github.com@evil.example/")));
		assertFalse(Updater.allowedRedirect(java.net.URI.create("https://user@github.com/")));
		assertFalse(Updater.allowedRedirect(java.net.URI.create("https://github.com:8443/x")));
		assertFalse(Updater.allowedRedirect(java.net.URI.create("file:///etc/passwd")));
	}

	@Test
	void anAddressOutsideTheReleasesIsNotDownloaded() throws Exception {
		Path old = Files.createFile(dir.resolve("belis-skylyrics-0.1.0+1.21.11.jar"));
		var asset = new UpdateCheck.Asset("belis-skylyrics-0.2.0+1.21.11.jar", "http://127.0.0.1:1/a.jar", null);
		assertFalse(Updater.install(HttpClient.newHttpClient(), old, asset));
		assertFalse(Files.exists(dir.resolve("belis-skylyrics-0.2.0+1.21.11.jar.part")));
	}

	private static final java.util.function.Predicate<java.net.URI> LOCAL = u -> "127.0.0.1".equals(u.getHost());

	@Test
	void redirectsAreFollowedByHandAndChecked() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/ok", ex -> {
			ex.getResponseHeaders().add("Location", "/body");
			ex.sendResponseHeaders(302, -1);
			ex.close();
		});
		server.createContext("/body", ex -> {
			byte[] b = new byte[100];
			ex.sendResponseHeaders(200, b.length);
			ex.getResponseBody().write(b);
			ex.close();
		});
		server.createContext("/evil", ex -> {
			ex.getResponseHeaders().add("Location", "https://evil.example/a.jar");
			ex.sendResponseHeaders(302, -1);
			ex.close();
		});
		server.createContext("/loop", ex -> {
			ex.getResponseHeaders().add("Location", "/loop");
			ex.sendResponseHeaders(302, -1);
			ex.close();
		});
		server.start();
		try {
			String base = "http://127.0.0.1:" + server.getAddress().getPort();
			HttpClient http = HttpClient.newHttpClient();
			Path f = dir.resolve("r.part");
			assertTrue(Updater.fetch(http, base + "/ok", f, 1000, LOCAL));
			assertEquals(100, Files.size(f));
			Path g = dir.resolve("g.part");
			assertFalse(Updater.fetch(http, base + "/evil", g, 1000, LOCAL));
			assertFalse(Files.exists(g));
			assertFalse(Updater.fetch(http, base + "/loop", g, 1000, LOCAL));
			assertFalse(Files.exists(g));
		} finally {
			server.stop(0);
		}
	}

	@Test
	void anOversizedDownloadIsRefusedAndLeavesNoFile() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		// Announces too much up front
		server.createContext("/announced", ex -> {
			ex.sendResponseHeaders(200, 5000);
			ex.getResponseBody().write(new byte[5000]);
			ex.close();
		});
		// Chunked, so only the copy can notice
		server.createContext("/chunked", ex -> {
			ex.sendResponseHeaders(200, 0);
			ex.getResponseBody().write(new byte[5000]);
			ex.close();
		});
		server.start();
		try {
			String base = "http://127.0.0.1:" + server.getAddress().getPort();
			HttpClient http = HttpClient.newHttpClient();
			Path f = dir.resolve("big.part");
			assertFalse(Updater.fetch(http, base + "/announced", f, 1000, LOCAL));
			assertFalse(Files.exists(f));
			assertFalse(Updater.fetch(http, base + "/chunked", f, 1000, LOCAL));
			assertFalse(Files.exists(f));
			assertTrue(Updater.fetch(http, base + "/chunked", f, 5000, LOCAL));
			assertEquals(5000, Files.size(f));
		} finally {
			server.stop(0);
		}
	}

	@Test
	void copyCappedStopsAndDeletesPastTheLimit() throws IOException {
		Path f = dir.resolve("c.part");
		assertTrue(Updater.copyCapped(new java.io.ByteArrayInputStream(new byte[100]), f, 100));
		assertEquals(100, Files.size(f));
		assertFalse(Updater.copyCapped(new java.io.ByteArrayInputStream(new byte[101]), f, 100));
		assertFalse(Files.exists(f));
	}

	@Test
	void oldLeftoverDownloadsOfOursAreCleanedButFreshOnesAndOtherFilesStay() throws Exception {
		long now = System.currentTimeMillis();
		java.nio.file.attribute.FileTime old = java.nio.file.attribute.FileTime.fromMillis(now - 2 * 24L * 3600 * 1000);
		java.nio.file.attribute.FileTime fresh = java.nio.file.attribute.FileTime.fromMillis(now - 3600 * 1000L);
		Path unique = Files.createFile(dir.resolve("belis-skylyrics-0.2.0+1.21.11.jar.123456.part"));
		Path fixed = Files.createFile(dir.resolve("belis-skylyrics-0.1.9+1.21.11.jar.part")); // the name earlier versions used
		Path staged = Files.createFile(dir.resolve("belis-skylyrics-0.2.0+1.21.11.jar.987.new"));
		Path young = Files.createFile(dir.resolve("belis-skylyrics-0.2.0+1.21.11.jar.555.part"));
		Path otherMod = Files.createFile(dir.resolve("othermod-1.0.jar.777.part"));
		Path jar = Files.createFile(dir.resolve("belis-skylyrics-0.1.0+1.21.11.jar"));
		for (Path p : List.of(unique, fixed, staged, otherMod, jar)) {
			Files.setLastModifiedTime(p, old);
		}
		Files.setLastModifiedTime(young, fresh);
		assertEquals(3, Updater.cleanLeftovers(dir, now));
		assertFalse(Files.exists(unique));
		assertFalse(Files.exists(fixed));
		assertFalse(Files.exists(staged));
		assertTrue(Files.exists(young), "another game may still be downloading it");
		assertTrue(Files.exists(otherMod), "not ours");
		assertTrue(Files.exists(jar), "never a jar");
	}

	@Test
	void eachDownloadGetsItsOwnWaitingNameOnWindows() {
		Path target = dir.resolve("belis-skylyrics-0.2.0+1.21.11.jar");
		Path a = dir.resolve("belis-skylyrics-0.2.0+1.21.11.jar.111.part");
		Path b = dir.resolve("belis-skylyrics-0.2.0+1.21.11.jar.222.part");
		assertEquals(dir.resolve("belis-skylyrics-0.2.0+1.21.11.jar.111.new"), Updater.waitingFor(a, target));
		assertFalse(Updater.waitingFor(a, target).equals(Updater.waitingFor(b, target)));
		assertTrue(Updater.swapPathsSafe(dir.resolve("belis-skylyrics-0.1.0+1.21.11.jar"), target));
		assertTrue(Updater.batchSafe(Updater.waitingFor(a, target).toString()));
	}

	/** The server sends the headers and a few bytes, then goes quiet: the download is abandoned and no file stays. */
	@Test
	void aStalledDownloadIsCutOffAndLeavesNoFile() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0);
		java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		server.createContext("/jar", ex -> {
			ex.sendResponseHeaders(200, 1000);
			ex.getResponseBody().write(new byte[10]);
			ex.getResponseBody().flush();
			try {
				release.await(30, java.util.concurrent.TimeUnit.SECONDS);
			} catch (InterruptedException ignored) {
			}
			ex.close();
		});
		server.start();
		try {
			HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
			Path part = dir.resolve("x.part");
			Files.createFile(part);
			String url = "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/jar";
			long start = System.nanoTime();
			assertThrows(IOException.class, () -> Updater.fetch(http, url, part, 1_000_000, u -> true, java.time.Duration.ofSeconds(1)));
			assertTrue(System.nanoTime() - start < java.util.concurrent.TimeUnit.SECONDS.toNanos(15), "should give up soon");
			assertFalse(Files.exists(part));
		} finally {
			release.countDown();
			server.stop(0);
		}
	}
}
