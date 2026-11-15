package dev.beli.skylyrics.media;

import org.jspecify.annotations.Nullable;

/** The timing decisions of the Spotify Web poller, kept free of the network so they can be tested. */
final class SpotifyPace {
	static final long RETRY_DEFAULT_S = 10;
	static final long RETRY_MAX_S = 300;

	static final long BACKOFF_FIRST_NS = 4_000_000_000L;
	static final long BACKOFF_MAX_NS = 60_000_000_000L;

	private SpotifyPace() {
	}

	/** 4 s after the first failure in a row, doubling each time, never over 60 s. */
	static long backoffNs(int failures) {
		long ns = BACKOFF_FIRST_NS;
		for (int i = 1; i < failures && ns < BACKOFF_MAX_NS; i++) {
			ns *= 2;
		}
		return Math.min(ns, BACKOFF_MAX_NS);
	}

	/** How long to leave Spotify alone after a failure: a 403 won't fix itself, so it waits the longest right away. */
	static long failureDelayNs(int status, int failures) {
		return status == 403 ? BACKOFF_MAX_NS : backoffNs(failures);
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
