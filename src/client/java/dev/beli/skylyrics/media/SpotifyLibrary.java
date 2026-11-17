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
import java.util.function.Predicate;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.beli.skylyrics.Tr;
import org.jspecify.annotations.Nullable;

/**
 * Reads the signed-in user's Spotify songs for the song library and starts the one they pick.
 * Searches and page loads share one background thread, and starting a song has its own, so a slow or rate-limited
 * search never holds up Play. The screen just checks whether the answer has arrived.
 */
public final class SpotifyLibrary implements Library {
	/** Spotify hands out at most this many search results per request. */
	public static final int SEARCH_PAGE = 10;
	/** Liked songs, playlists and playlist songs come this many at a time. */
	public static final int PAGE = 50;
	/** How long a busy Spotify can make us wait before we give up on one request. */
	private static final long MAX_WAIT_S = 30;
	/** Spotify refuses a search that starts past this many results. */
	static final int SEARCH_CAP = 1000;

	/** A slice of a longer list; {@code total} is how many there are altogether. */
	public record Page<T>(List<T> items, int total, int offset, int raw) {
		public Page(List<T> items, int total, int offset) {
			this(items, total, offset, items.size());
		}

		/** The same slice, said to start at {@code start}: the offset we asked for, not whatever the reply claims. */
		Page<T> at(int start) {
			return new Page<>(items, total, start, raw);
		}

		/** The same slice of a list that is treated as ending at {@code limit} songs. */
		Page<T> capped(int limit) {
			return total <= limit ? this : new Page<>(items, limit, offset, raw);
		}

		public static <T> Page<T> empty() {
			return new Page<>(List.of(), 0, 0);
		}

		/** Where the next slice starts, or -1 when this was the last one. {@code raw} counts every entry Spotify sent, kept or dropped. */
		public int next() {
			int next = offset + raw;
			return raw == 0 || next >= total ? -1 : next;
		}
	}

