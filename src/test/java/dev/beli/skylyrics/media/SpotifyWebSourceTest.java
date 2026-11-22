package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpotifyWebSourceTest {
	private static final String ID = "0123456789abcdef0123456789abcdef";

	@Test
	void challengeMatchesThePkceExample() {
		// The worked example in RFC 7636, appendix B
		assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
				SpotifyWebSource.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
	}

	@Test
	void onlyTheCallbackPageCounts() {
		Map<String, String> q = SpotifyWebSource.callbackQuery("GET /callback?code=abc%2B1&state=x+y HTTP/1.1");
		assertEquals("abc+1", q.get("code"));
		assertEquals("x y", q.get("state"));
		assertEquals(Map.of("error", "access_denied"), SpotifyWebSource.callbackQuery("GET /callback?error=access_denied HTTP/1.1"));
		assertNull(SpotifyWebSource.callbackQuery("GET /favicon.ico HTTP/1.1"));
		assertNull(SpotifyWebSource.callbackQuery("POST /callback?code=abc HTTP/1.1"));
		assertNull(SpotifyWebSource.callbackQuery(""));
	}

	@Test
	void signInPageAsksForTheRightThings() {
		String url = SpotifyWebSource.authorizeUrl(ID, "CH", "ST");
		assertTrue(url.startsWith("https://accounts.spotify.com/authorize?client_id=" + ID + "&"));
		assertTrue(url.contains("redirect_uri=http%3A%2F%2F127.0.0.1%3A43827%2Fcallback"));
		assertTrue(url.contains("code_challenge_method=S256&code_challenge=CH"));
		assertTrue(url.contains("scope=user-read-currently-playing+user-read-playback-state"));
		assertTrue(url.endsWith("&state=ST"));
		assertEquals("a=1+2&b=%26", SpotifyWebSource.form("a", "1 2", "b", "&"));
	}

	@Test
	void clientIdsAre32HexCharacters() {
		assertTrue(SpotifyWebSource.validClientId(ID));
		assertTrue(SpotifyWebSource.validClientId(ID.toUpperCase()));
		assertFalse(SpotifyWebSource.validClientId(""));
		assertFalse(SpotifyWebSource.validClientId(null));
		assertFalse(SpotifyWebSource.validClientId(ID + "0"));
		assertFalse(SpotifyWebSource.validClientId("https://open.spotify.com/xyz"));
	}

	@Test
	void readsASong() {
		String body = """
				{"is_playing": true, "progress_ms": 61000, "currently_playing_type": "track",
				 "item": {"name": "Neon Skyline", "duration_ms": 200000,
				  "artists": [{"name": "Beli"}, {"name": "The Demo Band"}],
				  "album": {"name": "Night Drive", "images": [
				   {"url": "https://i/640", "width": 640}, {"url": "https://i/300", "width": 300}, {"url": "https://i/64", "width": 64}]}}}
				""";
		Snapshot s = SpotifyWebSource.parse(body, 42);
		assertEquals(new Track("Neon Skyline", "Beli, The Demo Band", "Night Drive", 200000, "https://i/300", "Spotify"), s.track());
		assertEquals(61000, s.positionMs());
		assertTrue(s.playing());
		assertEquals(42, s.readAtNanos());
	}

	@Test
	void readsAPodcastEpisode() {
		String body = """
				{"is_playing": false, "progress_ms": 5000,
				 "item": {"name": "Episode 3", "duration_ms": 1800000, "images": [{"url": "https://i/ep", "width": 300}],
				  "show": {"name": "Sky Talk", "publisher": "Beli Radio", "images": [{"url": "https://i/show", "width": 300}]}}}
				""";
		Snapshot s = SpotifyWebSource.parse(body, 0);
		assertEquals(new Track("Episode 3", "Beli Radio", "Sky Talk", 1800000, "https://i/ep", "Spotify"), s.track());
		assertFalse(s.playing());
	}

	@Test
	void adsAndOddAnswersAreNothing() {
		assertSame(Snapshot.NOTHING, SpotifyWebSource.parse("{\"is_playing\": true, \"item\": null}", 0));
		assertSame(Snapshot.NOTHING, SpotifyWebSource.parse("", 0));
		assertSame(Snapshot.NOTHING, SpotifyWebSource.parse("not json", 0));
		// A position past the end is kept at the end
		Snapshot s = SpotifyWebSource.parse("{\"progress_ms\": 9999, \"item\": {\"name\": \"A\", \"duration_ms\": 1000}}", 0);
		assertEquals(1000, s.positionMs());
	}

	@Test
	void accountFollowsTheClientIdAndTheSavedSignIn(@TempDir Path dir) throws IOException {
		Path login = dir.resolve("spotify-login.json");
		String[] id = {""};
		assertEquals(SpotifyWebSource.Account.NO_CLIENT_ID, new SpotifyWebSource(() -> id[0], login).account());
		id[0] = ID;
		assertEquals(SpotifyWebSource.Account.SIGNED_OUT, new SpotifyWebSource(() -> id[0], login).account());

		Files.writeString(login, "{\"clientId\": \"" + ID + "\", \"refreshToken\": \"r\"}");
		SpotifyWebSource source = new SpotifyWebSource(() -> id[0], login);
		assertEquals(SpotifyWebSource.Account.SIGNED_IN, source.account());
		// Another app's Client ID can't use this sign-in
		id[0] = "ffffffffffffffffffffffffffffffff";
		assertEquals(SpotifyWebSource.Account.SIGNED_OUT, source.account());

		id[0] = ID;
		source.signOut();
		assertEquals(SpotifyWebSource.Account.SIGNED_OUT, source.account());
		assertFalse(Files.exists(login));
	}

	/** Runs the sign-in wait with an idle limit generous enough that a loaded machine cannot drop a client mid-request. */
	private static Thread wait(SpotifyWebSource source, ServerSocket server) {
		return wait(source, server, 30_000);
	}

	/** Runs the sign-in wait on the given server with the given idle limit. */
	private static Thread wait(SpotifyWebSource source, ServerSocket server, int idleMs) {
		source.loginServer = server; // this sign-in is the current one, as startLogin would make it
		Thread t = new Thread(() -> source.awaitBrowser(server, ID, "verifier", "st", idleMs));
		t.setDaemon(true);
		t.start();
		return t;
	}

	private static ServerSocket freeServer() throws IOException {
		return new ServerSocket(0, 4, InetAddress.getByAddress(new byte[] {127, 0, 0, 1}));
	}

	private static String get(int port, String path) throws IOException {
		try (Socket s = new Socket(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port)) {
			s.setSoTimeout(30_000);
			s.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: x\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
			s.getOutputStream().flush();
			InputStream in = s.getInputStream();
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	void theWholeRequestIsReadSoClosingDoesNotResetTheBrowser() throws Exception {
		// Unread headers make macOS reset the connection on close, so the browser shows an error instead of our page
		java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(
				"GET /callback?state=s HTTP/1.1\r\nHost: 127.0.0.1\r\nAccept: */*\r\n\r\nNEXT".getBytes(StandardCharsets.US_ASCII));
		assertEquals("GET /callback?state=s HTTP/1.1", SpotifyWebSource.requestLine(in));
		assertEquals("NEXT", new String(in.readAllBytes(), StandardCharsets.US_ASCII));
	}

	@Test
	void anIdleConnectionDoesNotEndTheSignIn(@TempDir Path dir) throws Exception {
		SpotifyWebSource source = new SpotifyWebSource(() -> ID, dir.resolve("login.json"));
		try (ServerSocket server = freeServer(); Socket idle = new Socket(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), server.getLocalPort())) {
			// Short on purpose: this test is about the idle limit; still long enough for the follow-up request below
			Thread t = wait(source, server, 2000);
			// Wait until the server has dropped the idle connection, which is when its idle limit has passed
			// (the old loop gave up the whole sign-in here)
			idle.setSoTimeout(30_000);
			try {
				assertEquals(-1, idle.getInputStream().read(), "the idle connection should be dropped");
			} catch (java.net.SocketException reset) {
				// dropped with a reset, which is the same thing
			}
			assertTrue(t.isAlive(), "the sign-in must still be waiting");
			get(server.getLocalPort(), "/callback?state=st&error=access_denied");
			t.join(30_000);
			assertFalse(t.isAlive());
			assertEquals("Sign-in was cancelled", source.problem());
		}
	}

	@Test
	void aConnectionThatHangsUpDoesNotEndTheSignIn(@TempDir Path dir) throws Exception {
		SpotifyWebSource source = new SpotifyWebSource(() -> ID, dir.resolve("login.json"));
		try (ServerSocket server = freeServer()) {
			Thread t = wait(source, server);
			new Socket(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), server.getLocalPort()).close();
			try (Socket rude = new Socket(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), server.getLocalPort())) {
				rude.getOutputStream().write("GET /favicon.ico HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
				rude.setSoLinger(true, 0); // hang up without reading the answer
			}
			Thread.sleep(200);
			assertTrue(t.isAlive());
			assertTrue(get(server.getLocalPort(), "/callback?state=st&error=access_denied").contains("Sign-in was cancelled"));
			t.join(30_000);
			assertFalse(t.isAlive());
			assertEquals("Sign-in was cancelled", source.problem());
		}
	}

	@Test
	void aWrongStateIsRefusedAndTheWaitContinues(@TempDir Path dir) throws Exception {
		SpotifyWebSource source = new SpotifyWebSource(() -> ID, dir.resolve("login.json"));
		try (ServerSocket server = freeServer()) {
			Thread t = wait(source, server);
			assertTrue(get(server.getLocalPort(), "/callback?state=other&error=access_denied").startsWith("HTTP/1.1 404"));
			assertTrue(t.isAlive());
			server.close();
			t.join(30_000);
			assertFalse(t.isAlive());
			assertEquals("", source.problem());
		}
	}

	@Test
	void aRefreshIsOnlyKeptWhenNobodySignedInOrOutMeanwhile() {
		assertTrue(SpotifyWebSource.keepRefresh(3, 3));
		assertFalse(SpotifyWebSource.keepRefresh(3, 4));
	}

	@Test
	void aRetryAfterFromAnyCallHoldsThePoller(@TempDir Path dir) {
		long sec = 1_000_000_000L;
		SpotifyWebSource source = new SpotifyWebSource(() -> ID, dir.resolve("login.json"));
		long now = System.nanoTime();
		assertTrue(source.pollDue(now + 1, true));
		source.holdOff("30");
		long stamped = System.nanoTime(); // the hold ends between now + 30 s and stamped + 30 s
		assertFalse(source.pollDue(now + 29 * sec, true));
		assertTrue(source.pollDue(stamped + 31 * sec, true));
		// A shorter ask later never shortens the hold
		source.holdOff("2");
		assertFalse(source.pollDue(now + 29 * sec, true));
		assertTrue(source.pollDue(stamped + 31 * sec, true));

		// A date instead of seconds is 10 s, not a crash (a fresh source, so no earlier hold covers it)
		SpotifyWebSource dated = new SpotifyWebSource(() -> ID, dir.resolve("login2.json"));
		long start = System.nanoTime();
		dated.holdOff("Wed, 21 Oct 2026 07:28:00 GMT");
		long end = System.nanoTime();
		assertFalse(dated.pollDue(start + 9 * sec, true), "still held at 9 s");
		assertTrue(dated.pollDue(end + 11 * sec, true), "free after 10 s");
	}

	@Test
	void theWebPollSlowsDownWhenNobodyLooksAndCatchesUpWhenSomeoneDoes(@TempDir Path dir) {
		SpotifyWebSource source = new SpotifyWebSource(() -> ID, dir.resolve("login.json"));
		long now = System.nanoTime();
		source.settle(now); // as if just asked, nothing playing
		assertTrue(source.pollDue(now + 5_000_000_000L, true));   // looking: every 4 s
		assertFalse(source.pollDue(now + 5_000_000_000L, false)); // nobody: every 15 s
		assertFalse(source.pollDue(now + 14_000_000_000L, false));
		assertTrue(source.pollDue(now + 16_000_000_000L, false));
	}

	private static final long SEC = 1_000_000_000L;
	private static final Track SONG = new Track("Song", "Made Up Band", "Album", 200_000, "", "Spotify");

	private static Snapshot snap(long positionMs, boolean playing, long atSec) {
		return new Snapshot(SONG, positionMs, playing, atSec * SEC);
	}

	@Test
	void staleProgressRightAfterAPauseIsIgnoredOnce() {
		SpotifyWebSource.StaleGuard guard = new SpotifyWebSource.StaleGuard();
		Snapshot playing = snap(61_000, true, 100);
		// Paused within the next second; Spotify still says 60.8 s, behind the 61 s it already said
		Snapshot paused = guard.apply(playing, snap(60_800, false, 101));
		assertFalse(paused.playing(), "the pause itself shows");
		assertEquals(61_000, paused.positionMs(), "no further than the song is known to have got");
		// The next reading is not shielded again
		assertEquals(61_500, guard.apply(paused, snap(61_500, false, 102)).positionMs());
	}

	@Test
	void aPauseReadingBetweenTheLastOneAndNowIsBelieved() {
		SpotifyWebSource.StaleGuard guard = new SpotifyWebSource.StaleGuard();
		// Paused half a second after the last reading: 61.5 s is right, not 2 s behind
		Snapshot paused = guard.apply(snap(61_000, true, 100), snap(61_500, false, 102));
		assertFalse(paused.playing());
		assertEquals(61_500, paused.positionMs(), "the lyrics must not sit ahead of the pause");
	}

	@Test
	void staleProgressRightAfterAResumeIsIgnored() {
		SpotifyWebSource.StaleGuard guard = new SpotifyWebSource.StaleGuard();
		Snapshot paused = snap(60_000, false, 100);
		Snapshot resumed = guard.apply(paused, snap(59_000, true, 104));
		assertTrue(resumed.playing());
		assertEquals(60_000, resumed.positionMs());
	}

	@Test
	void aRealSeekBackStillApplies() {
		SpotifyWebSource.StaleGuard guard = new SpotifyWebSource.StaleGuard();
		Snapshot playing = snap(61_000, true, 100);
		// A seek is not a play or pause change: applied at once, however small
		assertEquals(40_000, guard.apply(playing, snap(40_000, true, 101)).positionMs());
		assertEquals(60_500, guard.apply(playing, snap(60_500, true, 101)).positionMs());
		// And a jump of several seconds with a pause at the same time is a seek too
		assertEquals(30_000, guard.apply(playing, snap(30_000, false, 101)).positionMs());
	}

	@Test
	void aStaleLookingReadingLongAfterAPauseIsBelieved() {
		SpotifyWebSource.StaleGuard guard = new SpotifyWebSource.StaleGuard();
		Snapshot paused = guard.apply(snap(61_000, true, 100), snap(61_000, false, 101));
		// 4 s on (the idle poll), still paused: the user dragged the bar back a second
		assertEquals(60_000, guard.apply(paused, snap(60_000, false, 105)).positionMs());
	}

	/** A network that answers the token call with a good token and every Web API call with the same status. */
	private static final class FakeHttp extends HttpClient {
		final int apiStatus;
		final List<String> calls = new ArrayList<>();
		/** Answers for the next Web API calls (a bare status such as "204" or "401" with no body, else a 200 body); empty means apiStatus with no body. */
		final java.util.Deque<String> apiAnswers = new java.util.ArrayDeque<>();
		/** Token endpoint: refresh tokens it accepts; any other one gets invalid_grant. Null accepts everything. */
		java.util.Set<String> acceptedRefresh;
		/** A new refresh token Spotify hands out with each access token, or null. */
		String newRefresh;
		final List<String> refreshed = new ArrayList<>();
		/** When set, the token endpoint answers 400 with this (non-JSON) body. */
		String tokenBadBody;
		/** Runs inside every Web API call, before the answer is handed back. */
		volatile Runnable duringApi;
		/** Runs inside every token call, before the answer is handed back. */
		volatile Runnable duringToken;
		/** When set, the token endpoint answers this status with {@link #tokenBadBody} (or "{}"). */
		int tokenStatus;
		/** A Retry-After header on the token answer, or null. */
		String tokenRetryAfter;
		/** The body sent with {@link #tokenStatus}, when it isn't {@link #tokenBadBody}'s 400. */
		String tokenStatusBody;
		/** When set, the token call or the Web API calls get no answer at all: the connection fails with this. */
		volatile IOException tokenThrows;
		volatile IOException apiThrows;

		FakeHttp(int apiStatus) {
			this.apiStatus = apiStatus;
		}

		private static String bodyOf(HttpRequest request) {
			StringBuilder sb = new StringBuilder();
			request.bodyPublisher().ifPresent(p -> p.subscribe(new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
				public void onSubscribe(java.util.concurrent.Flow.Subscription s) { s.request(Long.MAX_VALUE); }
				public void onNext(java.nio.ByteBuffer b) { sb.append(StandardCharsets.UTF_8.decode(b)); }
				public void onError(Throwable t) { }
				public void onComplete() { }
			}));
			return sb.toString();
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
			boolean token = request.uri().getHost().startsWith("accounts");
			calls.add(token ? "token" : "api");
			IOException cut = token ? tokenThrows : apiThrows;
			if (cut != null) {
				throw cut;
			}
			int status = token ? 200 : apiStatus;
			String body = token ? "{\"access_token\":\"fresh\",\"expires_in\":3600" + (newRefresh == null ? "" : ",\"refresh_token\":\"" + newRefresh + "\"") + "}" : "";
			if (token) {
				String sent = bodyOf(request);
				String used = sent.replaceAll(".*refresh_token=([^&]*).*", "$1");
				refreshed.add(used);
				if (duringToken != null) {
					duringToken.run();
				}
				if (tokenStatus != 0) {
					status = tokenStatus;
					body = tokenStatusBody != null ? tokenStatusBody : tokenBadBody == null ? "{}" : tokenBadBody;
				}
				if (tokenBadBody != null) {
					status = 400;
					body = tokenBadBody;
				}
				if (acceptedRefresh != null && !acceptedRefresh.contains(used)) {
					status = 400;
					body = "{\"error\":\"invalid_grant\",\"error_description\":\"Refresh token revoked\"}";
				}
			} else if (duringApi != null) {
				duringApi.run();
			}
			if (!token && !apiAnswers.isEmpty()) {
				String a = apiAnswers.poll();
				boolean bare = a.matches("\\d{3}");
				status = bare ? Integer.parseInt(a) : 200;
				body = bare ? "" : a;
			}
			final int statusF = status;
			final String bodyF = body;
			return (HttpResponse<T>) new HttpResponse<String>() {
				public int statusCode() { return statusF; }
				public HttpRequest request() { return request; }
				public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
				public HttpHeaders headers() {
					return HttpHeaders.of(token && tokenRetryAfter != null ? Map.of("Retry-After", List.of(tokenRetryAfter)) : Map.of(), (a, b) -> true);
				}
				public String body() { return bodyF; }
				public Optional<SSLSession> sslSession() { return Optional.empty(); }
				public URI uri() { return request.uri(); }
				public Version version() { return Version.HTTP_1_1; }
			};
		}

		@Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
		@Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
		@Override public Redirect followRedirects() { return Redirect.NEVER; }
		@Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
		@Override public SSLContext sslContext() { throw new UnsupportedOperationException(); }
		@Override public SSLParameters sslParameters() { return new SSLParameters(); }
		@Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
		@Override public Version version() { return Version.HTTP_1_1; }
		@Override public Optional<Executor> executor() { return Optional.empty(); }

		@Override
		public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler,
				HttpResponse.PushPromiseHandler<T> push) {
			throw new UnsupportedOperationException();
		}
	}

	private static SpotifyWebSource signedIn(Path dir, FakeHttp http) throws IOException {
		Path login = dir.resolve("login.json");
		Files.writeString(login, "{\"clientId\":\"" + ID + "\",\"refreshToken\":\"r\",\"scopes\":\"\"}");
		SpotifyWebSource source = new SpotifyWebSource(() -> ID, login);
		source.useHttp(http);
		return source;
	}

	@Test
	void aTokenSpotifyKeepsRefusingIsRefreshedOnceAndThenBacksOff(@TempDir Path dir) throws Exception {
		FakeHttp http = new FakeHttp(401);
		SpotifyWebSource source = signedIn(dir, http);
		IOException e = assertThrows(IOException.class, source::poll);
		// One refresh for the first ask and one more after its 401, then it gives up
		assertEquals(List.of("token", "api", "token", "api"), http.calls);
		assertEquals("Spotify signed you out, sign in again", e.getMessage());
		// And does not ask (or refresh) again at the next poll or the one after
		long now = System.nanoTime();
		assertFalse(source.pollDue(now + 2 * SEC, true));
		assertTrue(source.pollDue(now + 5 * SEC, true));
	}

	@Test
	void aControlWhoseTokenWentStaleIsSentAgainWithAFreshOne(@TempDir Path dir) throws Exception {
		// The game's clock says the access token has most of its hour left, but that clock stands still while the
		// computer sleeps (Linux, macOS): after waking Spotify refuses the token, and the press must not be lost
		FakeHttp http = new FakeHttp(204);
		SpotifyWebSource source = signedIn(dir, http);
		http.apiAnswers.add("401");
		source.next();
		assertEquals(List.of("token", "api", "token", "api"), http.calls, "renewed the token and asked for Next again");
	}

	@Test
	void aTooManyRequestsFromTheTokenCallHoldsThePollerForItsRetryAfter(@TempDir Path dir) throws Exception {
		FakeHttp http = new FakeHttp(200);
		http.tokenStatus = 429;
		http.tokenRetryAfter = "120";
		SpotifyWebSource source = signedIn(dir, http);
		IOException e = assertThrows(IOException.class, source::poll);
		assertEquals("Spotify is getting too many requests, wait a moment", e.getMessage());
		long now = System.nanoTime();
		assertFalse(source.pollDue(now + 100 * SEC, true), "still held at 100 s");
		assertTrue(source.pollDue(now + 125 * SEC, true), "free after the 120 s it asked for");
	}

	@Test
	void aDeletedSpotifyAppSignsOutInsteadOfRetryingForever(@TempDir Path dir) throws Exception {
		for (String error : new String[] {"invalid_client", "unauthorized_client"}) {
			FakeHttp http = new FakeHttp(200);
			http.tokenStatus = 400;
			http.tokenBadBody = "{\"error\":\"" + error + "\",\"error_description\":\"Invalid client\"}";
			SpotifyWebSource source = signedIn(dir, http);
			source.sleeper = ms -> fail("a rejected app has no other login worth waiting for");
			IOException e = assertThrows(IOException.class, source::poll, error);
			assertEquals("Spotify no longer accepts your Client ID, check it and sign in again", e.getMessage(), error);
			assertEquals(SpotifyWebSource.Account.SIGNED_OUT, source.account(), error);
			assertEquals(e.getMessage(), source.problem(), error);
			assertFalse(Files.exists(dir.resolve("login.json")), error);
		}
	}

	@Test
	void aConnectionIsCutOffAfterItsTotalTimeEvenIfBytesKeepTrickling() {
		long[] clock = {0};
		int[] timeout = {-1};
		// An endless header that never ends its line, one byte per read, each read taking one second
		InputStream trickle = new InputStream() {
			@Override
			public int read() {
				clock[0] += 1_000_000_000L;
				return 'a';
			}
		};
		InputStream bounded = SpotifyWebSource.bounded(trickle, SpotifyWebSource.REQUEST_DEADLINE_NS, () -> clock[0], ms -> timeout[0] = ms, 5000);
		assertThrows(java.net.SocketTimeoutException.class, () -> SpotifyWebSource.requestLine(bounded));
		assertTrue(clock[0] >= SpotifyWebSource.REQUEST_DEADLINE_NS && clock[0] <= SpotifyWebSource.REQUEST_DEADLINE_NS + 1_000_000_000L, "gave up at the deadline, not earlier or later");
		assertTrue(timeout[0] > 0 && timeout[0] <= 5000, "each read waits no longer than what is left");
	}

	@Test
	void theTimeoutDoesNotReplaceAnExchangeThatIsStillRunning(@TempDir Path dir) throws Exception {
		FakeHttp http = new FakeHttp(200);
		http.tokenStatus = 400;
		java.util.concurrent.CountDownLatch inToken = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		http.duringToken = () -> {
			inToken.countDown();
			try {
				release.await(30, java.util.concurrent.TimeUnit.SECONDS);
			} catch (InterruptedException ignored) {
			}
		};
		SpotifyWebSource source = new SpotifyWebSource(() -> ID, dir.resolve("login.json"));
		source.useHttp(http);
		source.loginTimeoutMs = 300;
		try (ServerSocket server = freeServer()) {
			Thread t = wait(source, server);
			java.util.concurrent.CompletableFuture<String> page = java.util.concurrent.CompletableFuture
					.supplyAsync(() -> {
						try {
							return get(server.getLocalPort(), "/callback?state=st&code=c");
						} catch (IOException e) {
							return "";
						}
					});
			assertTrue(inToken.await(30, java.util.concurrent.TimeUnit.SECONDS));
			// The deadline passes while the exchange is still out: it closes the listener, the exchange goes on
			long limit = System.nanoTime() + 30 * SEC;
			while (!server.isClosed() && System.nanoTime() < limit) {
				Thread.onSpinWait();
			}
			assertTrue(server.isClosed());
			assertEquals("", source.problem(), "the exchange's own result is still to come");
			release.countDown();
			assertTrue(page.get(30, java.util.concurrent.TimeUnit.SECONDS).contains("Sign-in failed"));
			t.join(30_000);
			assertEquals("Sign-in failed: Spotify didn't answer, try again", source.problem());
		}
	}

	@Test
	void anOldSignInCannotWriteTheNewAttemptsProblem(@TempDir Path dir) throws Exception {
		FakeHttp http = new FakeHttp(200);
		http.tokenStatus = 400;
		java.util.concurrent.CountDownLatch inToken = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		http.duringToken = () -> {
			inToken.countDown();
			try {
				release.await(30, java.util.concurrent.TimeUnit.SECONDS);
			} catch (InterruptedException ignored) {
			}
		};
		SpotifyWebSource source = new SpotifyWebSource(() -> ID, dir.resolve("login.json"));
		source.useHttp(http);
		source.loginPort = 0;
		try {
			String url = source.startLogin();
			int oldPort = source.boundLoginPort();
			String state = java.util.regex.Pattern.compile("state=([^&]*)").matcher(url).results().findFirst().get().group(1);
			java.util.concurrent.CompletableFuture<String> page = java.util.concurrent.CompletableFuture
					.supplyAsync(() -> {
						try {
							return get(oldPort, "/callback?state=" + state + "&code=c");
						} catch (IOException e) {
							return "";
						}
					});
			assertTrue(inToken.await(30, java.util.concurrent.TimeUnit.SECONDS));
			source.startLogin(); // cancel and start over while the old exchange is out
			release.countDown();
			// The old browser tab still gets its answer, and by then the old thread has done everything it will do
			assertTrue(page.get(30, java.util.concurrent.TimeUnit.SECONDS).contains("Sign-in failed"));
			assertEquals("", source.problem(), "the new attempt starts clean");
		} finally {
			source.cancelLogin();
		}
	}

	@Test
	void aNonJsonRefusalIsAnOrdinaryFailureNotACrash(@TempDir Path dir) throws Exception {
		FakeHttp http = new FakeHttp(200);
		http.tokenBadBody = "<html>captive portal</html>";
		SpotifyWebSource source = signedIn(dir, http);
		IOException e = assertThrows(IOException.class, source::poll);
		assertEquals("Spotify didn't answer, try again", e.getMessage());
		assertSame(e, source.failing());
		// The failure counted: the next poll is held back
		assertFalse(source.pollDue(System.nanoTime() + SEC / 2, true));
	}

	@Test
	void aSignInFinishingAfterTheClientIdChangedSavesNothing(@TempDir Path dir) throws Exception {
		Path login = dir.resolve("login.json");
		String[] id = {ID};
		FakeHttp http = new FakeHttp(200);
		http.newRefresh = "newref";
		SpotifyWebSource source = new SpotifyWebSource(() -> id[0], login);
		source.useHttp(http);
		source.loginPort = 0;
		String url = source.startLogin();
		int port = source.boundLoginPort();
		String state = url.replaceAll(".*[?&]state=([^&]*).*", "$1");
		try {
			id[0] = "ffffffffffffffffffffffffffffffff";
			String page = get(port, "/callback?state=" + state + "&code=abc");
			assertTrue(page.contains("Sign-in was cancelled"), page);
			assertFalse(Files.exists(login), "nothing saved under the old ID");
		} finally {
			source.cancelLogin();
		}
	}

	@Test
	void anExceptionWithoutAMessageStillGetsATextWithoutNull() {
		assertEquals("Something went wrong, try again", SpotifyWebSource.failureText(new NullPointerException()));
		assertEquals("Something went wrong, try again", SpotifyWebSource.failureText(new InterruptedException()));
		// Java's own words about a bug are no sentence for the player either
		assertEquals("Something went wrong, try again", SpotifyWebSource.failureText(new NumberFormatException("For input string: \"soon\"")));
		assertEquals("boom", SpotifyWebSource.failureText(new IOException("boom")));
	}

	private static final String PLAYING = "{\"is_playing\": true, \"progress_ms\": 1000, \"currently_playing_type\": \"track\","
			+ " \"item\": {\"name\": \"A\", \"uri\": \"spotify:track:1\", \"duration_ms\": 200000, \"artists\": [{\"name\": \"B\"}], \"album\": {\"name\": \"C\"}}}";

	@Test
	void anEmptyAnswerRightAfterASongIsAskedAgainSoonAndTheIdlePaceReturnsLater(@TempDir Path dir) throws Exception {
		FakeHttp http = new FakeHttp(200);
		SpotifyWebSource source = signedIn(dir, http);
		http.apiAnswers.add(PLAYING);
		http.apiAnswers.add("204");
		http.apiAnswers.add("204");
		http.apiAnswers.add("204");
		http.apiAnswers.add("204");
		source.settle(System.nanoTime() - 10 * SEC);
		assertTrue(source.poll().playing());
		source.settle(System.nanoTime() - 10 * SEC);
		assertTrue(source.poll().track().isNone()); // the empty answer
		long now = System.nanoTime();
		assertTrue(source.pollDue(now + 3 * SEC / 2, true), "a second later, not 4");
		// the next few empty answers keep the quick pace, then the idle pace takes over
		for (int i = 0; i < 3; i++) {
			assertTrue(source.pollDue(System.nanoTime() + 3 * SEC / 2, true), "ask " + i);
			source.settle(System.nanoTime() - 10 * SEC);
			assertTrue(source.poll().track().isNone());
		}
		assertFalse(source.pollDue(System.nanoTime() + 3 * SEC / 2, true));
		assertTrue(source.pollDue(System.nanoTime() + 5 * SEC, true));
	}

	@Test
	void aSongReadRightAfterASignInRenewalIsTimedFromTheReadNotFromBeforeTheRenewal(@TempDir Path dir) throws Exception {
		// After the computer slept, Spotify refuses the access token mid-song and it is renewed before asking again. On a
		// network that has just woken up the renewal can take a while; the position must not be dated back into it, or
		// the lyrics run ahead by half of it (and stay ahead for the song when that is under the tracker's jitter limit)
		FakeHttp http = new FakeHttp(200);
		SpotifyWebSource source = signedIn(dir, http);
		http.apiAnswers.add("401");
		http.apiAnswers.add(PLAYING);
		long[] renewedAt = {0};
		http.duringToken = () -> {
			try {
				Thread.sleep(400);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			renewedAt[0] = System.nanoTime();
		};
		Snapshot s = source.poll();
		assertEquals(List.of("token", "api", "token", "api"), http.calls);
		assertEquals(1000, s.positionMs());
		assertTrue(s.readAtNanos() >= renewedAt[0], "read " + (renewedAt[0] - s.readAtNanos()) / 1_000_000
				+ " ms before the renewal ended, when the song was asked for only after it");
	}

	@Test
	void anAnswerForTheOldAccountIsDroppedWhenThePlayerSignsOutMeanwhile(@TempDir Path dir) throws Exception {
		FakeHttp http = new FakeHttp(200);
		SpotifyWebSource source = signedIn(dir, http);
		http.apiAnswers.add(PLAYING);
		java.util.concurrent.CountDownLatch inFlight = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		http.duringApi = () -> {
			inFlight.countDown();
			try {
				assertTrue(release.await(30, java.util.concurrent.TimeUnit.SECONDS));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		};
		Snapshot[] got = new Snapshot[1];
		Throwable[] failed = new Throwable[1];
		Thread media = new Thread(() -> {
			try {
				got[0] = source.poll();
			} catch (Throwable t) {
				failed[0] = t;
			}
		});
		media.start();
		assertTrue(inFlight.await(30, java.util.concurrent.TimeUnit.SECONDS));
		source.signOut();
		release.countDown();
		media.join(30_000);
		assertFalse(media.isAlive());
		assertNull(failed[0]);
		assertTrue(got[0].track().isNone(), "the old account's song is not handed on");
		// Between polls the tracker is handed the stored reading: it must be empty too
		source.settle(System.nanoTime());
		assertTrue(source.poll().track().isNone());
	}

	@Test
	void aSourceThatWasIdleAllAlongKeepsTheIdlePace(@TempDir Path dir) {
		SpotifyWebSource source = new SpotifyWebSource(() -> ID, dir.resolve("login.json"));
		long now = System.nanoTime();
		source.settle(now);
		assertFalse(source.pollDue(now + 3 * SEC / 2, true));
		assertTrue(source.pollDue(now + 5 * SEC, true));
	}

	@Test
	void aRefusedRefreshAdoptsTheTokenAnotherGameCopySavedInsteadOfSigningOut(@TempDir Path dir) throws Exception {
		FakeHttp http = new FakeHttp(204);
		SpotifyWebSource source = signedIn(dir, http); // memory has "r"
		Path login = dir.resolve("login.json");
		Files.writeString(login, "{\"clientId\":\"" + ID + "\",\"refreshToken\":\"x\",\"scopes\":\"\"}");
		http.acceptedRefresh = java.util.Set.of("x");
		source.poll();
		assertEquals(List.of("r", "x"), http.refreshed);
		assertTrue(Files.exists(login));
		assertTrue(Files.readString(login).contains("\"x\""));
		assertEquals(SpotifyWebSource.Account.SIGNED_IN, source.account());
	}

	/** Runs a refresh that saves "r2" while the write is held at the gate; returns once the write has reached it. */
	private static Thread slowRefresh(SpotifyWebSource source, FakeHttp http, java.util.concurrent.CountDownLatch atGate,
			java.util.concurrent.CountDownLatch release) throws Exception {
		http.newRefresh = "r2";
		source.beforeSave = () -> {
			atGate.countDown();
			try {
				release.await(30, java.util.concurrent.TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		};
		Thread media = new Thread(() -> {
			try {
				source.poll();
			} catch (Exception ignored) {
			}
		});
		media.start();
		assertTrue(atGate.await(30, java.util.concurrent.TimeUnit.SECONDS));
		return media;
	}

	/** Runs the call on its own thread and says whether it came back within 15 s (the stuck save waits 30 s). */
	private static boolean returnsSoon(Runnable call) throws Exception {
		java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
		Thread t = new Thread(() -> {
			call.run();
			done.countDown();
		});
		t.setDaemon(true);
		t.start();
		return done.await(15, java.util.concurrent.TimeUnit.SECONDS);
	}

	@Test
	void aSlowSaveDoesNotHoldUpSigningOutOrSigningIn(@TempDir Path dir) throws Exception {
		FakeHttp http = new FakeHttp(204);
		SpotifyWebSource source = signedIn(dir, http);
		source.loginPort = 0;
		java.util.concurrent.CountDownLatch atGate = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		Thread media = slowRefresh(source, http, atGate, release);
		try {
			assertTrue(returnsSoon(() -> {
				try {
					source.startLogin();
				} catch (IOException e) {
					throw new AssertionError(e);
				}
			}), "Sign in waited for the save");
			assertTrue(returnsSoon(source::cancelLogin), "Cancel waited for the save");
			assertTrue(returnsSoon(() -> source.signInGeneration()), "the library worker waited for the save");
			assertTrue(returnsSoon(source::signOut), "Sign out waited for the save");
		} finally {
			release.countDown();
			media.join(30_000);
		}
	}

	@Test
	void aSaveThatFinishesAfterSigningOutDoesNotBringTheTokensBack(@TempDir Path dir) throws Exception {
		FakeHttp http = new FakeHttp(204);
		SpotifyWebSource source = signedIn(dir, http);
		java.util.concurrent.CountDownLatch atGate = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		Thread media = slowRefresh(source, http, atGate, release);
		try {
			source.signOut();
		} finally {
			release.countDown();
			media.join(30_000);
		}
		assertFalse(media.isAlive());
		assertFalse(Files.exists(dir.resolve("login.json")), "no tokens on disk after Sign out");
		assertEquals(SpotifyWebSource.Account.SIGNED_OUT, source.account());
	}

	@Test
	void twoSavesInARowLeaveTheNewerOneOnDisk(@TempDir Path dir) throws Exception {
		FakeHttp http = new FakeHttp(204);
		SpotifyWebSource source = signedIn(dir, http);
		java.util.concurrent.CountDownLatch atGate = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		Thread media = slowRefresh(source, http, atGate, release); // "r2" is held at the disk
		source.beforeSave = () -> { };
		com.google.gson.JsonObject next = new com.google.gson.JsonObject();
		next.addProperty("access_token", "a");
		next.addProperty("refresh_token", "r3");
		source.acceptAndSave(ID, next);
		release.countDown();
		media.join(30_000);
		assertFalse(media.isAlive());
		assertTrue(Files.readString(dir.resolve("login.json")).contains("\"r3\""), Files.readString(dir.resolve("login.json")));
	}

	@Test
	void saveAfterSaveWhileOneIsWritingLeavesTheLastOneOnDisk(@TempDir Path dir) throws Exception {
		FakeHttp http = new FakeHttp(204);
		SpotifyWebSource source = signedIn(dir, http);
		java.util.concurrent.CountDownLatch atGate = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		Thread media = slowRefresh(source, http, atGate, release); // "r2" is held at the disk
		source.beforeSave = () -> { };
		for (String token : List.of("r3", "r4", "r5")) {
			com.google.gson.JsonObject next = new com.google.gson.JsonObject();
			next.addProperty("access_token", "a");
			next.addProperty("refresh_token", token);
			source.acceptAndSave(ID, next); // each returns at once and leaves its change for the writer
		}
		release.countDown();
		media.join(30_000);
		assertFalse(media.isAlive());
		String saved = Files.readString(dir.resolve("login.json"));
		assertTrue(saved.contains("\"r5\""), saved);
	}

	@Test
	void savingARotatedTokenLeavesAnotherGamesTempFileAlone(@TempDir Path dir) throws Exception {
		FakeHttp http = new FakeHttp(204);
		SpotifyWebSource source = signedIn(dir, http);
		Path other = dir.resolve("login.json.tmp"); // the other game is writing its own copy right now
		Files.writeString(other, "half");
		http.newRefresh = "r2";
		source.poll();
		assertTrue(Files.readString(dir.resolve("login.json")).contains("\"r2\""));
		assertEquals("half", Files.readString(other));
		try (var files = Files.list(dir)) {
			assertEquals(java.util.Set.of("login.json", "login.json.tmp"), files.map(f -> f.getFileName().toString()).collect(java.util.stream.Collectors.toSet()));
		}
	}

	@Test
	void aRefusedRefreshWaitsForTheOtherGamesSaveToLand(@TempDir Path dir) throws Exception {
		FakeHttp http = new FakeHttp(204);
		SpotifyWebSource source = signedIn(dir, http); // memory has "r"; the file still has it too
		Path login = dir.resolve("login.json");
		http.acceptedRefresh = java.util.Set.of("x");
		int[] sleeps = {0};
		source.sleeper = ms -> {
			if (++sleeps[0] == 2) { // the other game's rotated token arrives during the wait
				try {
					Files.writeString(login, "{\"clientId\":\"" + ID + "\",\"refreshToken\":\"x\",\"scopes\":\"\"}");
				} catch (IOException e) {
					throw new java.io.UncheckedIOException(e);
				}
			}
		};
		source.poll();
		assertEquals(2, sleeps[0]);
		assertEquals(List.of("r", "x"), http.refreshed);
		assertTrue(Files.exists(login));
		assertEquals(SpotifyWebSource.Account.SIGNED_IN, source.account());
	}

	@Test
	void aRefusedRefreshWithNothingNewInTheFileStillSignsOut(@TempDir Path dir) throws Exception {
		FakeHttp http = new FakeHttp(204);
		SpotifyWebSource source = signedIn(dir, http);
		http.acceptedRefresh = java.util.Set.of();
		long[] slept = {0};
		source.sleeper = ms -> slept[0] += ms;
		assertThrows(IOException.class, source::poll);
		assertEquals(List.of("r"), http.refreshed);
		assertTrue(slept[0] > 0 && slept[0] <= 3500, "it waited a bounded while for the other game: " + slept[0]);
		assertFalse(Files.exists(dir.resolve("login.json")));
		assertEquals(SpotifyWebSource.Account.SIGNED_OUT, source.account());
	}

	@Test
	void parseReadsTheTrackAddress() {
		assertEquals("spotify:track:1", SpotifyWebSource.parse(PLAYING, 0).track().uri());
		Snapshot ep = SpotifyWebSource.parse("{\"item\": {\"name\": \"E\", \"uri\": \"spotify:episode:9\", \"show\": {\"name\": \"S\"}}}", 0);
		assertEquals("spotify:episode:9", ep.track().uri());
	}

	@Test
	void aNullOrMissingIsPlayingReadsAsPausedNotACrash() {
		Snapshot nul = SpotifyWebSource.parse("{\"is_playing\": null, \"item\": {\"name\": \"A\"}}", 0);
		assertEquals("A", nul.track().title());
		assertFalse(nul.playing());
		assertFalse(SpotifyWebSource.parse("{\"item\": {\"name\": \"A\"}}", 0).playing());
	}

	@Test
	void anAdOrAnUnknownThingIsNothingEvenWithAnItem() {
		for (String type : new String[] {"ad", "unknown"}) {
			assertSame(Snapshot.NOTHING, SpotifyWebSource.parse("{\"is_playing\": true, \"currently_playing_type\": \"" + type
					+ "\", \"item\": {\"name\": \"Spotify\", \"artists\": [{\"name\": \"Spotify\"}]}}", 0), type);
		}
		assertEquals("A", SpotifyWebSource.parse("{\"currently_playing_type\": \"episode\", \"item\": {\"name\": \"A\"}}", 0).track().title());
	}

	@Test
	void aLocalFileReadsLikeASongWithItsOwnAddress() {
		// A song from the computer's own files in Spotify: no id, nulls where Spotify has nothing, no cover
		String body = """
				{"timestamp": 1700000000000, "progress_ms": 12000, "is_playing": true, "currently_playing_type": "track",
				 "item": {"id": null, "href": null, "is_local": true, "type": "track", "duration_ms": 215000, "popularity": 0,
				  "name": "Neon Skyline", "uri": "spotify:local:Beli:Night+Drive:Neon+Skyline:215",
				  "artists": [{"id": null, "href": null, "uri": null, "name": "Beli", "type": "artist"}],
				  "album": {"id": null, "href": null, "uri": null, "name": "", "images": [], "release_date": null}}}
				""";
		Snapshot s = SpotifyWebSource.parse(body, 7);
		assertEquals(new Track("Neon Skyline", "Beli", "", 215000, "", "Spotify", "spotify:local:Beli:Night+Drive:Neon+Skyline:215"), s.track());
		assertEquals(12000, s.positionMs());
		assertTrue(s.playing());
		// Another file with the same tags is another song
		assertTrue(s.track().otherUri(new Track("Neon Skyline", "Beli", "", 215000, "", "Spotify", "spotify:local:Beli:Night+Drive:Neon+Skyline:216")));
	}

	@Test
	void oldTempFilesOfTheSignInAreRemovedAtStart(@TempDir Path dir) throws Exception {
		Path login = dir.resolve("login.json");
		Path stale = Files.writeString(dir.resolve("login.json.4411.tmp"), "token");
		Path fresh = Files.writeString(dir.resolve("login.json.4412.tmp"), "token");
		Files.setLastModifiedTime(stale, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() - 2 * 24L * 3600 * 1000));
		new SpotifyWebSource(() -> "", login);
		assertFalse(Files.exists(stale));
		assertTrue(Files.exists(fresh));
	}

	@Test
	void withoutAClientIdTheStatusSaysToPasteItNotToSignIn(@TempDir Path dir) {
		assertEquals("Paste your Client ID first", SpotifyWebSource.notSetUp("").getMessage());
		assertEquals("Paste your Client ID first", SpotifyWebSource.notSetUp("nope").getMessage());
		assertEquals("Sign in to Spotify below", SpotifyWebSource.notSetUp(ID).getMessage());
		SpotifyWebSource source = new SpotifyWebSource(() -> "", dir.resolve("spotify-login.json"));
		Exception e = assertThrows(IOException.class, source::poll);
		assertTrue(e instanceof SpotifyWebSource.NotSetUp);
		assertEquals("Paste your Client ID first", e.getMessage());
		assertTrue(source.failing() instanceof SpotifyWebSource.NotSetUp);
	}

	@Test
	void aBadConnectionDuringSignInIsNotShownAsARawError() {
		String net = "Can't reach the internet or Spotify";
		assertEquals(net, SpotifyWebSource.failureText(new java.net.http.HttpConnectTimeoutException("HTTP connect timed out")));
		assertEquals(net, SpotifyWebSource.failureText(new java.net.UnknownHostException("accounts.spotify.com")));
		assertEquals(net, SpotifyWebSource.failureText(new IOException("wrapped", new java.net.ConnectException())));
	}

	/** What Java says when a connection to Spotify breaks in other ways than the ones it has a type for. */
	private static List<IOException> brokenConnections() {
		return List.of(new java.net.SocketException("Connection reset"),
				new javax.net.ssl.SSLHandshakeException("Remote host terminated the handshake"),
				new javax.net.ssl.SSLHandshakeException("PKIX path building failed: unable to find valid certification path to requested target"),
				new IOException("HTTP/1.1 header parser received no bytes"),
				new java.io.EOFException("EOF reached while reading"));
	}

	@Test
	void aConnectionThatBreaksShowsTheInternetSentenceNotJavasWords(@TempDir Path dir) throws Exception {
		String net = "Can't reach the internet or Spotify";
		for (IOException broken : brokenConnections()) {
			for (boolean atToken : new boolean[] {true, false}) {
				FakeHttp http = new FakeHttp(200);
				if (atToken) {
					http.tokenThrows = broken;
				} else {
					http.apiThrows = broken;
				}
				SpotifyWebSource source = signedIn(dir, http);
				IOException e = assertThrows(IOException.class, source::poll);
				String where = broken.getMessage() + (atToken ? " (sign-in renewal)" : " (song)");
				// The Music tab's Status line and its help paragraph
				assertEquals(net, MediaTracker.failureText(e), where);
				// The toast blames the internet, not the music player
				assertTrue(MediaTracker.networkFailure(e), where);
				assertSame(broken, rootOf(e), "Java's own words are kept for the log: " + where);
			}
		}
	}

	@Test
	void aConnectionThatBreaksDuringSignInSaysSoInWords(@TempDir Path dir) throws Exception {
		FakeHttp http = new FakeHttp(200);
		http.tokenThrows = new javax.net.ssl.SSLHandshakeException("Remote host terminated the handshake");
		SpotifyWebSource source = new SpotifyWebSource(() -> ID, dir.resolve("login.json"));
		source.useHttp(http);
		try (ServerSocket server = freeServer()) {
			Thread t = wait(source, server);
			String page = get(server.getLocalPort(), "/callback?state=st&code=c");
			t.join(30_000);
			assertEquals("Sign-in failed: Can't reach the internet or Spotify", source.problem());
			assertTrue(page.contains("Sign-in failed: Can't reach the internet or Spotify"), page);
			assertFalse(page.contains("handshake"), page);
		}
	}

	private static Throwable rootOf(Throwable e) {
		while (e.getCause() != null) {
			e = e.getCause();
		}
		return e;
	}

	@Test
	void spotifysOwnErrorWordsAndNumbersAreNotShown(@TempDir Path dir) throws Exception {
		String busy = "Spotify is having problems right now";
		// The token call: Spotify's own description, or the bare status when there is none
		for (int status : new int[] {500, 502, 503}) {
			for (String body : new String[] {"{\"error\":\"server_error\",\"error_description\":\"Server error\"}", "<html>Bad gateway</html>"}) {
				FakeHttp http = new FakeHttp(200);
				http.tokenStatus = status;
				http.tokenStatusBody = body;
				IOException e = assertThrows(IOException.class, signedIn(dir, http)::poll);
				assertEquals(busy, MediaTracker.failureText(e), status + " " + body);
				assertTrue(rootOf(e).getMessage().contains(String.valueOf(status)), "the status is kept for the log");
			}
		}
		FakeHttp odd = new FakeHttp(200);
		odd.tokenBadBody = "{\"error\":\"invalid_request\",\"error_description\":\"Invalid refresh_token parameter\"}";
		assertEquals("Spotify didn't answer, try again", MediaTracker.failureText(assertThrows(IOException.class, signedIn(dir, odd)::poll)));
		// The song itself: a server error is said in words, not as "error 502"
		FakeHttp api = new FakeHttp(502);
		assertEquals(busy, MediaTracker.failureText(assertThrows(IOException.class, signedIn(dir, api)::poll)));
	}

	@Test
	void aSignInSpotifyRefusesSaysSoWithoutSpotifysEnglish(@TempDir Path dir) throws Exception {
		String[][] cases = {
				{"400", "{\"error\":\"invalid_grant\",\"error_description\":\"Invalid authorization code\"}", "Sign-in failed: Something went wrong, try again"},
				{"400", "{\"error\":\"invalid_client\",\"error_description\":\"Invalid client\"}", "Sign-in failed: Spotify no longer accepts your Client ID, check it and sign in again"},
				{"503", "{\"error\":\"server_error\",\"error_description\":\"Service unavailable\"}", "Sign-in failed: Spotify is having problems right now"},
				{"429", "", "Sign-in failed: Spotify is getting too many requests, wait a moment"}};
		for (String[] c : cases) {
			FakeHttp http = new FakeHttp(200);
			http.tokenStatus = Integer.parseInt(c[0]);
			http.tokenStatusBody = c[1];
			SpotifyWebSource source = new SpotifyWebSource(() -> ID, dir.resolve("login.json"));
			source.useHttp(http);
			try (ServerSocket server = freeServer()) {
				Thread t = wait(source, server);
				String page = get(server.getLocalPort(), "/callback?state=st&code=c");
				t.join(30_000);
				assertEquals(c[2], source.problem(), c[1]);
				assertTrue(page.contains(c[2]), page);
			}
		}
	}

	@Test
	void aSignInAnswerThatCantBeReadSaysSoInWords(@TempDir Path dir) throws Exception {
		FakeHttp http = new FakeHttp(200);
		http.tokenStatus = 200;
		http.tokenStatusBody = "{\"access_token\":\"fresh\",\"expires_in\":\"soon\"}";
		SpotifyWebSource source = new SpotifyWebSource(() -> ID, dir.resolve("login.json"));
		source.useHttp(http);
		try (ServerSocket server = freeServer()) {
			Thread t = wait(source, server);
			String page = get(server.getLocalPort(), "/callback?state=st&code=c");
			t.join(30_000);
			assertEquals("Sign-in failed: Something went wrong, try again", source.problem());
			assertFalse(page.contains("input string"), page);
		}
	}
}
