package dev.beli.skylyrics.lyrics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntToLongFunction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
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
	/** Waits between tries while LRCLIB is having trouble; the last one repeats. */
	private static final long[] RETRY_DELAYS_MS = {2_000, 5_000, 10_000, 20_000, 30_000, 45_000, 60_000};

	public enum State {
		IDLE, LOADING, READY, NOT_FOUND, ERROR
	}

	private record CacheEntry(String synced, String plain, boolean instrumental, long savedAt) {
	}

	private final LrclibClient client;
	private final Path cacheDir;
	private final IntToLongFunction retryDelayMs;
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "SkyLyrics lyrics");
		t.setDaemon(true);
		return t;
	});
	/** Bumped on every song change so a slow answer for the previous song is dropped. */
	private final AtomicLong generation = new AtomicLong();

	private volatile Track track = Track.NONE;
	private volatile Lyrics lyrics = Lyrics.NONE;
	private volatile State state = State.IDLE;
	private volatile ScheduledFuture<?> pendingRetry;

	public LyricsService(LrclibClient client, Path cacheDir) {
		this(client, cacheDir, LyricsService::retryDelayMs);
	}

	LyricsService(LrclibClient client, Path cacheDir, IntToLongFunction retryDelayMs) {
		this.client = client;
		this.cacheDir = cacheDir;
		this.retryDelayMs = retryDelayMs;
	}

	/** How long to wait before trying again after {@code failures} failed tries in a row. */
	static long retryDelayMs(int failures) {
		return RETRY_DELAYS_MS[Math.min(Math.max(failures, 1), RETRY_DELAYS_MS.length) - 1];
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
		Track old = track;
		if (!newTrack.isNone() && newTrack.sameSong(old) && newTrack.lengthMs() == old.lengthMs()) {
			track = newTrack; // only the cover changed; keep the lyrics (or the lookup) going
			return;
		}
		long gen = generation.incrementAndGet();
		cancelRetry();
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
		executor.execute(() -> fetch(newTrack, gen, false, 0));
	}

	/** Forget the cached answer for this song and ask again. */
	public void retry() {
		Track t = track;
		if (t.isNone() || DemoSource.PLAYER.equals(t.player())) {
			return;
		}
		long gen = generation.incrementAndGet();
		cancelRetry();
		state = State.LOADING;
		executor.execute(() -> fetch(t, gen, true, 0));
	}

	private void cancelRetry() {
		ScheduledFuture<?> retry = pendingRetry;
		if (retry != null) {
			retry.cancel(false);
		}
	}

	/** LRCLIB busy or down, too many requests, a timeout or no network: worth asking again soon. */
	static boolean temporary(Exception e) {
		if (e instanceof LrclibClient.StatusException s) {
			return s.status() == 408 || s.status() == 429 || s.status() >= 500;
		}
		return e instanceof IOException || e instanceof JsonParseException;
	}

	/** @param failures tries for this song that already failed with a temporary problem */
	private void fetch(Track t, long gen, boolean skipCache, int failures) {
		if (generation.get() != gen) {
			return; // skipped past this song while it waited in the queue
		}
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
			if (failures > 0) {
				LOG.info("Got lyrics for {} after {} failed tries", t.key(), failures);
			}
			apply(t, result);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			if (generation.get() != gen) {
				return;
			}
			state = State.ERROR;
			if (!temporary(e)) {
				LOG.warn("Couldn't get lyrics for {}: {}", t.key(), e.toString());
				return;
			}
			// Keep trying while the song plays; never saved to the cache, so it isn't "no lyrics"
			long delay = retryDelayMs.applyAsLong(failures + 1);
			if (failures == 0) {
				LOG.warn("Couldn't get lyrics for {}: {}. Trying again while it plays.", t.key(), e.toString());
			} else {
				LOG.debug("Still no lyrics for {} ({}), try {} in {} ms", t.key(), e.toString(), failures + 2, delay);
			}
			try {
				pendingRetry = executor.schedule(() -> fetch(t, gen, skipCache, failures + 1), delay, TimeUnit.MILLISECONDS);
			} catch (RejectedExecutionException ignored) {
				// shutting down
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
		String id = (t.artist() + "\n" + t.title() + "\n" + t.album() + "\n" + Math.round(t.lengthMs() / 2000.0)).toLowerCase(Locale.ROOT);
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
