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
public final class LrclibClient {
	private static final String BASE = "https://lrclib.net/api";
	/** Durations closer than this count as the same recording. */
	private static final double DURATION_SLACK_S = 4;

	/** What LRCLIB gave us, before parsing. */
	public record Result(@Nullable String synced, @Nullable String plain, boolean instrumental) {
		public static final Result NOT_FOUND = new Result(null, null, false);

		public boolean found() {
			return instrumental || synced != null || plain != null;
		}
	}

	/** LRCLIB answered with an HTTP error other than 404. */
	public static final class StatusException extends IOException {
		private final int status;

		StatusException(int status) {
			super("LRCLIB answered " + status);
			this.status = status;
		}

		public int status() {
			return status;
		}
	}

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(6))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
	private final String userAgent;
	private final String base;

	public LrclibClient(String version) {
		this(version, BASE);
	}

	LrclibClient(String version, String base) {
		this.userAgent = "BelisSkyLyrics/" + version + " (https://github.com/arbelonson-source/belis-skylyrics)";
		this.base = base;
	}

	public Result find(Track track) throws IOException, InterruptedException {
		String title = TitleCleaner.forSearch(track.title());
		String artist = track.artist();
		double seconds = track.lengthMs() / 1000.0;

		// 1. Exact match (fast, uses LRCLIB's own matching)
		if (!artist.isEmpty() && seconds > 0) {
			Map<String, String> q = new LinkedHashMap<>();
			q.put("track_name", title);
			q.put("artist_name", artist);
			if (!track.album().isEmpty()) {
				q.put("album_name", track.album());
			}
			q.put("duration", Long.toString(Math.round(seconds)));
			JsonElement exact = get("/get", q);
			if (exact != null && exact.isJsonObject()) {
				Result r = toResult(exact.getAsJsonObject());
				if (r.found()) {
					return r;
				}
			}
		}

		// 2. Search with title + artist, then title + main artist, then free text
		Result best = searchBest(Map.of("track_name", title, "artist_name", artist), seconds);
		if (best == null && !artist.isEmpty()) {
			String main = TitleCleaner.mainArtist(artist);
			if (!main.equals(artist)) {
				best = searchBest(Map.of("track_name", title, "artist_name", main), seconds);
			}
		}
		if (best == null) {
			best = searchBest(Map.of("q", (title + " " + TitleCleaner.mainArtist(artist)).trim()), seconds);
		}
		return best == null ? Result.NOT_FOUND : best;
	}

	private @Nullable Result searchBest(Map<String, String> query, double seconds) throws IOException, InterruptedException {
		if (query.values().stream().allMatch(String::isBlank)) {
			return null;
		}
		JsonElement json = get("/search", query);
		if (json == null || !json.isJsonArray()) {
			return null;
		}
		JsonArray results = json.getAsJsonArray();
		JsonObject best = null;
		int bestScore = Integer.MIN_VALUE;
		for (JsonElement e : results) {
			if (!e.isJsonObject()) {
				continue;
			}
			JsonObject o = e.getAsJsonObject();
			int score = 0;
			if (hasText(o, "syncedLyrics")) {
				score += 100;
			} else if (hasText(o, "plainLyrics")) {
				score += 20;
			} else if (!bool(o, "instrumental")) {
				continue;
			}
			if (seconds > 0 && o.has("duration") && !o.get("duration").isJsonNull()) {
				double diff = Math.abs(o.get("duration").getAsDouble() - seconds);
				if (diff <= DURATION_SLACK_S) {
					score += 60 - (int) (diff * 5);
				} else {
					score -= (int) Math.min(200, diff * 2); // probably a different version (live, remix…)
				}
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
		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (response.statusCode() == 404) {
			return null;
		}
		if (response.statusCode() / 100 != 2) {
			throw new StatusException(response.statusCode());
		}
		return JsonParser.parseString(response.body());
	}
}
