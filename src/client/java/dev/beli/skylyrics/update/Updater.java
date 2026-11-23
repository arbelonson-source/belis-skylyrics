package dev.beli.skylyrics.update;

import dev.beli.skylyrics.util.LogText;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;
import java.util.zip.ZipFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.beli.skylyrics.SkyLyricsClient;
import dev.beli.skylyrics.config.SkyLyricsConfig.Updates;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.locale.Language;

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
	/** How long an update check's reply body may take after its headers. */
	private static final Duration REPLY_TIMEOUT = Duration.ofSeconds(60);

	/**
	 * What to tell the player: a new version is out, it is already downloaded and needs a restart, or it needs something
	 * newer than this game has ({@code unmet}, null when not).
	 */
	public record Notice(String version, boolean downloaded, Needs.Unmet unmet) {
		public Notice(String version, boolean downloaded) {
			this(version, downloaded, null);
		}

		/** What the new version needs, like "Fabric API 0.137.0 or newer", in the game's language as it is now; null when nothing. */
		public String needs() {
			return unmet == null ? null : unmet.text();
		}

		/** The thing to update, like "Fabric API"; null when nothing. */
		public String what() {
			return unmet == null ? null : unmet.name();
		}
	}

	/** The downloaded jar would not load on this game, so it was not installed. */
	static final class Incompatible extends Exception {
		final transient Needs.Unmet unmet;

		Incompatible(Needs.Unmet unmet) {
			super(unmet.text());
			this.unmet = unmet;
		}
	}

	private static volatile Notice notice;
	/**
	 * The game's language when the mod started, or null when the player's own was already loaded then. The mod starts
	 * before the loading screen, which loads the player's language only at its end, while the update check is often done
	 * in a second or two: until the language changes, a notice is kept back, or all its words would be English.
	 */
	private static volatile Language startLanguage;

	private Updater() {
	}

	/** The message to show, once. Null when there is nothing to say, or not yet: while the player's language is still loading. */
	public static Notice takeNotice() {
		Language start = startLanguage;
		if (start != null && Language.getInstance() == start) {
			return null;
		}
		Notice n = notice;
		notice = null;
		return n;
	}

	public static void start(Updates mode, String runningVersion) {
		Language now = Language.getInstance();
		startLanguage = now instanceof net.minecraft.client.resources.language.ClientLanguage ? null : now;
		if (mode == Updates.OFF) {
			return;
		}
		Thread thread = new Thread(() -> {
			try {
				check(mode, runningVersion);
			} catch (Exception e) {
				LOG.info("Update check skipped: {}", LogText.of(e));
			}
		}, "SkyLyrics-update");
		thread.setDaemon(true);
		thread.start();
	}

	private static void check(Updates mode, String runningVersion) throws Exception {
		String mcKey = UpdateCheck.mcKey(runningVersion);
		Path jar = ownJar();
		// A dev run has no Minecraft key; there is nothing to update. A jar that is not a plain file in mods (linked
		// from elsewhere) still gets the notice, it just can't be swapped
		if (mcKey == null) {
			return;
		}
		if (jar != null) {
			long now = System.currentTimeMillis();
			cleanLeftovers(jar.getParent(), now);
			cleanLeftovers(Path.of(System.getProperty("java.io.tmpdir")), now);
		}
		// Neither client follows redirects by itself: both follow them by hand after checking each one
		HttpClient api = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build();
		HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build();
		Path memory = FabricLoader.getInstance().getGameDir().resolve(".cache").resolve(SkyLyricsClient.MOD_ID)
				.resolve("update-refused.json");
		notice = look(mode, () -> {
			return fetchReply(api, LATEST, Updater::allowedApiRedirect);
		}, http, jar, runningVersion, mcKey, memory, Updater::running);
	}

	/**
	 * The release API's reply as text: follows at most MAX_HOPS redirects by hand, each only where redirectOk accepts.
	 * Null (with one line in the log) for a refused redirect, a non-200 answer or a reply that is too big.
	 */
	static String fetchReply(HttpClient api, String url, java.util.function.Predicate<URI> redirectOk)
			throws IOException, InterruptedException {
		return fetchReply(api, url, redirectOk, REPLY_TIMEOUT);
	}

	/**
	 * Like {@link #fetchReply(HttpClient, String, java.util.function.Predicate)}; a reply whose body hasn't come in
	 * {@code bodyLimit} after its headers is dropped, so a server that sends the headers and then goes silent can't hold
	 * the update check forever (the request's own time limit ends at the headers).
	 */
	static String fetchReply(HttpClient api, String url, java.util.function.Predicate<URI> redirectOk, Duration bodyLimit)
			throws IOException, InterruptedException {
		if (api.followRedirects() != HttpClient.Redirect.NEVER) {
			throw new IllegalArgumentException("the API client must not follow redirects by itself");
		}
		URI uri = URI.create(url);
		for (int hop = 0; hop <= MAX_HOPS; hop++) {
			HttpResponse<InputStream> response = api.send(request(uri.toString()).header("Accept", "application/vnd.github+json").build(),
					HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream in = response.body()) {
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
						LOG.info("Update check refused a redirect to {}", next == null ? "nowhere" : next.getHost());
						return null;
					}
					uri = next;
					continue;
				}
				if (status != 200) {
					LOG.info("Update check got HTTP {}", status);
					return null;
				}
				return readReply(dev.beli.skylyrics.util.Net.closeAfter(in, bodyLimit));
			}
		}
		LOG.info("Update check refused: too many redirects");
		return null;
	}

	/** The redirect rule for the API call: the download's rule, plus https to api.github.com itself. */
	static boolean allowedApiRedirect(URI uri) {
		if (allowedRedirect(uri)) {
			return true;
		}
		return uri != null && "https".equalsIgnoreCase(uri.getScheme()) && uri.getRawUserInfo() == null
				&& (uri.getPort() == -1 || uri.getPort() == 443) && "api.github.com".equalsIgnoreCase(uri.getHost());
	}

	/** The release API's reply as text, or null (with one line in the log) when it is bigger than 1 MB. */
	static String readReply(InputStream in) throws IOException {
		byte[] bytes = readCapped(in, MAX_API_REPLY);
		if (bytes == null) {
			LOG.info("Update check ignored a reply bigger than {} bytes", MAX_API_REPLY);
			return null;
		}
		return new String(bytes, StandardCharsets.UTF_8);
	}

	/** Asks for the latest release's JSON (null when there is none). */
	interface Latest {
		String get() throws Exception;
	}

	/**
	 * The notice for the latest release, or null. Off asks for nothing. The jar (null when it is not a plain file in
	 * mods) is needed only to install: without it, Install tells the player like "Tell me" does.
	 */
	static Notice look(Updates mode, Latest latest, HttpClient http, Path jar, String runningVersion, String mcKey, Path memory,
			UnaryOperator<String> running) throws Exception {
		if (mode == Updates.OFF || mcKey == null) {
			return null;
		}
		String body = latest.get();
		UpdateCheck.Release release = body == null ? null : UpdateCheck.parse(body);
		if (release == null || UpdateCheck.compare(release.version(), runningVersion) <= 0) {
			return null;
		}
		UpdateCheck.Asset asset = UpdateCheck.pick(release, mcKey);
		if (asset == null) {
			LOG.info("Release {} has no jar for Minecraft {}", release.version(), mcKey);
			return null;
		}
		if (jar == null && mode == Updates.INSTALL) {
			LOG.info("Update {} not installed: the mod's jar is not a plain file in the mods folder, telling the player instead", release.version());
			mode = Updates.NOTIFY;
		}
		if (jar != null && mode == Updates.INSTALL && packwizKeeps(jar)) {
			LOG.info("Update {} not installed: the modpack's installer (packwiz) keeps {} and would put it back next to the new one, telling the player instead",
					release.version(), jar.getFileName());
			mode = Updates.NOTIFY;
		}
		return update(mode, http, jar, release, asset, memory, running);
	}

	/**
	 * Installs the release when asked to. However the install fails (timeout, cut connection, bad zip,
	 * a move that does not work), the player is still told a new version is out, just not that it is downloaded.
	 */
	static Notice update(Updates mode, HttpClient http, Path jar, UpdateCheck.Release release, UpdateCheck.Asset asset) {
		return update(mode, http, jar, release, asset, null, Updater::running);
	}

	/**
	 * As above, remembering a refusal in {@code memory} (null for no memory): the same file on the same running versions is
	 * refused again without a download, and the player is told the same thing.
	 */
	static Notice update(Updates mode, HttpClient http, Path jar, UpdateCheck.Release release, UpdateCheck.Asset asset,
			Path memory, UnaryOperator<String> running) {
		boolean installed = false;
		if (mode == Updates.INSTALL) {
			Refusal known = Refusal.read(memory);
			if (known != null && known.matches(asset, running)) {
				LOG.info("Update {} not downloaded again: it needs {}", release.version(), known.unmet().text());
				return new Notice(release.version(), false, known.unmet());
			}
			try {
				installed = install(http, jar, asset, running);
				Refusal.forget(memory);
			} catch (Incompatible e) {
				LOG.info("Update {} not installed: it needs {}", release.version(), e.unmet.text());
				Refusal.of(asset, running, e.unmet).write(memory);
				return new Notice(release.version(), false, e.unmet);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (Exception e) {
				LOG.warn("Could not install the update, telling the player instead: {}", LogText.of(e));
				Refusal.forget(memory);
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
		return ownJar(modsFolder(FabricLoader.getInstance().getGameDir(), System.getProperty(MODS_FOLDER)), container.getOrigin().getPaths());
	}

	/** The Java setting that moves Fabric's mods folder away from the game folder ({@code -Dfabric.modsFolder=...}). */
	static final String MODS_FOLDER = "fabric.modsFolder";

	/**
	 * The folder Fabric loaded the mods from: the setting's folder when it is given (read the way Fabric Loader reads it, so
	 * a relative one is taken from where the game was started, as Fabric does), else the game folder's mods. Else a jar in such
	 * a folder counts as not in the mods folder, and Install only tells the player.
	 */
	static Path modsFolder(Path gameDir, String setting) {
		if (setting != null) {
			try {
				return Path.of(setting);
			} catch (java.nio.file.InvalidPathException e) {
				// Fabric couldn't have loaded from it either
			}
		}
		return gameDir.resolve("mods");
	}

	/** packwiz-installer's manifest, in the folder it installs a modpack into: every file it put there, each with its place. */
	static final String PACKWIZ_MANIFEST = "packwiz.json";
	private static final long MAX_PACKWIZ_MANIFEST = 4L * 1024 * 1024;

	/**
	 * True when a modpack's packwiz-installer put this jar here. It runs before every launch (a pre-launch command in Prism,
	 * MultiMC or ATLauncher) and downloads any file of its manifest that is missing again, so after a swap the old jar would
	 * be back next to the new one. Its manifest is in the pack folder, the game folder holding the mods folder, and gives
	 * each file's place relative to it ("cachedLocation", like "mods/x.jar"). A manifest that can't be read counts as none.
	 */
	static boolean packwizKeeps(Path jar) {
		Path mods = jar.getParent();
		Path pack = mods == null ? null : mods.getParent();
		if (pack == null) {
			return false;
		}
		Path manifest = pack.resolve(PACKWIZ_MANIFEST);
		try {
			if (!Files.isRegularFile(manifest) || Files.size(manifest) > MAX_PACKWIZ_MANIFEST) {
				return false;
			}
			var root = JsonParser.parseString(Files.readString(manifest, StandardCharsets.UTF_8));
			if (!root.isJsonObject() || !(root.getAsJsonObject().get("cachedFiles") instanceof JsonObject files)) {
				return false;
			}
			Path own = jar.toAbsolutePath().normalize();
			for (var entry : files.entrySet()) {
				if (entry.getValue() instanceof JsonObject file && string(file, "cachedLocation") instanceof String at) {
					try {
						if (pack.resolve(at).toAbsolutePath().normalize().equals(own)) {
							return true;
						}
					} catch (java.nio.file.InvalidPathException e) {
						// not a place on this computer
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			LOG.info("Could not read the modpack's {}: {}", PACKWIZ_MANIFEST, LogText.of(e));
		}
		return false;
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
		return install(http, oldJar, asset, Updater::running);
	}

	/** As above, with the running versions given (see {@link Needs#unmet}): the jar is refused when it would not load on them. */
	static boolean install(HttpClient http, Path oldJar, UpdateCheck.Asset asset, UnaryOperator<String> running) throws Exception {
		return install(http, oldJar, asset, running, Files::move);
	}

	/** As above, with the move of the jars given, so a test can make one fail. */
	static boolean install(HttpClient http, Path oldJar, UpdateCheck.Asset asset, UnaryOperator<String> running,
			dev.beli.skylyrics.util.SafeFile.Mover mover) throws Exception {
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
			// The swap could not handle this folder, so don't download a jar that would only be thrown away
			LOG.info("Update not downloaded: the mods folder path has a character the swap cannot carry, or is too long");
			return false;
		}
		// The download keeps a name Fabric ignores until it is checked, and one of its own: a second game sharing this folder may be updating too
		Path part = Files.createTempFile(dir, asset.name() + ".", ".part");
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
			Needs.Unmet unmet = Needs.unmet(dependsOf(part), running);
			if (unmet != null) {
				Files.deleteIfExists(part);
				throw new Incompatible(unmet);
			}
			if (windows()) {
				return installLater(oldJar, part, target);
			}
			keepMode(oldJar, part);
			// Like the Windows script: the old jar is set aside first, so a kill half way never leaves two jars with the mod's id
			Path aside = oldJar.resolveSibling(oldJar.getFileName() + ".skylyrics-old");
			boolean setAside = true;
			try {
				mover.move(oldJar, aside, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			} catch (java.nio.file.NoSuchFileException e) {
				setAside = false; // The other game removed it first: the new jar is ours to put in place
			}
			try {
				mover.move(part, target); // never over a jar another game just installed
			} catch (java.nio.file.FileAlreadyExistsException e) {
				// The new version is already in place: putting the old jar back would leave two jars with the mod's id
				if (setAside) {
					try {
						Files.deleteIfExists(aside);
					} catch (IOException ex) {
						LOG.info("Could not delete the old jar set aside: {}", LogText.of(ex));
					}
				}
				LOG.info("Another game already installed {}", target.getFileName());
				return false;
			} catch (IOException | RuntimeException e) {
				putBack(mover, aside, oldJar, setAside);
				throw e;
			}
			if (setAside) {
				try {
					Files.deleteIfExists(aside);
				} catch (IOException e) {
					// The game ignores the .skylyrics-old name, and the next start sweeps it
					LOG.info("Could not delete the old jar set aside: {}", LogText.of(e));
				}
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

	private static void putBack(dev.beli.skylyrics.util.SafeFile.Mover mover, Path aside, Path oldJar, boolean setAside) {
		if (!setAside) {
			return;
		}
		try {
			mover.move(aside, oldJar);
		} catch (IOException | RuntimeException e) {
			LOG.warn("Could not put the old jar back: {}", LogText.of(e));
		}
	}

	/** The stream's bytes, or null when there are more than max (a small jar can inflate a file to any size). */
	static byte[] readCapped(InputStream in, int max) throws IOException {
		byte[] bytes = in.readNBytes(max + 1);
		return bytes.length > max ? null : bytes;
	}

	private static final int MAX_MOD_JSON = 256 * 1024;
	private static final int MAX_API_REPLY = 1024 * 1024;

	/** The temp file is private (0600); give the new jar the old one's permissions, so what could read the old jar can read it. */
	private static void keepMode(Path oldJar, Path part) {
		try {
			if (part.getFileSystem().supportedFileAttributeViews().contains("posix")) {
				Files.setPosixFilePermissions(part, Files.getPosixFilePermissions(oldJar));
			}
		} catch (IOException | RuntimeException e) {
			LOG.info("Could not copy the old jar's permissions to the update: {}", LogText.of(e));
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

	/** The request timeout covers only the headers; the whole body gets this long (a jar takes seconds on a bad line too). */
	private static final java.time.Duration BODY_TIMEOUT = java.time.Duration.ofMinutes(5);

	/**
	 * Downloads the address to a file, following redirects by hand (at most 5) and only to places redirectOk accepts.
	 * True when the file holds a complete 200 body of at most max bytes; otherwise logs why, leaves no file, and is false.
	 */
	static boolean fetch(HttpClient http, String url, Path file, long max, java.util.function.Predicate<URI> redirectOk)
			throws IOException, InterruptedException {
		return fetch(http, url, file, max, redirectOk, BODY_TIMEOUT);
	}

	/** As above; a body still coming after {@code bodyLimit} is cut off (an IOException), so a stalled download can't hold the thread. */
	static boolean fetch(HttpClient http, String url, Path file, long max, java.util.function.Predicate<URI> redirectOk,
			java.time.Duration bodyLimit) throws IOException, InterruptedException {
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
				dev.beli.skylyrics.util.Net.closeAfter(body, bodyLimit);
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
		if (sha256 != null) {
			// The checksum first: nothing of a file that is not the published one is read as JSON
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream in = Files.newInputStream(file)) {
				byte[] buffer = new byte[65536];
				for (int n; (n = in.read(buffer)) > 0; ) {
					digest.update(buffer, 0, n);
				}
			}
			if (!HexFormat.of().formatHex(digest.digest()).equals(sha256)) {
				return false;
			}
		}
		try (ZipFile zip = new ZipFile(file.toFile())) {
			var entry = zip.getEntry("fabric.mod.json");
			if (entry == null) {
				return false;
			}
			try (InputStream in = zip.getInputStream(entry)) {
				byte[] bytes = readCapped(in, MAX_MOD_JSON);
				if (bytes == null) {
					LOG.info("Downloaded update's fabric.mod.json is bigger than {} bytes", MAX_MOD_JSON);
					return false;
				}
				JsonObject json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
				return SkyLyricsClient.MOD_ID.equals(string(json, "id"))
						&& (expectedVersion == null || expectedVersion.equals(string(json, "version")));
			}
		}
	}

	/** The "depends" of the jar's fabric.mod.json, or null when it has none or can't be read. */
	static JsonObject dependsOf(Path file) throws IOException {
		try (ZipFile zip = new ZipFile(file.toFile())) {
			var entry = zip.getEntry("fabric.mod.json");
			if (entry == null) {
				return null;
			}
			try (InputStream in = zip.getInputStream(entry)) {
				byte[] bytes = readCapped(in, MAX_MOD_JSON);
				if (bytes == null) {
					throw new IOException("fabric.mod.json is bigger than " + MAX_MOD_JSON + " bytes");
				}
				JsonObject json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
				return json.has("depends") && json.get("depends").isJsonObject() ? json.getAsJsonObject("depends") : null;
			}
		}
	}

	/** The version of what is running now, as Fabric Loader knows it; null when it isn't there. */
	static String running(String id) {
		if (id.equals("java")) {
			return String.valueOf(Runtime.version().feature());
		}
		FabricLoader loader = FabricLoader.getInstance();
		var container = loader.getModContainer(id);
		if (container.isEmpty() && id.equals("fabric-api")) {
			container = loader.getModContainer("fabric"); // Fabric API's id on old setups
		}
		return container.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(null);
	}

	private static String string(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
	}

	/**
	 * True when the swap can carry this path: the paths travel to the script as environment variables (no quoting, so
	 * spaces, accents, Hebrew, curly apostrophes, &, %, ^, ! and brackets are all fine), so only what no Windows path
	 * can hold is refused: a control character (a new line) or a quote.
	 */
	static boolean pathSafe(String s) {
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c < 0x20 || c == '"') {
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
		Path waiting = waitingFor(part, target);
		stage(part, waiting, Files::move);
		ProcessBuilder swap = swapProcess(powershell(), oldJar, waiting, target, ProcessHandle.current().pid());
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				swap.start();
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
	 * The waiting name of one game's download: the download's own unique name with .new in place of .part, so two games
	 * sharing a mods folder never stage over each other. It only adds digits to {@link #waitingName}, so that name's
	 * path check covers it.
	 */
	static Path waitingFor(Path part, Path target) {
		String name = part.getFileName().toString();
		return name.endsWith(".part") ? part.resolveSibling(name.substring(0, name.length() - 5) + ".new") : waitingName(target);
	}

	static final long LEFTOVER_AGE_MS = 24L * 60 * 60 * 1000;

	/**
	 * Deletes this mod's own leftovers in a folder: downloads (.part), Windows staged jars (.new) and the old jar set aside
	 * by a swap (.skylyrics-old), whose name starts with belis-skylyrics-, and the swap scripts (skylyrics-update-*.bat) that versions
	 * before 0.9 left in the temp or mods folder when the game was killed before they ran. Only those over a day old go:
	 * a fresh one may be another game's running download. Returns how many were deleted.
	 */
	static int cleanLeftovers(Path dir, long nowMs) {
		return cleanLeftovers(dir, nowMs, "belis-skylyrics-*", ".part", ".new", ".skylyrics-old")
				+ cleanLeftovers(dir, nowMs, "skylyrics-update-*", ".bat");
	}

	private static int cleanLeftovers(Path dir, long nowMs, String glob, String... endings) {
		int deleted = 0;
		try (var files = Files.newDirectoryStream(dir, glob)) {
			for (Path f : files) {
				String name = f.getFileName().toString();
				if (java.util.Arrays.stream(endings).noneMatch(name::endsWith)) {
					continue;
				}
				try {
					if (Files.isRegularFile(f, java.nio.file.LinkOption.NOFOLLOW_LINKS)
							&& nowMs - Files.getLastModifiedTime(f).toMillis() > LEFTOVER_AGE_MS && Files.deleteIfExists(f)) {
						deleted++;
					}
				} catch (IOException | RuntimeException e) {
					LOG.info("Could not remove the old leftover {}: {}", name, LogText.of(e));
				}
			}
		} catch (IOException | RuntimeException e) {
			LOG.info("Could not look for old update leftovers: {}", LogText.of(e));
		}
		return deleted;
	}

	/** Windows PowerShell 5.1 cannot open a path of 260 characters or more (long paths are off by default), so refuse well before. */
	static final int MAX_SWAP_PATH = 250;

	/** The staged name is the target's with .{@code <up to 19 digits>}.new added (see {@link #waitingFor}). */
	private static final int STAGED_EXTRA = 1 + 19 + 4;

	/**
	 * Whether the swap can handle these places (see {@link #pathSafe}, and none of the paths it touches too long: the old jar,
	 * the target, the staged jar as named at its longest, and the old jar's set-aside name); checked before anything is
	 * downloaded. Refused, the player is told to update by hand, and nothing is downloaded again.
	 */
	static boolean swapPathsSafe(Path oldJar, Path target) {
		for (Path p : List.of(oldJar, waitingName(target), target)) {
			if (!pathSafe(p.toString())) {
				return false;
			}
		}
		return oldJar.toAbsolutePath().toString().length() + ".skylyrics-old".length() < MAX_SWAP_PATH
				&& target.toAbsolutePath().toString().length() + STAGED_EXTRA < MAX_SWAP_PATH;
	}

	/** Moves the downloaded jar to its waiting name, trying again when Windows (an antivirus scanning the fresh file) briefly holds it. */
	static void stage(Path part, Path waiting, dev.beli.skylyrics.util.SafeFile.Mover mover) throws IOException {
		dev.beli.skylyrics.util.SafeFile.replace(part, waiting, mover, dev.beli.skylyrics.util.SafeFile.TRIES,
				dev.beli.skylyrics.util.SafeFile.PAUSE_MS);
	}

	/** Windows PowerShell 5.1, which comes with every Windows 10 and 11 (the music reader uses it too). */
	static String powershell() {
		String root = System.getenv("SystemRoot");
		if (root != null) {
			Path full = Path.of(root, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
			if (Files.isRegularFile(full)) {
				return full.toString();
			}
		}
		return "powershell.exe";
	}

	/**
	 * The swap as a process to start when the game has quit. The script is plain ASCII with no path in it: the paths come
	 * in the environment, so no name (Hebrew, accents, spaces, a curly apostrophe, &amp; % ^ !) can be mangled or break
	 * the quoting, and no file is left behind. It is run as a command, not a script file, so a signed-scripts-only
	 * policy doesn't stop it.
	 */
	static ProcessBuilder swapProcess(String exe, Path oldJar, Path waiting, Path target, long gamePid) {
		ProcessBuilder builder = new ProcessBuilder(swapCommand(exe));
		var env = builder.environment();
		env.remove("PSModulePath"); // a game started from PowerShell 7 hands down a path that confuses Windows PowerShell 5.1
		env.put("SKYLYRICS_OLD", oldJar.toString());
		env.put("SKYLYRICS_NEW", waiting.toString());
		env.put("SKYLYRICS_TARGET", target.toString());
		env.put("SKYLYRICS_PID", Long.toString(gamePid));
		builder.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
		return builder;
	}

	static List<String> swapCommand(String exe) {
		return List.of(exe, "-NoLogo", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", swapScript());
	}

	/**
	 * Waits for the game to quit, then: no staged jar, nothing is touched. The old jar is first set aside (renamed, tried for
	 * about a minute while Windows still holds it), then the new one moved into its place (tried again for ten seconds if an
	 * antivirus holds it); only when that worked is the old one deleted. If the new one can't be put in place, the old one
	 * is put back, so the folder never ends up without a SkyLyrics jar. The set-aside file is named
	 * {@code <old jar>.skylyrics-old}, so a player's own backup (.old) is never touched. Two games sharing one mods folder
	 * can each have an update staged: if the old jar is already gone and the target exists, another game's swap did the job,
	 * so this one deletes only its own staged jar and leaves everything else; and the set-aside file is only deleted or put
	 * back by the script that made it. One line, single quotes only, so it travels as one
	 * command-line argument.
	 */
	static String swapScript() {
		return String.join(";",
				"$ErrorActionPreference='SilentlyContinue'",
				"$o=$env:SKYLYRICS_OLD",
				"$w=$env:SKYLYRICS_NEW",
				"$t=$env:SKYLYRICS_TARGET",
				"$a=$o+'.skylyrics-old'",
				"if($env:SKYLYRICS_PID){try{Wait-Process -Id ([int]$env:SKYLYRICS_PID) -Timeout 120}catch{}}",
				"if(-not [IO.File]::Exists($w)){exit}",
				"$aside=$false",
				"$mine=$false",
				"for($i=0;$i -lt 20 -and -not $aside;$i++){if(-not [IO.File]::Exists($o)){if([IO.File]::Exists($t)){[IO.File]::Delete($w);exit};$aside=$true}else{try{[IO.File]::Delete($a);[IO.File]::Move($o,$a);$aside=$true;$mine=$true}catch{Start-Sleep -Seconds 3}}}",
				"if(-not $aside){[IO.File]::Delete($w);exit}",
				"$done=$false",
				"for($i=0;$i -lt 10 -and -not $done;$i++){try{[IO.File]::Delete($t);[IO.File]::Move($w,$t);$done=$true}catch{Start-Sleep -Seconds 1}}",
				"if($done){if($mine){try{[IO.File]::Delete($a)}catch{}}}elseif($mine){try{[IO.File]::Move($a,$o)}catch{}}");
	}

	private static boolean windows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}
}
