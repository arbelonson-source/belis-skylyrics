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
	/** When the reads started failing (System.nanoTime), or NOT_FAILING while they work. */
	private volatile long failingSince = NOT_FAILING;
	private volatile boolean networkDown;
	private static final long NOT_FAILING = Long.MIN_VALUE;
	/** How long reads must fail without a break before the player is told (once). */
	static final long TOAST_AFTER_NS = 10_000_000_000L;
	private Consumer<Track> onTrackChange = t -> {
	};
	private boolean loggedError;
	private boolean loggedFault;
	/** How many outages were logged and how many ended; one each per outage, whatever its length. */
	volatile int outageWarnings;
	volatile int recoveries;
	/** How many empty/failed reads in a row still keep the last song (about 1.5 s at the usual poll rate). */
	private static final int EMPTY_POLLS_HELD = 3;
	/** How many polls a new song with no artist yet waits for one (the second poll announces it anyway). */
	private static final int ARTISTLESS_POLLS_HELD = 1;
	/** A song that says it is playing but reports position 0 this many polls in a row has no position to trust. */
	private static final int ZERO_POLLS_TRUSTED = 3;
	private int emptyPolls;
	private int zeroPolls;
	private @Nullable Snapshot zeroRunBase;
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

	/** Whether the read failed because the network is down or slow, not because of the player. */
	static boolean networkFailure(@Nullable Throwable e) {
		for (int depth = 0; e != null && depth < 8; depth++, e = e.getCause()) {
			if (e instanceof java.net.UnknownHostException || e instanceof java.net.ConnectException
					|| e instanceof java.net.NoRouteToHostException || e instanceof java.net.SocketTimeoutException
					|| e instanceof java.net.http.HttpTimeoutException
					|| e instanceof java.nio.channels.UnresolvedAddressException) {
				return true;
			}
			// The HTTP client's own "no connection" error has no text at all
			if (e.getClass() == java.io.IOException.class && e.getMessage() == null) {
				return true;
			}
		}
		return false;
	}

	/** What the status shows for a failed read: the internet's fault when it is, else the message. */
	static String failureText(Throwable e) {
		return networkFailure(e) ? Tr.t("Can't reach the internet or Spotify")
				: errorText(e.getMessage(), Tr.t("Couldn't read your music player"));
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
		Throwable failure;
		try {
			fresh = source.poll();
			// A source that is backing off returns its last answer between retries: still the same outage
			failure = source.failing();
		} catch (Throwable e) {
			failure = e;
			fresh = Snapshot.NOTHING;
		}
		if (failure != null) {
			connected = false;
			error = failureText(failure);
			networkDown = networkFailure(failure);
			if (failingSince == NOT_FAILING) {
				failingSince = System.nanoTime();
			}
			if (!loggedError) {
				LOG.warn("Couldn't read the music player: {}", error);
				loggedError = true;
				outageWarnings++;
			}
		} else {
			connected = true;
			error = "";
			failingSince = NOT_FAILING;
			networkDown = false;
			if (loggedError) {
				LOG.info("The music player can be read again");
				loggedError = false;
				recoveries++;
			}
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
		if (fresh.track().isNone()) {
			zeroPolls = 0;
			zeroRunBase = null;
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
			zeroPolls = 0;
			zeroRunBase = null;
			noteZero(fresh);
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
		// A player that never reports its position says 0 every time, which would hold the lyrics on the first line:
		// after a few such polls let the clock run from where the zeros began (when the song appeared)
		if (noteZero(fresh) > ZERO_POLLS_TRUSTED && zeroRunBase != null) {
			Snapshot base = zeroRunBase;
			snapshot = new Snapshot(freshTrack, base.positionMs(), base.playing(), base.readAtNanos(), base.rate());
		}
		sourceName = source.displayName();
	}

	/**
	 * Counts polls in a row that read a playing song at exactly 0; the clock in effect at the first of them is where
	 * the zeros began. Call after {@code snapshot} is set for this poll.
	 */
	private int noteZero(Snapshot reading) {
		if (reading.playing() && reading.positionMs() == 0) {
			if (++zeroPolls == 1) {
				zeroRunBase = snapshot;
			}
		} else {
			zeroPolls = 0;
			zeroRunBase = null;
		}
		return zeroPolls;
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

	/** Whether to tell the player once that the music player can't be read: reads have failed for a while, no toast yet. */
	static boolean toastDue(long failingForNs, boolean alreadyShown) {
		return !alreadyShown && failingForNs >= TOAST_AFTER_NS;
	}

	/** True once, when reads have been failing for about 10 seconds straight (never for "nothing playing"). */
	public boolean takeReadFailure() {
		long since = failingSince;
		if (since == NOT_FAILING || !toastDue(System.nanoTime() - since, readFailureShown)) {
			return false;
		}
		readFailureShown = true;
		return true;
	}

	/** Whether the failing reads are the internet's fault, not the player's. */
	public boolean networkDown() {
		return networkDown;
	}

	private volatile boolean readFailureShown;

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
