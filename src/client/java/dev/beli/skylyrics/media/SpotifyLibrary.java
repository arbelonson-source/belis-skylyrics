package dev.beli.skylyrics.media;

import static dev.beli.skylyrics.media.SpotifyWebSource.array;
import static dev.beli.skylyrics.media.SpotifyWebSource.bestImage;
import static dev.beli.skylyrics.media.SpotifyWebSource.child;
import static dev.beli.skylyrics.media.SpotifyWebSource.number;
import static dev.beli.skylyrics.media.SpotifyWebSource.object;
import static dev.beli.skylyrics.media.SpotifyWebSource.orEmpty;
import static dev.beli.skylyrics.media.SpotifyWebSource.string;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.beli.skylyrics.Tr;
import org.jspecify.annotations.Nullable;

/**
 * Reads the signed-in user's Spotify songs for the song library and starts the one they pick.
 * Everything runs on one background thread; the screen just checks whether the answer has arrived.
 */
public final class SpotifyLibrary {
	/** Spotify hands out at most this many search results per request. */
	public static final int SEARCH_PAGE = 10;
	/** Liked songs, playlists and playlist songs come this many at a time. */
	public static final int PAGE = 50;
	/** How long a busy Spotify can make us wait before we give up on one request. */
	private static final long MAX_WAIT_S = 30;

	/** A slice of a longer list; {@code total} is how many there are altogether. */
	public record Page<T>(List<T> items, int total, int offset) {
		public static <T> Page<T> empty() {
			return new Page<>(List.of(), 0, 0);
		}

		/** Where the next slice starts, or -1 when this was the last one. */
		public int next() {
			int next = offset + items.size();
			return items.isEmpty() || next >= total ? -1 : next;
		}
	}

