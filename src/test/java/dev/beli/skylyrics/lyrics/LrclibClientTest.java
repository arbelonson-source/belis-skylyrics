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
		String body = path.endsWith("/get") ? "{}" : searchBody;
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
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
}
