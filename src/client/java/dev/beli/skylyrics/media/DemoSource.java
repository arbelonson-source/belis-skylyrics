package dev.beli.skylyrics.media;

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

	private long startNanos = System.nanoTime();
	private long pausedAtMs = -1;

	private long positionMs() {
		long pos = pausedAtMs >= 0 ? pausedAtMs : (System.nanoTime() - startNanos) / 1_000_000;
		return pos % TRACK.lengthMs();
	}

	@Override
	public synchronized Snapshot poll() {
		return new Snapshot(TRACK, positionMs(), pausedAtMs < 0, System.nanoTime());
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
		return "Demo";
	}
}
