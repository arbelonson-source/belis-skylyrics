package dev.beli.skylyrics.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.beli.skylyrics.config.SkyLyricsConfig.Updates;

/** A downloaded jar is only installed when it will load on this Minecraft, Fabric API, Fabric Loader and Java. */
class NeedsTest {
	private static final String NAME = "belis-skylyrics-0.2.0+1.21.11.jar";
	private static final String URL = "https://github.com/arbelonson-source/belis-skylyrics/releases/download/v0.2.0/" + NAME;
	private static final UnaryOperator<String> RUNNING = Map.of("minecraft", "1.21.11", "fabric-api", "0.139.0",
			"fabricloader", "0.17.2", "java", "21")::get;

	@TempDir
	Path dir;

	private static JsonObject depends(String json) {
		return JsonParser.parseString(json).getAsJsonObject();
	}

	private static byte[] jar(String depends) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream out = new ZipOutputStream(bytes)) {
			out.putNextEntry(new ZipEntry("fabric.mod.json"));
			out.write(("{\"id\":\"skylyrics\",\"version\":\"0.2.0+1.21.11\",\"depends\":" + depends + "}").getBytes(StandardCharsets.UTF_8));
			out.closeEntry();
		}
		return bytes.toByteArray();
	}

	private static String sha(byte[] data) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
	}

	/** Runs the real install against a jar with the given depends; returns the result or the refusal. */
	private Object tryInstall(String depends) throws Exception {
		Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"));
		byte[] data = jar(depends);
		Path old = Files.createFile(dir.resolve("belis-skylyrics-0.1.0+1.21.11.jar"));
		UpdaterChecksTest.Net net = new UpdaterChecksTest.Net(data);
		var asset = new UpdateCheck.Asset(NAME, URL, sha(data));
		try {
			return Updater.install(net, old, asset, RUNNING);
		} catch (Updater.Incompatible e) {
			return e;
		}
	}

	private void assertRefused(Object result, String text) throws IOException {
		assertTrue(result instanceof Updater.Incompatible, "the jar must be refused, got " + result);
		assertEquals(text, ((Updater.Incompatible) result).unmet.text());
		assertTrue(Files.exists(dir.resolve("belis-skylyrics-0.1.0+1.21.11.jar")), "the working jar stays");
		assertFalse(Files.exists(dir.resolve(NAME)), "the new jar is not installed");
		try (var files = Files.list(dir)) {
			assertEquals(1, files.count(), "the download is deleted");
		}
	}

	@Test
	void aJarNeedingANewerFabricApiIsRefused() throws Exception {
		assertRefused(tryInstall("{\"minecraft\":\"1.21.11\",\"fabric-api\":\">=0.140.0\"}"), "Fabric API 0.140.0 or newer");
	}

	@Test
	void aJarForAnotherMinecraftIsRefused() throws Exception {
		assertRefused(tryInstall("{\"minecraft\":\">=1.21.6 <1.21.9\"}"), "Minecraft >=1.21.6 <1.21.9");
	}

	@Test
	void aJarNeedingNewerJavaOrLoaderIsRefused() throws Exception {
		assertRefused(tryInstall("{\"java\":\">=25\"}"), "Java 25 or newer");
	}

	@Test
	void anUnparsableConstraintIsRefused() throws Exception {
		Object result = tryInstall("{\"minecraft\":\">=>=what\"}");
		assertTrue(result instanceof Updater.Incompatible, "got " + result);
	}

	@Test
	void anArrayWithOneMatchIsAccepted() throws Exception {
		assertEquals(true, tryInstall("{\"minecraft\":[\"1.21.9\",\"1.21.11\"],\"fabric-api\":[\">=0.200.0\",\">=0.130.0\"]}"));
	}

	@Test
	void aSatisfiedJarIsAccepted() throws Exception {
		assertEquals(true, tryInstall("{\"minecraft\":\"~1.21.11\",\"fabric-api\":\">=0.139.0\",\"fabricloader\":\">=0.17.0\",\"java\":\">=21\"}"));
	}

	@Test
	void updateTellsThePlayerWhatToUpdate() throws Exception {
		Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"));
		byte[] data = jar("{\"fabric-api\":\">=999.0.0\"}");
		Path old = Files.createFile(dir.resolve("belis-skylyrics-0.1.0+1.21.11.jar"));
		var asset = new UpdateCheck.Asset(NAME, URL, sha(data));
		// the real lookup: no game is running in a test, so Fabric API is not there, and that is a refusal too
		Updater.Notice n = Updater.update(Updates.INSTALL, new UpdaterChecksTest.Net(data), old, new UpdateCheck.Release("0.2.0", List.of(asset)), asset);
		assertFalse(n.downloaded());
		assertEquals("Fabric API 999.0.0 or newer", n.needs());
		assertEquals("Fabric API", n.what());
		assertTrue(Files.exists(old));
	}

	@Test
	void pureCheck() {
		assertNull(Needs.unmet(null, RUNNING));
		assertNull(Needs.unmet(depends("{}"), RUNNING));
		assertNull(Needs.unmet(depends("{\"fabric-api\":\"*\",\"other-mod\":\">=99\"}"), RUNNING));
		assertNotNull(Needs.unmet(depends("{\"fabric-api\":\">=0.139.1\"}"), RUNNING));
		assertNotNull(Needs.unmet(depends("{\"fabric-api\":\">=0.100.0\"}"), k -> null), "not installed at all");
		assertNotNull(Needs.unmet(depends("{\"fabric-api\":[]}"), RUNNING));
		assertNotNull(Needs.unmet(depends("{\"fabric-api\":5}"), RUNNING));
	}
}
