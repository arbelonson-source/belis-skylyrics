package dev.beli.skylyrics.lyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.media.Track;

class NeteaseClientTest {
	private static final String LYRIC = "[00:00.000] 作词 : Somebody\\n[00:01.000] 作曲 : Somebody\\n"
			+ "[00:12.50]A made-up first line\\n[00:16.00]男：A made-up duet line";

	private HttpServer server;
	private NeteaseClient client;
	private final List<String> asked = new CopyOnWriteArrayList<>();
	private volatile String searchAnswer = "{\"result\":{\"songs\":[]},\"code\":200}";
	private final Map<String, String> lyricAnswers = new HashMap<>();
	private volatile int status = 200;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/api", this::answer);
		server.start();
		client = new NeteaseClient("test", "http://127.0.0.1:" + server.getAddress().getPort() + "/api");
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private void answer(HttpExchange ex) throws IOException {
		String query = URLDecoder.decode(ex.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
		String path = ex.getRequestURI().getPath();
		asked.add(path + "?" + query);
		String body;
		if (path.endsWith("/search/get")) {
			body = searchAnswer;
		} else {
			String id = query.replaceAll(".*id=(\\d+).*", "$1");
			body = lyricAnswers.getOrDefault(id, "{\"uncollected\":true,\"lrc\":{\"lyric\":\"\"},\"code\":200}");
		}
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		ex.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = ex.getResponseBody()) {
			out.write(bytes);
		}
	}

	private static String song(long id, String name, String artist, long durationMs) {
		return "{\"id\":" + id + ",\"name\":\"" + name + "\",\"duration\":" + durationMs
				+ ",\"artists\":[{\"name\":\"" + artist + "\"}],\"album\":{\"name\":\"Some Album\"}}";
	}

	private void songs(String... songs) {
		searchAnswer = "{\"result\":{\"songs\":[" + String.join(",", songs) + "]},\"code\":200}";
	}

	private void lyric(long id, String lyric) {
		lyricAnswers.put(Long.toString(id), "{\"lrc\":{\"version\":1,\"lyric\":\"" + lyric + "\"},\"code\":200}");
	}

	private static Track track(String title, String artist, long lengthMs) {
		return new Track(title, artist, "Made Up Album", lengthMs, "", "spotify");
	}

	@Test
	void findsTheMatchingSongAndDropsTheCredits() throws Exception {
		songs(song(1, "A Made-Up Song (Live)", "Made Up Band", 200_000), song(2, "A Made-Up Song", "Made Up Band", 201_000));
		lyric(2, LYRIC);
		LrclibClient.Result r = client.find(track("A Made-Up Song", "Made Up Band", 200_500));
		assertTrue(r.found());
		assertEquals("[00:12.50]A made-up first line\n[00:16.00]男：A made-up duet line", r.synced());
		assertNull(r.plain());
		assertEquals(2, LrcParser.parseSynced(r.synced()).lines().size());
	}

	@Test
	void ignoresSongsWithAnotherTitle() throws Exception {
		songs(song(3, "A Different Song", "Made Up Band", 200_000));
		lyric(3, LYRIC);
		assertFalse(client.find(track("A Made-Up Song", "Made Up Band", 200_000)).found());
	}

	@Test
	void sameTitleByAnotherArtistNeedsTheSameLength() throws Exception {
		songs(song(4, "A Made-Up Song", "Someone Else", 230_000));
		lyric(4, LYRIC);
		assertFalse(client.find(track("A Made-Up Song", "Made Up Band", 200_000)).found());

		songs(song(5, "A Made-Up Song", "Band Name In Another Alphabet", 200_400));
		lyric(5, LYRIC);
		assertTrue(client.find(track("A Made-Up Song", "Made Up Band", 200_000)).found());
	}

	@Test
	void unknownLengthNeedsTheSameArtist() throws Exception {
		songs(song(6, "A Made-Up Song", "Someone Else", 200_000));
		lyric(6, LYRIC);
		assertFalse(client.find(track("A Made-Up Song", "Made Up Band", 0)).found());

		songs(song(7, "A Made-Up Song", "made up band", 200_000));
		lyric(7, LYRIC);
		assertTrue(client.find(track("A Made-Up Song", "Made Up Band", 0)).found());
	}

	@Test
	void titleNoiseAndFeaturedArtistsAreIgnored() throws Exception {
		songs(song(8, "A Made-Up Song", "Made Up Band", 200_000));
		lyric(8, LYRIC);
		assertTrue(client.find(track("A Made-Up Song - Remastered 2011", "Made Up Band, Guest Singer", 200_000)).found());
	}

	@Test
	void instrumentalIsReported() throws Exception {
		songs(song(9, "A Made-Up Tune", "Made Up Band", 200_000));
		lyricAnswers.put("9", "{\"pureMusic\":true,\"lrc\":{\"version\":1,\"lyric\":\"\"},\"code\":200}");
		LrclibClient.Result r = client.find(track("A Made-Up Tune", "Made Up Band", 200_000));
		assertTrue(r.instrumental());
	}

	@Test
	void songWithoutLyricsIsNotFound() throws Exception {
		songs(song(10, "A Made-Up Song", "Made Up Band", 200_000));
		assertFalse(client.find(track("A Made-Up Song", "Made Up Band", 200_000)).found());
	}

	@Test
	void untimedLyricsArePlain() throws Exception {
		songs(song(11, "A Made-Up Song", "Made Up Band", 200_000));
		lyric(11, "A made-up line\\nAnother made-up line");
		LrclibClient.Result r = client.find(track("A Made-Up Song", "Made Up Band", 200_000));
		assertNull(r.synced());
		assertEquals("A made-up line\nAnother made-up line", r.plain());
	}

	@Test
	void serverErrorThrows() {
		status = 503;
		assertThrows(IOException.class, () -> client.find(track("A Made-Up Song", "Made Up Band", 200_000)));
	}

	@Test
	void refusedRequestThrows() {
		searchAnswer = "{\"code\":-460,\"message\":\"Cheating\"}";
		assertThrows(IOException.class, () -> client.find(track("A Made-Up Song", "Made Up Band", 200_000)));
	}

	@Test
	void searchesWithTitleAndMainArtistFirst() throws Exception {
		client.find(track("A Made-Up Song (feat. Guest)", "Made Up Band & Friend", 200_000));
		assertTrue(asked.get(0).contains("s=A Made-Up Song Made Up Band"), asked.get(0));
	}

	@Test
	void creditCleanerKeepsLyrics() {
		assertEquals("[00:05.00]Hello there", NeteaseClient.clean("[00:00.00] 作词 : X\n[00:01.00] 制作人 : Y\n[00:05.00]Hello there"));
		assertEquals("[00:05.00]男：a line", NeteaseClient.clean("[00:05.00]男：a line"));
		assertEquals("Ab12שלום", NeteaseClient.key("A b-1'2 שלום").replace("ab12", "Ab12"));
	}
}
