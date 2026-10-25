package dev.beli.skylyrics.media;

/**
 * One reading from a music player.
 *
 * @param positionMs where the song was when this was read
 * @param readAtNanos {@link System#nanoTime()} at the read
 */
public record Snapshot(Track track, long positionMs, boolean playing, long readAtNanos) {
	public static final Snapshot NOTHING = new Snapshot(Track.NONE, 0, false, 0);
}
