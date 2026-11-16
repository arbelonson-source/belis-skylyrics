package dev.beli.skylyrics.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.config.SkyLyricsConfig.Updates;

/** A refused release is remembered, so the same jar is not downloaded on every start. */
class RefusalTest {
	private static final String NAME = "belis-skylyrics-0.2.0+1.21.11.jar";
	private static final String URL = "https://github.com/arbelonson-source/belis-skylyrics/releases/download/v0.2.0/" + NAME;
	private static final String TEXT = "Fabric API 0.140.0 or newer";

	@TempDir
	Path dir;
	Path mods;
	Path memory;
	Path old;

	@BeforeEach
	void setUp() throws IOException {
		Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"));
		mods = Files.createDirectory(dir.resolve("mods"));
		memory = dir.resolve("cache").resolve("update-refused.json");
		old = Files.createFile(mods.resolve("belis-skylyrics-0.1.0+1.21.11.jar"));
	}

	private static UnaryOperator<String> running(String fabricApi) {
		return id -> Map.of("minecraft", "1.21.11", "fabric-api", fabricApi, "fabricloader", "0.17.2", "java", "21").get(id);
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

	private static UpdateCheck.Asset asset(byte[] data) throws Exception {
		return new UpdateCheck.Asset(NAME, URL, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)));
	}

	private Updater.Notice check(UpdaterChecksTest.Net net, UpdateCheck.Asset asset, UnaryOperator<String> running) {
		return Updater.update(Updates.INSTALL, net, old, new UpdateCheck.Release("0.2.0", List.of(asset)), asset, memory, running);
	}

	private void assertRefusal(Updater.Notice n) {
		assertFalse(n.downloaded());
		assertEquals(TEXT, n.needs());
		assertEquals("Fabric API", n.what());
	}

	@Test
	void aRefusedReleaseIsDownloadedOnceAndRefusedAgainWithTheSameText() throws Exception {
		byte[] data = jar("{\"fabric-api\":\">=0.140.0\"}");
		UpdaterChecksTest.Net net = new UpdaterChecksTest.Net(data);
		assertRefusal(check(net, asset(data), running("0.139.0")));
		assertEquals(1, net.requests.size());
		assertTrue(Files.isRegularFile(memory), "the refusal is remembered");
		assertRefusal(check(net, asset(data), running("0.139.0")));
		assertEquals(1, net.requests.size(), "the second start downloads nothing");
		assertRefusal(check(net, asset(data), running("0.139.0")));
		assertEquals(1, net.requests.size());
	}

	@Test
	void updatingFabricApiDownloadsAgainAndForgets() throws Exception {
		byte[] data = jar("{\"fabric-api\":\">=0.140.0\"}");
		UpdaterChecksTest.Net net = new UpdaterChecksTest.Net(data);
		check(net, asset(data), running("0.139.0"));
		Updater.Notice n = check(net, asset(data), running("0.140.0"));
		assertEquals(2, net.requests.size());
		assertTrue(n.downloaded(), "it fits now and is installed");
		assertFalse(Files.exists(memory), "the stale memory is gone");
	}

	@Test
	void aNewerReleaseDownloadsAndReplacesTheMemory() throws Exception {
		byte[] data = jar("{\"fabric-api\":\">=0.140.0\"}");
		UpdaterChecksTest.Net net = new UpdaterChecksTest.Net(data);
		check(net, asset(data), running("0.139.0"));
		byte[] newer = jar("{\"fabric-api\":\">=0.150.0\"}");
		Updater.Notice n = check(new UpdaterChecksTest.Net(newer), asset(newer), running("0.139.0"));
		assertEquals("Fabric API 0.150.0 or newer", n.needs());
		assertEquals(asset(newer).sha256(), Refusal.read(memory).sha256());
	}

	@Test
	void aGarbageMemoryFileCountsAsNone() throws Exception {
		byte[] data = jar("{\"fabric-api\":\">=0.140.0\"}");
		for (String garbage : new String[] {"", "not json", "[]", "{\"asset\":1}", "{\"asset\":\"x\",\"running\":5}"}) {
			Files.createDirectories(memory.getParent());
			Files.writeString(memory, garbage);
			UpdaterChecksTest.Net net = new UpdaterChecksTest.Net(data);
			assertRefusal(check(net, asset(data), running("0.139.0")));
			assertEquals(1, net.requests.size(), "downloads as today for: " + garbage);
		}
	}

	@Test
	void aReleaseWithoutAChecksumIsNeverRemembered() throws Exception {
		byte[] data = jar("{\"fabric-api\":\">=0.140.0\"}");
		UpdateCheck.Asset noDigest = new UpdateCheck.Asset(NAME, URL, null);
		UpdaterChecksTest.Net net = new UpdaterChecksTest.Net(data);
		assertRefusal(check(net, noDigest, running("0.139.0")));
		assertRefusal(check(net, noDigest, running("0.139.0")));
		assertEquals(2, net.requests.size(), "without a checksum the same name could be another jar, so it downloads again");
	}

	@Test
	void theSameNameWithAnotherChecksumDownloadsAgain() throws Exception {
		byte[] data = jar("{\"fabric-api\":\">=0.140.0\"}");
		UpdaterChecksTest.Net net = new UpdaterChecksTest.Net(data);
		check(net, asset(data), running("0.139.0"));
		byte[] reuploaded = jar("{\"fabric-api\":\">=0.130.0\"}");
		UpdaterChecksTest.Net again = new UpdaterChecksTest.Net(reuploaded);
		Updater.Notice n = check(again, asset(reuploaded), running("0.139.0"));
		assertEquals(1, again.requests.size(), "a fixed re-upload under the same name is fetched");
		assertTrue(n.downloaded(), "and installed");
		assertFalse(Files.exists(memory));
	}

	@Test
	void tellMeAndOffLeaveTheMemoryAlone() throws Exception {
		byte[] data = jar("{\"fabric-api\":\">=0.140.0\"}");
		check(new UpdaterChecksTest.Net(data), asset(data), running("0.139.0"));
		String before = Files.readString(memory);
		for (Updates mode : new Updates[] {Updates.NOTIFY, Updates.OFF}) {
			UpdaterChecksTest.Net net = new UpdaterChecksTest.Net(data);
			Updater.update(mode, net, old, new UpdateCheck.Release("0.2.0", List.of(asset(data))), asset(data), memory, running("0.139.0"));
			assertEquals(0, net.requests.size(), mode + " downloads nothing");
			assertEquals(before, Files.readString(memory), mode + " leaves the memory as it was");
		}
	}

	@Test
	void anUnwritableMemoryNeverStopsTheCheck() throws Exception {
		Files.writeString(dir.resolve("cache"), "a file where the folder should be");
		byte[] data = jar("{\"fabric-api\":\">=0.140.0\"}");
		assertRefusal(check(new UpdaterChecksTest.Net(data), asset(data), running("0.139.0")));
	}

	@Test
	void staleTempOfAKilledGameIsCleanedWhenTheRefusalIsRead() throws IOException {
		Files.createDirectories(memory.getParent());
		Path stale = memory.resolveSibling("update-refused.json.99.tmp");
		Files.writeString(stale, "x");
		Files.setLastModifiedTime(stale, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() - 2 * dev.beli.skylyrics.util.Leftovers.AGE_MS));
		Refusal.read(memory);
		assertFalse(Files.exists(stale));
	}
}
