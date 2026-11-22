package dev.beli.skylyrics.media;

import dev.beli.skylyrics.Tr;

/**
 * A built-in fake song with original lyrics, so the mod can be tried (and filmed) without music
 * and without showing anyone's copyrighted lyrics.
 */
public final class DemoSource implements MediaSource {
	public static final String PLAYER = "demo";
	public static final Track TRACK = new Track("Neon Skyline", "SkyLyrics Demo", "Built-in demo", 78_000, "", PLAYER);

	/** Written for this mod. */
	public static final String LYRICS = """
			[00:02.00]Lights come on above the trees
			[00:06.00]Every block is glowing softly
			[00:10.00]Hold the beat and follow me
			[00:14.00]Up where the clouds are slowly drifting
			[00:18.50]
			[00:20.00]Neon skyline, can you hear it
			[00:24.00]Every word is written in the stars
			[00:28.00]Neon skyline, keep it spinning
			[00:32.00]Sing it back to me from where you are
			[00:36.50]
			[00:39.00]Torches flicker down below
			[00:43.00]Rivers carry all the echoes
			[00:47.00]Build it up and let it go
			[00:51.00]Paint the night in purple shadows
			[00:55.50]
			[00:57.00]Neon skyline, can you hear it
			[01:01.00]Every word is written in the stars
			[01:05.00]Neon skyline, keep it spinning
			[01:09.00]Sing it back to me from where you are
			[01:14.00]
			""";

	/** The song the demo plays: {@link #TRACK}, or the title and artist a test check gave it (see {@link #renameForTest}). */
	private static volatile Track playing = TRACK;

	/**
	 * Test checks only (the test hook's {@code song} command): the demo song is reported with this title and artist, so
	 * a check can show how the bar and the Record card draw odd song details. It keeps the demo's lyrics, player and the
	 * length it reports now (see {@link #lengthForTest}); both blank goes back to {@link #TRACK}. Passed through
	 * {@link Track}'s own cleaning, like a player's.
	 */
	public static void renameForTest(String title, String artist) {
		playing = title.isBlank() && artist.isBlank() ? TRACK
				: new Track(title, artist, TRACK.album(), playing.lengthMs(), TRACK.artUrl(), PLAYER);
	}

	/**
	 * Test checks only (the test hook's {@code songlength} command): the demo song is reported with this length, 0 for
	 * none, so a check can show a song whose length comes in a moment after its name, as a browser's does. It keeps the
	 * title, artist, lyrics and player, and the demo still loops after {@link #TRACK}'s length.
	 */
	public static void lengthForTest(long lengthMs) {
		Track p = playing;
		playing = new Track(p.title(), p.artist(), p.album(), Math.max(0, lengthMs), p.artUrl(), PLAYER);
	}

	private long startNanos = System.nanoTime();
	private long pausedAtMs = -1;

	private long positionMs() {
		long pos = pausedAtMs >= 0 ? pausedAtMs : (System.nanoTime() - startNanos) / 1_000_000;
		return pos % TRACK.lengthMs();
	}

	/** From the top and playing: the demo starts at 0:00 each time it's switched on, not wherever its clock got to. */
	public synchronized void restart() {
		startNanos = System.nanoTime();
		pausedAtMs = -1;
	}

	@Override
	public synchronized Snapshot poll() {
		return new Snapshot(playing, positionMs(), pausedAtMs < 0, System.nanoTime());
	}

	@Override
	public synchronized void playPause() {
		if (pausedAtMs >= 0) {
			startNanos = System.nanoTime() - pausedAtMs * 1_000_000;
			pausedAtMs = -1;
		} else {
			pausedAtMs = positionMs();
		}
	}

	@Override
	public synchronized void next() {
		seek(0);
	}

	@Override
	public synchronized void previous() {
		seek(0);
	}

	@Override
	public synchronized void seek(long positionMs) {
		if (pausedAtMs >= 0) {
			pausedAtMs = positionMs;
		} else {
			startNanos = System.nanoTime() - positionMs * 1_000_000;
		}
	}

	@Override
	public String displayName() {
		return Tr.t("Demo");
	}
}
