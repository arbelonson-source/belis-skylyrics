package dev.beli.skylyrics.render;

/**
 * Notices a seek in the song position for the World layout: it remembers the last position seen and which asked-for
 * seek (the progress-bar click, stamped by the tracker) already rewound the words or was passed by, so each seek
 * rewinds at most once.
 */
final class SeekWatch {
	/** The song time of the last step; MIN_VALUE when there was none. */
	private long time = Long.MIN_VALUE;
	/** The nano time of the last step. */
	private long timeNanos;
	/** The request time of the last seek that already rewound the words (or arrived as a jump forward). */
	private long used;

	/** Forget the last position: the next step never rewinds. */
	void reset() {
		time = Long.MIN_VALUE;
	}

	/**
	 * One frame. {@code seekRequestedNanos} is the tracker's stamp of the last asked seek (0 for none). Returns whether
	 * the words should rewind.
	 */
	boolean step(long positionMs, long seekRequestedNanos, long nowNanos) {
		boolean asked = seekRequestedNanos != used && LyricsRenderer.seekRecent(seekRequestedNanos, nowNanos);
		boolean rewind = LyricsRenderer.jumpedBack(time, positionMs, asked);
		if (rewind) {
			used = seekRequestedNanos;
		} else if (asked && time != Long.MIN_VALUE) {
			// The asked seek arrived as a jump forward. The position moves on by itself between polls, so only what
			// is beyond the time that passed counts: a stalled frame is not the seek, and the drop may still come.
			long elapsedMs = (nowNanos - timeNanos) / 1_000_000L;
			if (positionMs - time - elapsedMs > LyricsRenderer.SEEK_BACK_MS) {
				used = seekRequestedNanos;
			}
		}
		time = positionMs;
		timeNanos = nowNanos;
		return rewind;
	}
}