	private final SpotifyWebSource spotify;
	private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "SkyLyrics library");
		t.setDaemon(true);
		return t;
	});
	private volatile @Nullable String me;

	public SpotifyLibrary(SpotifyWebSource spotify) {
		this.spotify = spotify;
	}

	public CompletableFuture<Page<LibraryItem>> search(String query, int offset) {
		String q = query.strip();
		if (q.isEmpty()) {
			return CompletableFuture.completedFuture(Page.empty());
		}
		return run(() -> parseSearch(get("/search?type=track&limit=" + SEARCH_PAGE + "&offset=" + offset + "&q=" + encode(q))));
	}

	public CompletableFuture<Page<LibraryItem>> liked(int offset) {
		return run(() -> parseSaved(get("/me/tracks?limit=" + PAGE + "&offset=" + offset)));
	}

	public CompletableFuture<Page<LibraryPlaylist>> playlists(int offset) {
		return run(() -> parsePlaylists(get("/me/playlists?limit=" + PAGE + "&offset=" + offset), myId()));
	}

	public CompletableFuture<Page<LibraryItem>> playlist(LibraryPlaylist playlist, int offset) {
		return run(() -> parsePlaylistItems(get("/playlists/" + playlist.id() + "/items?limit=" + PAGE + "&offset=" + offset), playlist.uri()));
	}

	/**
	 * Starts {@code item}, then the songs listed after it (or the rest of its playlist).
	 * Completes with an empty string when it worked, otherwise with a short message for the user.
	 */
	public CompletableFuture<String> play(LibraryItem item, List<LibraryItem> after) {
		String body = playBody(item, after);
		return run(() -> {
			HttpResponse<String> response = spotify.call("PUT", "/me/player/play", body);
			if (response.statusCode() == 404) {
				String device = firstDevice(get("/me/player/devices"));
				if (device == null) {
					return Tr.t("Open Spotify on your computer or phone first");
				}
				response = spotify.call("PUT", "/me/player/play?device_id=" + encode(device), body);
			}
			return switch (response.statusCode()) {
				case 200, 202, 204 -> "";
				case 403 -> Tr.t("Spotify Premium is needed to start songs");
				case 404 -> Tr.t("Open Spotify on your computer or phone first");
				default -> Tr.t("Spotify didn't start the song, try again");
			};
		}).exceptionally(e -> Tr.t("Spotify didn't start the song, try again"));
	}

	private interface Job<T> {
		T run() throws IOException, InterruptedException;
	}

	private <T> CompletableFuture<T> run(Job<T> job) {
		CompletableFuture<T> result = new CompletableFuture<>();
		worker.execute(() -> {
			try {
				result.complete(job.run());
			} catch (Exception e) {
				result.completeExceptionally(e);
			}
		});
		return result;
	}

	private String get(String path) throws IOException, InterruptedException {
		HttpResponse<String> response = spotify.call("GET", path, null);
		if (response.statusCode() == 429) {
			long wait = response.headers().firstValueAsLong("Retry-After").orElse(2);
			Thread.sleep(Math.clamp(wait, 1, MAX_WAIT_S) * 1000);
			response = spotify.call("GET", path, null);
		}
		if (response.statusCode() >= 300) {
			throw new IOException(response.statusCode() == 403
					? Tr.t("Sign in again to use the library")
					: Tr.t("Spotify didn't answer, try again"));
		}
		return response.body();
	}

	private String myId() throws IOException, InterruptedException {
		String id = me;
		if (id == null) {
			JsonObject json = object(get("/me"));
			id = json == null ? "" : orEmpty(string(json, "id"));
			me = id;
		}
		return id;
	}

	static String encode(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
	}

	static Page<LibraryItem> parseSearch(String body) {
		JsonObject json = object(body);
		JsonObject tracks = json == null ? null : child(json, "tracks");
		return tracks == null ? Page.empty() : page(tracks, entry -> entry, "");
	}

	static Page<LibraryItem> parseSaved(String body) {
		JsonObject json = object(body);
		return json == null ? Page.empty() : page(json, entry -> child(entry, "track"), "");
	}

	static Page<LibraryItem> parsePlaylistItems(String body, String playlistUri) {
		JsonObject json = object(body);
		if (json == null) {
			return Page.empty();
		}
		// Newer replies call each entry's song "item", older ones "track"
		return page(json, entry -> {
			JsonObject song = child(entry, "item");
			return song != null ? song : child(entry, "track");
		}, playlistUri);
	}

	static Page<LibraryPlaylist> parsePlaylists(String body, String me) {
		JsonObject json = object(body);
		if (json == null) {
			return Page.empty();
		}
		List<LibraryPlaylist> out = new ArrayList<>();
		for (JsonObject p : objects(array(json, "items"))) {
			String id = string(p, "id");
			if (id == null) {
				continue;
			}
			JsonObject owner = child(p, "owner");
			boolean mine = owner != null && me.equals(string(owner, "id"));
			boolean shared = p.get("collaborative") instanceof JsonElement e && e.isJsonPrimitive() && e.getAsBoolean();
			JsonObject count = child(p, "items");
			if (count == null) {
				count = child(p, "tracks");
			}
			int songs = count == null ? 0 : (int) number(count, "total");
			out.add(new LibraryPlaylist(id, orEmpty(string(p, "uri")), orEmpty(string(p, "name")), bestImage(array(p, "images")), songs, mine || shared));
		}
		return new Page<>(List.copyOf(out), (int) number(json, "total"), (int) number(json, "offset"));
	}

	private interface Unwrap {
		@Nullable JsonObject song(JsonObject entry);
	}

	private static Page<LibraryItem> page(JsonObject list, Unwrap unwrap, String context) {
		List<LibraryItem> out = new ArrayList<>();
		JsonArray entries = array(list, "items");
		for (JsonObject entry : objects(entries)) {
			JsonObject song = unwrap.song(entry);
			LibraryItem item = song == null ? null : item(song, context);
			if (item != null) {
				out.add(item);
			}
		}
		int total = (int) number(list, "total");
		// Skipped entries (podcasts, local files) still count towards the total, so paging stays right
		return new Page<>(List.copyOf(out), total, (int) number(list, "offset"));
	}

	/** A playable song, or null for podcasts, local files and gaps. */
	static @Nullable LibraryItem item(JsonObject song, String context) {
		String id = string(song, "id");
		String uri = string(song, "uri");
		if (id == null || uri == null || !uri.startsWith("spotify:track:")) {
			return null;
		}
		List<String> names = new ArrayList<>();
		for (JsonObject artist : objects(array(song, "artists"))) {
			String name = string(artist, "name");
			if (name != null) {
				names.add(name);
			}
		}
		JsonObject album = child(song, "album");
		return new LibraryItem(id, uri, orEmpty(string(song, "name")), String.join(", ", names),
				album == null ? "" : orEmpty(string(album, "name")),
				album == null ? "" : bestImage(array(album, "images")),
				number(song, "duration_ms"), context);
	}

	private static List<JsonObject> objects(@Nullable JsonArray array) {
		List<JsonObject> out = new ArrayList<>();
		if (array != null) {
			for (JsonElement e : array) {
				if (e.isJsonObject()) {
					out.add(e.getAsJsonObject());
				}
			}
		}
		return out;
	}

	/** Playlist songs play on through the playlist; anything else plays the picked song and the ones listed after it. */
	static String playBody(LibraryItem item, List<LibraryItem> after) {
		JsonObject body = new JsonObject();
		if (!item.context().isEmpty()) {
			body.addProperty("context_uri", item.context());
			JsonObject offset = new JsonObject();
			offset.addProperty("uri", item.uri());
			body.add("offset", offset);
		} else {
			JsonArray uris = new JsonArray();
			uris.add(item.uri());
			for (LibraryItem next : after) {
				if (uris.size() >= PAGE) {
					break;
				}
				if (!next.uri().equals(item.uri())) {
					uris.add(next.uri());
				}
			}
			body.add("uris", uris);
		}
		return body.toString();
	}

	static @Nullable String firstDevice(String body) {
		JsonObject json = object(body);
		List<JsonObject> devices = objects(json == null ? null : array(json, "devices"));
		for (JsonObject d : devices) {
			if (d.get("is_restricted") instanceof JsonElement e && e.isJsonPrimitive() && e.getAsBoolean()) {
				continue;
			}
			String id = string(d, "id");
			if (id != null) {
				return id;
			}
		}
		return null;
	}
}
