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
	private static final String REPO = "arbelonson-source/belis-skylyrics";
	private static final String LATEST = "https://api.github.com/repos/" + REPO + "/releases/latest";
	/** The only place a release jar may come from; GitHub then redirects it to its asset hosts. */
	private static final String DOWNLOAD_PREFIX = "https://github.com/" + REPO + "/releases/download/";
	/** The real jar is about 1.5 MB. */
	static final long MAX_DOWNLOAD = 20L * 1024 * 1024;
	private static final int MAX_HOPS = 5;
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
		// The API call may follow redirects; the jar download never does, it follows them itself after checking each one
		HttpClient api = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();
		HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build();
		HttpResponse<String> response = api.send(request(LATEST).header("Accept", "application/vnd.github+json").build(),
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
		return ownJar(FabricLoader.getInstance().getGameDir().resolve("mods"), container.getOrigin().getPaths());
	}

	/** The first of the paths that is a real .jar inside the mods folder (links followed), or null. */
	static Path ownJar(Path modsFolder, Iterable<Path> paths) {
		Path mods = real(modsFolder);
		for (Path path : paths) {
			Path p = real(path);
			if (Files.isRegularFile(p) && p.getFileName().toString().endsWith(".jar") && p.startsWith(mods)) {
				return p;
			}
		}
		return null;
	}

	/** Launchers often link the mods or instance folder, so compare where things really are when that can be told. */
	private static Path real(Path path) {
		try {
			return path.toRealPath();
		} catch (IOException | RuntimeException e) {
			return path.toAbsolutePath().normalize();
		}
	}

	/** Downloads the new jar next to the old one and makes sure only one of them is left. True when it is in place. */
	static boolean install(HttpClient http, Path oldJar, UpdateCheck.Asset asset) throws Exception {
		Path dir = oldJar.getParent();
		Path target = dir.resolve(asset.name());
		if (!target.getFileName().toString().equals(asset.name()) || Files.exists(target)) {
			return false;
		}
		if (!allowedDownload(asset.url())) {
			LOG.warn("Update not downloaded: {} is not a release file of this mod", asset.url());
			return false;
		}
		if (windows() && !swapPathsSafe(oldJar, target)) {
			// The swap script could not handle this folder, so don't download a jar that would only be thrown away
			LOG.info("Update not downloaded: the mods folder path is not plain ASCII");
			return false;
		}
		// The download keeps a name Fabric ignores until it is checked
		Path part = dir.resolve(asset.name() + ".part");
		try {
			if (!fetch(http, asset.url(), part, MAX_DOWNLOAD, Updater::allowedRedirect)) {
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

	/** True when the address is a release download of this mod on github.com, and nothing else. */
	static boolean allowedDownload(String url) {
		if (url == null || !url.startsWith(DOWNLOAD_PREFIX)) {
			return false;
		}
		try {
			URI uri = new URI(url);
			String path = uri.getPath();
			return allowedRedirect(uri) && "github.com".equalsIgnoreCase(uri.getHost()) && path != null
					&& path.startsWith("/" + REPO + "/releases/download/") && !path.contains("..");
		} catch (java.net.URISyntaxException e) {
			return false;
		}
	}

	/** True for https to github.com or a *.githubusercontent.com host: no user info, no odd port. */
	static boolean allowedRedirect(URI uri) {
		if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
				|| uri.getPort() != -1 && uri.getPort() != 443) {
			return false;
		}
		String host = uri.getHost();
		if (host == null) {
			return false;
		}
		host = host.toLowerCase(Locale.ROOT);
		return host.equals("github.com") || host.endsWith(".githubusercontent.com");
	}

	/**
	 * Downloads the address to a file, following redirects by hand (at most 5) and only to places redirectOk accepts.
	 * True when the file holds a complete 200 body of at most max bytes; otherwise logs why, leaves no file, and is false.
	 */
	static boolean fetch(HttpClient http, String url, Path file, long max, java.util.function.Predicate<URI> redirectOk)
			throws IOException, InterruptedException {
		if (http.followRedirects() != HttpClient.Redirect.NEVER) {
			throw new IllegalArgumentException("the download client must not follow redirects by itself");
		}
		URI uri = URI.create(url);
		for (int hop = 0; hop <= MAX_HOPS; hop++) {
			HttpResponse<InputStream> response = http.send(request(uri.toString()).build(), HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream body = response.body()) {
				int status = response.statusCode();
				if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
					String location = response.headers().firstValue("Location").orElse(null);
					URI next;
					try {
						next = location == null ? null : uri.resolve(location);
					} catch (IllegalArgumentException e) {
						next = null;
					}
					if (next == null || !redirectOk.test(next)) {
						LOG.warn("Update download refused a redirect to {}", next == null ? "nowhere" : next.getHost());
						return false;
					}
					uri = next;
					continue;
				}
				if (status != 200) {
					LOG.info("Update download got HTTP {}", status);
					return false;
				}
				if (response.headers().firstValueAsLong("Content-Length").orElse(-1) > max) {
					LOG.warn("Update download refused: it announces more than {} bytes", max);
					return false;
				}
				if (!copyCapped(body, file, max)) {
					LOG.warn("Update download refused: it is bigger than {} bytes", max);
					return false;
				}
				return true;
			}
		}
		LOG.warn("Update download refused: too many redirects");
		return false;
	}

	/** Copies the stream to the file; past max bytes it stops, deletes the partial file and returns false. */
	static boolean copyCapped(InputStream in, Path file, long max) throws IOException {
		long total = 0;
		boolean ok = false;
		try (java.io.OutputStream out = Files.newOutputStream(file)) {
			byte[] buffer = new byte[65536];
			for (int n; (n = in.read(buffer)) > 0; ) {
				total += n;
				if (total > max) {
					return false;
				}
				out.write(buffer, 0, n);
			}
			ok = true;
			return true;
		} finally {
			if (!ok) {
				Files.deleteIfExists(file);
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
		if (!swapPathsSafe(oldJar, target)) {
			return false;
		}
		Path waiting = waitingName(target);
		Path bat = writeSwapScript(oldJar, waiting, target, part);
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

	private static Path waitingName(Path target) {
		return target.resolveSibling(target.getFileName() + ".new");
	}

	/**
	 * Whether the swap script can handle these places. cmd reads a .bat in the OEM code page, so any non-ASCII
	 * character (C:\Users\José) would be mangled and the swap would fail without a word. Characters with a meaning
	 * in a batch file are not worth the risk either. The script goes where its path is safe too (see
	 * {@link #scriptFolder}), so all of this can be checked before anything is downloaded.
	 */
	static boolean swapPathsSafe(Path oldJar, Path target) {
		for (Path p : List.of(oldJar, waitingName(target), target)) {
			if (!batchSafe(p.toString())) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Where the swap script goes: the temp folder, out of sight, unless cmd couldn't read its name there (C:\\Users\\José
	 * puts accents in it); then next to the old jar, whose place {@link #swapPathsSafe} has already checked. The script's
	 * own name is plain ASCII either way, and it deletes itself when it has run.
	 */
	static Path scriptFolder(Path temp, Path oldJar) {
		return batchSafe(temp.toAbsolutePath().toString()) ? temp : oldJar.getParent();
	}

	/** Moves the new jar to its waiting name and writes the script that swaps it in (see {@link #scriptFolder}). */
	static Path writeSwapScript(Path oldJar, Path waiting, Path target, Path part) throws IOException {
		Path temp = Path.of(System.getProperty("java.io.tmpdir"));
		Path bat = Files.createTempFile(scriptFolder(temp, oldJar), "skylyrics-update-", ".bat");
		try {
			Files.move(part, waiting, StandardCopyOption.REPLACE_EXISTING);
			Files.write(bat, swapScript(oldJar, waiting, target).getBytes(StandardCharsets.US_ASCII));
		} catch (IOException | RuntimeException e) {
			Files.deleteIfExists(bat);
			throw e;
		}
		return bat;
	}

	static String swapScript(Path oldJar, Path waiting, Path target) {
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
		return script;
	}

	private static boolean windows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}
}
