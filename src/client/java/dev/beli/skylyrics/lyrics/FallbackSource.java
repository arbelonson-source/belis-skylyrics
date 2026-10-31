package dev.beli.skylyrics.lyrics;

import java.io.IOException;
import java.util.function.LongSupplier;

import dev.beli.skylyrics.media.Track;

/**
 * Asks the main source first, and the backup when the main one is down or has nothing.
 * When the main source is down and the backup has nothing, the main error is thrown,
 * so the song is retried later instead of being remembered as having no lyrics.
 * After the main source fails, it's skipped for a while so each song doesn't wait for it to time out.
 */
public final class FallbackSource implements LyricsSource {
	/** How long to skip the main source after it fails. */
	static final long SKIP_MAIN_MS = 2 * 60_000;

	private final LyricsSource main;
	private final LyricsSource backup;
	private final LongSupplier clockMs;
	private volatile long skipMainUntil;

	public FallbackSource(LyricsSource main, LyricsSource backup) {
		this(main, backup, System::currentTimeMillis);
	}

	FallbackSource(LyricsSource main, LyricsSource backup, LongSupplier clockMs) {
		this.main = main;
		this.backup = backup;
		this.clockMs = clockMs;
	}

	@Override
	public LrclibClient.Result find(Track track) throws IOException, InterruptedException {
		if (clockMs.getAsLong() < skipMainUntil) {
			return fromBackup(track, new IOException("Main lyrics source is down, trying again soon"));
		}
		LrclibClient.Result result;
		try {
			result = main.find(track);
		} catch (IOException | RuntimeException mainDown) {
			skipMainUntil = clockMs.getAsLong() + SKIP_MAIN_MS;
			return fromBackup(track, mainDown instanceof IOException io ? io : new IOException(mainDown));
		}
		if (result.found()) {
			return result;
		}
		try {
			return backup.find(track);
		} catch (IOException | RuntimeException backupDown) {
			return result;
		}
	}

	/** The backup's lyrics, or the main error when the backup has none, so the song is retried. */
	private LrclibClient.Result fromBackup(Track track, IOException mainDown) throws IOException, InterruptedException {
		try {
			LrclibClient.Result result = backup.find(track);
			if (result.found()) {
				return result;
			}
		} catch (IOException | RuntimeException backupDown) {
			mainDown.addSuppressed(backupDown);
		}
		throw mainDown;
	}
}
