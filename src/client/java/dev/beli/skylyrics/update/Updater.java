package dev.beli.skylyrics.update;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.beli.skylyrics.SkyLyricsClient;
import dev.beli.skylyrics.config.SkyLyricsConfig.Updates;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/** Looks for a newer release once at start and, if asked to, swaps the mod's jar for it. Never blocks the game. */
public final class Updater {
	private static final String LATEST = "https://api.github.com/repos/arbelonson-source/belis-skylyrics/releases/latest";
	private static final Logger LOG = LoggerFactory.getLogger("SkyLyrics");
	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	/** What to tell the player: a new version is out, or it is already downloaded and needs a restart. */
	public record Notice(String version, boolean downloaded) {
	}

	private static volatile Notice notice;

	private Updater() {
	}

	/** The message to show, once. Null when there is nothing to say. */
	public static Notice takeNotice() {
		Notice n = notice;
		notice = null;
		return n;
	}

	public static void start(Updates mode, String runningVersion) {
		if (mode == Updates.OFF) {
			return;
		}
		Thread thread = new Thread(() -> {
			try {
				check(mode, runningVersion);
			} catch (Exception e) {
				LOG.info("Update check skipped: {}", e.toString());
			}
		}, "SkyLyrics-update");
		thread.setDaemon(true);
		thread.start();
	}

	private static void check(Updates mode, String runningVersion) throws Exception {
		String mcKey = UpdateCheck.mcKey(runningVersion);
		Path jar = ownJar();
		// A dev run has no real jar in a mods folder and no Minecraft key; there is nothing to update
		if (mcKey == null || jar == null) {
			return;
		}
		HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();
		HttpResponse<String> response = http.send(request(LATEST).header("Accept", "application/vnd.github+json").build(),
				HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			LOG.info("Update check got HTTP {}", response.statusCode());
			return;
		}
		UpdateCheck.Release release = UpdateCheck.parse(response.body());
		if (release == null || UpdateCheck.compare(release.version(), runningVersion) <= 0) {
			return;
		}
		UpdateCheck.Asset asset = UpdateCheck.pick(release, mcKey);
		if (asset == null) {
			LOG.info("Release {} has no jar for Minecraft {}", release.version(), mcKey);
			return;
		}
		notice = update(mode, http, jar, release, asset);
	}

