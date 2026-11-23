package dev.beli.skylyrics.lyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

/**
 * LRCLIB and NetEase are sites on the internet, so a redirect from either (a network that tampers with the answer, the
 * site itself taken over) must not send the game's request into the player's own network: a router's or a printer's
 * page, a NAS. Covers were already kept out of it; the lyrics sites' redirects were followed wherever they led. Here the
 * "home network" is a second page on this computer's test server, which counts as local like any 127.x or 192.168.x one.
 */
class LyricsRedirectTest {
	private static final Track TRACK = new Track("A Made-Up Song", "Made Up Band", "Made Up Album", 200_000, "", "spotify");

	private HttpServer server;
	private String base;
	private final List<String> home = new CopyOnWriteArrayList<>();

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		int port = server.getAddress().getPort();
		// Every question to the "site" is sent on to a page in the home network
		server.createContext("/api", ex -> {
			ex.getResponseHeaders().add("Location", "http://127.0.0.1:" + port + "/home" + ex.getRequestURI().getRawPath());
			ex.sendResponseHeaders(302, -1);
			ex.close();
		});
		server.createContext("/home", ex -> {
			home.add(ex.getRequestURI().getPath());
			reply(ex, ex.getRequestURI().getPath().contains("/search/get") || ex.getRequestURI().getPath().contains("/song/")
					? "{\"code\":200}" : "[]");
		});
		server.start();
		base = "http://127.0.0.1:" + port + "/api";
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private static void reply(HttpExchange ex, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		ex.sendResponseHeaders(200, bytes.length);
		try (OutputStream out = ex.getResponseBody()) {
			out.write(bytes);
		}
	}

	@Test
	void anLrclibRedirectIntoTheHomeNetworkIsNotFollowed() {
		assertThrows(IOException.class, () -> new LrclibClient("test", base).find(TRACK), "the redirect was followed");
		assertEquals(List.of(), home, "the home network was asked");
	}

	@Test
	void aNeteaseRedirectIntoTheHomeNetworkIsNotFollowed() {
		assertThrows(IOException.class, () -> new NeteaseClient("test", base).find(TRACK), "the redirect was followed");
		assertEquals(List.of(), home, "the home network was asked");
	}
}
