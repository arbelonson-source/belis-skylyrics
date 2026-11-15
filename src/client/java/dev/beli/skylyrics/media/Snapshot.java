package dev.beli.skylyrics.media;

/**
 * One reading from a music player.
 *
 * @param positionMs where the song was when this was read
 * @param readAtNanos {@link System#nanoTime()} at the read
 * @param rate        playback speed (1.0 normal, 1.5 for a 1.5x podcast); song time passes this much faster than the clock
 */
public record Snapshot(Track track, long positionMs, boolean playing, long readAtNanos, double rate) {
	public static final Snapshot NOTHING = new Snapshot(Track.NONE, 0, false, 0);

	public Snapshot(Track track, long positionMs, boolean playing, long readAtNanos) {
		this(track, positionMs, playing, readAtNanos, 1.0);
	}

	public Snapshot {
		if (!(rate > 0) || Double.isInfinite(rate)) {
			rate = 1.0;
		}
	}
}
