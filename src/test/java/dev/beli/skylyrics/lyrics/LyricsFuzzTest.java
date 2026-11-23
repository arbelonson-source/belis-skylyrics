package dev.beli.skylyrics.lyrics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.Fuzz;
import dev.beli.skylyrics.media.Track;

/**
 * Lyrics come from anyone who can upload to a lyrics site, through two sites' JSON, and back from files on disk: random
 * and damaged text, answers and files must never throw out of the parser or the clients (other than the IOException
 * that means "try again"), hang, or give lines the rest of the mod can't draw.
 */
class LyricsFuzzTest {
	private static final Duration BOUND = Duration.ofSeconds(20);
	private static final String GOOD_LRC = "[ar:Made Up Band]\n[offset:+120]\n[00:01.00]A made-up line\n[00:04.50][01:10.00]Chorus <00:05.00>words here\n"
			+ "[00:08.00]\n[00:09.00](Instrumental)\n[00:12.00]Last made-up line\n[00:12.00]Its translation\n";

	private HttpServer server;
	private String base;
	private volatile Function<HttpExchange, Answer> answers = ex -> new Answer(404, "", null);

	record Answer(int status, String body, String retryAfter) {
	}

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/api", ex -> {
			Answer a = answers.apply(ex);
			byte[] bytes = a.body().getBytes(StandardCharsets.UTF_8);
			if (a.retryAfter() != null) {
				ex.getResponseHeaders().add("Retry-After", a.retryAfter());
			}
			ex.sendResponseHeaders(a.status(), bytes.length == 0 ? -1 : bytes.length);
			try (OutputStream out = ex.getResponseBody()) {
				out.write(bytes);
			}
		});
		server.start();
		base = "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	/** LRC-shaped text: stamps right and wrong, odd words, tags, breaks; or a good file with a few edits; or no LRC at all. */
	private static String lrc(Fuzz f) {
		switch (f.random.nextInt(4)) {
			case 0:
				return f.mutate(GOOD_LRC);
			case 1:
				return f.nasty(200);
			default:
				StringBuilder b = new StringBuilder();
				int lines = f.random.nextInt(60);
				for (int i = 0; i < lines; i++) {
					int stamps = f.random.nextInt(4);
					for (int s = 0; s < stamps; s++) {
						b.append(switch (f.random.nextInt(6)) {
							case 0 -> String.format(java.util.Locale.ROOT, "[%02d:%02d.%02d]", f.random.nextInt(100), f.random.nextInt(100), f.random.nextInt(100));
							case 1 -> "[" + (f.random.nextBoolean() ? "-" : "") + f.random.nextInt(1000) + ":" + f.random.nextInt(100) + "." + "9".repeat(f.random.nextInt(30)) + "]";
							case 2 -> "[" + f.random.nextInt(100) + ":" + f.random.nextInt(1000) + ":" + f.random.nextInt(100) + ".5]";
							case 3 -> "[offset:" + (f.random.nextBoolean() ? "-" : "+") + "9".repeat(1 + f.random.nextInt(25)) + "]";
							case 4 -> "<" + f.random.nextInt(100) + ":" + f.random.nextInt(100) + ".00>";
							default -> f.text(2);
						});
					}
					b.append(f.nasty(20)).append(f.random.nextInt(5) == 0 ? "\r" : "\n");
				}
				return b.toString();
		}
	}

	/** What every part of the mod relies on in parsed lyrics. */
	private static void checkShape(Lyrics l, String input) {
		List<LyricLine> lines = l.lines();
		assertTrue(lines.size() <= LrcParser.MAX_LINES, "lines: " + lines.size());
		long last = Long.MIN_VALUE;
		for (LyricLine line : lines) {
			assertTrue(line.timeMs() >= 0, "time " + line.timeMs() + " for " + Fuzz.show(input));
			assertTrue(line.timeMs() >= last, "out of order for " + Fuzz.show(input));
			last = line.timeMs();
			String text = line.text();
			// Up to MAX_JOINED lines that share a stamp join with " / "
			assertTrue(text.length() <= LrcParser.MAX_JOINED * (LrcParser.MAX_LINE + 3), "line of " + text.length());
			assertFalse(text.indexOf('§') >= 0, "formatting code in " + Fuzz.show(text));
			assertTrue(text.chars().noneMatch(c -> Character.getType(c) == Character.CONTROL), "control char in " + Fuzz.show(text));
			assertTrue(text.isEmpty() || text.equals(text.strip()), "unstripped " + Fuzz.show(text));
		}
		for (long t : new long[] {Long.MIN_VALUE, -1, 0, 1, 5_000, 600_000, Long.MAX_VALUE}) {
			int i = l.indexAt(t);
			assertTrue(i >= -1 && i < lines.size());
			if (i >= 0) {
				l.lineEnd(i);
				l.sungEnd(i);
				l.endOf(i, 0);
			}
		}
	}

	@Test
	void syncedTextNeverBreaksTheParser() {
		assertTimeoutPreemptively(BOUND, () -> Fuzz.cases(1, 1000, (f, i) -> {
			String text = lrc(f);
			checkShape(LrcParser.parseSynced(text), text);
		}));
	}

	@Test
	void plainTextNeverBreaksTheParser() {
		long[] lengths = {Long.MIN_VALUE, -1, 0, 1, 1000, 200_000, Long.MAX_VALUE};
		assertTimeoutPreemptively(BOUND, () -> Fuzz.cases(2, 1000, (f, i) -> {
			String text = f.random.nextBoolean() ? f.nasty(200) : lrc(f);
			checkShape(LrcParser.parsePlain(text, lengths[f.random.nextInt(lengths.length)]), text);
		}));
	}

	@Test
	void aResultOfAnyTextGivesDrawableLyrics() {
		assertTimeoutPreemptively(BOUND, () -> Fuzz.cases(3, 1500, (f, i) -> {
			String synced = f.random.nextInt(4) == 0 ? null : lrc(f);
			String plain = f.random.nextInt(3) == 0 ? null : f.nasty(100);
			LrclibClient.Result r = new LrclibClient.Result(synced, plain, f.random.nextBoolean());
			r.found();
			checkShape(LyricsService.parse(r, f.random.nextLong()), String.valueOf(synced));
		}));
	}

	@Test
	void neteaseTextHelpersTakeAnyText() {
		assertTimeoutPreemptively(BOUND, () -> Fuzz.cases(4, 2000, (f, i) -> {
			String text = f.random.nextBoolean() ? lrc(f) : f.nasty(100);
			NeteaseClient.clean(text);
			NeteaseClient.isPlaceholder(text);
			NeteaseClient.key(text);
			JsonElement e = f.value(2);
			JsonObject o = new JsonObject();
			o.add("code", e);
			// A value only {@link Fuzz#raw} can hold goes in through its text (newer Gson refuses the deepest nesting)
			JsonElement parsed;
			try {
				parsed = JsonParser.parseString(f.write(o));
			} catch (com.google.gson.JsonParseException tooDeep) {
				return;
			}
			NeteaseClient.codeOk(parsed.getAsJsonObject());
		}));
	}

	@Test
	void anyRetryAfterHeaderIsAWaitOrNothing() {
		String[] odd = {"", " ", "-1", "0", "86401", "99999999999999999999", "1e3", "1.5", "NaN", "Wed, 21 Oct 2026 07:28:00 GMT",
				"Wed, 21 Oct 99999 07:28:00 GMT", "Mon, 01 Jan 0001 00:00:00 GMT", "Fri, 31 Dec 9999 23:59:59 GMT", "\u0000", "120\r\n"};
		java.time.Instant now = java.time.Instant.parse("2026-11-22T00:00:00Z");
		assertTimeoutPreemptively(BOUND, () -> {
			for (String h : odd) {
				long ms = LrclibClient.retryAfterMs(h, now);
				assertTrue(ms == -1 || ms >= 0 && ms <= 86_400_000L, h + " -> " + ms);
			}
			Fuzz.cases(5, 2000, (f, i) -> {
				String h = f.random.nextBoolean() ? f.nasty(10) : f.mutate("Wed, 21 Oct 2026 07:28:00 GMT");
				long ms = LrclibClient.retryAfterMs(h, now);
				assertTrue(ms == -1 || ms >= 0 && ms <= 86_400_000L, Fuzz.show(h) + " -> " + ms);
			});
		});
	}

	// The two sites, served damaged answers

	private static final String LRCLIB_HIT = "{\"id\":1,\"trackName\":\"A Made-Up Song\",\"artistName\":\"Made Up Band\",\"albumName\":\"Made Up Album\","
			+ "\"duration\":200,\"instrumental\":false,\"plainLyrics\":\"A made-up line\\nAnother\",\"syncedLyrics\":\"[00:01.00]A made-up line\\n[00:03.00]Another\"}";
	private static final Track TRACK = new Track("A Made-Up Song", "Made Up Band", "Made Up Album", 200_000, "", "spotify");

	private static Track track(Fuzz f) {
		return f.random.nextInt(3) > 0 ? TRACK
				: new Track(f.nasty(30), f.nasty(10), f.nasty(10), f.random.nextInt(3) == 0 ? f.random.nextLong() : 200_000, "", "x");
	}

	private static int status(Fuzz f) {
		int[] odd = {200, 200, 200, 200, 404, 429, 500, 503, 301, 418, 999};
		return odd[f.random.nextInt(odd.length)];
	}

	private static String retryAfter(Fuzz f) {
		return switch (f.random.nextInt(4)) {
			case 0 -> null;
			case 1 -> f.text(3).replaceAll("[\\p{Cntrl}\\x{80}-\\x{10FFFF}]", "");
			default -> Long.toString(f.random.nextLong());
		};
	}

	/** Returns normally or with the IOException that means "try again later"; anything else is a bug. */
	private static void findOrTryAgain(LyricsSource source, Track t, String what) throws InterruptedException {
		try {
			LrclibClient.Result r = source.find(t);
			if (r.synced() != null) {
				LyricsService.parse(r, t.lengthMs());
			}
		} catch (IOException expected) {
			// a failed try
		} catch (RuntimeException e) {
			throw new AssertionError(e + " for " + what, e);
		}
	}

	@Test
	void lrclibAnswersOfAnyShapeAreLyricsNoneOrATryAgain() {
		LrclibClient client = new LrclibClient("test", base);
		assertTimeoutPreemptively(Duration.ofSeconds(60), () -> Fuzz.cases(6, 300, (f, i) -> {
			String get = f.damagedText(LRCLIB_HIT, 0.25);
			String search = f.damagedText("[" + LRCLIB_HIT + "," + LRCLIB_HIT.replace("Made Up Album", "Live") + "]", 0.25);
			int getStatus = status(f), searchStatus = status(f);
			String wait = retryAfter(f);
			answers = ex -> ex.getRequestURI().getPath().endsWith("/get") ? new Answer(getStatus, get, wait) : new Answer(searchStatus, search, wait);
			findOrTryAgain(client, track(f), "get " + Fuzz.show(get) + " search " + Fuzz.show(search));
		}));
	}

	private static final String NETEASE_SEARCH = "{\"result\":{\"songs\":[{\"id\":7,\"name\":\"A Made-Up Song\",\"duration\":200000,"
			+ "\"artists\":[{\"name\":\"Made Up Band\"}],\"album\":{\"name\":\"Made Up Album\"}}]},\"code\":200}";
	private static final String NETEASE_LYRIC = "{\"lrc\":{\"version\":1,\"lyric\":\"[00:00.000] 作词 : Somebody\\n[00:01.00]A made-up line\"},"
			+ "\"pureMusic\":false,\"code\":200}";

	/**
	 * The backup site through {@link FallbackSource}, as the game asks it: an answer it can't read (not JSON, say) comes
	 * out of {@link NeteaseClient} as any exception, which counts as "the backup is down", so only the caller's view is checked.
	 */
	@Test
	void neteaseAnswersOfAnyShapeAreLyricsNoneOrATryAgain() {
		LyricsSource client = new FallbackSource(t -> LrclibClient.Result.NOT_FOUND, new NeteaseClient("test", base));
		assertTimeoutPreemptively(Duration.ofSeconds(60), () -> Fuzz.cases(7, 300, (f, i) -> {
			String search = f.damagedText(NETEASE_SEARCH, 0.25);
			String lyric = f.damagedText(NETEASE_LYRIC, 0.25);
			int st = status(f);
			answers = ex -> new Answer(f.random.nextInt(5) == 0 ? st : 200, ex.getRequestURI().getPath().endsWith("/search/get") ? search : lyric, null);
			findOrTryAgain(client, track(f), "search " + Fuzz.show(search) + " lyric " + Fuzz.show(lyric));
		}));
	}

	@Test
	void aFiftyMegabyteAnswerIsATryAgainWithoutReadingItAll() throws Exception {
		HttpServer big = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		big.createContext("/api", ex -> {
			ex.sendResponseHeaders(200, 0); // chunked, no length to refuse it by
			byte[] chunk = ("{\"syncedLyrics\":\"" + "[00:01.00]la ".repeat(5000)).getBytes(StandardCharsets.UTF_8);
			try (OutputStream out = ex.getResponseBody()) {
				for (long sent = 0; sent < 50L * 1024 * 1024; sent += chunk.length) {
					out.write(chunk);
				}
			} catch (IOException closed) {
				// the client stopped reading, as it should
			}
		});
		big.start();
		try {
			String at = "http://127.0.0.1:" + big.getAddress().getPort() + "/api";
			assertTimeoutPreemptively(BOUND, () -> {
				for (LyricsSource s : List.of(new LrclibClient("test", at), new NeteaseClient("test", at))) {
					try {
						s.find(TRACK);
						throw new AssertionError(s + " took a 50 MB answer");
					} catch (IOException expected) {
						// too big: a failed try
					}
				}
			});
		} finally {
			big.stop(0);
		}
	}

	// Saved lyrics files

	@TempDir
	Path cache;

	/** The lyrics file name for a song, as {@code LyricsService} names it. */
	private Path fileFor(Track t) throws Exception {
		String id = (t.artist() + "\n" + t.title() + "\n" + t.album() + "\n" + Math.round(t.lengthMs() / 2000.0)).toLowerCase(Locale.ROOT);
		byte[] hash = MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8));
		return cache.resolve(HexFormat.of().formatHex(hash, 0, 12) + ".json");
	}

	private static final String SAVED = "{\"synced\":\"[00:01.00]A made-up line\\n[00:05.00]Another made-up line\",\"plain\":null,"
			+ "\"instrumental\":false,\"savedAt\":1763000000000,\"v\":3,\"backup\":false}";

	@Test
	void aDamagedLyricsFileStillEndsInLyricsOrNone() throws Exception {
		Fuzz.cases(8, 60, (f, i) -> {
			Path file = fileFor(TRACK);
			byte[] bytes = switch (f.random.nextInt(6)) {
				case 0 -> f.bytes(3000);
				case 1 -> f.mutate(SAVED.getBytes(StandardCharsets.UTF_8));
				case 2 -> SAVED.getBytes(StandardCharsets.UTF_16);
				case 3 -> ("﻿" + SAVED).getBytes(StandardCharsets.UTF_8);
				default -> f.damagedText(SAVED, 0.3).getBytes(StandardCharsets.UTF_8);
			};
			Files.write(file, bytes);
			LyricsService service = new LyricsService(t -> new LrclibClient.Result("[00:02.00]From the site", null, false), cache);
			try {
				service.load(TRACK);
				long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
				while (service.state() == LyricsService.State.LOADING || service.state() == LyricsService.State.IDLE) {
					assertTrue(System.nanoTime() < until, "stuck loading with " + Fuzz.show(new String(bytes, StandardCharsets.ISO_8859_1)));
					Thread.sleep(5);
				}
				checkShape(service.lyrics(), "file");
			} finally {
				assertTrue(service.shutdownAndWait(10, TimeUnit.SECONDS));
			}
		});
	}
}
