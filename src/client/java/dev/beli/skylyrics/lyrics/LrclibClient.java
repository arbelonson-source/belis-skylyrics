package dev.beli.skylyrics.lyrics;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;

import dev.beli.skylyrics.media.TitleCleaner;
import dev.beli.skylyrics.media.Track;

/**
 * Looks lyrics up on <a href="https://lrclib.net">LRCLIB</a>, a free, open lyrics database.
 * No account or API key needed. Blocking; call from a background thread.
 */
public final class LrclibClient implements LyricsSource {
	private static final String BASE = "https://lrclib.net/api";
	/** Durations closer than this count as the same recording. */
	private static final double DURATION_SLACK_S = 4;
	/** A record this much longer or shorter than the track is another recording; never used. */
	private static final double DURATION_MAX_S = 20;

	/** What LRCLIB gave us, before parsing. */
	public record Result(@Nullable String synced, @Nullable String plain, boolean instrumental, boolean stopgap) {
		public static final Result NOT_FOUND = new Result(null, null, false);

		public Result(@Nullable String synced, @Nullable String plain, boolean instrumental) {
			this(synced, plain, instrumental, false);
		}

		/** Same lyrics, but only a stand-in used because the main source was down: don't keep it. */
		public Result asStopgap() {
			return new Result(synced, plain, instrumental, true);
		}

		/** Whether the synced text has any line (tags alone parse to nothing, and don't count). */
		public boolean hasSynced() {
			if (synced == null) {
				return false;
			}
			Lyrics parsed = LrcParser.parseSynced(synced);
			return !parsed.isEmpty() || parsed.instrumental();
		}

		public boolean found() {
			return instrumental || hasSynced() || plain != null;
		}
	}

	/** LRCLIB answered with an HTTP error other than 404. */
	public static final class StatusException extends IOException {
		private final int status;
		private final long retryAfterMs;

		StatusException(int status) {
			this(status, -1);
		}

		/** @param retryAfterMs what the server's Retry-After header asked for, or -1 */
		StatusException(int status, long retryAfterMs) {
			super("LRCLIB answered " + status);
			this.status = status;
			this.retryAfterMs = retryAfterMs;
		}

		public int status() {
			return status;
		}

		/** How long the server asked us to wait, or -1 when it didn't say. */
		public long retryAfterMs() {
			return retryAfterMs;
		}
	}

	/** The most a reply may weigh, and take to arrive. */
	static final int MAX_REPLY = 2 * 1024 * 1024;
	private static final Duration BODY_TIMEOUT = Duration.ofSeconds(20);

	/** Built on first use: the SSL context and java.net.http classes cost tens of milliseconds at mod init. */
	private volatile @org.jspecify.annotations.Nullable HttpClient httpClient;

	private HttpClient http() {
		HttpClient c = httpClient;
		if (c == null) {
			synchronized (this) {
				c = httpClient;
				if (c == null) {
					httpClient = c = HttpClient.newBuilder()
						.connectTimeout(Duration.ofSeconds(6))
						.followRedirects(HttpClient.Redirect.NORMAL)
						.build();
				}
			}
		}
		return c;
	}
	private final String userAgent;
	private final String base;

	public LrclibClient(String version) {
		this(version, BASE);
	}

	LrclibClient(String version, String base) {
		this.userAgent = "BelisSkyLyrics/" + version + " (https://github.com/arbelonson-source/belis-skylyrics)";
		this.base = base;
	}

