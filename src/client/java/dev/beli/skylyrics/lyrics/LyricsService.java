package dev.beli.skylyrics.lyrics;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntToLongFunction;
import java.util.regex.Pattern;

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
	/** Songs kept on disk (a few KB each); past this the ones not played for longest go. */
	private static final int DISK_FILES = 3000;
	/** The version written into new cache files. */
	private static final int CACHE_VERSION = 3;
	/** Only our own lyrics files; the folder also holds the Spotify login. */
	private static final Pattern CACHE_NAME = Pattern.compile("[0-9a-f]{24}\\.json(\\.tmp)?");

	public enum State {
		IDLE, LOADING, READY, NOT_FOUND, ERROR
	}

	/**
	 * @param v which version wrote the file; 0 (missing) for 0.7.8 and older, which also saved the backup site's
	 *          stand-ins, so those can't be told apart from a real answer
	 */
	record CacheEntry(String synced, String plain, boolean instrumental, long savedAt, int v) {
	}

	private final LyricsSource client;
	private final Path cacheDir;
	private final IntToLongFunction retryDelayMs;
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "SkyLyrics lyrics");
		t.setDaemon(true);
		return t;
	});
	/** Bumped on every song change so a slow answer for the previous song is dropped. */
	private final AtomicLong generation = new AtomicLong();

	/** What the song, its lyrics and the state are right now. One object, so a reader never sees the new song with the old lyrics. */
	public record Snapshot(Track track, Lyrics lyrics, State state) {
		/**
		 * What to show while {@code playing} is the song the player hears. The tracker moves to a new song a moment before
		 * the lyrics are asked for: until then the old song's lyrics and state must not be drawn with the new title. The
		 * same song means the same title and artist (a late album alone doesn't reload the lyrics).
		 */
		public Snapshot forSong(Track playing) {
			if (track.title().equals(playing.title()) && track.artist().equals(playing.artist())) {
				return this;
			}
			return new Snapshot(playing, Lyrics.NONE, State.IDLE);
		}
	}

	private volatile Snapshot current = new Snapshot(Track.NONE, Lyrics.NONE, State.IDLE);
	/** Test seam: runs just before a worker publishes its answer, after the work that takes time. */
	volatile Runnable beforePublish = () -> { };
	/** The fetch running or waiting (or the retry scheduled) for the current song. */
	private volatile Future<?> pendingFetch;
	/**
	 * Held while the generation changes together with {@link #pendingFetch}, and while a fetch that checked its
	 * generation sets {@link #pendingFetch}: a late retry or re-check can't take it over from the next song's fetch.
	 */
	private final Object fetchLock = new Object();

	public LyricsService(LyricsSource client, Path cacheDir) {
		this(client, cacheDir, LyricsService::retryDelayMs);
	}

	LyricsService(LyricsSource client, Path cacheDir, IntToLongFunction retryDelayMs) {
		this.client = client;
		this.cacheDir = cacheDir;
		this.retryDelayMs = retryDelayMs;
		executor.execute(() -> tidyDisk(cacheDir, DISK_FILES));
	}

	/** How long to wait before trying again after {@code failures} failed tries in a row. */
	static long retryDelayMs(int failures) {
		return RETRY_DELAYS_MS[Math.min(Math.max(failures, 1), RETRY_DELAYS_MS.length) - 1];
	}

	/** The song, its lyrics and the state as they are right now, read once so they belong together. */
	public Snapshot snapshot() {
		return current;
	}

	/** Dev test hook only: swaps the shown lyrics for the current song, keeping track and state. */
	public void swapLyricsForTest(Lyrics l) {
		synchronized (fetchLock) {
			current = new Snapshot(current.track, l, current.state);
		}
	}

	public Track track() {
		return current.track;
	}

	public Lyrics lyrics() {
		return current.lyrics;
	}

	public State state() {
		return current.state;
	}

	/** Called when the song changes. */
	public void load(Track newTrack) {
		synchronized (fetchLock) {
			Snapshot now = current;
			Track old = now.track;
			if (!newTrack.isNone() && newTrack.sameSong(old) && newTrack.lengthMs() == old.lengthMs()) {
				current = new Snapshot(newTrack, now.lyrics, now.state); // only the cover changed; keep the lyrics (or the lookup) going
				return;
			}
			long gen = generation.incrementAndGet();
			cancelRetry();
			if (newTrack.isNone()) {
				current = new Snapshot(newTrack, Lyrics.NONE, State.IDLE);
				return;
			}
			if (DemoSource.PLAYER.equals(newTrack.player())) {
				current = new Snapshot(newTrack, LrcParser.parseSynced(DemoSource.LYRICS), State.READY);
				return;
			}
			current = new Snapshot(newTrack, Lyrics.NONE, State.LOADING);
			pendingFetch = executor.submit(() -> fetch(newTrack, gen, false, 0));
		}
	}

	/** Forget the cached answer for this song and ask again. */
	public void retry() {
		synchronized (fetchLock) {
			Snapshot now = current;
			Track t = now.track; // read under the lock: a load() in between can't leave us asking about the old song
			if (t.isNone() || DemoSource.PLAYER.equals(t.player())) {
				return;
			}
			long gen = generation.incrementAndGet();
			cancelRetry();
			current = new Snapshot(t, now.lyrics, State.LOADING);
			pendingFetch = executor.submit(() -> fetch(t, gen, true, 0));
		}
	}

	/**
	 * Stops the previous song's fetch or retry. One still waiting on the network is interrupted, so the new
	 * song isn't queued behind up to four slow requests; its answer would be dropped anyway.
	 */
	private void cancelRetry() {
		Future<?> previous = pendingFetch;
		if (previous != null) {
			previous.cancel(true);
		}
	}

	/** A lyrics site busy or down, too many requests, a timeout or no network: worth asking again soon. */
	static boolean temporary(Exception e) {
		if (e instanceof LrclibClient.StatusException s) {
			// 3xx: a hotspot login page redirecting an https call; 403: a proxy or the site pushing back
			int status = s.status();
			return status / 100 == 3 || status == 403 || status == 408 || status == 429 || status >= 500;
		}
		return e instanceof IOException || e instanceof JsonParseException;
	}

	/** The longest wait between tries. */
	private static final long MAX_DELAY_MS = RETRY_DELAYS_MS[RETRY_DELAYS_MS.length - 1];

	/**
	 * How long to wait before the next try after {@code e}, given the usual wait: longer when the site said to
	 * slow down (429) or turned us away (403), and as long as its Retry-After asked, but never over the usual maximum.
	 */
	static long delayFor(Exception e, long usualMs) {
		if (e instanceof LrclibClient.StatusException s && (s.status() == 429 || s.status() == 403)) {
			long wait = s.retryAfterMs() >= 0 ? Math.max(usualMs, s.retryAfterMs()) : usualMs * 3;
			return Math.min(wait, MAX_DELAY_MS);
		}
		return usualMs;
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
			CacheEntry unchecked = null; // a found answer saved by an older version, to ask the main site about once more
			if (cached != null && keepCached(parse(new LrclibClient.Result(cached.synced, cached.plain, cached.instrumental), t.lengthMs()),
					cached.savedAt, System.currentTimeMillis())) {
				result = new LrclibClient.Result(cached.synced, cached.plain, cached.instrumental);
				file.toFile().setLastModified(System.currentTimeMillis()); // played again, so kept longer
				if (cached.v < CACHE_VERSION && found(parse(result, t.lengthMs()))) {
					unchecked = cached;
				}
			} else {
				result = client.find(t);
				// A stand-in from the backup (main site was down) isn't saved, so the next play asks the main site again
				if (!result.stopgap()) {
					writeCache(file, new CacheEntry(result.synced(), result.plain(), result.instrumental(), System.currentTimeMillis(), CACHE_VERSION));
				}
			}
			if (generation.get() != gen) {
				return;
			}
			if (failures > 0) {
				LOG.info("Got lyrics after {} failed tries", failures);
				LOG.debug("Those lyrics were for {}", t.key());
			}
			if (!apply(t, gen, result)) {
				return;
			}
			if (unchecked != null) {
				recheckLater(t, gen, file, unchecked);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			if (generation.get() != gen) {
				return;
			}
			if (!temporary(e)) {
				// The site answered (an odd status) or something broke here: not the internet's fault, so it isn't blamed
				LOG.warn("Couldn't get lyrics: {}", e.toString());
				LOG.debug("That was for {}", t.key());
				setNotFound(gen);
				return;
			}
			// Keep trying while the song plays; never saved to the cache, so it isn't "no lyrics"
			long delay = delayFor(e, retryDelayMs.applyAsLong(failures + 1));
			if (failures == 0) {
				LOG.info("Couldn't get lyrics yet: {}. Trying again while it plays.", e.toString());
				LOG.debug("That was for {}", t.key());
			} else {
				LOG.debug("Still no lyrics for {} ({}), try {} in {} ms", t.key(), e.toString(), failures + 2, delay);
			}
			beforePublish.run();
			synchronized (fetchLock) {
				if (generation.get() != gen) {
					return; // a new song's load() came in meanwhile and owns pendingFetch now
				}
				current = new Snapshot(current.track, current.lyrics, State.ERROR);
				try {
					pendingFetch = executor.schedule(() -> fetch(t, gen, skipCache, failures + 1), delay, TimeUnit.MILLISECONDS);
				} catch (RejectedExecutionException ignored) {
					// shutting down
				}
			}
		}
	}

	private void recheckLater(Track t, long gen, Path file, CacheEntry old) {
		synchronized (fetchLock) {
			if (generation.get() != gen) {
				return; // a new song's load() came in meanwhile and owns pendingFetch now
			}
			try {
				pendingFetch = executor.submit(() -> recheck(t, gen, file, old));
			} catch (RejectedExecutionException ignored) {
				// shutting down
			}
		}
	}

	/**
	 * Asks the main site once more about a song saved by an older version, whose lyrics may have been the backup's
	 * stand-in. Other lyrics replace the file (and the shown lyrics, if the song is still playing); the same lyrics keep the
	 * old ones and stamp the file so it isn't asked again. Nothing found keeps the shown lyrics for this play and deletes
	 * the file, so the next play looks the song up fresh. Another stand-in means the main site is down
	 * again, so that, like a failure, leaves the file as it is and the next play tries again.
	 */
	private void recheck(Track t, long gen, Path file, CacheEntry old) {
		if (generation.get() != gen) {
			return;
		}
		try {
			LrclibClient.Result result = client.find(t);
			if (result.stopgap()) {
				return;
			}
			if (!found(parse(result, t.lengthMs()))) {
				// A real "no lyrics" from the main site: what v0.8.0 and older saved came from looser matching.
				// The lyrics shown now stay for this play (they may well be fine); the file goes, so the next play asks fresh.
				try {
					Files.deleteIfExists(file);
				} catch (IOException e) {
					LOG.debug("Couldn't delete the old lyrics file {}: {}", file, e.toString());
				}
				return;
			}
			boolean same = java.util.Objects.equals(result.synced(), old.synced) && java.util.Objects.equals(result.plain(), old.plain)
					&& result.instrumental() == old.instrumental;
			// The same lyrics again (most songs) aren't applied again, which would start the shown line over
			if (!same && found(parse(result, t.lengthMs()))) {
				writeCache(file, new CacheEntry(result.synced(), result.plain(), result.instrumental(), System.currentTimeMillis(), CACHE_VERSION));
				apply(t, gen, result);
			} else {
				writeCache(file, new CacheEntry(old.synced, old.plain, old.instrumental, old.savedAt, CACHE_VERSION));
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			LOG.debug("Couldn't check the saved lyrics for {} again: {}", t.key(), e.toString());
		}
	}

	/** Parses first (that takes time), then publishes only if this song is still the current one. @return false if it was dropped */
	private boolean apply(Track t, long gen, LrclibClient.Result result) {
		Lyrics parsed = parse(result, t.lengthMs());
		beforePublish.run();
		synchronized (fetchLock) {
			if (generation.get() != gen) {
				return false;
			}
			current = new Snapshot(current.track, parsed, found(parsed) ? State.READY : State.NOT_FOUND);
			return true;
		}
	}

	/**
	 * An answer that isn't lyrics and isn't a network problem: shown as "no lyrics", not saved, unless the song has
	 * changed meanwhile. Lyrics already on show (a Retry) stay, as they did when this showed an error.
	 */
	private void setNotFound(long gen) {
		beforePublish.run();
		synchronized (fetchLock) {
			if (generation.get() == gen) {
				current = new Snapshot(current.track, current.lyrics, State.NOT_FOUND);
			}
		}
	}

	/** What the shown lyrics will be for a result: synced first, then plain, then the instrumental flag. */
	static Lyrics parse(LrclibClient.Result result, long lengthMs) {
		if (result.synced() != null) {
			Lyrics parsed = LrcParser.parseSynced(result.synced());
			if (!parsed.isEmpty()) {
				return parsed;
			}
			if (parsed.instrumental()) {
				return Lyrics.INSTRUMENTAL;
			}
		}
		if (result.plain() != null) {
			Lyrics parsed = LrcParser.parsePlain(result.plain(), lengthMs);
			if (!parsed.isEmpty()) {
				return parsed;
			}
		}
		return result.instrumental() ? Lyrics.INSTRUMENTAL : Lyrics.NONE;
	}

	private static boolean found(Lyrics l) {
		return !l.isEmpty() || l.instrumental();
	}

	/**
	 * Whether a saved answer still stands: one that gives lyrics always does; one that gives nothing (including
	 * text that parses to nothing) only until the not-found time is up.
	 */
	static boolean keepCached(Lyrics parsed, long savedAtMs, long nowMs) {
		return found(parsed) || nowMs - savedAtMs < NOT_FOUND_TTL_MS;
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

	static void writeCache(Path file, CacheEntry entry) {
		if (Thread.currentThread().isInterrupted()) {
			return; // this lookup was cancelled by a skip: the write would only fail halfway
		}
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(tmp, GSON.toJson(entry), StandardCharsets.UTF_8);
			dev.beli.skylyrics.util.SafeFile.replace(tmp, file);
		} catch (IOException e) {
			LOG.debug("Couldn't write lyrics cache: {}", e.toString());
		} finally {
			try {
				Files.deleteIfExists(tmp); // gone after a good write; a failed or interrupted one must not leave a half file
			} catch (IOException ignored) {
				// tidyDisk or the next write of this song cleans it
			}
		}
	}

	/** Keeps the folder from growing forever: removes the songs not played for longest. */
	static void tidyDisk(Path dir, int keep) {
		File[] files = dir.toFile().listFiles((d, name) -> CACHE_NAME.matcher(name).matches());
		if (files == null || files.length <= keep) {
			return;
		}
		Arrays.sort(files, Comparator.comparingLong(File::lastModified));
		for (int i = 0; i < files.length - keep; i++) {
			files[i].delete();
		}
	}

	public void shutdown() {
		executor.shutdownNow();
	}

	/** For tests: shuts down and waits for the running task to finish. */
	boolean shutdownAndWait(long timeout, TimeUnit unit) throws InterruptedException {
		executor.shutdownNow();
		return executor.awaitTermination(timeout, unit);
	}
}
