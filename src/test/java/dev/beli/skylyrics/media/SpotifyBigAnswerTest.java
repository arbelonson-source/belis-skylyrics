package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * An answer far bigger than any Spotify sends: a broken proxy, a captive portal or a server posing as Spotify sends
 * megabytes, or never stops. Every answer was read whole into memory however big it was, while the lyrics sites' are
 * capped at 2 MB; now one over {@link SpotifyWebSource#MAX_REPLY} fails the ask like a dropped connection. A big real
 * answer (a page of 50 songs is a few hundred KB) still reads.
 * Runs Java's real HttpClient against a server on this computer; nothing is sent to Spotify.
 */
class SpotifyBigAnswerTest {
	private static final String ID = "0123456789abcdef0123456789abcdef";
	private static final String TOKEN = "{\"access_token\":\"fresh\",\"expires_in\":3600}";
	private static final String PLAYING = "{\"is_playing\": true, \"progress_ms\": 1000, \"currently_playing_type\": \"track\","
			+ " \"item\": {\"name\": \"Made Up Song\", \"uri\": \"spotify:track:1\", \"duration_ms\": 200000,"
			+ " \"artists\": [{\"name\": \"B\"}], \"album\": {\"name\": \"C\"}}}";
	/** Bigger than the limit; spaces after the JSON, so the answer still reads as long as nothing stops it. */
	private static final String TOO_BIG = " ".repeat(5 * 1024 * 1024);
	/** Far more than a real answer, but under the limit. */
	private static final String BIG = " ".repeat(1024 * 1024);

	private HttpServer server;
	private volatile String token = TOKEN;
	private volatile String playing = PLAYING;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(Executors.newCachedThreadPool());
		server.createContext("/api/token", ex -> reply(ex, token));
		server.createContext("/v1/me/player/currently-playing", ex -> reply(ex, playing));
		server.start();
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private static void reply(HttpExchange ex, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().add("Content-Type", "application/json");
		try {
			ex.sendResponseHeaders(200, bytes.length);
			try (OutputStream out = ex.getResponseBody()) {
				out.write(bytes);
			}
		} catch (IOException dropped) {
			// the game hung up part way, as it should for this answer
		} finally {
			ex.close();
		}
	}

	private SpotifyWebSource source(Path dir) throws IOException {
		Path login = dir.resolve("login.json");
		Files.writeString(login, "{\"clientId\":\"" + ID + "\",\"refreshToken\":\"r\",\"scopes\":\"\"}");
		SpotifyWebSource source = new SpotifyWebSource(() -> ID, login);
		source.useHttp(new SpotifyStalledAnswerTest.ToLocal(server.getAddress().getPort()));
		return source;
	}

	@Test
	void aSongAnswerBiggerThanAnyRealOneFailsTheAsk(@TempDir Path dir) throws IOException {
		playing = PLAYING + TOO_BIG;
		SpotifyWebSource source = source(dir);
		assertTimeoutPreemptively(Duration.ofSeconds(30), () -> assertThrows(IOException.class, source::poll,
				"a 5 MB answer was read whole"));
	}

	@Test
	void aTokenAnswerBiggerThanAnyRealOneFailsTheAsk(@TempDir Path dir) throws IOException {
		token = TOKEN + TOO_BIG;
		SpotifyWebSource source = source(dir);
		assertTimeoutPreemptively(Duration.ofSeconds(30), () -> assertThrows(IOException.class, source::poll,
				"a 5 MB sign-in answer was read whole"));
	}

	@Test
	void aBigButRealSizedAnswerStillReads(@TempDir Path dir) throws Exception {
		token = TOKEN + BIG;
		playing = PLAYING + BIG;
		SpotifyWebSource source = source(dir);
		assertEquals("Made Up Song", source.poll().track().title());
	}
}