	@Override
	public Result find(Track track) throws IOException, InterruptedException {
		String title = TitleCleaner.forSearch(track.title());
		String artist = track.artist();
		double seconds = track.lengthMs() / 1000.0;

		Result plainOnly = null; // an exact hit with no synced text: kept in case the search finds a synced copy
		// 1. Exact match (fast, uses LRCLIB's own matching)
		if (!artist.isEmpty() && seconds > 0) {
			Map<String, String> q = new LinkedHashMap<>();
			q.put("track_name", title);
			q.put("artist_name", artist);
			if (!track.album().isEmpty()) {
				q.put("album_name", track.album());
			}
			q.put("duration", Long.toString(Math.round(seconds)));
			JsonElement exact = null;
			try {
				exact = get("/get", q);
			} catch (StatusException busy) {
				// LRCLIB often answers 5xx on /get for songs it hasn't cached while /search works.
				// A quick error reply means the site is reachable, so go on to search. A 503 or 429
				// says it is overloaded, and other errors (timeouts, no network) fail too: searching
				// would only repeat them, so they are reported and the retry policy waits.
				if (busy.status() < 500 || busy.status() == 503) {
					throw busy;
				}
			}
			if (exact != null && exact.isJsonObject()) {
				Result r = toResult(exact.getAsJsonObject());
				if (r.found()) {
					if (r.hasSynced() || r.instrumental()) {
						return r;
					}
					plainOnly = r;
				}
			}
		}

		// With no artist and no length, nothing but the title says which song this is (browser audio is often
		// like that), so then several artists sharing the title also count as no match
		boolean titleOnly = artist.isEmpty() && seconds <= 0;
		String main = TitleCleaner.mainArtist(artist);

		// 2. Search with title + artist, then title + main artist, then free text (which also checks the artist)
		Result best = searchBest(Map.of("track_name", title, "artist_name", artist), seconds, title, null, titleOnly);
		if (best == null && !artist.isEmpty() && !main.equals(artist)) {
			best = searchBest(Map.of("track_name", title, "artist_name", main), seconds, title, null, false);
		}
		if (best == null) {
			best = searchBest(Map.of("q", (title + " " + main).trim()), seconds, title, main, titleOnly);
		}
		if (best != null && (best.hasSynced() || plainOnly == null)) {
			return best;
		}
		return plainOnly != null ? plainOnly : Result.NOT_FOUND;
	}

	/**
	 * @param title the wanted title: a result must be the same song
	 * @param mainArtist when not null/empty, the result's artist must contain it or the reverse (free-text tier,
	 *                   where nothing else ties the result to the artist)
	 * @param titleOnly nothing but the title identifies the song: several artists sharing it means no match
	 */
	/** Added to a hit whose title matches exactly (version brackets aside) when the song's length is unknown. */
	private static final int EXACT_TITLE_BONUS = 5;

	private @Nullable Result searchBest(Map<String, String> query, double seconds, String title, @Nullable String mainArtist, boolean titleOnly) throws IOException, InterruptedException {
		if (query.values().stream().allMatch(String::isBlank)) {
			return null;
		}
		JsonElement json = get("/search", query);
		if (json == null || !json.isJsonArray()) {
			return null;
		}
		JsonArray results = json.getAsJsonArray();
		String wantArtist = mainArtist == null ? "" : TitleCleaner.key(mainArtist);
		JsonObject best = null;
		int bestScore = Integer.MIN_VALUE;
		java.util.Set<String> sameTitleArtists = new java.util.HashSet<>();
		for (JsonElement e : results) {
			if (!e.isJsonObject()) {
				continue;
			}
			JsonObject o = e.getAsJsonObject();
			if (!TitleCleaner.sameSong(title, string(o, "trackName"))) {
				continue;
			}
			if (!wantArtist.isEmpty()) {
				String name = TitleCleaner.key(string(o, "artistName"));
				if (name.isEmpty() || !(name.contains(wantArtist) || wantArtist.contains(name))) {
					continue;
				}
			}
			int score = 0;
			boolean lengthScored = false;
			if (hasText(o, "syncedLyrics")) {
				score += 100;
			} else if (hasText(o, "plainLyrics")) {
				score += 20;
			} else if (!bool(o, "instrumental")) {
				continue;
			}
			if (seconds > 0 && o.has("duration") && !o.get("duration").isJsonNull()) {
				lengthScored = true;
				double diff = Math.abs(o.get("duration").getAsDouble() - seconds);
				if (diff > DURATION_MAX_S) {
					continue; // showing another recording's timing is worse than none
				}
				if (diff <= DURATION_SLACK_S) {
					score += 60 - (int) (diff * 5);
				} else {
					score -= (int) Math.min(200, diff * 2); // probably a different version (live, remix…)
				}
			}
			// With no length to compare, every synced hit scores the same: an exact title beats "Song (Live)". Small
			// enough to never outweigh synced over plain (100 vs 20); never added when the length scoring ran (its
			// steps are 5 per second off).
			if (!lengthScored && TitleCleaner.key(TitleCleaner.forSearch(string(o, "trackName"))).equals(TitleCleaner.key(TitleCleaner.forSearch(title)))) {
				score += EXACT_TITLE_BONUS;
			}
			if (titleOnly) {
				sameTitleArtists.add(TitleCleaner.key(string(o, "artistName")));
			}
			if (score > bestScore) {
				bestScore = score;
				best = o;
			}
		}
		// Reject a match that's a totally different length; showing wrong lyrics is worse than none
		if (best == null || bestScore < 0) {
			return null;
		}
		// Songs by different artists share this title and nothing tells them apart: better no lyrics than another
		// song's (several uploads of the same artist's song are fine)
		if (titleOnly && sameTitleArtists.size() > 1) {
			return null;
		}
		return toResult(best);
	}

