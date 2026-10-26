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

	private MediaSource current() {
		return SpotifyWebSource.KEY.equals(preferredPlayer.get()) ? web : local;
	}

	@Override
	public Snapshot poll() throws Exception {
		return current().poll();
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
