package dev.beli.skylyrics.media;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.beli.skylyrics.Tr;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Follows what plays on your Spotify account through the Spotify Web API, on any device: the desktop app, your
 * phone, a speaker.
 *
 * <p>Spotify lets a small app reach only five accounts, so there can't be one shared app for everyone. Each player
 * makes their own free app on the Spotify dashboard and pastes its Client ID. Signing in uses PKCE, the flow for
 * apps that can't keep a secret: the browser comes back to a tiny server on 127.0.0.1 with a one-time code, which
 * is swapped for tokens. Only the refresh token is kept. It goes in the cache folder, readable by you alone, and
 * not in the config folder, because modpacks often share that one.
 */
public final class SpotifyWebSource implements MediaSource {
	private static final Logger LOG = LoggerFactory.getLogger("SkyLyrics");
	/** The {@code preferredPlayer} value that picks this source. */
	public static final String KEY = "spotify-web";
	public static final int PORT = 43827;
	/** Has to be added, exactly like this, to the app on the Spotify dashboard. */
	public static final String REDIRECT = "http://127.0.0.1:" + PORT + "/callback";
	public static final String DASHBOARD = "https://developer.spotify.com/dashboard";
	static final String SCOPES = "user-read-currently-playing user-read-playback-state user-modify-playback-state"
			+ " user-library-read playlist-read-private playlist-read-collaborative";
	/** What the song library needs on top of reading the player; older sign-ins lack these. */
	static final String LIBRARY_SCOPES = "user-library-read playlist-read-private";
	private static final String ACCOUNTS = "https://accounts.spotify.com";
	private static final String API = "https://api.spotify.com/v1";
	/** The tracker's smooth clock fills the gaps, so once a second while playing is plenty. */
	private static final long POLL_PLAYING_NS = 1_000_000_000L;
	private static final long POLL_IDLE_NS = 4_000_000_000L;
	private static final int LOGIN_TIMEOUT_MS = 5 * 60_000;
	/** Asked for the access token a little early, so it never runs out halfway through a request. */
	private static final long EARLY_NS = 60_000_000_000L;
	private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

	public enum Account { NO_CLIENT_ID, SIGNED_OUT, WAITING, SIGNED_IN }

	/** Spotify said no to the refresh token: it was revoked or belongs to another app. */
	private static final class Refused extends IOException {
		Refused(String message) {
			super(message);
		}
	}

