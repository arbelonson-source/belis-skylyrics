package dev.beli.skylyrics.media;

import dev.beli.skylyrics.util.LogText;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.beli.skylyrics.Tr;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import dev.beli.skylyrics.util.SafeFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A song library that needs no account: every different song the mod sees playing is remembered, newest first,
 * in a small file on this computer. Clicking one asks the player that played it to play it again, when that player
 * can (Linux, over MPRIS). Nothing leaves the computer.
 */
public final class HeardLibrary implements Library {
	/** The temp name 0.8.0 and earlier saved through ("heard" + a number + ".tmp"); a killed game's copy is cleaned up too. */
	static final java.util.regex.Pattern OLD_TEMP = java.util.regex.Pattern.compile("heard\\d+\\.tmp");

	private static final Logger LOG = LoggerFactory.getLogger("SkyLyrics");
	/** The oldest songs fall off the end after this many. */
	public static final int CAP = 200;
	static final int PAGE = 50;
	private static final int MAX_FIELD = 1000;

	/** How a heard song is started again. Only Linux can; elsewhere {@link #available()} is false. */
	public interface Opener {
		boolean available();

		/** What the running player says it can open ("file", "spotify"…), or null when that player isn't running. */
		@Nullable List<String> schemes(String player);

		boolean open(String player, String uri);
	}

	/** Opener for Windows and macOS, where there is nothing like MPRIS OpenUri. */
	public static final Opener NO_OPENER = new Opener() {
		@Override
		public boolean available() {
			return false;
		}

		@Override
		public @Nullable List<String> schemes(String player) {
			return null;
		}

		@Override
		public boolean open(String player, String uri) {
			return false;
		}
	};

	/** The Linux opener, over MPRIS. */
	public static final Opener MPRIS = new Opener() {
		@Override
		public boolean available() {
			return true;
		}

		@Override
		public @Nullable List<String> schemes(String player) {
			return MprisSource.supportedSchemes(player);
		}

		@Override
		public boolean open(String player, String uri) {
			return MprisSource.openUri(player, uri);
		}
	};

	/** One remembered song. */
	record Entry(String title, String artist, String album, long lengthMs, String coverUrl, String uri, String player) {
		String key() {
			return (artist + " – " + title).toLowerCase(Locale.ROOT);
		}

		LibraryItem item() {
			return new LibraryItem(key(), uri, title, artist, album, coverUrl, lengthMs, "");
		}
	}