	private static Result toResult(JsonObject o) {
		String synced = hasText(o, "syncedLyrics") ? o.get("syncedLyrics").getAsString() : null;
		String plain = hasText(o, "plainLyrics") ? o.get("plainLyrics").getAsString() : null;
		return new Result(synced, plain, bool(o, "instrumental"));
	}

	private static boolean hasText(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonPrimitive() && !o.get(key).getAsString().isBlank();
	}

	private static String string(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
	}

	private static boolean bool(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsBoolean();
	}

	private @Nullable JsonElement get(String path, Map<String, String> query) throws IOException, InterruptedException {
		StringJoiner qs = new StringJoiner("&");
		query.forEach((k, v) -> {
			if (!v.isBlank()) {
				qs.add(k + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8));
			}
		});
		HttpRequest request = HttpRequest.newBuilder(URI.create(base + path + "?" + qs))
				.timeout(Duration.ofSeconds(10))
				.header("User-Agent", userAgent)
				.header("Accept", "application/json")
				.GET()
				.build();
		HttpResponse<java.io.InputStream> response = http().send(request, HttpResponse.BodyHandlers.ofInputStream());
		String body;
		try (java.io.InputStream in = response.body()) {
			if (response.statusCode() == 404) {
				return null;
			}
			if (response.statusCode() / 100 != 2) {
				throw new StatusException(response.statusCode(), retryAfterMs(response.headers().firstValue("Retry-After").orElse(null)));
			}
			// Too big or too slow counts as a failed try, like a dropped connection
			dev.beli.skylyrics.util.Net.closeAfter(in, BODY_TIMEOUT);
			body = dev.beli.skylyrics.util.Net.readText(in, MAX_REPLY);
		}
		// A proxy or login page can answer 200 with nothing, or a bare word: that is a failed try, not "no lyrics"
		if (body.isBlank()) {
			throw new IOException("LRCLIB sent an empty reply");
		}
		JsonElement json;
		try {
			json = JsonParser.parseString(body);
		} catch (com.google.gson.JsonParseException e) {
			throw new IOException("LRCLIB sent a reply that isn't JSON", e);
		}
		if (!json.isJsonObject() && !json.isJsonArray()) {
			throw new IOException("LRCLIB sent a reply that isn't lyrics data");
		}
		return json;
	}

	/** A Retry-After header (seconds, or an HTTP date) as milliseconds from now, or -1 when missing, past or odd. */
	static long retryAfterMs(@Nullable String header) {
		return retryAfterMs(header, java.time.Instant.now());
	}

	static long retryAfterMs(@Nullable String header, java.time.Instant now) {
		if (header == null) {
			return -1;
		}
		String text = header.strip();
		try {
			long seconds = Long.parseLong(text);
			return seconds < 0 || seconds > 86_400 ? -1 : seconds * 1000;
		} catch (NumberFormatException e) {
			// maybe a date: "Wed, 21 Oct 2026 07:28:00 GMT"
		}
		try {
			long ms = java.time.Duration.between(now,
					java.time.ZonedDateTime.parse(text, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toMillis();
			return ms <= 0 || ms > 86_400_000L ? -1 : ms;
		} catch (java.time.format.DateTimeParseException | ArithmeticException e) {
			return -1;
		}
	}
}
