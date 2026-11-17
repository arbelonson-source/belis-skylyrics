package dev.beli.skylyrics.render;

import java.io.IOException;

/** When a cover that failed to download is tried again: only for failures that may pass, and with a growing wait. */
final class CoverRetry {
	private static final long[] DELAYS_MS = {5_000, 15_000, 30_000, 60_000};

	/** How many retries one cover gets before it is given up for the song. */
	static final int MAX_RETRIES = 6;

	private CoverRetry() {
	}

	/** An answer that says "not now" (server trouble, too many requests, timeout), not "never". */
	static final class TemporaryFailure extends IOException {
		TemporaryFailure(String message, Throwable cause) {
			super(message, cause);
		}
	}

	/** How long to wait before try number {@code attempt} (1 for the first retry); every 60 s after the fourth. */
	static long delayMs(int attempt) {
		return DELAYS_MS[Math.min(Math.max(attempt, 1), DELAYS_MS.length) - 1];
	}

	/** An HTTP status that may pass: 5xx, 429, 408. A 404 or other 4xx never will. */
	static boolean temporaryStatus(int status) {
		return status >= 500 || status == 429 || status == 408;
	}

	/** A failure that says the network or the server is the problem right now, not the cover. */
	static boolean temporary(Throwable failure) {
		for (Throwable t = failure; t != null; t = t.getCause() == t ? null : t.getCause()) {
			if (t instanceof javax.imageio.IIOException || t instanceof java.security.cert.CertificateException) {
				return false; // a picture that won't decode (even if cut short), or a certificate that won't pass: never will
			}
		}
		for (Throwable t = failure; t != null; t = t.getCause() == t ? null : t.getCause()) {
			if (t instanceof TemporaryFailure
					|| t instanceof java.net.ConnectException
					|| t instanceof java.net.UnknownHostException
					|| t instanceof java.net.SocketException
					|| t instanceof java.net.SocketTimeoutException
					|| t instanceof java.net.http.HttpTimeoutException
					|| t instanceof javax.net.ssl.SSLException
					|| t instanceof java.io.EOFException) {
				return true;
			}
		}
		return false;
	}
}
