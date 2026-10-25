package dev.beli.skylyrics.media;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

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

	/** Browsers often send the length and cover a moment after the title. */
	private static boolean filledIn(Track old, Track fresh) {
		return (old.lengthMs() <= 0 && fresh.lengthMs() > 0) || (old.artUrl().isEmpty() && !fresh.artUrl().isEmpty());
	}

	private void pollOnce() {
		MediaSource source = source();
		Snapshot fresh;
		try {
			fresh = source.poll();
			connected = true;
			error = "";
			loggedError = false;
		} catch (Throwable e) {
			connected = false;
			error = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
			if (!loggedError) {
				LOG.warn("Couldn't read the music player: {}", error);
				loggedError = true;
			}
			fresh = Snapshot.NOTHING;
		}

		Snapshot old = snapshot;
		if (!fresh.track().sameSong(old.track()) || filledIn(old.track(), fresh.track())) {
			snapshot = fresh;
			sourceName = source.displayName();
			onTrackChange.accept(fresh.track());
			return;
		}

		// Same song: only accept the new position if it really moved (seek), to avoid jitter
		long predicted = positionAt(old, fresh.readAtNanos());
		boolean jumped = Math.abs(predicted - fresh.positionMs()) > SEEK_THRESHOLD_MS;
		if (jumped || fresh.playing() != old.playing()) {
			snapshot = fresh;
		} else {
			snapshot = new Snapshot(fresh.track(), old.positionMs(), old.playing(), old.readAtNanos());
		}
		sourceName = source.displayName();
	}

	private static long positionAt(Snapshot s, long nanos) {
		if (!s.playing()) {
			return s.positionMs();
		}
		long pos = s.positionMs() + (nanos - s.readAtNanos()) / 1_000_000;
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
