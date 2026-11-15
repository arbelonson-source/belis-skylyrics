package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
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

	/** Runs the sign-in wait on a free port with a short idle limit; returns the server's port once it listens. */
	private static Thread wait(SpotifyWebSource source, ServerSocket server) {
		Thread t = new Thread(() -> source.awaitBrowser(server, ID, "verifier", "st", 300));
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
			Thread t = wait(source, server);
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
		SpotifyWebSource source = new SpotifyWebSource(() -> ID, dir.resolve("login.json"));
		long now = System.nanoTime();
		assertTrue(source.pollDue(now + 1));
		source.holdOff("30");
		assertFalse(source.pollDue(now + 20_000_000_000L));
		assertTrue(source.pollDue(now + 31_000_000_000L));
		// A shorter ask later never shortens the hold
		source.holdOff("2");
		assertFalse(source.pollDue(now + 20_000_000_000L));
		// A date instead of seconds is 10 s, not a crash
		source.holdOff("Wed, 21 Oct 2026 07:28:00 GMT");
		assertFalse(source.pollDue(now + 20_000_000_000L));
	}
}
