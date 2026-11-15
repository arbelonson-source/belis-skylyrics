package dev.beli.skylyrics.media;

import org.jspecify.annotations.Nullable;

/** The timing decisions of the Spotify Web poller, kept free of the network so they can be tested. */
final class SpotifyPace {
	static final long RETRY_DEFAULT_S = 10;
	static final long RETRY_MAX_S = 300;

	private SpotifyPace() {
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
