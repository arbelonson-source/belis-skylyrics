package dev.beli.skylyrics.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.config.SkyLyricsConfig.Updates;

/**
 * A modpack installed with packwiz-installer (a Prism, MultiMC or ATLauncher pre-launch command that keeps the pack up to
 * date at every launch) lists every file it put in the game folder in its manifest, packwiz.json, and downloads any of
 * them again that is missing. If the pack brought SkyLyrics and the mod swapped its own jar, the next launch would put the
 * old jar back next to the new one. So in such a pack, Install only tells the player.
 */
class UpdaterPackwizTest {
	private static final String NAME = "belis-skylyrics-0.2.0+1.21.11.jar";
	private static final String URL = "https://github.com/arbelonson-source/belis-skylyrics/releases/download/v0.2.0/" + NAME;
	private static final String LATEST = "{\"tag_name\":\"v0.2.0\",\"assets\":[{\"name\":\"" + NAME + "\",\"browser_download_url\":\"" + URL + "\"}]}";

	@TempDir
	Path game;
	Path mods;
	Path old;

	@BeforeEach
	void setUp() throws IOException {
		mods = Files.createDirectory(game.resolve("mods"));
		old = Files.writeString(mods.resolve("belis-skylyrics-0.1.0+1.21.11.jar"), "old");
	}

	private static byte[] jarBytes() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream out = new ZipOutputStream(bytes)) {
			out.putNextEntry(new ZipEntry("fabric.mod.json"));
			out.write("{\"id\":\"skylyrics\",\"version\":\"0.2.0+1.21.11\"}".getBytes(StandardCharsets.UTF_8));
			out.closeEntry();
		}
		return bytes.toByteArray();
	}

	/** packwiz-installer's manifest as it writes it: each file's place relative to the pack folder, with forward slashes. */
	private void manifest(String... cachedLocations) throws IOException {
		StringBuilder files = new StringBuilder();
		for (int i = 0; i < cachedLocations.length; i++) {
			files.append(i == 0 ? "" : ",").append("\"mods/mod").append(i).append(".pw.toml\":{\"hash\":\"ab12\",\"linkedFileHash\":\"cd34\",")
					.append("\"cachedLocation\":\"").append(cachedLocations[i]).append("\"}");
		}
		Files.writeString(game.resolve("packwiz.json"), "{\"packFileHash\":\"0f\",\"indexFileHash\":\"1e\",\"cachedSide\":\"client\","
				+ "\"cachedFiles\":{" + files + "}}");
	}

	private Updater.Notice look(UpdaterChecksTest.Net net) throws Exception {
		return Updater.look(Updates.INSTALL, () -> LATEST, net, old.toRealPath(), "0.1.0+1.21.11", "1.21.11", null, id -> null);
	}

	@Test
	void aJarThePacksInstallerKeepsIsNotSwappedTheNewVersionIsOnlyAnnounced() throws Exception {
		manifest("mods/sodium.jar", "mods/" + old.getFileName(), "config/x.json");
		UpdaterChecksTest.Net net = new UpdaterChecksTest.Net(jarBytes());
		Updater.Notice n = look(net);
		assertNotNull(n, "the player still hears about the new version");
		assertEquals("0.2.0", n.version());
		assertFalse(n.downloaded());
		assertEquals(0, net.requests.size(), "nothing downloaded");
		try (var files = Files.list(mods)) {
			assertEquals(List.of(old.getFileName().toString()), files.map(f -> f.getFileName().toString()).toList(),
					"the pack's jar stays, so its installer has nothing to put back and the folder never holds two SkyLyrics");
		}
	}

	@Test
	void aPackThatDoesNotBringSkyLyricsStillGetsItUpdated() throws Exception {
		Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"));
		manifest("mods/sodium.jar", "mods/belis-skylyrics-0.1.0+1.21.11.jar.disabled", "config/mods/" + old.getFileName());
		UpdaterChecksTest.Net net = new UpdaterChecksTest.Net(jarBytes());
		assertTrue(look(net).downloaded(), "the player added SkyLyrics to the pack themselves: the installer never touches it");
		assertTrue(Files.exists(mods.resolve(NAME)));
		assertFalse(Files.exists(old));
	}

	@Test
	void aBrokenOrStrangeManifestDoesNotStopTheUpdate() throws Exception {
		Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"));
		for (String text : new String[] {"", "not json", "[]", "{\"cachedFiles\":[]}", "{\"cachedFiles\":{\"a\":5,\"b\":{\"cachedLocation\":7}}}"}) {
			Files.writeString(game.resolve("packwiz.json"), text);
			Files.writeString(old, "old");
			Files.deleteIfExists(mods.resolve(NAME));
			assertTrue(look(new UpdaterChecksTest.Net(jarBytes())).downloaded(), text);
		}
	}
}
