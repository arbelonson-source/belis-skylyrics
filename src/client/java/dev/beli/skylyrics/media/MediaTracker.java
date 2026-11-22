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
	private final DemoSource demo = new DemoSource();
	private final BooleanSupplier demoMode;
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "SkyLyrics media");
		t.setDaemon(true);
		return t;
	});

	private volatile Snapshot snapshot = Snapshot.NOTHING;
	private volatile long seekRequestedAt;
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
	private Consumer<Track> onCoverChange = t -> {
	};
	private Consumer<Track> onDetailsChange = t -> {
	};
	/**
	 * The most cover corrections passed on per song. A player's first cover can be the last song's (the Windows helper reads it
	 * again 5 s in), so one or two corrections are real; but some players (a few Linux browsers) write a new temp file on every
	 * update, and each one would reload the cover for the whole song.
	 */
	static final int MAX_COVER_CHANGES = 2;
	private int coverChanges;
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
	/**
	 * This song has read 0 while playing for more than {@link #ZERO_POLLS_TRUSTED} polls: the player never says where it
	 * is, so a 0 says nothing (not even when paused) until the song changes or something else is read.
	 */
	private boolean noPosition;
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

	/** Called when a new song starts (or the music stops: {@link Track#NONE}). */
	public void onTrackChange(Consumer<Track> listener) {
		this.onTrackChange = listener;
	}

	/**
	 * Called when the same song comes with a length, cover or artist that was missing (browsers often send them a moment
	 * after the title): worth looking the lyrics up again with and loading the cover, but not a new song.
	 */
	public void onDetailsChange(Consumer<Track> listener) {
		this.onDetailsChange = listener;
	}

	/**
	 * Called when the same song comes with another, non-empty cover address (a corrected cover): only the cover should reload,
	 * not the lyrics or the song flash. At most {@link #MAX_COVER_CHANGES} per song.
	 */
	public void onCoverChange(Consumer<Track> listener) {
		this.onCoverChange = listener;
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
					|| e instanceof java.nio.channels.UnresolvedAddressException || e instanceof SpotifyWebSource.Unreachable) {
				return true;
			}
			// The HTTP client's own "no connection" error has no text at all
			if (e.getClass() == java.io.IOException.class && e.getMessage() == null) {
				return true;
			}
		}
		return false;
	}

	/** Java's or a server's own words behind a failure, for the log, when the player was shown other words; else nothing. */
	static String rawDetail(Throwable failure, String shown) {
		return failure.getCause() == null && shown.equals(failure.getMessage()) ? "" : " (" + dev.beli.skylyrics.util.LogText.chain(failure) + ")";
	}

	/** Whether the source only isn't set up yet (no Client ID, not signed in): the player has a step to do, nothing failed. */
	static boolean setupPending(@Nullable Throwable e) {
		return e instanceof SpotifyWebSource.NotSetUp;
	}

	/**
	 * The second line of the player bar when nothing is playing: where music can come from. Empty while the reader itself
	 * is failing (the Status line says why), and on a Mac without the browsers it can't read. With Spotify Web only the
	 * Spotify account counts, on any device, so neither another player nor a browser video is offered.
	 */
	public static String idleHint(boolean reading, boolean mac, boolean webPlayer) {
		if (!reading) {
			return "";
		}
		if (webPlayer) {
			return Tr.t("Play a song in Spotify on any device");
		}
		return mac ? Tr.t("Play a song in Spotify or the Music app") : Tr.t("Play a song in Spotify, a browser or any player");
	}

	/**
	 * What the status shows for a failed read: the internet's fault when it is, else the reader's own sentence. Every
	 * reader puts that sentence in an IOException; anything else holds Java's or a library's own English (a bug, D-Bus
	 * not answering), which only the log gets.
	 */
	static String failureText(Throwable e) {
		if (networkFailure(e)) {
			return Tr.t("Can't reach the internet or Spotify");
		}
		String fallback = Tr.t("Couldn't read your music player");
		return e instanceof java.io.IOException ? errorText(e.getMessage(), fallback) : fallback;
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
		if (switchedToDemo(source, lastSource, demo)) {
			demo.restart();
			if (lastSource == player) {
				player.standBy(); // no hidden PowerShell left idling on Windows while the demo plays; the next read starts it
			}
		}
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
			if (setupPending(failure)) {
				failingSince = NOT_FAILING; // the status line says what to do; an error toast would be wrong
			} else if (failingSince == NOT_FAILING) {
				failingSince = System.nanoTime();
			}
			if (!loggedError) {
				if (setupPending(failure)) {
					LOG.debug("The music player isn't set up yet: {}", error); // waiting for the player to do a step, not a problem
				} else {
					LOG.warn("Couldn't read the music player: {}{}", error, rawDetail(failure, error));
				}
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

		if (newSong) {
			coverChanges = 0;
			snapshot = fresh;
			zeroPolls = 0;
			zeroRunBase = null;
			noPosition = false;
			noteZero(fresh);
			sourceName = source.displayName();
			onTrackChange.accept(freshTrack);
			return;
		}
		if (filledIn(old.track(), freshTrack)) {
			// Still the same song, so its clock goes on (a player that reads 0 keeps counting from when the song appeared)
			followSameSong(old, fresh);
			sourceName = source.displayName();
			onDetailsChange.accept(freshTrack);
			return;
		}

		// Same song with a different cover (not an empty one filled in, which is passed on above)
		boolean coverChanged = !old.track().artUrl().isEmpty() && !freshTrack.artUrl().isEmpty()
				&& !old.track().artUrl().equals(freshTrack.artUrl()) && ++coverChanges <= MAX_COVER_CHANGES;
		followSameSong(old, fresh);
		sourceName = source.displayName();
		if (coverChanged) {
			onCoverChange.accept(freshTrack);
		}
	}

	/** Moves the clock on for a new reading of the song it already follows. */
	private void followSameSong(Snapshot old, Snapshot fresh) {
		Track freshTrack = fresh.track();
		// Same song: only accept the new position if it really moved (seek), to avoid jitter
		long predicted = positionAt(old, fresh.readAtNanos());
		boolean jumped = Math.abs(predicted - fresh.positionMs()) > SEEK_THRESHOLD_MS;
		if (fresh.positionMs() != 0) {
			noPosition = false;
		}
		boolean changed = fresh.playing() != old.playing() || fresh.rate() != old.rate();
		if (noPosition) {
			// Its 0 on a pause or a resume is not where the song is: it stops, and later goes on, where the clock got to
			snapshot = changed ? new Snapshot(freshTrack, predicted, fresh.playing(), fresh.readAtNanos(), fresh.rate())
					: new Snapshot(freshTrack, old.positionMs(), old.playing(), old.readAtNanos(), old.rate());
		} else if (jumped || changed) {
			snapshot = fresh;
		} else {
			snapshot = new Snapshot(freshTrack, old.positionMs(), old.playing(), old.readAtNanos(), old.rate());
		}
		// A player that never reports its position says 0 every time, which would hold the lyrics on the first line:
		// after a few such polls let the clock run from where the zeros began (when the song appeared)
		if (noteZero(fresh) > ZERO_POLLS_TRUSTED && zeroRunBase != null) {
			noPosition = true;
			Snapshot base = zeroRunBase;
			snapshot = new Snapshot(freshTrack, base.positionMs(), base.playing(), base.readAtNanos(), base.rate());
		}
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

	/** The song, position and play state as one reading: use it with {@link #positionMs(Snapshot)} when they must belong together. */
	public Snapshot snapshot() {
		return snapshot;
	}

	/** Song position right now of a reading taken with {@link #snapshot()}. */
	public long positionMs(Snapshot reading) {
		return positionAt(reading, System.nanoTime());
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

	/** When the last seek was asked for (System.nanoTime, never 0), or 0 if none: a drop in the position soon after it is that seek. */
	public long seekRequestedNanos() {
		return seekRequestedAt;
	}

	public void seek(long positionMs) {
		long at = System.nanoTime();
		seekRequestedAt = at == 0 ? 1 : at;
		executor.execute(() -> safely(() -> source().seek(positionMs)));
	}

	/** Whether this read is the first from the demo after another source (or none): the demo then starts from the top. */
	static boolean switchedToDemo(MediaSource source, @Nullable MediaSource last, MediaSource demo) {
		return source == demo && last != demo;
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
