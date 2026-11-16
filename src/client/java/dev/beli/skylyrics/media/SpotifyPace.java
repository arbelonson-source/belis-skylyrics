package dev.beli.skylyrics.media;

import org.jspecify.annotations.Nullable;

/** The timing decisions of the Spotify Web poller, kept free of the network so they can be tested. */
public final class SpotifyPace {
	static final long RETRY_DEFAULT_S = 10;
	static final long RETRY_MAX_S = 300;

	static final long BACKOFF_FIRST_NS = 4_000_000_000L;
	/** Short enough that a song change shows up soon after the network returns. */
	static final long BACKOFF_MAX_NS = 30_000_000_000L;
	/** A 403 won't fix itself. */
	static final long FORBIDDEN_NS = 60_000_000_000L;

	/** The tracker's smooth clock fills the gaps, so once a second while playing is plenty. */
	static final long PLAYING_NS = 1_000_000_000L;
	static final long IDLE_NS = 4_000_000_000L;
	/** While nothing on screen shows the song. */
	static final long QUIET_NS = 15_000_000_000L;

	private SpotifyPace() {
	}

	/**
	 * Whether any visible part needs the song's position: the lyrics (the sky lyrics, the top bar, the Record card and
	 * the title screen lyrics all need Lyrics on), or the settings, library or intro screen, which show what is playing.
	 * Whether the window has focus doesn't count: sky lyrics can be watched from a second monitor.
	 */
	public static boolean looking(boolean lyricsOn, boolean songScreenOpen) {
		return lyricsOn || songScreenOpen;
	}

	static long intervalNs(boolean playing, boolean looking) {
		return !looking ? QUIET_NS : playing ? PLAYING_NS : IDLE_NS;
	}

	/** 4 s after the first failure in a row, doubling each time, never over 30 s. */
	static long backoffNs(int failures) {
		long ns = BACKOFF_FIRST_NS;
		for (int i = 1; i < failures && ns < BACKOFF_MAX_NS; i++) {
			ns *= 2;
		}
		return Math.min(ns, BACKOFF_MAX_NS);
	}

	/** How long to leave Spotify alone after a failure: a 403 won't fix itself, so it waits the longest right away. */
	static long failureDelayNs(int status, int failures) {
		return status == 403 ? FORBIDDEN_NS : backoffNs(failures);
	}

	/** The {@code Retry-After} header as nanoseconds: seconds only (an HTTP date or junk reads as 10 s), kept within 1 to 300 s. */
	static long retryAfterNs(@Nullable String header) {
		long seconds = RETRY_DEFAULT_S;
		if (header != null) {
			try {
				seconds = Long.parseLong(header.strip());
			} catch (NumberFormatException e) {
				seconds = RETRY_DEFAULT_S;
			}
		}
		return Math.max(1, Math.min(seconds, RETRY_MAX_S)) * 1_000_000_000L;
	}
}
