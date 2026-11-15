package dev.beli.skylyrics.lyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
	private volatile boolean emptyOk;
	private volatile String getAnswer = "";
	/** The next this many requests (about {@link #failOnly}, if set) get {@link #failCode}. */
	private final AtomicInteger failures = new AtomicInteger();
	private volatile int failCode = 503;
	private volatile String failOnly = "";
	private String base;
	private int port;
	private LyricsService service;

	@BeforeEach
	void start() throws IOException {
		server = serve(0);
		port = server.getAddress().getPort();
		base = "http://127.0.0.1:" + port + "/api";
		service = new LyricsService(new LrclibClient("test", base), cache);
	}

	private HttpServer serve(int port) throws IOException {
		HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
		s.setExecutor(Executors.newCachedThreadPool());
		s.createContext("/api", this::answer);
		s.start();
		return s;
	}

	private HttpServer restart() {
		try {
			// the same port again; the old socket may take a moment to be free, and the client's address can't change
			long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
			while (true) {
				try {
					return serve(port);
				} catch (IOException e) {
					if (System.nanoTime() > until) {
						throw e;
					}
					Thread.sleep(50);
				}
			}
		} catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	/** Same service, but retries after 30 ms instead of seconds. */
	private void fastRetries() {
		service.shutdown();
		service = new LyricsService(new LrclibClient("test", base), cache, attempt -> 30);
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
				release.await(60, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		String body;
		int code;
		if (query.contains(failOnly) && failures.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
			code = failCode;
			body = "{}";
		} else if (emptyOk) {
			code = 200;
			body = "";
		} else if (path.endsWith("/get")) {
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
		long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
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
	void redirectsForbiddenAndBusyAreTemporary() {
		for (int status : new int[] {301, 302, 307, 403, 408, 429, 500, 503}) {
			assertTrue(LyricsService.temporary(new LrclibClient.StatusException(status)), "status " + status);
		}
		for (int status : new int[] {400, 401, 410}) {
			assertFalse(LyricsService.temporary(new LrclibClient.StatusException(status)), "status " + status);
		}
	}

	@Test
	void slowDownAnswersWaitLongerAndHonourRetryAfter() {
		assertEquals(2_000, LyricsService.delayFor(new LrclibClient.StatusException(503), 2_000));
		assertEquals(2_000, LyricsService.delayFor(new LrclibClient.StatusException(302), 2_000));
		assertEquals(6_000, LyricsService.delayFor(new LrclibClient.StatusException(429), 2_000));
		assertEquals(6_000, LyricsService.delayFor(new LrclibClient.StatusException(403), 2_000));
		assertEquals(25_000, LyricsService.delayFor(new LrclibClient.StatusException(429, 25_000), 2_000));
		assertEquals(60_000, LyricsService.delayFor(new LrclibClient.StatusException(429, 900_000), 2_000));
		assertEquals(60_000, LyricsService.delayFor(new LrclibClient.StatusException(403), 45_000));
		assertEquals(10_000, LyricsService.delayFor(new LrclibClient.StatusException(429, 1_000), 10_000));
	}

	@Test
	void savedTextThatParsesToNothingExpiresLikeNotFound() {
		long now = 10L * 24 * 60 * 60 * 1000;
		long old = now - 4L * 24 * 60 * 60 * 1000;
		long recent = now - 60_000;
		Lyrics junk = LyricsService.parse(new LrclibClient.Result("no stamps here", null, false), 200_000);
		assertTrue(junk.isEmpty() && !junk.instrumental());
		assertTrue(LyricsService.keepCached(junk, recent, now));
		assertFalse(LyricsService.keepCached(junk, old, now));
		assertFalse(LyricsService.keepCached(LyricsService.parse(LrclibClient.Result.NOT_FOUND, 0), old, now));

		Lyrics good = LyricsService.parse(new LrclibClient.Result("[00:01.00]words", null, false), 200_000);
		assertTrue(LyricsService.keepCached(good, old, now));
		assertTrue(LyricsService.keepCached(LyricsService.parse(new LrclibClient.Result(null, null, true), 0), old, now));
		// all-notes synced text is an instrumental, which is a real answer
		assertTrue(LyricsService.keepCached(LyricsService.parse(new LrclibClient.Result("[00:00.00] ♪", null, false), 0), old, now));
	}

	@Test
	void unparseableSyncedTextFallsBackToPlain() {
		Lyrics l = LyricsService.parse(new LrclibClient.Result("junk", "line one\nline two", false), 200_000);
		assertFalse(l.isEmpty());
	}

	@Test
	void emptyOkReplyIsRetriedAndNotSaved() throws InterruptedException, IOException {
		fastRetries();
		getAnswer = "";
		emptyOk = true;
		service.load(track("Song One", 200_000));
		waitFor(() -> service.state() == LyricsService.State.ERROR);
		try (var files = Files.list(cache)) {
			assertEquals(0, files.count());
		}
	}

	@Test
	void skippingASongDoesNotWaitForTheSlowRequestBeforeIt() throws InterruptedException {
		getAnswer = "{\"syncedLyrics\":\"" + SYNCED + "\"}";
		holdFirst = true;
		service.load(track("Song One", 200_000));
		assertTrue(firstSeen.await(10, TimeUnit.SECONDS));

		service.load(track("Song Two", 200_000));
		// Song One is held for 10 s; Song Two must be READY long before that is over
		long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
		while (service.state() != LyricsService.State.READY && System.nanoTime() < until) {
			Thread.sleep(10);
		}
		assertEquals(LyricsService.State.READY, service.state(), "Song Two queued behind the slow Song One");
		assertEquals("Song Two", service.track().title());

		// the cancelled fetch finishing late must not show an error or replace the lyrics
		release.countDown();
		Thread.sleep(300);
		assertEquals(LyricsService.State.READY, service.state());
		assertEquals("Song Two", service.track().title());
	}

	@Test
	void retriesBackOff() {
		long[] expected = {2_000, 5_000, 10_000, 20_000, 30_000, 45_000, 60_000, 60_000, 60_000};
		for (int i = 0; i < expected.length; i++) {
			assertEquals(expected[i], LyricsService.retryDelayMs(i + 1), "attempt " + (i + 1));
		}
	}

	@Test
	void serverTroubleIsRetriedUntilTheLyricsCome() throws InterruptedException {
		fastRetries();
		getAnswer = "{\"syncedLyrics\":\"" + SYNCED + "\"}";
		failures.set(2); // /get and /search both fail on the first try
		service.load(track("Song One", 200_000));
		waitFor(() -> service.state() == LyricsService.State.READY);
		assertFalse(service.lyrics().isEmpty());
		// a 503 on /get now also tries /search, so one try can take two requests
		assertTrue(asked.size() >= 3, asked.toString());
	}

	@Test
	void tooManyRequestsAndTimeoutsAreRetriedToo() throws InterruptedException {
		fastRetries();
		getAnswer = "{\"syncedLyrics\":\"" + SYNCED + "\"}";
		failCode = 429;
		failures.set(2);
		service.load(track("Song One", 200_000));
		waitFor(() -> service.state() == LyricsService.State.READY);

		server.stop(0); // nobody answering at all
		service.load(track("Song Two", 200_000));
		waitFor(() -> service.state() == LyricsService.State.ERROR);
		server = restart();
		waitFor(() -> service.state() == LyricsService.State.READY);
		assertEquals("Song Two", service.track().title());
	}

	@Test
	void serverTroubleIsNotSavedAsNoLyrics() throws InterruptedException, IOException {
		failures.set(1_000);
		service.load(track("Song One", 200_000));
		waitFor(() -> service.state() == LyricsService.State.ERROR);
		try (var files = Files.list(cache)) {
			assertEquals(0, files.count());
		}
	}

	@Test
	void backupLyricsFromAMainOutageAreNotKeptOnDisk() throws Exception {
		java.util.concurrent.atomic.AtomicBoolean mainUp = new java.util.concurrent.atomic.AtomicBoolean();
		AtomicInteger mainAsked = new AtomicInteger();
		java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong(1_000_000);
		LrclibClient.Result fromMain = new LrclibClient.Result("[00:01.00]Main version", null, false);
		LrclibClient.Result fromBackup = new LrclibClient.Result(null, "Backup version", false);
		FallbackSource source = new FallbackSource(t -> {
			mainAsked.incrementAndGet();
			if (!mainUp.get()) {
				throw new LrclibClient.StatusException(503);
			}
			return fromMain;
		}, t -> fromBackup, now::get);
		service.shutdown();
		service = new LyricsService(source, cache, attempt -> 30);

		service.load(track("Song One", 200_000));
		waitFor(() -> service.state() == LyricsService.State.READY);
		service.load(track("Song Two", 200_000));
		waitFor(() -> service.track().title().equals("Song Two") && service.state() == LyricsService.State.READY);

		mainUp.set(true);
		now.addAndGet(FallbackSource.SKIP_MAIN_MS + 1);
		int before = mainAsked.get();
		service.load(track("Song One", 200_000));
		waitFor(() -> mainAsked.get() > before);
		waitFor(() -> service.state() == LyricsService.State.READY);
		assertTrue(service.lyrics().lines().get(0).text().contains("Main version"), "still the backup's lyrics");
	}

	/** Saves a found answer for the song the way 0.7.8 did: the same file, but without a version. */
	private Path saveOldVersionFile(Track t, LrclibClient.Result saved) throws Exception {
		return saveOldVersionFile(t, saved, "");
	}

	/** Same, but stamped with the given version text instead of the current one (empty: no version at all). */
	private Path saveOldVersionFile(Track t, LrclibClient.Result saved, String versionText) throws Exception {
		service.shutdown();
		service = new LyricsService(x -> saved, cache);
		service.load(t);
		waitFor(() -> service.state() == LyricsService.State.READY);
		Path file;
		try (var files = Files.list(cache)) {
			file = files.filter(f -> f.toString().endsWith(".json")).findFirst().orElseThrow();
		}
		String json = Files.readString(file);
		assertTrue(json.contains(",\"v\":3"), json);
		Files.writeString(file, json.replace(",\"v\":3", versionText));
		service.shutdown();
		return file;
	}

	private static final Track OLD_SONG = track("Song One", 200_000);
	private static final LrclibClient.Result STANDIN = new LrclibClient.Result(null, "Backup version", false);
	private static final LrclibClient.Result BETTER = new LrclibClient.Result("[00:01.00]Main version", null, false);

	@Test
	void newFilesCarryTheVersion() throws Exception {
		service.shutdown();
		service = new LyricsService(x -> BETTER, cache);
		service.load(OLD_SONG);
		waitFor(() -> service.state() == LyricsService.State.READY);
		try (var files = Files.list(cache)) {
			assertTrue(Files.readString(files.filter(f -> f.toString().endsWith(".json")).findFirst().orElseThrow()).contains("\"v\":3"));
		}
	}

	@Test
	void versionlessLyricsAreShownThenReplacedWhenTheMainSiteHasBetter() throws Exception {
		Path file = saveOldVersionFile(OLD_SONG, STANDIN);
		AtomicInteger asks = new AtomicInteger();
		service = new LyricsService(x -> {
			asks.incrementAndGet();
			return BETTER;
		}, cache);
		service.load(OLD_SONG);
		waitFor(() -> service.state() == LyricsService.State.READY);
		waitFor(() -> service.lyrics().lines().get(0).text().contains("Main version"));
		assertEquals(1, asks.get());
		assertTrue(Files.readString(file).contains("Main version"));
		assertTrue(Files.readString(file).contains("\"v\":3"));
	}

	@Test
	void olderLyricsStayShownWhenTheMainSiteSaysThereAreNoneButTheFileGoes() throws Exception {
		Path file = saveOldVersionFile(OLD_SONG, STANDIN, ",\"v\":2");
		AtomicInteger asks = new AtomicInteger();
		service = new LyricsService(x -> {
			asks.incrementAndGet();
			return LrclibClient.Result.NOT_FOUND;
		}, cache);
		service.load(OLD_SONG);
		waitFor(() -> service.state() == LyricsService.State.READY);
		waitFor(() -> !Files.exists(file));
		Thread.sleep(300);
		// this play keeps what it shows
		assertEquals(LyricsService.State.READY, service.state());
		assertFalse(service.lyrics().isEmpty());
		assertEquals(1, asks.get());
		// the next play looks the song up fresh
		service.load(track("Song Two", 200_000));
		waitFor(() -> service.track().title().equals("Song Two") && service.state() != LyricsService.State.LOADING);
		service.load(OLD_SONG);
		waitFor(() -> service.state() == LyricsService.State.NOT_FOUND);
		assertEquals(3, asks.get()); // Song One, Song Two, Song One again
	}

	@Test
	void olderInstrumentalIsReplacedByLyricsTheMainSiteHas() throws Exception {
		Path file = saveOldVersionFile(OLD_SONG, new LrclibClient.Result(null, null, true), ",\"v\":2");
		service = new LyricsService(x -> BETTER, cache);
		service.load(OLD_SONG);
		waitFor(() -> service.state() == LyricsService.State.READY && !service.lyrics().isEmpty());
		assertTrue(service.lyrics().lines().get(0).text().contains("Main version"));
		waitFor(() -> readFile(file).contains("Main version"));
		assertTrue(readFile(file).contains("\"v\":3"));
	}

	@Test
	void olderLyricsAreKeptWhenTheCheckFails() throws Exception {
		Path file = saveOldVersionFile(OLD_SONG, STANDIN, ",\"v\":2");
		String before = readFile(file);
		AtomicInteger asks = new AtomicInteger();
		service = new LyricsService(x -> {
			asks.incrementAndGet();
			throw new java.io.IOException("offline");
		}, cache, attempt -> 30);
		service.load(OLD_SONG);
		waitFor(() -> asks.get() == 1);
		Thread.sleep(300);
		assertEquals(before, readFile(file));
		assertEquals(LyricsService.State.READY, service.state());
		assertTrue(service.lyrics().lines().get(0).text().contains("Backup version"));
	}

	@Test
	void currentVersionLyricsAreNotAskedAgain() throws Exception {
		saveOldVersionFile(OLD_SONG, STANDIN, ",\"v\":3");
		AtomicInteger asks = new AtomicInteger();
		service = new LyricsService(x -> {
			asks.incrementAndGet();
			return LrclibClient.Result.NOT_FOUND;
		}, cache);
		service.load(OLD_SONG);
		waitFor(() -> service.state() == LyricsService.State.READY);
		Thread.sleep(300);
		assertEquals(0, asks.get());
	}

	@Test
	void versionlessLyricsStayAsTheyAreWhenTheMainSiteAnswersWithAnotherStandIn() throws Exception {
		Path file = saveOldVersionFile(OLD_SONG, STANDIN);
		String before = readFile(file);
		AtomicInteger asks = new AtomicInteger();
		service = new LyricsService(x -> {
			asks.incrementAndGet();
			return BETTER.asStopgap(); // the main site is down again
		}, cache);
		service.load(OLD_SONG);
		waitFor(() -> asks.get() == 1);
		Thread.sleep(300);
		assertEquals(before, readFile(file), "not stamped, so a later play asks again");
		assertTrue(service.lyrics().lines().get(0).text().contains("Backup version"));
	}

	@Test
	void versionlessLyricsThatAreTheSameAreStampedButNotShownAgain() throws Exception {
		Path file = saveOldVersionFile(OLD_SONG, BETTER);
		service = new LyricsService(x -> BETTER, cache);
		service.load(OLD_SONG);
		waitFor(() -> service.state() == LyricsService.State.READY);
		Lyrics shown = service.lyrics();
		waitFor(() -> readFile(file).contains("\"v\":3"));
		Thread.sleep(100);
		assertTrue(shown == service.lyrics(), "the same lyrics were applied again");
	}

	@Test
	void versionlessLyricsStayAsTheyAreWhenTheMainSiteFails() throws Exception {
		Path file = saveOldVersionFile(OLD_SONG, STANDIN);
		String before = readFile(file);
		AtomicInteger asks = new AtomicInteger();
		service = new LyricsService(x -> {
			asks.incrementAndGet();
			throw new LrclibClient.StatusException(503);
		}, cache, attempt -> 30);
		service.load(OLD_SONG);
		waitFor(() -> asks.get() == 1);
		Thread.sleep(300);
		assertEquals(1, asks.get(), "no retry loop");
		assertEquals(before, readFile(file));
		assertEquals(LyricsService.State.READY, service.state());
		assertTrue(service.lyrics().lines().get(0).text().contains("Backup version"));
	}

	@Test
	void aSongSkippedBeforeTheCheckReturnsIsNotShown() throws Exception {
		Path file = saveOldVersionFile(OLD_SONG, STANDIN);
		CountDownLatch asked = new CountDownLatch(1);
		CountDownLatch go = new CountDownLatch(1);
		CountDownLatch songTwoAsked = new CountDownLatch(1);
		CountDownLatch songTwoGo = new CountDownLatch(1);
		service = new LyricsService(t -> {
			if (!t.title().equals("Song One")) {
				// Song Two's own lookup waits too, so nothing else can reset the lyrics while the stale check lands
				songTwoAsked.countDown();
				songTwoGo.await();
				return LrclibClient.Result.NOT_FOUND;
			}
			asked.countDown();
			while (true) { // ignores the skip's interrupt, as a request that was already answered would
				try {
					go.await();
					break;
				} catch (InterruptedException ignored) {
					// keep waiting
				}
			}
			return BETTER;
		}, cache);
		service.load(OLD_SONG);
		assertTrue(asked.await(10, TimeUnit.SECONDS));
		service.load(track("Song Two", 100_000));
		go.countDown();
		waitFor(() -> readFile(file).contains("Main version"));
		// the single worker is now stuck in Song Two's lookup, so only the stale check could have changed the lyrics
		assertTrue(songTwoAsked.await(10, TimeUnit.SECONDS));
		Thread.sleep(200);
		assertEquals("Song Two", service.track().title());
		assertTrue(service.lyrics().isEmpty(), "the skipped song's lyrics must not show while Song Two is still loading");
		assertFalse(service.state() == LyricsService.State.READY);
		songTwoGo.countDown();
		waitFor(() -> service.state() == LyricsService.State.NOT_FOUND);
		assertTrue(service.lyrics().isEmpty(), "the skipped song's lyrics must not show");
		assertEquals(LyricsService.State.NOT_FOUND, service.state());
	}

	private static String readFile(Path f) {
		try {
			return Files.readString(f);
		} catch (IOException e) {
			return "";
		}
	}

	@Test
	void realNotFoundIsSavedAndNotRetried() throws InterruptedException, IOException {
		fastRetries();
		service.load(track("Song One", 200_000));
		waitFor(() -> service.state() == LyricsService.State.NOT_FOUND);
		int before = asked.size();
		Thread.sleep(300);
		assertEquals(before, asked.size(), asked.toString());
		try (var files = Files.list(cache)) {
			assertEquals(1, files.count());
		}
	}

	@Test
	void badRequestIsNotRetried() throws InterruptedException {
		fastRetries();
		failCode = 400;
		failures.set(1_000);
		service.load(track("Song One", 200_000));
		waitFor(() -> service.state() == LyricsService.State.ERROR);
		Thread.sleep(300);
		assertEquals(1, asked.size(), asked.toString());
	}

	@Test
	void songChangeStopsTheRetries() throws InterruptedException {
		fastRetries();
		getAnswer = "{\"syncedLyrics\":\"" + SYNCED + "\"}";
		failOnly = "Song One";
		failures.set(1_000);
		service.load(track("Song One", 200_000));
		waitFor(() -> askedAbout("Song One") >= 3);

		service.load(track("Song Two", 200_000));
		waitFor(() -> service.state() == LyricsService.State.READY);
		// let a request that was already in flight when the song changed land, then nothing more may come
		long before;
		do {
			before = askedAbout("Song One");
			Thread.sleep(150);
		} while (askedAbout("Song One") != before);
		Thread.sleep(300);
		assertEquals(before, askedAbout("Song One"), asked.toString());
		assertEquals("Song Two", service.track().title());
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
		// load() cancels the fetch in flight with an interrupt, which frees the single worker at once in real life (a
		// real request stops when interrupted). So this test's source ignores the interrupt for Song One: the worker is
		// provably busy while Two, Three and Four are loaded, and Two and Three can only be skipped by their stale check.
		service.shutdown();
		List<String> askedTitles = new CopyOnWriteArrayList<>();
		CountDownLatch oneAsked = new CountDownLatch(1);
		CountDownLatch letOneGo = new CountDownLatch(1);
		service = new LyricsService(t -> {
			askedTitles.add(t.title());
			if (t.title().equals("Song One")) {
				oneAsked.countDown();
				while (letOneGo.getCount() > 0) {
					try {
						letOneGo.await();
					} catch (InterruptedException e) {
						// keep waiting: the point is a worker that stays busy
					}
				}
			}
			return LrclibClient.Result.NOT_FOUND;
		}, cache);
		service.load(track("Song One", 200_000));
		assertTrue(oneAsked.await(60, TimeUnit.SECONDS), "Song One was never asked");

		service.load(track("Song Two", 200_000));
		service.load(track("Song Three", 200_000));
		Track last = track("Song Four", 200_000);
		service.load(last);
		letOneGo.countDown();

		waitFor(() -> askedTitles.contains("Song Four"));
		waitFor(() -> service.state() == LyricsService.State.NOT_FOUND);
		assertEquals(last, service.track());
		assertFalse(askedTitles.contains("Song Two"), askedTitles.toString());
		assertFalse(askedTitles.contains("Song Three"), askedTitles.toString());
		assertEquals(List.of("Song One", "Song Four"), askedTitles);
	}

	@Test
	void tidyingKeepsTheNewestSongsAndTheLogin() throws IOException {
		Path dir = Files.createDirectories(cache.resolve("tidy"));
		for (int i = 0; i < 5; i++) {
			Path song = Files.writeString(dir.resolve(String.format("%024x.json", i)), "{}");
			song.toFile().setLastModified(1_000_000L * (i + 1));
		}
		Path login = Files.writeString(dir.resolve("spotify-login.json"), "{}");
		login.toFile().setLastModified(1L);
		LyricsService.tidyDisk(dir, 3);
		for (int i = 0; i < 5; i++) {
			assertEquals(i >= 2, Files.exists(dir.resolve(String.format("%024x.json", i))), "song " + i);
		}
		assertTrue(Files.exists(login));
	}

	@Test
	void aSnapshotOfTheSongBeforeShowsNothingForTheNewOne() {
		Lyrics lines = new Lyrics(List.of(new LyricLine(1000, "old line")), true, false);
		LyricsService.Snapshot before = new LyricsService.Snapshot(
				new Track("A", "X", "", 0, "", ""), lines, LyricsService.State.LOADING);
		Track next = new Track("B", "X", "", 0, "", "");
		LyricsService.Snapshot shown = before.forSong(next);
		assertEquals(Lyrics.NONE, shown.lyrics(), "B must not get A's lines");
		assertEquals(LyricsService.State.IDLE, shown.state(), "nor A's state");
		// The same song, even with the album arriving late, keeps its lyrics
		assertSame(before, before.forSong(new Track("A", "X", "Late album", 0, "", "")));
	}
}
