package dev.beli.skylyrics.lyrics;

import java.io.IOException;

import dev.beli.skylyrics.media.Track;

/** A place to look lyrics up. Blocking; call from a background thread. */
public interface LyricsSource {
	/** The lyrics for a track, or {@link LrclibClient.Result#NOT_FOUND}. Throws when the site can't be reached. */
	LrclibClient.Result find(Track track) throws IOException, InterruptedException;
}
