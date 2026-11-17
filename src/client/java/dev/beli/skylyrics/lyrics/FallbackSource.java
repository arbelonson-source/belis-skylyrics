package dev.beli.skylyrics.lyrics;

import java.io.IOException;
import java.util.function.LongSupplier;

import dev.beli.skylyrics.media.Track;

/**
 * Asks the main source first, and the backup when the main one is down or has nothing.
 * When the main source is down and the backup has nothing, the main error is thrown,
 * so the song is retried later instead of being remembered as having no lyrics.
 * After the main source fails as down (not for one odd song), it's skipped for a while so each song doesn't wait for it to time out.
 */
public final class FallbackSource implements LyricsSource {
	/** How long to skip the main source after it fails. */
	static final long SKIP_MAIN_MS = 2 * 60_000;

	private final LyricsSource main;
	private final LyricsSource backup;
	private final LongSupplier clockMs;
	/** Only meaningful once {@link #skipping} is set: the monotonic clock can read any value, including negative ones. */
	private volatile long skipMainUntil;
	private volatile boolean skipping;

	public FallbackSource(LyricsSource main, LyricsSource backup) {
		this(main, backup, () -> System.nanoTime() / 1_000_000); // monotonic, so a changed PC clock can't extend the skip
	}

	FallbackSource(LyricsSource main, LyricsSource backup, LongSupplier clockMs) {
		this.main = main;
		this.backup = backup;
		this.clockMs = clockMs;
	}

	@Override
	public LrclibClient.Result find(Track track) throws IOException, InterruptedException {
		if (skipping && clockMs.getAsLong() - skipMainUntil < 0) {
			return whileSkipping(track);
		}
		LrclibClient.Result result;
		try {
			result = main.find(track);
		} catch (IOException | RuntimeException mainDown) {
			cancelledOrThrow(mainDown); // a skipped song's interrupt isn't the site being down
			if (saysSiteIsDown(mainDown)) {
				startSkip();
			}
			return fromBackup(track, mainDown instanceof IOException io ? io : new IOException(mainDown));
		}
		return withBackup(track, result);
	}

	private void startSkip() {
		skipMainUntil = clockMs.getAsLong() + SKIP_MAIN_MS;
		skipping = true;
	}

	/** Main lyrics site is being skipped: use the backup, and ask main once anyway for a song the backup lacks. */
	private LrclibClient.Result whileSkipping(Track track) throws IOException, InterruptedException {
		IOException skipped = new IOException("Main lyrics source is down, trying again soon");
		boolean backupFailed = false;
		try {
			LrclibClient.Result result = backup.find(track);
			if (result.found()) {
				return result.asStopgap();
			}
		} catch (IOException | RuntimeException backupDown) {
			cancelledOrThrow(backupDown);
			skipped.addSuppressed(backupDown);
			backupFailed = true;
		}
		LrclibClient.Result result;
		try {
			result = main.find(track);
		} catch (IOException | RuntimeException mainDown) {
			cancelledOrThrow(mainDown);
			if (saysSiteIsDown(mainDown)) {
				startSkip(); // still down: keep skipping
				throw skipped;
			}
			throw mainDown instanceof IOException io ? io : new IOException(mainDown);
		}
		skipping = false; // main answered, so it is up again
		return backupFailed && !result.found() ? result.asStopgap() : result;
	}

	/**
	 * Main had nothing, or only plain text: the backup may do better. Its answer replaces a plain-only one only
	 * when it is synced; a backup that fails never loses the main answer.
	 */
	private LrclibClient.Result withBackup(Track track, LrclibClient.Result result) throws InterruptedException {
		boolean plainOnly = result.found() && !result.hasSynced() && !result.instrumental();
		if (result.found() && !plainOnly) {
			return result;
		}
		try {
			LrclibClient.Result other = backup.find(track);
			if (plainOnly) {
				return other.hasSynced() ? other.asFromBackup() : result;
			}
			return other.found() ? other.asFromBackup() : other;
		} catch (IOException | RuntimeException backupDown) {
			cancelledOrThrow(backupDown);
			// The backup didn't answer, so this isn't a real "no lyrics": show nothing now, but don't keep it
			return plainOnly ? result : result.asStopgap();
		}
	}

	/**
	 * Whether a failure says the site is down or overloaded (no connection, timeout, 5xx, 429, 408), as opposed to
	 * a refusal of one odd song (a 400, say), which must not switch the site off for every other song.
	 */
	static boolean saysSiteIsDown(Exception failure) {
		if (failure instanceof LrclibClient.StatusException s) {
			return s.status() >= 500 || s.status() == 429 || s.status() == 408;
		}
		return failure instanceof IOException;
	}

	/** The backup's lyrics, or the main error when the backup has none, so the song is retried. */
	private LrclibClient.Result fromBackup(Track track, IOException mainDown) throws IOException, InterruptedException {
		try {
			LrclibClient.Result result = backup.find(track);
			if (result.found()) {
				return result.asStopgap();
			}
		} catch (IOException | RuntimeException backupDown) {
			cancelledOrThrow(backupDown);
			mainDown.addSuppressed(backupDown);
		}
		throw mainDown;
	}

	/**
	 * A lookup cancelled by a song skip is interrupted, and the JDK may turn that into an IOException while the
	 * interrupt flag is set again. That says nothing about the site, so it must not switch the main source off:
	 * the lookup just ends. Timeouts are real signals and don't count (SocketTimeoutException is an InterruptedIOException).
	 */
	private static void cancelledOrThrow(Exception failure) throws InterruptedException {
		boolean cancelled = Thread.currentThread().isInterrupted();
		for (Throwable t = failure; t != null && !cancelled; t = t.getCause() == t ? null : t.getCause()) {
			cancelled = t instanceof InterruptedException || t instanceof java.nio.channels.ClosedByInterruptException;
		}
		if (cancelled) {
			Thread.currentThread().interrupt();
			throw new InterruptedException("Lyrics lookup cancelled");
		}
	}
}
