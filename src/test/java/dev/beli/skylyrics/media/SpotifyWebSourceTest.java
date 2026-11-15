package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
		return wait(source, server, 2500);
	}

	/** Runs the sign-in wait on the given server with the given idle limit. */
	private static Thread wait(SpotifyWebSource source, ServerSocket server, int idleMs) {
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
			Thread t = wait(source, server, 1000);
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
		/** Answers for the next Web API calls ("204" for no content, else a 200 body); empty means apiStatus with no body. */
		final java.util.Deque<String> apiAnswers = new java.util.ArrayDeque<>();
		/** Token endpoint: refresh tokens it accepts; any other one gets invalid_grant. Null accepts everything. */
		java.util.Set<String> acceptedRefresh;
		final List<String> refreshed = new ArrayList<>();

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
		public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
			boolean token = request.uri().getHost().startsWith("accounts");
			calls.add(token ? "token" : "api");
			int status = token ? 200 : apiStatus;
			String body = token ? "{\"access_token\":\"fresh\",\"expires_in\":3600}" : "";
			if (token) {
				String sent = bodyOf(request);
				String used = sent.replaceAll(".*refresh_token=([^&]*).*", "$1");
				refreshed.add(used);
				if (acceptedRefresh != null && !acceptedRefresh.contains(used)) {
					status = 400;
					body = "{\"error\":\"invalid_grant\",\"error_description\":\"Refresh token revoked\"}";
				}
			} else if (!apiAnswers.isEmpty()) {
				String a = apiAnswers.poll();
				status = a.equals("204") ? 204 : 200;
				body = a.equals("204") ? "" : a;
			}
			final int statusF = status;
			final String bodyF = body;
			return (HttpResponse<T>) new HttpResponse<String>() {
				public int statusCode() { return statusF; }
				public HttpRequest request() { return request; }
				public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
				public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
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

	@Test
	void aRefusedRefreshWithNothingNewInTheFileStillSignsOut(@TempDir Path dir) throws Exception {
		FakeHttp http = new FakeHttp(204);
		SpotifyWebSource source = signedIn(dir, http);
		http.acceptedRefresh = java.util.Set.of();
		assertThrows(IOException.class, source::poll);
		assertEquals(List.of("r"), http.refreshed);
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
}