	private final SpotifyWebSource spotify;
	private final Predicate<String> openHere;
	private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "SkyLyrics library");
		t.setDaemon(true);
		return t;
	});
	/** Starting a song and the like never wait behind searches and page loads. */
	private final ExecutorService controls = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "SkyLyrics library play");
		t.setDaemon(true);
		return t;
	});
	private final OwnId me = new OwnId();
	/** The job running on this thread, so a wait for Spotify can end as soon as its list is thrown away. */
	private final ThreadLocal<CompletableFuture<?>> running = new ThreadLocal<>();
	private final Caller caller;

	/** One request to Spotify; the tests put their own answers here. */
	interface Caller {
		HttpResponse<String> call(String method, String path, @Nullable String json) throws IOException, InterruptedException;
	}

	public SpotifyLibrary(SpotifyWebSource spotify) {
		this(spotify, uri -> false);
	}

	/**
	 * @param openHere plays a song in the Spotify app on this computer, for when Spotify's servers won't start it
	 *                 (no Premium, or no app open as far as they know); false if that didn't work either
	 */
	public SpotifyLibrary(SpotifyWebSource spotify, Predicate<String> openHere) {
		this(spotify, openHere, spotify::call);
	}

	SpotifyLibrary(SpotifyWebSource spotify, Predicate<String> openHere, Caller caller) {
		this.spotify = spotify;
		this.openHere = openHere;
		this.caller = caller;
	}

	@Override
	public CompletableFuture<Page<LibraryItem>> search(String query, int offset) {
		String q = query.strip();
		if (q.isEmpty()) {
			return CompletableFuture.completedFuture(Page.empty());
		}
		return run(() -> parseSearch(get("/search?type=track&limit=" + SEARCH_PAGE + "&offset=" + offset + "&q=" + encode(q))).at(offset).capped(SEARCH_CAP));
	}

	@Override
	public CompletableFuture<Page<LibraryItem>> liked(int offset) {
		return run(() -> parseSaved(get("/me/tracks?limit=" + PAGE + "&offset=" + offset)).at(offset));
	}

	@Override
	public CompletableFuture<Page<LibraryPlaylist>> playlists(int offset) {
		return run(() -> parsePlaylists(get("/me/playlists?limit=" + PAGE + "&offset=" + offset), myId()).at(offset));
	}

	@Override
	public CompletableFuture<Page<LibraryItem>> playlist(LibraryPlaylist playlist, int offset) {
		return run(() -> parsePlaylistItems(get("/playlists/" + encode(playlist.id()) + "/items?limit=" + PAGE + "&offset=" + offset), playlist.uri()).at(offset));
	}

	/**
	 * Starts {@code item}, then the songs listed after it (or the rest of its playlist).
	 * Completes with an empty string when it worked, otherwise with a short message for the user.
	 */
	@Override
	public CompletableFuture<String> play(LibraryItem item, List<LibraryItem> after) {
		return send(playBody(item, after), item.uri());
	}

	@Override
	public CompletableFuture<String> playPlaylist(LibraryPlaylist playlist) {
		JsonObject body = new JsonObject();
		body.addProperty("context_uri", playlist.uri());
		return send(body.toString(), playlist.uri());
	}

	private CompletableFuture<String> send(String body, String uri) {
		return run(controls, () -> {
			HttpResponse<String> response = caller.call("PUT", "/me/player/play", body);
			if (response.statusCode() == 404) {
				String device;
				try {
					device = firstDevice(get("/me/player/devices"));
				} catch (IOException e) {
					device = null; // no answer about devices counts as none, so the app on this computer still gets its try
				}
				if (device != null) {
					response = caller.call("PUT", "/me/player/play?device_id=" + encode(device), body);
				}
			}
			return outcome(response.statusCode(), response.body(), uri, openHere);
		}).exceptionally(e -> {
			Throwable cause = e instanceof java.util.concurrent.CompletionException && e.getCause() != null ? e.getCause() : e;
			boolean specific = cause instanceof SpotifyWebSource.NotSetUp || MediaTracker.networkFailure(cause);
			return specific ? errorText(cause) : Tr.t("Spotify didn't start the song, try again");
		});
	}

	/** What to tell the user after Spotify answered {@code status}, trying the app on this computer if it said no. */
	static String outcome(int status, String uri, Predicate<String> openHere) {
		return outcome(status, null, uri, openHere);
	}

	static String outcome(int status, @Nullable String body, String uri, Predicate<String> openHere) {
		return switch (status) {
			case 200, 202, 204 -> "";
			case 403 -> openHere.test(uri) ? "" : premiumMissing(body) ? Tr.t("Spotify Premium is needed to start songs")
					: Tr.t("Spotify didn't allow that on this device");
			case 404 -> openHere.test(uri) ? "" : Tr.t("Open Spotify on your computer or phone first");
			default -> Tr.t("Spotify didn't start the song, try again");
		};
	}

	/** False only when Spotify's 403 says in {@code error.reason} that it is something other than Premium; no readable reason keeps the old Premium text. */
	private static boolean premiumMissing(@Nullable String body) {
		JsonObject json = body == null ? null : object(body);
		JsonObject error = json == null ? null : child(json, "error");
		String reason = error == null ? null : string(error, "reason");
		return reason == null || reason.equals("PREMIUM_REQUIRED");
	}

	/** What the library shows for a failed load: the step to do or the internet's fault in words, never a raw network error. */
	public static String errorText(@Nullable Throwable error) {
		if (error instanceof SpotifyWebSource.NotSetUp) {
			return Tr.t("Sign in to Spotify on the Music tab to see your songs here");
		}
		if (MediaTracker.networkFailure(error)) {
			return Tr.t("Can't reach the internet or Spotify");
		}
		return error instanceof IOException && error.getMessage() != null ? error.getMessage() : Tr.t("Something went wrong, try again");
	}

	private interface Job<T> {
		T run() throws IOException, InterruptedException;
	}

	private <T> CompletableFuture<T> run(Job<T> job) {
		return run(worker, job);
	}

	/** Runs {@code job} on {@code pool}; a future cancelled before its turn (its list was thrown away) never makes the request. */
	private <T> CompletableFuture<T> run(ExecutorService pool, Job<T> job) {
		CompletableFuture<T> result = new CompletableFuture<>();
		pool.execute(() -> {
			if (result.isDone()) {
				return;
			}
			running.set(result);
			try {
				result.complete(job.run());
			} catch (Exception e) {
				result.completeExceptionally(e);
			} finally {
				running.remove();
			}
		});
		return result;
	}

	/** Waits {@code ms}, but ends at once (by CancellationException) when the job's list was thrown away meanwhile, so the next job isn't held up. */
	private void pause(long ms) throws InterruptedException {
		CompletableFuture<?> job = running.get();
		if (job == null) {
			Thread.sleep(ms);
			return;
		}
		try {
			job.get(ms, java.util.concurrent.TimeUnit.MILLISECONDS);
		} catch (java.util.concurrent.TimeoutException e) {
			// waited it out
		} catch (java.util.concurrent.ExecutionException e) {
			// not possible while the job runs, nothing to wait for either way
		}
	}

	private String get(String path) throws IOException, InterruptedException {
		HttpResponse<String> response = caller.call("GET", path, null);
		if (response.statusCode() == 429) {
			long wait = waitSeconds(response.headers().firstValue("Retry-After").orElse(null));
			if (wait > MAX_WAIT_S) {
				throw new IOException(Tr.t("Spotify didn't answer, try again")); // a long wait would hold up everything queued behind it
			}
			pause(Math.max(1, wait) * 1000);
			response = caller.call("GET", path, null);
		}
		if (response.statusCode() >= 300) {
			// With the library scopes granted a 403 is Spotify refusing this app or account (development mode), and signing in again can't change that
			throw new IOException(response.statusCode() != 403 ? Tr.t("Spotify didn't answer, try again")
					: spotify.canBrowse() ? Tr.t("Spotify blocked this app. On the Spotify dashboard, add your account under User Management")
					: Tr.t("Sign out and sign in again on the Music tab to use the song library"));
		}
		if (object(response.body()) == null) {
			// An empty reply or a login page from the Wi-Fi is no answer: say so instead of showing an end of the list
			throw new IOException(Tr.t("Spotify didn't answer, try again"));
		}
		return response.body();
	}

	/** The songs of {@code page} that {@code shown} doesn't have yet, matched by id; anything but a song stays. */
	public static <T> List<T> withoutRepeats(List<?> shown, List<T> page) {
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (Object o : shown) {
			if (o instanceof LibraryItem item) {
				seen.add(item.id());
			}
		}
		List<T> out = new ArrayList<>();
		for (T o : page) {
			if (!(o instanceof LibraryItem item) || seen.add(item.id())) {
				out.add(o);
			}
		}
		return out;
	}

	/** A Retry-After header (seconds or an HTTP date) in whole seconds; 2 when it is missing or unreadable. */
	static long waitSeconds(@Nullable String header) {
		if (header == null) {
			return 2;
		}
		try {
			return Math.max(0, Long.parseLong(header.strip()));
		} catch (NumberFormatException e) {
			long ms = dev.beli.skylyrics.lyrics.LrclibClient.retryAfterMs(header);
			return ms < 0 ? 2 : (ms + 999) / 1000;
		}
	}

	private String myId() throws IOException, InterruptedException {
		return me.get(spotify.signInGeneration(), () -> {
			JsonObject json = object(get("/me"));
			return json == null ? "" : orEmpty(string(json, "id"));
		});
	}

	/** Reads the id of the signed-in account's /me once per sign-in, and never keeps an empty one. */
	static final class OwnId {
		interface Fetch {
			String run() throws IOException, InterruptedException;
		}

		private @Nullable String id;
		private long generation;

		synchronized String get(long signIn, Fetch fetch) throws IOException, InterruptedException {
			if (id != null && generation == signIn) {
				return id;
			}
			String fresh = fetch.run();
			if (fresh.isEmpty()) {
				id = null; // a bad answer isn't remembered: the next call asks again
			} else {
				id = fresh;
				generation = signIn;
			}
			return fresh;
		}
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
			boolean mine = me.isEmpty() || owner != null && me.equals(string(owner, "id"));
			boolean shared = p.get("collaborative") instanceof JsonElement e && e.isJsonPrimitive() && e.getAsBoolean();
			JsonObject count = child(p, "items");
			if (count == null) {
				count = child(p, "tracks");
			}
			int songs = count == null ? 0 : (int) number(count, "total");
			out.add(new LibraryPlaylist(id, orEmpty(string(p, "uri")), plain(string(p, "name")), bestImage(array(p, "images")), songs, mine || shared));
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
		// Skipped entries (podcasts, local files) still count towards Spotify's offset, so paging goes by the raw count
		return new Page<>(List.copyOf(out), total, (int) number(list, "offset"), entries == null ? 0 : entries.size());
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
				names.add(plain(name));
			}
		}
		JsonObject album = child(song, "album");
		return new LibraryItem(id, uri, plain(string(song, "name")), String.join(", ", names),
				album == null ? "" : plain(string(album, "name")),
				album == null ? "" : bestImage(array(album, "images")),
				number(song, "duration_ms"), context);
	}

	/** A name for the list on screen: never null, and without the section-sign codes Minecraft would read as styling. */
	static String plain(@Nullable String name) {
		return Track.cap(Track.clean(orEmpty(name)), Track.MAX_TEXT);
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

	/** The device to start on: the one playing now, else a computer, else any that isn't restricted. */
	static @Nullable String firstDevice(String body) {
		JsonObject json = object(body);
		String computer = null;
		String any = null;
		for (JsonObject d : objects(json == null ? null : array(json, "devices"))) {
			if (flag(d, "is_restricted")) {
				continue;
			}
			String id = string(d, "id");
			if (id == null) {
				continue;
			}
			if (flag(d, "is_active")) {
				return id;
			}
			if (computer == null && "Computer".equals(string(d, "type"))) {
				computer = id;
			}
			if (any == null) {
				any = id;
			}
		}
		return computer != null ? computer : any;
	}

	private static boolean flag(JsonObject o, String key) {
		return o.get(key) instanceof JsonElement e && e.isJsonPrimitive() && e.getAsBoolean();
	}
}
