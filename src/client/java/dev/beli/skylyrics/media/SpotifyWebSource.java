package dev.beli.skylyrics.media;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
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
	private static final int LOGIN_TIMEOUT_MS = 5 * 60_000;
	/** How long the sign-in waits for the browser; a seam for tests. */
	int loginTimeoutMs = LOGIN_TIMEOUT_MS;
	/** The most time one connection gets in all, however slowly it trickles bytes. */
	static final long REQUEST_DEADLINE_NS = 10_000_000_000L;
	/** How long a connection may stay silent before it is dropped. */
	static final int SOCKET_TIMEOUT_MS = 5000;
	private static final int MAX_CONNECTIONS = 8;
	/** Asked for the access token a little early, so it never runs out halfway through a request. */
	private static final long EARLY_NS = 60_000_000_000L;
	private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

	public enum Account { NO_CLIENT_ID, SIGNED_OUT, WAITING, SIGNED_IN }

	/** Spotify said no to the refresh token: it was revoked or belongs to another app. */
	private static final class Refused extends IOException {
		/** The app itself is refused (deleted or reset on the dashboard), so no other login can help. */
		final boolean appRejected;

		Refused(String message, boolean appRejected) {
			super(message);
			this.appRejected = appRejected;
		}
	}

	/** Setup isn't finished (no Client ID yet, or not signed in): the player has a step to do, nothing is broken. */
	static final class NotSetUp extends IOException {
		NotSetUp(String message) {
			super(message);
		}
	}

	/**
	 * No answer came back at all: no internet, or a connection that was refused, broke off, timed out or couldn't be made
	 * safe. The player is told that in words; Java's own ("Connection reset", a handshake text) stays the cause, for the log.
	 */
	static final class Unreachable extends IOException {
		Unreachable(IOException cause) {
			super(Tr.t("Can't reach the internet or Spotify"), cause);
		}
	}

	/** What to tell a player whose Spotify source can't ask yet: the first missing step. */
	static NotSetUp notSetUp(String clientId) {
		return new NotSetUp(validClientId(clientId) ? Tr.t("Sign in to Spotify below") : Tr.t("Paste your Client ID first"));
	}

	private final Supplier<String> clientId;
	private final Path loginFile;
	/** Reads the sign-in file at start; a seam so tests can hold the file the way Windows sometimes does. */
	private final dev.beli.skylyrics.util.SafeFile.Reader loginReader;
	private final SecureRandom random = new SecureRandom();
	/** Built on first use: the SSL context and java.net.http classes cost tens of milliseconds at mod init. */
	private volatile @org.jspecify.annotations.Nullable HttpClient httpClient;

	/** The tests' stand-in for the network. */
	void useHttp(HttpClient fake) {
		httpClient = fake;
	}

	private HttpClient http() {
		HttpClient c = httpClient;
		if (c == null) {
			synchronized (this) {
				c = httpClient;
				if (c == null) {
					httpClient = c = HttpClient.newBuilder()
						.connectTimeout(Duration.ofSeconds(6))
						.build();
				}
			}
		}
		return c;
	}

	private volatile @Nullable String refreshToken;
	/** The Client ID the refresh token was made for; pasting another one means signing in again. */
	private volatile String tokenClient = "";
	/** What the user agreed to share at sign-in, space separated. */
	private volatile String grantedScopes = "";
	private @Nullable String accessToken;
	/** Counts sign-ins and sign-outs; a token refresh that started under an older number is thrown away. Guarded by this. */
	private long signInGeneration;
	/** Only one token refresh at a time; the network call runs under this, never under the main lock. */
	private final Object refreshLock = new Object();
	private long accessUntilNs;
	volatile @Nullable ServerSocket loginServer;
	private volatile String problem = "";
	/** Written under this lock (a sign-out must not be overwritten by an answer for the old account); read freely. */
	private volatile Snapshot last = Snapshot.NOTHING;
	/** When Spotify was last asked, how many asks in a row failed, and how long to wait after the last failure. */
	private long askedNs = System.nanoTime();
	private int failures;
	/** Why the last ask failed, until one works; the tracker keeps reporting it between retries. */
	private volatile @Nullable IOException lastFailure;
	private long failDelayNs;
	/** Quick re-asks still to make after Spotify answered empty right after a song (an ad, a device hand-off, a track change). */
	private int recheck;
	/** Set to ask again right away (a control was used, or the sign-in changed). */
	private volatile boolean pollNow = true;
	/** Whether anything on screen shows the song; when not, the poll slows right down. */
	private final BooleanSupplier looking;
	/** Nothing is asked before this: the latest Retry-After any call (poll, library, controls) was given. */
	private volatile long holdUntilNs = System.nanoTime();
	private final Object holdLock = new Object();
	private final StaleGuard staleGuard = new StaleGuard();

	public SpotifyWebSource(Supplier<String> clientId, Path loginFile) {
		this(clientId, loginFile, () -> true);
	}

	public SpotifyWebSource(Supplier<String> clientId, Path loginFile, BooleanSupplier looking) {
		this(clientId, loginFile, looking, f -> Files.readString(f, StandardCharsets.UTF_8));
	}

	SpotifyWebSource(Supplier<String> clientId, Path loginFile, BooleanSupplier looking, dev.beli.skylyrics.util.SafeFile.Reader loginReader) {
		this.clientId = clientId;
		this.loginFile = loginFile;
		this.looking = looking;
		this.loginReader = loginReader;
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
		ServerSocket server;
		try {
			server = new ServerSocket(loginPort, 4, InetAddress.getByAddress(new byte[] {127, 0, 0, 1}));
		} catch (BindException e) {
			throw new IOException(Tr.t("Spotify sign-in needs port %s, but another program is using it. Close the other game or app and try again", PORT), e);
		}
		loginServer = server;
		problem = "";
		Thread thread = new Thread(() -> awaitBrowser(server, id, verifier, state), "SkyLyrics Spotify sign-in");
		thread.setDaemon(true);
		thread.start();
		return authorizeUrl(id, challenge(verifier), state);
	}

	/** Port the sign-in listens on; tests set 0 so parallel test runs never fight over {@link #PORT}. */
	int loginPort = PORT;

	/** The port the running sign-in actually listens on, or -1. */
	synchronized int boundLoginPort() {
		ServerSocket server = loginServer;
		return server == null ? -1 : server.getLocalPort();
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

	/** Changes whenever someone signs in or out, so what was learned about one account isn't kept for the next. */
	synchronized long signInGeneration() {
		return signInGeneration;
	}

	public void signOut() {
		synchronized (this) {
			signOutInMemory();
		}
		flush();
	}

	/** The in-memory half of a sign-out; the file goes through {@link #flush} once the lock is free. */
	private synchronized void signOutInMemory() {
		cancelLogin();
		signInGeneration++;
		refreshToken = null;
		tokenClient = "";
		grantedScopes = "";
		accessToken = null;
		last = Snapshot.NOTHING;
		problem = "";
		pendingWrite = new Write(null);
	}

	private byte[] bytes(int n) {
		byte[] b = new byte[n];
		random.nextBytes(b);
		return b;
	}

	private void awaitBrowser(ServerSocket server, String id, String verifier, String state) {
		awaitBrowser(server, id, verifier, state, SOCKET_TIMEOUT_MS);
	}

	/**
	 * Answers the browser until the real redirect arrives, the deadline passes or the server is closed. Every
	 * connection gets its own short-lived thread, so an idle or hung-up socket (browsers open spare ones) can't
	 * hold up the real redirect or end the sign-in.
	 */
	void awaitBrowser(ServerSocket server, String id, String verifier, String state, int socketTimeoutMs) {
		long deadline = System.currentTimeMillis() + loginTimeoutMs;
		AtomicBoolean claimed = new AtomicBoolean();
		AtomicInteger open = new AtomicInteger();
		AtomicReference<Thread> winner = new AtomicReference<>();
		try {
			try (server) {
				while (true) {
					long left = deadline - System.currentTimeMillis();
					if (left <= 0) {
						throw new SocketTimeoutException();
					}
					server.setSoTimeout((int) left);
					Socket socket = server.accept();
					if (open.incrementAndGet() > MAX_CONNECTIONS) {
						open.decrementAndGet();
						closeQuietly(socket);
						continue;
					}
					Thread thread = new Thread(() -> {
						try (socket) {
							handle(socket, server, id, verifier, state, socketTimeoutMs, claimed, winner);
						} catch (IOException e) {
							// An idle socket or a browser that hung up: only this connection is over
						} finally {
							open.decrementAndGet();
						}
					}, "SkyLyrics Spotify sign-in connection");
					thread.setDaemon(true);
					thread.start();
				}
			} catch (SocketTimeoutException e) {
				// A sign-in already being exchanged reports its own result
				if (!claimed.get()) {
					setProblem(server, Tr.t("Sign-in timed out"));
				}
			} catch (IOException e) {
				// Closed by cancelLogin or after the answer came
			}
			Thread last = winner.get();
			if (last != null) {
				try {
					last.join(20_000);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		} finally {
			synchronized (this) {
				if (loginServer == server) {
					loginServer = null;
				}
			}
		}
	}

	private void handle(Socket socket, ServerSocket server, String id, String verifier, String state, int socketTimeoutMs,
			AtomicBoolean claimed, AtomicReference<Thread> winner) throws IOException {
		socket.setSoTimeout(socketTimeoutMs);
		Map<String, String> query = callbackQuery(requestLine(
				bounded(socket.getInputStream(), System.nanoTime() + REQUEST_DEADLINE_NS, System::nanoTime, ms -> {
					try {
						socket.setSoTimeout(ms);
					} catch (java.net.SocketException e) {
						throw new java.io.UncheckedIOException(e);
					}
				}, socketTimeoutMs)));
		// Browsers also ask for /favicon.ico and such; only the real answer counts, and only once
		if (query == null || !state.equals(query.get("state")) || !claimed.compareAndSet(false, true)) {
			respond(socket.getOutputStream(), "404 Not Found", Tr.t("Nothing here."));
			return;
		}
		winner.set(Thread.currentThread());
		try {
			String code = query.get("code");
			if (code == null) {
				String cancelled = Tr.t("Sign-in was cancelled");
				setProblem(server, cancelled);
				respond(socket.getOutputStream(), "200 OK", cancelled);
				return;
			}
			String message;
			try {
				JsonObject tokens = token(form("grant_type", "authorization_code", "code", code, "redirect_uri", REDIRECT,
						"client_id", id, "code_verifier", verifier));
				boolean finished = finishLogin(server, id, tokens);
				flush();
				if (finished) {
					message = Tr.t("Signed in. You can close this tab and go back to Minecraft.");
				} else {
					// Cancelled or signed out while Spotify was answering: don't sign the player back in
					message = Tr.t("Sign-in was cancelled");
				}
			} catch (IOException | InterruptedException | RuntimeException e) {
				message = Tr.t("Sign-in failed: %s", failureText(e));
				LOG.warn("Spotify sign-in failed: {}", dev.beli.skylyrics.util.LogText.chain(e));
				setProblem(server, message);
			}
			respond(socket.getOutputStream(), "200 OK", message);
		} finally {
			closeQuietly(server);
		}
	}

	/** Records why a sign-in failed, but only for the sign-in that is still the current one; an old attempt stays quiet. */
	private synchronized void setProblem(ServerSocket server, String text) {
		if (loginServer == server) {
			problem = text;
		}
	}

	/** Keeps the tokens only while this sign-in is still the current one. */
	private synchronized boolean finishLogin(ServerSocket server, String id, JsonObject tokens) {
		// Also refuse when the Client ID changed while the browser was open: the tokens belong to the old app
		if (loginServer != server || !id.equals(id())) {
			return false;
		}
		signInGeneration++;
		accept(id, tokens);
		return true;
	}

	/**
	 * What to show for a failure: the internet's fault when it is, else our own sentence. Our failures carry one in an
	 * IOException; anything else (a bug, an answer that can't be read) only has Java's words, so it gets a plain one (never "null").
	 */
	static String failureText(Throwable e) {
		if (MediaTracker.networkFailure(e)) {
			return Tr.t("Can't reach the internet or Spotify");
		}
		if (e instanceof Refused r) {
			// Spotify's reason ("Invalid authorization code") is English and says nothing to do: it goes to the log
			return r.appRejected ? Tr.t("Spotify no longer accepts your Client ID, check it and sign in again")
					: Tr.t("Something went wrong, try again");
		}
		String message = e.getMessage();
		return e instanceof IOException && message != null && !message.isBlank() ? message : Tr.t("Something went wrong, try again");
	}

	private static void closeQuietly(java.io.Closeable c) {
		try {
			c.close();
		} catch (IOException ignored) {
		}
	}

	/**
	 * The first line of the request. The headers after it are read too and dropped: closing a socket with unread
	 * data makes macOS reset the connection, so the browser would get an error instead of our page.
	 */
	static String requestLine(InputStream in) throws IOException {
		String first = line(in);
		for (int total = first.length(); total < 16384; ) {
			String header = line(in);
			if (header.isEmpty()) {
				break;
			}
			total += header.length();
		}
		return first;
	}

	/**
	 * The stream cut off at {@code deadlineNs} in all: a client trickling a byte at a time can't outlast it, since each
	 * read only waits for what is left of the time (at most {@code perReadMs}).
	 */
	static InputStream bounded(InputStream in, long deadlineNs, java.util.function.LongSupplier nowNs,
			java.util.function.IntConsumer setTimeoutMs, int perReadMs) {
		return new InputStream() {
			@Override
			public int read() throws IOException {
				long leftMs = (deadlineNs - nowNs.getAsLong()) / 1_000_000L;
				if (leftMs <= 0) {
					throw new SocketTimeoutException("The request took too long");
				}
				try {
					setTimeoutMs.accept((int) Math.min(perReadMs, leftMs));
				} catch (java.io.UncheckedIOException e) {
					throw e.getCause();
				}
				return in.read();
			}
		};
	}

	/** One line without its line end; empty at the end of the stream. */
	private static String line(InputStream in) throws IOException {
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
				+ "<div dir=auto>" + safe + "</div></div>").getBytes(StandardCharsets.UTF_8);
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
		HttpResponse<String> response = send(request);
		JsonObject json = object(response.body());
		if (response.statusCode() == 429) {
			holdOff(response); // the poller waits out what Spotify asked, like for the Web API
		}
		String error = json == null || response.statusCode() != 400 ? null : string(json, "error");
		if ("invalid_grant".equals(error)) {
			throw new Refused(string(json, "error_description"), false);
		}
		if ("invalid_client".equals(error) || "unauthorized_client".equals(error)) {
			throw new Refused(string(json, "error_description"), true);
		}
		if (response.statusCode() != 200 || json == null || string(json, "access_token") == null) {
			String why = json == null ? null : string(json, "error_description");
			// Spotify's own English words and the bare status are kept as the cause, for the log
			throw new IOException(answerText(response.statusCode()),
					new IOException("HTTP " + response.statusCode() + (why != null ? ": " + why : "")));
		}
		return json;
	}

	/** What the player is told when Spotify's sign-in server answered {@code status} instead of a token: never the bare number. */
	static String answerText(int status) {
		return status == 429 ? Tr.t("Spotify is getting too many requests, wait a moment")
				: status >= 500 ? Tr.t("Spotify is having problems right now")
				: Tr.t("Spotify didn't answer, try again");
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
			pendingWrite = new Write(snapshotJson());
		}
		problem = "";
		pollNow = true;
	}

	/** Whether a token refresh that began under {@code started} may still be stored: only if nobody signed in or out since. */
	static boolean keepRefresh(long started, long current) {
		return started == current;
	}

	private String access() throws IOException, InterruptedException {
		synchronized (refreshLock) {
			String id = id();
			String refresh;
			long started;
			synchronized (this) {
				refresh = refreshToken;
				if (refresh == null || !id.equals(tokenClient)) {
					throw notSetUp(id);
				}
				if (accessToken != null && System.nanoTime() < accessUntilNs) {
					return accessToken;
				}
				started = signInGeneration;
			}
			// The network call happens with the main lock free, so signing out or quitting never waits for it
			JsonObject result = null;
			boolean refused = false;
			boolean appRejected = false;
			String @Nullable [] adopted = null;
			try {
				result = token(form("grant_type", "refresh_token", "refresh_token", refresh, "client_id", id));
			} catch (Refused e) {
				if (e.appRejected) {
					refused = true;
					appRejected = true;
				} else {
					// A second game copy sharing this folder may have renewed the token and saved the new one: try that
					// once before calling it a sign-out (which would also delete the file the other copy just wrote)
					adopted = waitForOtherLogin(id, refresh);
					if (adopted == null) {
						refused = true;
					} else {
						try {
							result = token(form("grant_type", "refresh_token", "refresh_token", adopted[0], "client_id", id));
						} catch (Refused e2) {
							refused = true;
							appRejected = e2.appRejected;
						}
					}
				}
			}
			try {
				synchronized (this) {
					if (!keepRefresh(started, signInGeneration)) {
						throw notSetUp(id);
					}
					if (refused) {
						signOutInMemory();
						problem = appRejected ? Tr.t("Spotify no longer accepts your Client ID, check it and sign in again")
								: Tr.t("Spotify signed you out, sign in again");
						throw new IOException(problem);
					}
					if (adopted != null) {
						refreshToken = adopted[0];
						tokenClient = id;
						grantedScopes = adopted[1];
					}
					accept(id, result);
					return accessToken;
				}
			} finally {
				// The file is written with the main lock free, so a click on Sign out never waits for the disk
				flush();
			}
		}
	}

	/** Pauses between looks at the login file while another game may still be saving a renewed token: about 3 s in all. */
	long[] otherLoginWaitsMs = {250, 500, 750, 750, 750};

	/** How the waiting is done; a seam for tests. */
	@FunctionalInterface
	interface Sleeper {
		void sleep(long ms) throws InterruptedException;
	}

	Sleeper sleeper = Thread::sleep;

	/**
	 * Looks for a newer token in the login file; when there is none yet, looks a few more times over a short while, because
	 * the other game that was just refused the same token may not have saved its renewed one. Null when none turns up.
	 */
	private String @Nullable [] waitForOtherLogin(String id, String used) throws InterruptedException {
		String[] found = otherLogin(id, used);
		for (int i = 0; found == null && i < otherLoginWaitsMs.length; i++) {
			sleeper.sleep(otherLoginWaitsMs[i]);
			found = otherLogin(id, used);
		}
		return found;
	}

	/** The refresh token and scopes in the saved login when it is for this Client ID and differs from {@code used}, else null. */
	private String @Nullable [] otherLogin(String id, String used) {
		try {
			if (Files.exists(loginFile)) {
				JsonObject json = object(Files.readString(loginFile, StandardCharsets.UTF_8));
				String saved = json == null ? null : string(json, "refreshToken");
				if (saved != null && !saved.equals(used) && id.equals(string(json, "clientId"))) {
					return new String[] {saved, orEmpty(string(json, "scopes"))};
				}
			}
		} catch (IOException e) {
			LOG.warn("Couldn't read the Spotify sign-in: {}", e.toString());
		}
		return null;
	}

	private void load() {
		if (loginFile.getParent() != null) {
			dev.beli.skylyrics.util.Leftovers.cleanTemps(loginFile.getParent(), loginFile.getFileName().toString(), System.currentTimeMillis());
		}
		try {
			if (Files.exists(loginFile)) {
				if (dev.beli.skylyrics.util.SafeFile.tooBig(loginFile)) {
					damagedLogin("it is far too big to be ours");
					return;
				}
				// Tried again for a moment when Windows, an antivirus or OneDrive holds it, like the settings: else you start signed out
				String text = dev.beli.skylyrics.util.SafeFile.readText(loginFile, loginReader, dev.beli.skylyrics.util.SafeFile.READ_TRIES,
						dev.beli.skylyrics.util.SafeFile.READ_PAUSE_MS);
				JsonObject json = object(text);
				if (json != null) {
					refreshToken = string(json, "refreshToken");
					String client = string(json, "clientId");
					tokenClient = client == null ? "" : client;
					grantedScopes = orEmpty(string(json, "scopes"));
				} else if (!text.isBlank()) {
					damagedLogin("it isn't valid sign-in JSON");
				}
			}
		} catch (java.nio.charset.CharacterCodingException e) {
			damagedLogin("it isn't valid text");
		} catch (IOException e) {
			LOG.warn("Couldn't read the Spotify sign-in: {}", e.toString());
		}
	}

	/** The sign-in file can't be used: say so (never its text, it holds a token), keep a copy, and start signed out. */
	private void damagedLogin(String why) {
		LOG.warn("The Spotify sign-in file is damaged ({}), so you are signed out (kept a copy as {}.bad).", why, loginFile.getFileName());
		try {
			Path copy = dev.beli.skylyrics.util.SafeFile.keepBad(loginFile);
			// The copy can hold the token too. On Windows a copy gets the folder's access list, not the file's (on a second
			// drive that lets every account on the PC read it), so it is made private like the sign-in itself
			if (Files.getFileAttributeView(copy, java.nio.file.attribute.PosixFileAttributeView.class) == null) {
				dev.beli.skylyrics.util.PrivateFile.restrictToOwner(copy);
			}
		} catch (IOException | RuntimeException e) {
			LOG.warn("Couldn't keep a copy of the damaged Spotify sign-in file: {}", dev.beli.skylyrics.util.LogText.of(e));
		}
	}

	/** What the login file should hold next: the text to write, or null to remove the file. */
	private record Write(@Nullable String json) {
	}

	/** The newest file change nobody has written yet; a newer one replaces an older one. Guarded by this. */
	private @Nullable Write pendingWrite;
	/** True while one thread is writing the login file; the others leave their change in {@link #pendingWrite} for it. */
	private final java.util.concurrent.atomic.AtomicBoolean writing = new java.util.concurrent.atomic.AtomicBoolean();
	/** The tests' way to hold a save on its way to the disk. */
	volatile Runnable beforeSave = () -> { };

	/** Call with the main lock held. */
	private String snapshotJson() {
		JsonObject json = new JsonObject();
		json.addProperty("clientId", tokenClient);
		json.addProperty("refreshToken", refreshToken);
		json.addProperty("scopes", grantedScopes);
		return json.toString();
	}

	/** Package-private for the tests: stores tokens the way a refresh does, then writes the file. */
	void acceptAndSave(String id, JsonObject json) {
		synchronized (this) {
			accept(id, json);
		}
		flush();
	}

	/**
	 * Writes the pending file change without holding the main lock. One writer at a time, in the order the changes
	 * were made; a thread that finds a write running leaves its change to that writer and returns at once (so the
	 * render thread never waits for a slow disk). A sign-out's removal is queued like any save, so it always lands
	 * after a save that was already on its way.
	 */
	private void flush() {
		while (writing.compareAndSet(false, true)) {
			try {
				Write w;
				synchronized (this) {
					w = pendingWrite;
					pendingWrite = null;
				}
				if (w != null) {
					if (w.json() == null) {
						try {
							Files.deleteIfExists(loginFile);
						} catch (IOException e) {
							LOG.warn("Couldn't remove the Spotify sign-in: {}", e.toString());
						}
					} else {
						beforeSave.run();
						writeFile(w.json());
					}
				}
			} finally {
				writing.set(false);
			}
			synchronized (this) {
				if (pendingWrite == null) {
					return;
				}
			}
		}
	}

	private void writeFile(String text) {
		try {
			Files.createDirectories(loginFile.getParent());
			// A temp file of our own: a second game saving at the same moment must not write to or delete it
			Path tmp = Files.createTempFile(loginFile.getParent(), loginFile.getFileName() + ".", ".tmp");
			boolean done = false;
			try {
				// Owner-only already on Linux and macOS; Windows: the folder may be outside the user's profile (D:\Games) and readable by other accounts
				if (Files.getFileAttributeView(tmp, java.nio.file.attribute.PosixFileAttributeView.class) == null) {
					dev.beli.skylyrics.util.PrivateFile.restrictToOwner(tmp);
				}
				dev.beli.skylyrics.util.SafeFile.writeDurably(tmp, text);
				dev.beli.skylyrics.util.SafeFile.replace(tmp, loginFile);
				done = true;
			} finally {
				if (!done) {
					// Don't leave a partly written token file behind
					try {
						Files.deleteIfExists(tmp);
					} catch (IOException ignored) {
					}
				}
			}
		} catch (IOException e) {
			LOG.warn("Couldn't save the Spotify sign-in: {}", e.toString());
		}
	}

	// Playing

	/** Leaves Spotify alone for as long as it asked in a 429's {@code Retry-After}; the longest ask wins. */
	void holdOff(@Nullable String retryAfter) {
		long until = System.nanoTime() + SpotifyPace.retryAfterNs(retryAfter);
		synchronized (holdLock) {
			if (until - holdUntilNs > 0) {
				holdUntilNs = until;
			}
		}
	}

	private void holdOff(HttpResponse<?> response) {
		holdOff(response.headers().firstValue("Retry-After").orElse(null));
	}

	/** How many quick asks follow an empty answer that came right after a song, before the idle pace takes over. */
	static final int RECHECK_POLLS = 3;

	/** Notes that Spotify is being asked at {@code now}. */
	void settle(long now) {
		pollNow = false;
		askedNs = now;
	}

	/** Whether it is time to ask Spotify about the song again. */
	boolean pollDue(long now, boolean looking) {
		if (now - holdUntilNs < 0) {
			return false;
		}
		if (pollNow) {
			return true;
		}
		long wait = failures > 0 ? failDelayNs : SpotifyPace.intervalNs(last.playing() || recheck > 0, looking);
		return now - askedNs >= wait;
	}

	/** Spotify answered with a status that isn't an answer; the poller backs off by it. */
	private static final class BadStatus extends IOException {
		final int status;

		BadStatus(int status) {
			// The usual 403: a development-mode app only answers the accounts listed under User Management.
			// A 401 here means a token we had just refreshed was refused too, so only signing in again helps.
			super(status == 403 ? Tr.t("Spotify blocked this app. On the Spotify dashboard, add your account under User Management")
					: status == 401 ? Tr.t("Spotify signed you out, sign in again")
					: status >= 500 ? Tr.t("Spotify is having problems right now")
					: Tr.t("Spotify answered with error %s", status));
			this.status = status;
		}
	}

	@Override
	public Snapshot poll() throws Exception {
		long now = System.nanoTime();
		if (!pollDue(now, looking.getAsBoolean())) {
			return last;
		}
		// Marked before asking, so a broken connection or a missing sign-in isn't retried twice a second
		settle(now);
		try {
			Snapshot result = ask();
			failures = 0;
			lastFailure = null;
			return result;
		} catch (IOException e) {
			failures++;
			lastFailure = e;
			failDelayNs = SpotifyPace.failureDelayNs(e instanceof BadStatus b ? b.status : 0, failures);
			if (e instanceof BadStatus b && b.status == 401) {
				// The refresh that came before this 401 asked for an immediate re-poll; the backoff must rule instead
				pollNow = false;
			}
			throw e;
		}
	}

	@Override
	public @Nullable Throwable failing() {
		return lastFailure;
	}

	private Snapshot ask() throws IOException, InterruptedException {
		long generation = signInGeneration();
		String token = access();
		long sent = System.nanoTime();
		Snapshot before = last;
		HttpResponse<String> response = api("GET", "/me/player/currently-playing?additional_types=episode", token);
		if (response.statusCode() == 401) {
			synchronized (this) {
				accessToken = null;
			}
			// Renewed first: the round trip the position is dated in starts only when the song is asked for again
			String renewed = access();
			sent = System.nanoTime();
			response = api("GET", "/me/player/currently-playing?additional_types=episode", renewed);
		}
		// The position was read somewhere during the round trip; the middle is the best guess
		long readAt = sent + (System.nanoTime() - sent) / 2;
		switch (response.statusCode()) {
			case 200, 204 -> {
				synchronized (this) {
					if (!keepRefresh(generation, signInGeneration)) {
						// Signed out (or in again) while the answer was on its way: it belongs to the old account
						return Snapshot.NOTHING;
					}
					last = response.statusCode() == 200 ? staleGuard.apply(last, parse(response.body(), readAt)) : Snapshot.NOTHING;
				}
			}
			case 429 -> {
				holdOff(response);
				return last;
			}
			default -> throw new BadStatus(response.statusCode());
		}
		if (!last.track().isNone()) {
			recheck = 0;
		} else if (!before.track().isNone()) {
			recheck = RECHECK_POLLS;
		} else if (recheck > 0) {
			recheck--;
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
		return send(request.build());
	}

	/** Sends one request to Spotify; when no answer comes back at all, the failure says so in words (see {@link Unreachable}). */
	private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
		try {
			return http().send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException e) {
			throw new Unreachable(e);
		}
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
		if (response.statusCode() == 429) {
			holdOff(response); // the poller waits too
		}
		return response;
	}

	/**
	 * Right after a pause, resume or seek, Spotify's {@code progress_ms} is often the old position. A reading that
	 * would move the song back by under {@link #MAX_BEHIND_MS} within {@link #WINDOW_NS} of a play or pause change
	 * keeps the position we expected instead, once; the change itself still shows. A real seek back is several
	 * seconds and is not a play or pause change, so it always applies.
	 *
	 * <p>The response's {@code timestamp} could be used instead, but it is when Spotify's server took its reading,
	 * not when the progress was true, and it has been seen to be wrong on exactly these stale answers.
	 */
	static final class StaleGuard {
		static final long WINDOW_NS = 1_500_000_000L;
		static final long MAX_BEHIND_MS = 2_000;

		private long flipAt;
		private boolean used;

		Snapshot apply(Snapshot before, Snapshot reading) {
			if (before.track().isNone() || reading.track().isNone() || !before.track().equals(reading.track())) {
				flipAt = 0;
				return reading;
			}
			if (reading.playing() != before.playing()) {
				flipAt = reading.readAtNanos();
				used = false;
			}
			if (flipAt == 0 || used || reading.readAtNanos() - flipAt > WINDOW_NS) {
				return reading;
			}
			// Only count on while it played on: after a pause the song stopped somewhere between the two readings, so a
			// reading in that span is right, and only one behind the last reading is stale
			boolean ranOn = before.playing() && reading.playing();
			long expected = before.positionMs() + (ranOn ? (reading.readAtNanos() - before.readAtNanos()) / 1_000_000 : 0);
			long length = reading.track().lengthMs();
			if (length > 0) {
				expected = Math.min(expected, length);
			}
			long behind = expected - reading.positionMs();
			if (behind > 0 && behind < MAX_BEHIND_MS) {
				used = true;
				return new Snapshot(reading.track(), expected, reading.playing(), reading.readAtNanos(), reading.rate());
			}
			return reading;
		}
	}

	/** The answer of {@code /me/player/currently-playing}; an ad or a private session reads as nothing. */
	static Snapshot parse(String body, long readAtNanos) {
		JsonObject json = object(body);
		JsonObject item = json == null ? null : child(json, "item");
		String type = json == null ? null : string(json, "currently_playing_type");
		if (item == null || "ad".equals(type) || "unknown".equals(type)) {
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
		// A missing or null is_playing reads as paused
		boolean playing = json.get("is_playing") instanceof JsonPrimitive p && p.isBoolean() && p.getAsBoolean();
		Track track = TitleCleaner.clean(new Track(title, artist, album, length, bestImage(images), "Spotify", orEmpty(string(item, "uri"))));
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
			// Through call(), so a token Spotify calls stale (the game's clock stood still while the computer slept) is
			// renewed and the press sent again instead of lost, and a 429 holds the poller too
			HttpResponse<String> response = call(method, path, null);
			if (response.statusCode() >= 300) {
				LOG.debug("Spotify control {} answered {}", path, response.statusCode());
			}
		} catch (IOException e) {
			LOG.debug("Spotify control failed", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		pollNow = true; // show the result right away
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
		pollNow = true;
	}
}