	private final Supplier<String> clientId;
	private final Path loginFile;
	private final SecureRandom random = new SecureRandom();
	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(6))
			.build();

	private volatile @Nullable String refreshToken;
	/** The Client ID the refresh token was made for; pasting another one means signing in again. */
	private volatile String tokenClient = "";
	/** What the user agreed to share at sign-in, space separated. */
	private volatile String grantedScopes = "";
	private @Nullable String accessToken;
	private long accessUntilNs;
	private volatile @Nullable ServerSocket loginServer;
	private volatile String problem = "";
	private Snapshot last = Snapshot.NOTHING;
	private long nextPollNs;

	public SpotifyWebSource(Supplier<String> clientId, Path loginFile) {
		this.clientId = clientId;
		this.loginFile = loginFile;
		load();
	}

	private String id() {
		String id = clientId.get();
		return id == null ? "" : id.strip();
	}

	public static boolean validClientId(String id) {
		return id != null && id.matches("[0-9a-fA-F]{32}");
	}

	public Account account() {
		String id = id();
		if (!validClientId(id)) {
			return Account.NO_CLIENT_ID;
		}
		if (loginServer != null) {
			return Account.WAITING;
		}
		return refreshToken != null && id.equals(tokenClient) ? Account.SIGNED_IN : Account.SIGNED_OUT;
	}

	/** True when signed in with a sign-in that also lets us read saved songs and playlists. */
	public boolean canBrowse() {
		return account() == Account.SIGNED_IN && covers(grantedScopes, LIBRARY_SCOPES);
	}

	static boolean covers(String granted, String needed) {
		List<String> have = List.of(granted.strip().split("\\s+"));
		for (String scope : needed.split(" ")) {
			if (!have.contains(scope)) {
				return false;
			}
		}
		return true;
	}

	/** Why the last sign-in didn't work, or empty. */
	public String problem() {
		return problem;
	}

	// Signing in

	/**
	 * Starts listening for the browser to come back and returns the Spotify page to open. Listening stops after a
	 * few minutes, after one sign-in, or on {@link #cancelLogin}.
	 */
	public synchronized String startLogin() throws IOException {
		String id = id();
		if (!validClientId(id)) {
			throw new IOException(Tr.t("Paste your Client ID first"));
		}
		cancelLogin();
		String verifier = B64.encodeToString(bytes(48));
		String state = B64.encodeToString(bytes(16));
		ServerSocket server = new ServerSocket(PORT, 4, InetAddress.getByAddress(new byte[] {127, 0, 0, 1}));
		loginServer = server;
		problem = "";
		Thread thread = new Thread(() -> awaitBrowser(server, id, verifier, state), "SkyLyrics Spotify sign-in");
		thread.setDaemon(true);
		thread.start();
		return authorizeUrl(id, challenge(verifier), state);
	}

	public synchronized void cancelLogin() {
		ServerSocket server = loginServer;
		loginServer = null;
		if (server != null) {
			try {
				server.close();
			} catch (IOException ignored) {
			}
		}
	}

	public synchronized void signOut() {
		cancelLogin();
		refreshToken = null;
		tokenClient = "";
		grantedScopes = "";
		accessToken = null;
		last = Snapshot.NOTHING;
		problem = "";
		try {
			Files.deleteIfExists(loginFile);
		} catch (IOException e) {
			LOG.warn("Couldn't remove the Spotify sign-in: {}", e.toString());
		}
	}

	private byte[] bytes(int n) {
		byte[] b = new byte[n];
		random.nextBytes(b);
		return b;
	}

	private void awaitBrowser(ServerSocket server, String id, String verifier, String state) {
		long deadline = System.currentTimeMillis() + LOGIN_TIMEOUT_MS;
		try (server) {
			while (true) {
				long left = deadline - System.currentTimeMillis();
				if (left <= 0) {
					throw new SocketTimeoutException();
				}
				server.setSoTimeout((int) left);
				try (Socket socket = server.accept()) {
					socket.setSoTimeout(5000);
					Map<String, String> query = callbackQuery(requestLine(socket.getInputStream()));
					// Browsers also ask for /favicon.ico and such; only the real answer counts
					if (query == null || !state.equals(query.get("state"))) {
						respond(socket.getOutputStream(), "404 Not Found", Tr.t("Nothing here."));
						continue;
					}
					String code = query.get("code");
					if (code == null) {
						problem = Tr.t("Sign-in was cancelled");
						respond(socket.getOutputStream(), "200 OK", problem);
						return;
					}
					try {
						accept(id, token(form("grant_type", "authorization_code", "code", code, "redirect_uri", REDIRECT,
								"client_id", id, "code_verifier", verifier)));
						respond(socket.getOutputStream(), "200 OK", Tr.t("Signed in. You can close this tab and go back to Minecraft."));
					} catch (IOException | InterruptedException e) {
						problem = Tr.t("Sign-in failed: %s", e.getMessage());
						respond(socket.getOutputStream(), "200 OK", problem);
					}
					return;
				}
			}
		} catch (SocketTimeoutException e) {
			problem = Tr.t("Sign-in timed out");
		} catch (IOException e) {
			// Closed by cancelLogin, or the browser hung up
		} finally {
			synchronized (this) {
				if (loginServer == server) {
					loginServer = null;
				}
			}
		}
	}

	private static String requestLine(InputStream in) throws IOException {
		StringBuilder line = new StringBuilder();
		for (int c; (c = in.read()) != -1 && c != '\n' && line.length() < 8192; ) {
			if (c != '\r') {
				line.append((char) c);
			}
		}
		return line.toString();
	}

	private static void respond(OutputStream out, String status, String message) throws IOException {
		String safe = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		byte[] body = ("<!doctype html><meta charset=utf-8><title>Beli's SkyLyrics</title>"
				+ "<body style=\"background:#14111f;color:#ece8f8;font:18px system-ui,sans-serif;display:grid;place-items:center;height:90vh\">"
				+ "<div style=\"text-align:center\"><div style=\"color:#b79cff;font-weight:700;margin-bottom:12px\">Beli's SkyLyrics</div>"
				+ safe + "</div>").getBytes(StandardCharsets.UTF_8);
		out.write(("HTTP/1.1 " + status + "\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + body.length
				+ "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
		out.write(body);
		out.flush();
	}

	/** The query of a {@code GET /callback?...} request line, or null for anything else. */
	static @Nullable Map<String, String> callbackQuery(String requestLine) {
		String[] parts = requestLine.split(" ");
		if (parts.length < 2 || !parts[0].equals("GET")) {
			return null;
		}
		String target = parts[1];
		int q = target.indexOf('?');
		if (!(q < 0 ? target : target.substring(0, q)).equals("/callback")) {
			return null;
		}
		Map<String, String> out = new HashMap<>();
		if (q >= 0) {
			for (String pair : target.substring(q + 1).split("&")) {
				int eq = pair.indexOf('=');
				try {
					out.put(URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8),
							eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
				} catch (IllegalArgumentException ignored) {
				}
			}
		}
		return out;
	}

	static String challenge(String verifier) {
		try {
			return B64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	static String authorizeUrl(String id, String challenge, String state) {
		return ACCOUNTS + "/authorize?" + form("client_id", id, "response_type", "code", "redirect_uri", REDIRECT,
				"code_challenge_method", "S256", "code_challenge", challenge, "scope", SCOPES, "state", state);
	}

	static String form(String... keysAndValues) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
			if (!sb.isEmpty()) {
				sb.append('&');
			}
			sb.append(URLEncoder.encode(keysAndValues[i], StandardCharsets.UTF_8)).append('=')
					.append(URLEncoder.encode(keysAndValues[i + 1], StandardCharsets.UTF_8));
		}
		return sb.toString();
	}

	// Tokens

	private JsonObject token(String body) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(ACCOUNTS + "/api/token"))
				.timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
		JsonObject json = object(response.body());
		if (response.statusCode() == 400 && "invalid_grant".equals(string(json, "error"))) {
			throw new Refused(string(json, "error_description"));
		}
		if (response.statusCode() != 200 || json == null || string(json, "access_token") == null) {
			String why = json == null ? null : string(json, "error_description");
			throw new IOException(why != null ? why : "HTTP " + response.statusCode());
		}
		return json;
	}

	private synchronized void accept(String id, JsonObject json) {
		accessToken = string(json, "access_token");
		long seconds = json.has("expires_in") ? json.get("expires_in").getAsLong() : 3600;
		accessUntilNs = System.nanoTime() + seconds * 1_000_000_000L - EARLY_NS;
		// Spotify may hand out a new refresh token; the old one then stops working
		String refresh = string(json, "refresh_token");
		String scope = string(json, "scope");
		boolean changed = scope != null && !scope.equals(grantedScopes);
		if (changed) {
			grantedScopes = scope;
		}
		if (refresh != null && (!refresh.equals(refreshToken) || !id.equals(tokenClient))) {
			refreshToken = refresh;
			tokenClient = id;
			changed = true;
		}
		if (changed && refreshToken != null) {
			save();
		}
		problem = "";
		nextPollNs = 0;
	}

	private synchronized String access() throws IOException, InterruptedException {
		String id = id();
		String refresh = refreshToken;
		if (refresh == null || !id.equals(tokenClient)) {
			throw new IOException(Tr.t("Sign in to Spotify below"));
		}
		if (accessToken != null && System.nanoTime() < accessUntilNs) {
			return accessToken;
		}
		try {
			accept(id, token(form("grant_type", "refresh_token", "refresh_token", refresh, "client_id", id)));
		} catch (Refused e) {
			signOut();
			problem = Tr.t("Spotify signed you out, sign in again");
			throw new IOException(problem);
		}
		return accessToken;
	}

	private void load() {
		try {
			if (Files.exists(loginFile)) {
				JsonObject json = object(Files.readString(loginFile, StandardCharsets.UTF_8));
				if (json != null) {
					refreshToken = string(json, "refreshToken");
					String client = string(json, "clientId");
					tokenClient = client == null ? "" : client;
					grantedScopes = orEmpty(string(json, "scopes"));
				}
			}
		} catch (IOException e) {
			LOG.warn("Couldn't read the Spotify sign-in: {}", e.toString());
		}
	}

	private void save() {
		JsonObject json = new JsonObject();
		json.addProperty("clientId", tokenClient);
		json.addProperty("refreshToken", refreshToken);
		json.addProperty("scopes", grantedScopes);
		try {
			Files.createDirectories(loginFile.getParent());
			Path tmp = loginFile.resolveSibling(loginFile.getFileName() + ".tmp");
			Files.deleteIfExists(tmp);
			try {
				Files.createFile(tmp, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
			} catch (UnsupportedOperationException e) {
				Files.createFile(tmp); // Windows: the user's own folder is private already
			}
			Files.writeString(tmp, json.toString(), StandardCharsets.UTF_8);
			Files.move(tmp, loginFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			LOG.warn("Couldn't save the Spotify sign-in: {}", e.toString());
		}
	}

	// Playing

	@Override
	public Snapshot poll() throws Exception {
		long now = System.nanoTime();
		if (now < nextPollNs) {
			return last;
		}
		// Set before asking, so a broken connection or a missing sign-in isn't retried twice a second
		nextPollNs = now + POLL_IDLE_NS;
		String token = access();
		long sent = System.nanoTime();
		HttpResponse<String> response = api("GET", "/me/player/currently-playing?additional_types=episode", token);
		if (response.statusCode() == 401) {
			synchronized (this) {
				accessToken = null;
			}
			sent = System.nanoTime();
			response = api("GET", "/me/player/currently-playing?additional_types=episode", access());
		}
		// The position was read somewhere during the round trip; the middle is the best guess
		long readAt = sent + (System.nanoTime() - sent) / 2;
		switch (response.statusCode()) {
			case 200 -> last = parse(response.body(), readAt);
			case 204 -> last = Snapshot.NOTHING;
			case 429 -> {
				long wait = response.headers().firstValueAsLong("Retry-After").orElse(10);
				nextPollNs = now + Math.max(1, Math.min(wait, 300)) * 1_000_000_000L;
				return last;
			}
			default -> throw new IOException(Tr.t("Spotify answered with error %s", response.statusCode()));
		}
		if (last.playing()) {
			nextPollNs = now + POLL_PLAYING_NS;
		}
		return last;
	}

	private HttpResponse<String> api(String method, String path, String token) throws IOException, InterruptedException {
		return api(method, path, token, null);
	}

	private HttpResponse<String> api(String method, String path, String token, @Nullable String json) throws IOException, InterruptedException {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(API + path))
				.timeout(Duration.ofSeconds(8))
				.header("Authorization", "Bearer " + token);
		if (json == null) {
			request.method(method, HttpRequest.BodyPublishers.noBody());
		} else {
			request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json));
		}
		return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	/** Calls the Web API as the signed-in user, fetching a fresh access token once if Spotify says it went stale. */
	HttpResponse<String> call(String method, String path, @Nullable String json) throws IOException, InterruptedException {
		HttpResponse<String> response = api(method, path, access(), json);
		if (response.statusCode() == 401) {
			synchronized (this) {
				accessToken = null;
			}
			response = api(method, path, access(), json);
		}
		return response;
	}

	/** The answer of {@code /me/player/currently-playing}; an ad or a private session reads as nothing. */
	static Snapshot parse(String body, long readAtNanos) {
		JsonObject json = object(body);
		JsonObject item = json == null ? null : child(json, "item");
		if (item == null) {
			return Snapshot.NOTHING;
		}
		String title = orEmpty(string(item, "name"));
		String artist;
		String album;
		JsonArray images;
		JsonObject show = child(item, "show");
		if (show != null) { // a podcast episode
			artist = orEmpty(string(show, "publisher"));
			album = orEmpty(string(show, "name"));
			images = item.has("images") && item.get("images").isJsonArray() ? item.getAsJsonArray("images") : array(show, "images");
		} else {
			List<String> names = new ArrayList<>();
			JsonArray artists = array(item, "artists");
			if (artists != null) {
				for (JsonElement a : artists) {
					if (a.isJsonObject() && string(a.getAsJsonObject(), "name") != null) {
						names.add(string(a.getAsJsonObject(), "name"));
					}
				}
			}
			artist = String.join(", ", names);
			JsonObject albumJson = child(item, "album");
			album = albumJson == null ? "" : orEmpty(string(albumJson, "name"));
			images = albumJson == null ? null : array(albumJson, "images");
		}
		long length = number(item, "duration_ms");
		long position = Math.max(0, Math.min(number(json, "progress_ms"), length > 0 ? length : Long.MAX_VALUE));
		boolean playing = json.has("is_playing") && json.get("is_playing").getAsBoolean();
		Track track = TitleCleaner.clean(new Track(title, artist, album, length, bestImage(images), "Spotify"));
		return new Snapshot(track, position, playing, readAtNanos);
	}

	/** The picture closest to 300 pixels wide, big enough for the bar and the sky without downloading 640. */
	static String bestImage(@Nullable JsonArray images) {
		String best = "";
		long bestDiff = Long.MAX_VALUE;
		if (images != null) {
			for (JsonElement e : images) {
				if (e.isJsonObject() && string(e.getAsJsonObject(), "url") != null) {
					long w = number(e.getAsJsonObject(), "width");
					long diff = w <= 0 ? 1000 : Math.abs(w - 300);
					if (diff < bestDiff) {
						bestDiff = diff;
						best = string(e.getAsJsonObject(), "url");
					}
				}
			}
		}
		return best;
	}

	static @Nullable JsonObject object(@Nullable String text) {
		try {
			JsonElement e = text == null || text.isBlank() ? null : JsonParser.parseString(text);
			return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	static @Nullable JsonObject child(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	static @Nullable JsonArray array(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
	}

	static @Nullable String string(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	static long number(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsLong() : 0;
	}

	static String orEmpty(@Nullable String s) {
		return s == null ? "" : s;
	}

	// Controls, which Spotify only allows with Premium

	private void control(String method, String path) {
		try {
			HttpResponse<String> response = api(method, path, access());
			if (response.statusCode() >= 300) {
				LOG.debug("Spotify control {} answered {}", path, response.statusCode());
			}
		} catch (IOException e) {
			LOG.debug("Spotify control failed", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		nextPollNs = 0; // show the result right away
	}

	@Override
	public void playPause() {
		control("PUT", last.playing() ? "/me/player/pause" : "/me/player/play");
	}

	@Override
	public void next() {
		control("POST", "/me/player/next");
	}

	@Override
	public void previous() {
		control("POST", "/me/player/previous");
	}

	@Override
	public void seek(long positionMs) {
		control("PUT", "/me/player/seek?position_ms=" + Math.max(0, positionMs));
	}

	@Override
	public String displayName() {
		return "Spotify";
	}

	@Override
	public void close() {
		nextPollNs = 0;
	}
}
