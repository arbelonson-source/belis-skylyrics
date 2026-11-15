package dev.beli.skylyrics.lyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.media.Track;

class LrclibClientTest {
	private static final Track TRACK = new Track("A Made-Up Song", "Made Up Band", "Made Up Album", 200_000, "", "spotify");
	private static final String HIT = "[{\"duration\":200,\"syncedLyrics\":\"[00:01.00]A made-up line\",\"instrumental\":false}]";

	private HttpServer server;
	private LrclibClient client;
	private final List<String> asked = new CopyOnWriteArrayList<>();
	private volatile int getCode = 404;
	private volatile int searchCode = 200;
	private volatile String searchBody = "[]";
	private volatile String getBody = "{}";
	private volatile String retryAfter;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/api", this::answer);
		server.start();
		client = new LrclibClient("test", "http://127.0.0.1:" + server.getAddress().getPort() + "/api");
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private void answer(HttpExchange ex) throws IOException {
		String path = ex.getRequestURI().getPath();
		asked.add(path);
		int code = path.endsWith("/get") ? getCode : searchCode;
		String body = path.endsWith("/get") ? getBody : searchBody;
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		if (retryAfter != null) {
			ex.getResponseHeaders().add("Retry-After", retryAfter);
		}
		ex.sendResponseHeaders(code, bytes.length);
		try (OutputStream out = ex.getResponseBody()) {
			out.write(bytes);
		}
	}

	@Test
	void getNotFoundFallsThroughToSearch() throws Exception {
		getCode = 404;
		searchBody = HIT;
		assertTrue(client.find(TRACK).found());
		assertEquals("/api/get", asked.get(0));
		assertEquals("/api/search", asked.get(1));
	}

	@Test
	void getServerErrorStillTriesSearch() throws Exception {
		getCode = 503;
		searchBody = HIT;
		LrclibClient.Result r = client.find(TRACK);
		assertEquals("[00:01.00]A made-up line", r.synced());
	}

	@Test
	void getServerErrorWithSearchErrorIsAnError() {
		getCode = 503;
		searchCode = 503;
		LrclibClient.StatusException e = assertThrows(LrclibClient.StatusException.class, () -> client.find(TRACK));
		assertEquals(503, e.status());
	}

	@Test
	void getServerErrorWithEmptySearchIsNoLyrics() throws Exception {
		getCode = 503;
		searchBody = "[]";
		assertFalse(client.find(TRACK).found());
	}

	@Test
	void getClientErrorIsStillAnError() {
		getCode = 429;
		searchBody = HIT;
		LrclibClient.StatusException e = assertThrows(LrclibClient.StatusException.class, () -> client.find(TRACK));
		assertEquals(429, e.status());
		assertEquals(1, asked.size());
	}

	@Test
	void emptyOkReplyIsATemporaryErrorNotNoLyrics() {
		for (String body : new String[] {"", "  \n", "nope", "null", "42"}) {
			getCode = 200;
			getBody = body;
			asked.clear();
			assertThrows(IOException.class, () -> client.find(TRACK), "get body '" + body + "'");
		}
		getCode = 404;
		for (String body : new String[] {"", "<html>log in</html>", "true"}) {
			searchBody = body;
			assertThrows(java.io.IOException.class, () -> client.find(TRACK), "search body '" + body + "'");
		}
	}

	@Test
	void forbiddenIsAStatusError() {
		getCode = 403;
		assertEquals(403, assertThrows(LrclibClient.StatusException.class, () -> client.find(TRACK)).status());
	}

	@Test
	void retryAfterIsKept() {
		getCode = 429;
		retryAfter = "7";
		assertEquals(7000, assertThrows(LrclibClient.StatusException.class, () -> client.find(TRACK)).retryAfterMs());
		assertEquals(-1, LrclibClient.retryAfterMs("Wed, 21 Oct 2026 07:28:00 GMT"));
		assertEquals(-1, LrclibClient.retryAfterMs(null));
	}

	private static final Track BARE = new Track("Made Up Song", "", "", 0, "", "browser");

	private static String hit(String name, String lyric) {
		return "{\"trackName\":\"" + name + "\",\"duration\":200,\"syncedLyrics\":\"" + lyric + "\",\"instrumental\":false}";
	}

	@Test
	void noArtistAndNoLengthNeedsTheSameTitle() throws Exception {
		searchBody = "[" + hit("Made Up Song (Remix Of Another Thing)", "[00:01.00]wrong") + "," + hit("MADE UP SONG", "[00:01.00]right") + "]";
		assertEquals("[00:01.00]right", client.find(BARE).synced());
	}

	@Test
	void noArtistAndNoLengthWithOnlyOtherTitlesFindsNothing() throws Exception {
		searchBody = "[" + hit("Something Else Entirely", "[00:01.00]wrong") + "]";
		assertFalse(client.find(BARE).found());
	}

	@Test
	void noArtistAndNoLengthWithSeveralSameTitlesPicksNone() throws Exception {
		searchBody = "[" + hit("Made Up Song", "[00:01.00]one") + "," + hit("Made Up Song", "[00:01.00]two") + "]";
		assertFalse(client.find(BARE).found());
	}

	@Test
	void anArtistStillAllowsALooserTitleMatch() throws Exception {
		searchBody = "[" + hit("Made Up Song - Remastered", "[00:01.00]fine") + "]";
		assertTrue(client.find(TRACK).found());
	}
}