	/**
	 * Installs the release when asked to. However the install fails (timeout, cut connection, bad zip,
	 * a move that does not work), the player is still told a new version is out, just not that it is downloaded.
	 */
	static Notice update(Updates mode, HttpClient http, Path jar, UpdateCheck.Release release, UpdateCheck.Asset asset) {
		boolean installed = false;
		if (mode == Updates.INSTALL) {
			try {
				installed = install(http, jar, asset);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (Exception e) {
				LOG.warn("Could not install the update, telling the player instead: {}", e.toString());
			}
		}
		return new Notice(release.version(), installed);
	}

	private static HttpRequest.Builder request(String url) {
		return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
				.header("User-Agent", "belis-skylyrics-updater");
	}

	/** The jar this mod was loaded from, when it is a real .jar file inside the game's mods folder. */
	private static Path ownJar() {
		ModContainer container = FabricLoader.getInstance().getModContainer(SkyLyricsClient.MOD_ID).orElse(null);
		if (container == null) {
			return null;
		}
		Path mods = FabricLoader.getInstance().getGameDir().resolve("mods").toAbsolutePath().normalize();
		for (Path path : container.getOrigin().getPaths()) {
			Path p = path.toAbsolutePath().normalize();
			if (Files.isRegularFile(p) && p.getFileName().toString().endsWith(".jar") && p.startsWith(mods)) {
				return p;
			}
		}
		return null;
	}

	/** Downloads the new jar next to the old one and makes sure only one of them is left. True when it is in place. */
	static boolean install(HttpClient http, Path oldJar, UpdateCheck.Asset asset) throws Exception {
		Path dir = oldJar.getParent();
		Path target = dir.resolve(asset.name());
		if (!target.getFileName().toString().equals(asset.name()) || Files.exists(target)) {
			return false;
		}
		// The download keeps a name Fabric ignores until it is checked
		Path part = dir.resolve(asset.name() + ".part");
		try {
			HttpResponse<Path> response = http.send(request(asset.url()).build(), HttpResponse.BodyHandlers.ofFile(part));
			if (response.statusCode() != 200) {
				LOG.info("Update download got HTTP {}", response.statusCode());
				Files.deleteIfExists(part);
				return false;
			}
			if (!valid(part, asset.sha256(), expectedVersion(asset.name()))) {
				LOG.warn("Downloaded update {} failed its check, discarding it", asset.name());
				Files.deleteIfExists(part);
				return false;
			}
			if (windows()) {
				return installLater(oldJar, part, target);
			}
			Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
			try {
				Files.delete(oldJar);
			} catch (IOException e) {
				// Two jars with the mod's id would stop the game from starting, so never leave both
				LOG.warn("Could not remove the old jar, dropping the update: {}", e.toString());
				Files.deleteIfExists(target);
				return false;
			}
			LOG.info("Updated SkyLyrics: {} replaces {}", target.getFileName(), oldJar.getFileName());
			return true;
		} finally {
			try {
				Files.deleteIfExists(part);
			} catch (IOException ignored) {
				// leftover .part files are ignored by the game
			}
		}
	}

	/** The mod version inside a jar name like belis-skylyrics-0.7.9+1.21.11.jar, or null for another name. */
	static String expectedVersion(String assetName) {
		String prefix = "belis-skylyrics-";
		return assetName.startsWith(prefix) && assetName.endsWith(".jar")
				? assetName.substring(prefix.length(), assetName.length() - 4) : null;
	}

	/** A readable jar whose fabric.mod.json is this mod at the expected version, and the published checksum when there is one. */
	static boolean valid(Path file, String sha256, String expectedVersion) throws Exception {
		try (ZipFile zip = new ZipFile(file.toFile())) {
			var entry = zip.getEntry("fabric.mod.json");
			if (entry == null) {
				return false;
			}
			try (InputStream in = zip.getInputStream(entry)) {
				JsonObject json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
				if (!SkyLyricsClient.MOD_ID.equals(string(json, "id"))
						|| expectedVersion != null && !expectedVersion.equals(string(json, "version"))) {
					return false;
				}
			}
		}
		if (sha256 == null) {
			return true;
		}
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		try (InputStream in = Files.newInputStream(file)) {
			byte[] buffer = new byte[65536];
			for (int n; (n = in.read(buffer)) > 0; ) {
				digest.update(buffer, 0, n);
			}
		}
		return HexFormat.of().formatHex(digest.digest()).equals(sha256);
	}

	private static String string(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
	}

	/** True when the text is plain ASCII with nothing that means something to a batch file. */
	static boolean batchSafe(String s) {
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c < 0x20 || c > 0x7e || "%\"^&!|<>".indexOf(c) >= 0) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Windows keeps the running jar locked, so it cannot be deleted now. The new jar waits under a name the game
	 * ignores, and when the game has quit a small detached script removes the old jar and only then gives the
	 * new one its real name. If the script never gets to run, the old version simply stays and nothing breaks.
	 */
	static boolean installLater(Path oldJar, Path part, Path target) throws IOException {
		Path waiting = target.resolveSibling(target.getFileName() + ".new");
		for (Path p : List.of(oldJar, waiting, target)) {
			// cmd reads a .bat in the OEM code page, so any non-ASCII character (C:\Users\José) would be mangled and the
			// swap would fail without a word. Characters with a meaning in a batch file are not worth the risk either.
			if (!batchSafe(p.toString())) {
				return false;
			}
		}
		Path bat = Files.createTempFile("skylyrics-update-", ".bat");
		if (!batchSafe(bat.toString())) {
			Files.deleteIfExists(bat);
			return false;
		}
		Files.move(part, waiting, StandardCopyOption.REPLACE_EXISTING);
		String script = String.join("\r\n",
				"@echo off",
				"ping -n 6 127.0.0.1 >nul",
				"set n=0",
				":retry",
				"del /f /q \"" + oldJar + "\" 2>nul",
				"if not exist \"" + oldJar + "\" goto swap",
				"set /a n+=1",
				"if %n% lss 20 (ping -n 4 127.0.0.1 >nul & goto retry)",
				"del /f /q \"" + waiting + "\"",
				"goto end",
				":swap",
				"move /y \"" + waiting + "\" \"" + target + "\" >nul",
				":end",
				"(goto) 2>nul & del \"%~f0\"",
				"");
		Files.write(bat, script.getBytes(StandardCharsets.US_ASCII));
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				new ProcessBuilder("cmd", "/c", bat.toString())
						.redirectOutput(ProcessBuilder.Redirect.DISCARD)
						.redirectError(ProcessBuilder.Redirect.DISCARD)
						.start();
			} catch (IOException e) {
				// the old jar stays, and the .new file is ignored by the game
			}
		}, "SkyLyrics-update-swap"));
		LOG.info("SkyLyrics {} will replace {} when the game closes", target.getFileName(), oldJar.getFileName());
		return true;
	}

	private static boolean windows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}
}
