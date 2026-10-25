package dev.beli.skylyrics.media;

/** Somewhere we can read "what's playing" from. Methods are called from a background thread. */
public interface MediaSource extends AutoCloseable {
	/** Current state, or {@link Snapshot#NOTHING} when nothing is playing. May block briefly. */
	Snapshot poll() throws Exception;

	void playPause();

	void next();

	void previous();

	/** Seek to an absolute position. Not every player supports this. */
	default void seek(long positionMs) {
	}

	/** Shown in the settings, e.g. "Spotify". */
	String displayName();

	@Override
	default void close() {
	}
}
