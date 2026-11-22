package dev.beli.skylyrics.media;

import java.util.function.Supplier;

/**
 * The computer's own media controls, or the Spotify account when "Spotify Web" is picked in the Music tab.
 * The choice is read on every call, so switching takes effect on the next poll without a restart.
 */
public final class ChosenSource implements MediaSource {
	private final MediaSource local;
	private final SpotifyWebSource web;
	private final Supplier<String> preferredPlayer;

	public ChosenSource(MediaSource local, SpotifyWebSource web, Supplier<String> preferredPlayer) {
		this.local = local;
		this.web = web;
		this.preferredPlayer = preferredPlayer;
	}

	/** The source the last poll asked, so its failure is read from the same one even if the setting changed meanwhile. */
	private volatile @org.jspecify.annotations.Nullable MediaSource polled;

	private MediaSource current() {
		return SpotifyWebSource.KEY.equals(preferredPlayer.get()) ? web : local;
	}

	@Override
	public Snapshot poll() throws Exception {
		MediaSource source = current(); // the setting is read once for this poll
		MediaSource last = polled;
		polled = source;
		if (last == local && source != local) {
			local.standBy(); // no hidden PowerShell left idling for the rest of the game on Windows
		}
		return source.poll();
	}

	@Override
	public void playPause() {
		current().playPause();
	}

	@Override
	public void next() {
		current().next();
	}

	@Override
	public void previous() {
		current().previous();
	}

	@Override
	public void seek(long positionMs) {
		current().seek(positionMs);
	}

	/** The demo song is played instead: the source in use stops what it keeps running (the other one already has). */
	@Override
	public void standBy() {
		MediaSource source = polled;
		if (source != null) {
			source.standBy();
		}
	}

	/** The chosen source's own failing streak, so its quiet retries count as the same outage (see {@link MediaTracker}). */
	@Override
	public @org.jspecify.annotations.Nullable Throwable failing() {
		MediaSource source = polled;
		return (source != null ? source : current()).failing();
	}

	@Override
	public String displayName() {
		return current().displayName();
	}

	@Override
	public void close() {
		try {
			local.close();
		} finally {
			web.close();
		}
	}
}
