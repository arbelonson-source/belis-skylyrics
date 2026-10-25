package dev.beli.skylyrics.lyrics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.beli.skylyrics.media.DemoSource;
import dev.beli.skylyrics.media.Track;

/**
 * Keeps the lyrics for the current song. Fetches in the background and caches on disk,
 * so a song you've heard before shows lyrics instantly (and offline).
 */
public final class LyricsService {
	private static final Logger LOG = LoggerFactory.getLogger("SkyLyrics");
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	/** Don't re-ask LRCLIB about a song it didn't have for this long. */
	private static final long NOT_FOUND_TTL_MS = 3L * 24 * 60 * 60 * 1000;

	public enum State {
		IDLE, LOADING, READY, NOT_FOUND, ERROR
	}

	private record CacheEntry(String synced, String plain, boolean instrumental, long savedAt) {
	}

	private final LrclibClient client;
	private final Path cacheDir;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "SkyLyrics lyrics");
		t.setDaemon(true);
		return t;
	});
	/** Bumped on every song change so a slow answer for the previous song is dropped. */
	private final AtomicLong generation = new AtomicLong();

	private volatile Track track = Track.NONE;
	private volatile Lyrics lyrics = Lyrics.NONE;
	private volatile State state = State.IDLE;

	public LyricsService(LrclibClient client, Path cacheDir) {
		this.client = client;
		this.cacheDir = cacheDir;
	}

	public Track track() {
		return track;
	}

	public Lyrics lyrics() {
		return lyrics;
	}

	public State state() {
		return state;
	}

	/** Called when the song changes. */
	public void load(Track newTrack) {
		long gen = generation.incrementAndGet();
		track = newTrack;
		lyrics = Lyrics.NONE;
		if (newTrack.isNone()) {
			state = State.IDLE;
			return;
		}
		if (DemoSource.PLAYER.equals(newTrack.player())) {
			lyrics = LrcParser.parseSynced(DemoSource.LYRICS);
			state = State.READY;
			return;
		}
		state = State.LOADING;
		executor.execute(() -> fetch(newTrack, gen, false));
	}

	/** Forget the cached answer for this song and ask again. */
	public void retry() {
		Track t = track;
		if (t.isNone() || DemoSource.PLAYER.equals(t.player())) {
			return;
		}
		long gen = generation.incrementAndGet();
		state = State.LOADING;
		executor.execute(() -> fetch(t, gen, true));
	}

	private void fetch(Track t, long gen, boolean skipCache) {
		Path file = cacheFile(t);
		try {
			CacheEntry cached = skipCache ? null : readCache(file);
			LrclibClient.Result result;
			if (cached != null && (found(cached) || System.currentTimeMillis() - cached.savedAt < NOT_FOUND_TTL_MS)) {
				result = new LrclibClient.Result(cached.synced, cached.plain, cached.instrumental);
			} else {
				result = client.find(t);
				writeCache(file, new CacheEntry(result.synced(), result.plain(), result.instrumental(), System.currentTimeMillis()));
			}
			if (generation.get() != gen) {
				return;
			}
			apply(t, result);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			if (generation.get() == gen) {
				LOG.warn("Couldn't get lyrics for {}: {}", t.key(), e.toString());
				state = State.ERROR;
			}
		}
	}

	private void apply(Track t, LrclibClient.Result result) {
		if (result.synced() != null) {
			Lyrics parsed = LrcParser.parseSynced(result.synced());
			if (!parsed.isEmpty()) {
				lyrics = parsed;
				state = State.READY;
				return;
			}
		}
		if (result.plain() != null) {
			lyrics = LrcParser.parsePlain(result.plain(), t.lengthMs());
			state = lyrics.isEmpty() ? State.NOT_FOUND : State.READY;
			return;
		}
		if (result.instrumental()) {
			lyrics = Lyrics.INSTRUMENTAL;
			state = State.READY;
			return;
		}
		state = State.NOT_FOUND;
	}

	private static boolean found(CacheEntry e) {
		return e.instrumental || e.synced != null || e.plain != null;
	}

	private Path cacheFile(Track t) {
		String id = (t.artist() + "\n" + t.title() + "\n" + t.album() + "\n" + Math.round(t.lengthMs() / 2000.0)).toLowerCase();
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8));
			return cacheDir.resolve(HexFormat.of().formatHex(hash, 0, 12) + ".json");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static CacheEntry readCache(Path file) {
		try {
			if (Files.isRegularFile(file)) {
				return GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), CacheEntry.class);
			}
		} catch (Exception e) {
			LOG.debug("Ignoring broken lyrics cache file {}", file);
		}
		return null;
	}

	private static void writeCache(Path file, CacheEntry entry) {
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(entry), StandardCharsets.UTF_8);
			Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			LOG.debug("Couldn't write lyrics cache: {}", e.toString());
		}
	}

	public void shutdown() {
		executor.shutdownNow();
	}
}
