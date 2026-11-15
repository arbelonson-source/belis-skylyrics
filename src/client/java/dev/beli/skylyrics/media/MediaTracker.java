package dev.beli.skylyrics.media;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import dev.beli.skylyrics.Tr;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Polls the music player in the background and keeps a smooth song clock for rendering.
 * Everything public is safe to call from the render thread.
 */
public final class MediaTracker {
	private static final Logger LOG = LoggerFactory.getLogger("SkyLyrics");
	/** Small differences between our clock and the player's are jitter, not a seek. */
	private static final long SEEK_THRESHOLD_MS = 350;

	private final MediaSource player;
	private final MediaSource demo = new DemoSource();
	private final BooleanSupplier demoMode;
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "SkyLyrics media");
		t.setDaemon(true);
		return t;
	});

	private volatile Snapshot snapshot = Snapshot.NOTHING;
	private volatile String sourceName = "";
	private volatile boolean connected;
	private volatile String error = "";
	private Consumer<Track> onTrackChange = t -> {
	};
	private boolean loggedError;
	private boolean loggedFault;
	/** How many empty/failed reads in a row still keep the last song (about 1.5 s at the usual poll rate). */
	private static final int EMPTY_POLLS_HELD = 3;
	/** How many polls a new song with no artist yet waits for one (the second poll announces it anyway). */
	private static final int ARTISTLESS_POLLS_HELD = 1;
	private int emptyPolls;
	private @Nullable MediaSource lastSource;
	private @Nullable Track pending;
	private int pendingCount;

	public MediaTracker(MediaSource player, BooleanSupplier demoMode) {
		this.player = player;
		this.demoMode = demoMode;
	}

	public void start(long pollMs) {
		executor.scheduleWithFixedDelay(this::pollOnce, 0, pollMs, TimeUnit.MILLISECONDS);
	}

	public void onTrackChange(Consumer<Track> listener) {
		this.onTrackChange = listener;
	}

	private MediaSource source() {
		return demoMode.getAsBoolean() ? demo : player;
	}

	/**
	 * Browsers often send the length, cover, artist or album a moment after the title: the same song (same title and
	 * player) with something that was empty now filled in, and nothing else different.
	 */
	private static boolean lateFill(Track old, Track fresh) {
		if (old.isNone() || !old.title().equals(fresh.title()) || !old.player().equals(fresh.player()) || old.otherUri(fresh)) {
			return false;
		}
		boolean artistOk = old.artist().equals(fresh.artist()) || (old.artist().isEmpty() && !fresh.artist().isEmpty());
		boolean albumOk = old.album().equals(fresh.album()) || (old.album().isEmpty() && !fresh.album().isEmpty());
		return artistOk && albumOk;
	}

	/** What a player sees for a failed read: the message alone, never the Java class name. */
	static String errorText(@Nullable String message, String fallback) {
		return message == null || message.isBlank() ? fallback : message.strip();
	}

	/** A late length, cover or artist is worth passing on (lyrics lookup, cover); a late album alone is not. */
	private static boolean filledIn(Track old, Track fresh) {
		return lateFill(old, fresh) && ((old.lengthMs() <= 0 && fresh.lengthMs() > 0)
				|| (old.artUrl().isEmpty() && !fresh.artUrl().isEmpty())
				|| (old.artist().isEmpty() && !fresh.artist().isEmpty()));
	}

	/** Never lets an exception out: an escaped one would silently cancel every later scheduled poll. */
	private void pollOnce() {
		try {
			pollInner();
			loggedFault = false;
		} catch (Throwable e) {
			if (!loggedFault) {
				LOG.warn("Media poll failed, carrying on", e);
				loggedFault = true; // logged again only after a poll that went through
			}
		}
	}

	private void pollInner() {
		MediaSource source = source();
		Snapshot fresh;
		try {
			fresh = source.poll();
			connected = true;
			error = "";
			loggedError = false;
		} catch (Throwable e) {
			connected = false;
			error = errorText(e.getMessage(), Tr.t("Couldn't read your music player"));
			if (!loggedError) {
				LOG.warn("Couldn't read the music player: {}", error);
				loggedError = true;
			}
			fresh = Snapshot.NOTHING;
		}

		Snapshot old = snapshot;
		boolean sameSource = source == lastSource;
		lastSource = source;

		// A read that comes back empty or fails is usually a hiccup (busy player, track switching): keep the song
		// through a few of them, and only say "nothing playing" if it keeps being empty.
		if (sameSource && fresh.track().isNone() && !old.track().isNone()) {
			if (++emptyPolls <= EMPTY_POLLS_HELD) {
				sourceName = source.displayName();
				return;
			}
		} else {
			emptyPolls = 0;
		}

		Track freshTrack = fresh.track();
		boolean newSong = !freshTrack.sameSong(old.track()) && !lateFill(old.track(), freshTrack);
		// A title can arrive before its artist: wait a poll or two for the artist before announcing a new song
		if (newSong && !freshTrack.isNone() && freshTrack.artist().isEmpty()) {
			if (pending != null && freshTrack.sameSong(pending)) {
				pendingCount++;
			} else {
				pending = freshTrack;
				pendingCount = 1;
			}
			if (pendingCount <= ARTISTLESS_POLLS_HELD) {
				sourceName = source.displayName();
				return;
			}
		}
		pending = null;
		pendingCount = 0;

		if (newSong || filledIn(old.track(), freshTrack)) {
			snapshot = fresh;
			sourceName = source.displayName();
			onTrackChange.accept(freshTrack);
			return;
		}

		// Same song: only accept the new position if it really moved (seek), to avoid jitter
		long predicted = positionAt(old, fresh.readAtNanos());
		boolean jumped = Math.abs(predicted - fresh.positionMs()) > SEEK_THRESHOLD_MS;
		if (jumped || fresh.playing() != old.playing() || fresh.rate() != old.rate()) {
			snapshot = fresh;
		} else {
			snapshot = new Snapshot(freshTrack, old.positionMs(), old.playing(), old.readAtNanos(), old.rate());
		}
		sourceName = source.displayName();
	}

	private static long positionAt(Snapshot s, long nanos) {
		if (!s.playing()) {
			return s.positionMs();
		}
		long pos = s.positionMs() + (long) ((nanos - s.readAtNanos()) / 1_000_000.0 * s.rate());
		long length = s.track().lengthMs();
		return length > 0 ? Math.min(pos, length) : pos;
	}

	/** Song position right now, in milliseconds. */
	public long positionMs() {
		return positionAt(snapshot, System.nanoTime());
	}

	public Track track() {
		return snapshot.track();
	}

	public boolean playing() {
		return snapshot.playing();
	}

	public boolean connected() {
		return connected;
	}

	public String error() {
		return error;
	}

	public String sourceName() {
		return sourceName;
	}

	public void playPause() {
		executor.execute(() -> safely(source()::playPause));
	}

	public void next() {
		executor.execute(() -> safely(source()::next));
	}

	public void previous() {
		executor.execute(() -> safely(source()::previous));
	}

	public void seek(long positionMs) {
		executor.execute(() -> safely(() -> source().seek(positionMs)));
	}

	/** Re-read now (e.g. right after switching demo mode). */
	public void refresh() {
		executor.execute(this::pollOnce);
	}

	public void reconnect() {
		executor.execute(() -> {
			player.close();
			pollOnce();
		});
	}

	private static void safely(Runnable r) {
		try {
			r.run();
		} catch (Throwable e) {
			LOG.debug("Player control failed", e);
		}
	}

	public void shutdown() {
		executor.shutdownNow();
		player.close();
	}
}
