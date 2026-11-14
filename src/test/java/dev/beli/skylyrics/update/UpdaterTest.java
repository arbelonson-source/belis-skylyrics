package dev.beli.skylyrics.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
