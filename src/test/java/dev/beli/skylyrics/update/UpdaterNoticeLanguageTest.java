package dev.beli.skylyrics.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.InLanguage;
import dev.beli.skylyrics.config.SkyLyricsConfig.Updates;

/**
 * The update pop-up is told in the player's language. The check runs in the background from the moment the mod starts,
 * while the game is still on its loading screen and hasn't loaded the player's language yet; a refused release is also
 * remembered for later starts, when the player may have picked another language.
 */
class UpdaterNoticeLanguageTest {
	private static final String NAME = "belis-skylyrics-0.2.0+1.21.11.jar";
	private static final String URL = "https://github.com/arbelonson-source/belis-skylyrics/releases/download/v0.2.0/" + NAME;

	@TempDir
	Path dir;
	Path old;
	Path memory;

	@BeforeEach
	void setUp() throws IOException {
		Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"));
		old = Files.createFile(Files.createDirectory(dir.resolve("mods")).resolve("belis-skylyrics-0.1.0+1.21.11.jar"));
		memory = dir.resolve("cache").resolve("update-refused.json");
	}

	@AfterEach
	void noNoticeLeft() throws Exception {
		notice().set(null, null);
	}

	private static UnaryOperator<String> running() {
		return id -> Map.of("minecraft", "1.21.11", "fabric-api", "0.139.0", "fabricloader", "0.17.2", "java", "21").get(id);
	}

	private static byte[] jar() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream out = new ZipOutputStream(bytes)) {
			out.putNextEntry(new ZipEntry("fabric.mod.json"));
			out.write("{\"id\":\"skylyrics\",\"version\":\"0.2.0+1.21.11\",\"depends\":{\"fabric-api\":\">=0.140.0\"}}"
					.getBytes(StandardCharsets.UTF_8));
			out.closeEntry();
		}
		return bytes.toByteArray();
	}

	/** One start's check of a release that needs a newer Fabric API than the game has. */
	private Updater.Notice check(UpdaterChecksTest.Net net) throws Exception {
		byte[] data = net.body;
		UpdateCheck.Asset asset = new UpdateCheck.Asset(NAME, URL,
				HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)));
		return Updater.update(Updates.INSTALL, net, old, new UpdateCheck.Release("0.2.0", List.of(asset)), asset, memory, running());
	}

	private static Field notice() throws NoSuchFieldException {
		Field f = Updater.class.getDeclaredField("notice");
		f.setAccessible(true);
		return f;
	}

	@Test
	void whatTheUpdateNeedsIsInTheLanguageThePopUpIsShownIn() throws Exception {
		// The check finishes while the game still speaks English (its language before the loading screen ends)
		Updater.Notice n = check(new UpdaterChecksTest.Net(jar()));
		try (InLanguage de = InLanguage.of("de_de")) {
			assertEquals("Fabric API " + de.says("%s or newer", "0.140.0"), n.needs(), "the German pop-up has no English words in it");
			assertEquals("Fabric API", n.what());
		}
	}

	@Test
	void aRememberedRefusalIsToldInTodaysLanguageNotTheOneOfTheDayItWasRefused() throws Exception {
		try (InLanguage de = InLanguage.of("de_de")) {
			check(new UpdaterChecksTest.Net(jar())); // refused and remembered while the game was in German
		}
		UpdaterChecksTest.Net net = new UpdaterChecksTest.Net(jar());
		Updater.Notice n = check(net); // a later start, after switching the game to English
		assertEquals(0, net.requests.size(), "still remembered: nothing downloaded again");
		assertEquals("Fabric API 0.140.0 or newer", n.needs());
	}

	@Test
	void aNoticeThatArrivesDuringTheLoadingScreenWaitsForThePlayersLanguage() throws Exception {
		Updater.start(Updates.OFF, "0.1.0+1.21.11"); // the mod starts while the game still has its start-up language
		notice().set(null, new Updater.Notice("0.2.0", false)); // the check in the background is done
		assertNull(Updater.takeNotice(), "not told yet: every word of it would be English for a German player");
		assertNull(Updater.takeNotice());
		try (InLanguage de = InLanguage.of("de_de")) { // the loading screen is over: the player's language is in
			Updater.Notice n = Updater.takeNotice();
			assertNotNull(n, "told now");
			assertEquals("0.2.0", n.version());
			assertNull(Updater.takeNotice(), "and only once");
		}
	}
}