	private final Path file;
	private final BooleanSupplier enabled;
	private final Opener opener;
	private final Object lock = new Object();
	private final List<Entry> songs = new ArrayList<>();
	private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "SkyLyrics heard");
		t.setDaemon(true);
		return t;
	});

	/**
	 * The keys of the list as last read from or written to the file. A song in the file that is in neither this nor our
	 * list is new from another game on the same folder; one that is in this but no longer in our list we removed. Only the
	 * writer thread touches it after the constructor.
	 */
	private final java.util.Set<String> baseline = new java.util.HashSet<>();
	private final SafeFile.Reader reader;
	private final SafeFile.Mover mover;
	private volatile boolean blockedLogged;

	public HeardLibrary(Path file, BooleanSupplier enabled, Opener opener) {
		this(file, enabled, opener, SafeFile::readText);
	}

	HeardLibrary(Path file, BooleanSupplier enabled, Opener opener, SafeFile.Reader reader) {
		this(file, enabled, opener, reader, Files::move);
	}

	HeardLibrary(Path file, BooleanSupplier enabled, Opener opener, SafeFile.Reader reader, SafeFile.Mover mover) {
		this.mover = mover;
		this.file = file;
		this.enabled = enabled;
		this.opener = opener;
		this.reader = reader;
		Loaded loaded = load(file, reader, SafeFile.READ_TRIES, SafeFile.READ_PAUSE_MS);
		songs.addAll(loaded.songs());
		for (Entry e : loaded.songs()) {
			baseline.add(e.key());
		}
	}

	public boolean enabled() {
		return enabled.getAsBoolean();
	}

	public int size() {
		synchronized (lock) {
			return songs.size();
		}
	}

	/**
	 * Remembers a song the mod just saw playing: it goes to the top, replacing an earlier copy of the same song.
	 * Does nothing when the feature is off, for nothing playing, or for the built-in demo song. Safe from any thread.
	 */
	public void record(Track track) {
		if (!enabled.getAsBoolean() || track.isNone() || track.sameSong(DemoSource.TRACK)) {
			return;
		}
		Entry fresh = new Entry(clip(track.title()), clip(track.artist()), clip(track.album()), Math.max(0, track.lengthMs()),
				remoteCover(clip(track.artUrl())), clip(track.uri()), clip(track.player()));
		synchronized (lock) {
			Entry old = null;
			for (int i = 0; i < songs.size(); i++) {
				if (songs.get(i).key().equals(fresh.key())) {
					old = songs.remove(i);
					break;
				}
			}
			if (old != null) {
				// The same song heard again: keep what the earlier time knew if this time doesn't say
				fresh = new Entry(fresh.title, fresh.artist, fresh.album.isEmpty() ? old.album : fresh.album,
						fresh.lengthMs > 0 ? fresh.lengthMs : old.lengthMs, fresh.coverUrl.isEmpty() ? remoteCover(old.coverUrl) : fresh.coverUrl,
						fresh.uri.isEmpty() ? old.uri : fresh.uri, fresh.player.isEmpty() ? old.player : fresh.player);
			}
			songs.addFirst(fresh);
			while (songs.size() > CAP) {
				songs.removeLast();
			}
		}
		save();
	}

	/**
	 * Only an http(s) cover is worth remembering: the list's cover cache loads nothing else, and a {@code file:} one is the
	 * Windows helper's temporary copy that is deleted within a few songs, so it would only be a dead path.
	 */
	static String remoteCover(String url) {
		return url.regionMatches(true, 0, "file:", 0, 5) ? "" : url;
	}

	/** Forgets every remembered song. */
	public void clear() {
		synchronized (lock) {
			songs.clear();
		}
		save();
	}

	/** Waits until everything asked to be written has been written. For tests and shutting down. */
	public void flush() {
		try {
			writer.submit(() -> {
			}).get();
		} catch (Exception e) {
			Thread.currentThread().interrupt();
		}
	}

	private static String clip(String s) {
		return dev.beli.skylyrics.util.TextCut.limit(s, MAX_FIELD);
	}

	private List<Entry> snapshot() {
		synchronized (lock) {
			return List.copyOf(songs);
		}
	}

	// ---- file ----

	/**
	 * Reads the file again and adds the songs another game (or a file that couldn't be read at start) put there since we
	 * last read or wrote it: those whose key is neither in our list nor in the baseline. They go after ours (the file holds
	 * no heard times), in the file's order, up to the cap. Songs we removed are in the baseline, so they stay gone.
	 * False when the file can't be read right now: writing then would replace what we couldn't see. Writer thread only.
	 */
	private boolean mergeFile() {
		List<Entry> old = new ArrayList<>();
		try {
			String text = SafeFile.readText(file, reader, SafeFile.READ_TRIES, SafeFile.READ_PAUSE_MS); // a lock may let go in a moment; this is the writer thread
			if (!text.isBlank()) {
				readSongs(text, old);
			}
		} catch (java.nio.file.NoSuchFileException | java.nio.charset.CharacterCodingException e) {
			old.clear(); // gone or damaged: nothing worth protecting
		} catch (IOException e) {
			if (!blockedLogged) {
				blockedLogged = true;
				LOG.warn("Recently heard songs not saved: {} can't be read right now ({})", LogText.of(file), LogText.of(e));
			}
			return false;
		} catch (RuntimeException e) {
			old.clear();
			keepCopy(file);
		}
		synchronized (lock) {
			java.util.Set<String> seen = new java.util.HashSet<>();
			for (Entry e : songs) {
				seen.add(e.key());
			}
			for (Entry e : old) {
				if (!baseline.contains(e.key()) && seen.add(e.key())) {
					songs.add(e);
				}
			}
			while (songs.size() > CAP) {
				songs.removeLast();
			}
		}
		return true;
	}

	/** Asks for the list to be written. The re-read and merge happen on the writer thread, never on the caller's. */
	private void save() {
		try {
			writer.execute(this::merged);
		} catch (RuntimeException e) {
			// Shutting down
		}
	}

	private void merged() {
		if (!mergeFile()) {
			return;
		}
		List<Entry> copy = snapshot();
		if (!write(file, copy, mover)) {
			return; // the file kept its old songs, so the baseline must too: what we removed stays removed on the next save
		}
		baseline.clear();
		for (Entry e : copy) {
			baseline.add(e.key());
		}
	}

	/** Writes a temporary file and moves it over the real one, so a crash never leaves half a file. */
	static boolean write(Path file, List<Entry> entries) {
		return write(file, entries, Files::move);
	}

	/** False when it couldn't be written (after SafeFile's retries); the file is then as it was. */
	static boolean write(Path file, List<Entry> entries, SafeFile.Mover mover) {
		try {
			Path dir = file.toAbsolutePath().getParent();
			Files.createDirectories(dir);
			Path tmp = Files.createTempFile(dir, file.getFileName() + ".", ".tmp");
			try {
				Files.writeString(tmp, toJson(entries), StandardCharsets.UTF_8);
				SafeFile.replace(tmp, file, mover, SafeFile.TRIES, SafeFile.PAUSE_MS);
			} finally {
				Files.deleteIfExists(tmp);
			}
			return true;
		} catch (IOException e) {
			LOG.warn("Couldn't save the recently heard songs: {}", LogText.of(e));
			return false;
		}
	}

	static String toJson(List<Entry> entries) {
		JsonArray array = new JsonArray();
		for (Entry e : entries) {
			JsonObject o = new JsonObject();
			o.addProperty("title", e.title);
			o.addProperty("artist", e.artist);
			o.addProperty("album", e.album);
			o.addProperty("lengthMs", e.lengthMs);
			o.addProperty("cover", e.coverUrl);
			o.addProperty("uri", e.uri);
			o.addProperty("player", e.player);
			array.add(o);
		}
		JsonObject root = new JsonObject();
		root.add("songs", array);
		return new Gson().toJson(root);
	}

	/**
	 * The songs in {@code file}, or none when it is missing, empty or damaged. A damaged file is first copied to
	 * {@code heard.json.bad}, so the next saved song doesn't wipe the history it still holds.
	 */
	static List<Entry> read(Path file) {
		return load(file, SafeFile::readText, SafeFile.READ_TRIES, SafeFile.READ_PAUSE_MS).songs();
	}

	/** What reading the file gave, and whether it couldn't be read at all (as opposed to missing, empty or damaged). */
	record Loaded(List<Entry> songs, boolean failed) {
	}

	static Loaded load(Path file, SafeFile.Reader reader, int tries, long pauseMs) {
		return load(file, reader, tries, pauseMs, Files::isReadable);
	}

	static Loaded load(Path file, SafeFile.Reader reader, int tries, long pauseMs, java.util.function.Predicate<Path> readable) {
		List<Entry> out = new ArrayList<>();
		if (file.toAbsolutePath().getParent() != null) {
			dev.beli.skylyrics.util.Leftovers.cleanTemps(file.toAbsolutePath().getParent(), file.getFileName().toString(), System.currentTimeMillis());
			dev.beli.skylyrics.util.Leftovers.cleanTemps(file.toAbsolutePath().getParent(), OLD_TEMP, System.currentTimeMillis());
		}
		if (!Files.isRegularFile(file)) {
			return new Loaded(out, false);
		}
		String text;
		try {
			text = SafeFile.readText(file, reader, tries, pauseMs);
		} catch (java.nio.file.NoSuchFileException e) {
			return new Loaded(out, false);
		} catch (java.nio.charset.CharacterCodingException e) {
			LOG.warn("The recently heard file isn't valid text, starting empty (kept a copy as heard.json.bad): {}", LogText.of(e));
			keepCopy(file);
			return new Loaded(out, false);
		} catch (IOException e) {
			if (!readable.test(file)) {
				// Not readable by permissions (a sharing lock leaves it readable): it will never read, so move it aside and start a new one.
				try {
					Path moved = SafeFile.moveAside(file);
					LOG.warn("The recently heard file {} can't be read (permissions), so it was moved to {} and a new one will be started.", LogText.of(file), LogText.of(moved));
					return new Loaded(out, false);
				} catch (IOException | RuntimeException moveError) {
					LOG.warn("The recently heard file {} can't be read and couldn't be moved aside: {}", LogText.of(file), LogText.of(moveError));
				}
			}
			LOG.warn("Couldn't read {}; recently heard songs won't be saved this session. Check the file's permissions or whether another program has it open: {}", LogText.of(file), LogText.of(e));
			return new Loaded(out, true);
		}
		if (text.isBlank()) {
			return new Loaded(out, false);
		}
		try {
			readSongs(text, out);
		} catch (RuntimeException e) {
			out.clear();
			LOG.warn("The recently heard file is damaged, starting empty (kept a copy as heard.json.bad): {}", LogText.of(e));
			keepCopy(file);
		}
		return new Loaded(out, false);
	}

	private static void keepCopy(Path file) {
		try {
			SafeFile.keepBad(file);
		} catch (IOException | RuntimeException e) {
			LOG.warn("Couldn't keep a copy of the damaged recently heard file: {}", LogText.of(e));
		}
	}

	/** Adds the songs in {@code text} to {@code out}; throws when it isn't a JSON object. A missing list is just no songs. */
	private static void readSongs(String text, List<Entry> out) {
		JsonElement root = new Gson().fromJson(text, JsonElement.class);
		if (root == null || !root.isJsonObject()) {
			throw new IllegalStateException("not a JSON object");
		}
		JsonElement songs = root.getAsJsonObject().get("songs");
		if (songs == null || songs.isJsonNull()) {
			return;
		}
		if (!songs.isJsonArray()) {
			throw new IllegalStateException("\"songs\" is not a list");
		}
		for (JsonElement e : songs.getAsJsonArray()) {
			try {
				if (!e.isJsonObject()) {
					continue;
				}
				JsonObject o = e.getAsJsonObject();
				String title = text(o, "title");
				if (title.isEmpty()) {
					continue;
				}
				Entry entry = new Entry(title, text(o, "artist"), text(o, "album"), Math.max(0, number(o, "lengthMs")),
						text(o, "cover"), text(o, "uri"), text(o, "player"));
				if (out.stream().noneMatch(x -> x.key().equals(entry.key())) && out.size() < CAP) {
					out.add(entry);
				}
			} catch (RuntimeException bad) {
				// One bad song doesn't cost the rest
			}
		}
	}

	private static String text(JsonObject o, String name) {
		JsonElement e = o.get(name);
		return e != null && e.isJsonPrimitive() ? clip(e.getAsString()) : "";
	}

	private static long number(JsonObject o, String name) {
		JsonElement e = o.get(name);
		try {
			return e != null && e.isJsonPrimitive() ? e.getAsLong() : 0;
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	// ---- Library ----

	/** The songs whose title, artist or album contain every word of {@code query}, ignoring case. Empty query = all. */
	static List<LibraryItem> filter(List<Entry> all, String query) {
		String[] words = query.strip().toLowerCase(Locale.ROOT).split("\\s+");
		List<LibraryItem> found = new ArrayList<>();
		for (Entry e : all) {
			String hay = (e.title + " " + e.artist + " " + e.album).toLowerCase(Locale.ROOT);
			boolean matches = true;
			for (String w : words) {
				if (!hay.contains(w)) {
					matches = false;
					break;
				}
			}
			if (matches) {
				found.add(e.item());
			}
		}
		return found;
	}

	@Override
	public CompletableFuture<SpotifyLibrary.Page<LibraryItem>> search(String query, int offset) {
		if (query.isBlank()) {
			return done(SpotifyLibrary.Page.empty());
		}
		return done(page(filter(snapshot(), query), offset));
	}

	/** The remembered songs, newest first. (Spotify's "liked songs" slot is where this list shows.) */
	@Override
	public CompletableFuture<SpotifyLibrary.Page<LibraryItem>> liked(int offset) {
		return done(page(filter(snapshot(), ""), offset));
	}

	@Override
	public CompletableFuture<SpotifyLibrary.Page<LibraryPlaylist>> playlists(int offset) {
		return done(SpotifyLibrary.Page.empty());
	}

	@Override
	public CompletableFuture<SpotifyLibrary.Page<LibraryItem>> playlist(LibraryPlaylist playlist, int offset) {
		return done(SpotifyLibrary.Page.empty());
	}

	/** What happened when a heard song was asked to play again. */
	enum Start {
		STARTED, CANT, NOT_RUNNING
	}

	@Override
	public CompletableFuture<String> play(LibraryItem item, List<LibraryItem> after) {
		Entry entry = null;
		for (Entry e : snapshot()) {
			if (e.key().equals(item.id())) {
				entry = e;
				break;
			}
		}
		if (entry == null) {
			return done(Tr.t("This player can't start songs from here"));
		}
		Entry chosen = entry;
		return CompletableFuture.supplyAsync(() -> switch (start(chosen)) {
			case STARTED -> "";
			case NOT_RUNNING -> Tr.t("%s isn't open, so it can't play this", chosen.player);
			case CANT -> Tr.t("This player can't start songs from here");
		});
	}

	@Override
	public CompletableFuture<String> playPlaylist(LibraryPlaylist playlist) {
		return done(Tr.t("This player can't start songs from here"));
	}

	/** Asks the player that played {@code entry} to play it again. */
	Start start(Entry entry) {
		if (entry.uri.isEmpty() || entry.player.isEmpty() || !opener.available()) {
			return Start.CANT;
		}
		List<String> schemes = opener.schemes(entry.player);
		if (schemes == null) {
			return Start.NOT_RUNNING;
		}
		String uri = openableUri(entry.uri, schemes);
		return uri != null && opener.open(entry.player, uri) ? Start.STARTED : Start.CANT;
	}

	/**
	 * The address to hand to a player that accepts {@code schemes}, or null when it can't take it. Spotify's web
	 * address for a song becomes its own spotify:track: form, which is the one the Spotify app accepts.
	 */
	public static @Nullable String openableUri(String uri, List<String> schemes) {
		String scheme = schemeOf(uri);
		if (scheme.equals("https") || scheme.equals("http")) {
			try {
				URI u = URI.create(uri);
				String[] parts = u.getPath() == null ? new String[0] : u.getPath().split("/");
				if ("open.spotify.com".equals(u.getHost()) && parts.length >= 3 && parts[1].equals("track") && !parts[2].isEmpty()
						&& schemes.contains("spotify")) {
					return "spotify:track:" + parts[2];
				}
			} catch (IllegalArgumentException e) {
				return null;
			}
		}
		if (scheme.isEmpty()) {
			return null;
		}
		// mpv leaves "file" out of its list (it lists http, https, smb…) yet plays a file address fine, so a player
		// that lists web addresses counts as able to play files too. Browsers list nothing and Spotify only spotify.
		boolean fileToo = scheme.equals("file") && (schemes.contains("http") || schemes.contains("https"));
		return schemes.contains(scheme) || fileToo ? uri : null;
	}

	/** The lower-case part before the first colon ("file" for file:///a.mp3), or empty. */
	static String schemeOf(String uri) {
		int colon = uri.indexOf(':');
		if (colon <= 0) {
			return "";
		}
		String scheme = uri.substring(0, colon).toLowerCase(Locale.ROOT);
		return scheme.matches("[a-z][a-z0-9+.-]*") ? scheme : "";
	}

	static <T> SpotifyLibrary.Page<T> page(List<T> all, int offset) {
		int from = Math.max(0, Math.min(offset, all.size()));
		return new SpotifyLibrary.Page<>(List.copyOf(all.subList(from, Math.min(all.size(), from + PAGE))), all.size(), from);
	}

	private static <T> CompletableFuture<T> done(T value) {
		return CompletableFuture.completedFuture(value);
	}
}
