package dev.beli.skylyrics.lyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.media.Track;

class LyricsServiceTest {
	private static final String SYNCED = "[00:01.00]A made-up line\\n[00:05.00]Another made-up line";

	@TempDir
	Path cache;

	private HttpServer server;
	private final List<String> asked = new CopyOnWriteArrayList<>();
	private final CountDownLatch firstSeen = new CountDownLatch(1);
	private final CountDownLatch release = new CountDownLatch(1);
	private volatile boolean holdFirst;
	private volatile String getAnswer = "";
	private LyricsService service;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(Executors.newCachedThreadPool());
		server.createContext("/api", this::answer);
		server.start();
		String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
		service = new LyricsService(new LrclibClient("test", base), cache);
	}

	@AfterEach
	void stop() {
		release.countDown();
		service.shutdown();
		server.stop(0);
	}

	private void answer(HttpExchange ex) throws IOException {
		String query = URLDecoder.decode(ex.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
		String path = ex.getRequestURI().getPath();
		asked.add(path + "?" + query);
		if (holdFirst && firstSeen.getCount() > 0) {
			firstSeen.countDown();
			try {
				release.await(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		String body;
		int code;
		if (path.endsWith("/get")) {
			code = getAnswer.isEmpty() ? 404 : 200;
			body = getAnswer.isEmpty() ? "{\"code\":404}" : getAnswer;
		} else {
			code = 200;
			body = "[]";
		}
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		ex.sendResponseHeaders(code, bytes.length);
		try (OutputStream out = ex.getResponseBody()) {
			out.write(bytes);
		}
	}

	private static Track track(String title, long lengthMs) {
		return new Track(title, "Made Up Band", "Made Up Album", lengthMs, "", "spotify");
	}

	private static void waitFor(BooleanSupplier done) throws InterruptedException {
		long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (!done.getAsBoolean()) {
			assertTrue(System.nanoTime() < until, "timed out");
			Thread.sleep(10);
		}
	}

	private long askedAbout(String title) {
		return asked.stream().filter(a -> a.contains("track_name=" + title + "&") || a.endsWith("track_name=" + title)
				|| a.contains("q=" + title)).count();
	}

	@Test
	void lateCoverDoesNotBlankTheLyrics() throws InterruptedException {
		getAnswer = "{\"syncedLyrics\":\"" + SYNCED + "\"}";
		service.load(track("Song One", 200_000));
		waitFor(() -> service.state() == LyricsService.State.READY);
		int before = asked.size();

		Track withCover = new Track("Song One", "Made Up Band", "Made Up Album", 200_000, "file:///tmp/cover.png", "spotify");
		service.load(withCover);
		assertEquals(LyricsService.State.READY, service.state());
		assertFalse(service.lyrics().isEmpty());
		assertEquals(withCover, service.track());
		assertEquals(before, asked.size());
	}

	@Test
	void lateLengthLooksTheSongUpAgain() throws InterruptedException {
		service.load(track("Song One", 0));
		waitFor(() -> service.state() == LyricsService.State.NOT_FOUND);
		assertTrue(asked.stream().noneMatch(a -> a.startsWith("/api/get")), asked.toString());

		service.load(track("Song One", 200_000));
		waitFor(() -> asked.stream().anyMatch(a -> a.startsWith("/api/get")));
		waitFor(() -> service.state() == LyricsService.State.NOT_FOUND);
	}

	@Test
	void skippedSongsAreNotLookedUp() throws InterruptedException {
		holdFirst = true;
		service.load(track("Song One", 200_000));
		assertTrue(firstSeen.await(10, TimeUnit.SECONDS));

		service.load(track("Song Two", 200_000));
		service.load(track("Song Three", 200_000));
		Track last = track("Song Four", 200_000);
		service.load(last);
		release.countDown();

		waitFor(() -> service.state() == LyricsService.State.NOT_FOUND);
		assertEquals(last, service.track());
		assertEquals(0, askedAbout("Song Two"), asked.toString());
		assertEquals(0, askedAbout("Song Three"), asked.toString());
		assertTrue(askedAbout("Song Four") > 0, asked.toString());
	}
}
