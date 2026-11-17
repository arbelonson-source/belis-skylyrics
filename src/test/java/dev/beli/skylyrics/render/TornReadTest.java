package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.lyrics.LrclibClient;
import dev.beli.skylyrics.lyrics.LyricsService;
import dev.beli.skylyrics.media.MediaSource;
import dev.beli.skylyrics.media.MediaTracker;
import dev.beli.skylyrics.media.Snapshot;
import dev.beli.skylyrics.media.Track;

class TornReadTest {
	private static final Track A = new Track("Song A", "Band", "", 0, "", "firefox");
	private static final Track B = new Track("Song B", "Band", "", 0, "", "firefox");

	/** Alternates between song A near its start and song B far into it, at every poll. */
	private static final class Flipper implements MediaSource {
		final AtomicInteger polls = new AtomicInteger();

		@Override
		public Snapshot poll() {
			boolean a = polls.getAndIncrement() % 2 == 0;
			return new Snapshot(a ? A : B, a ? 1_000 : 500_000, true, System.nanoTime());
		}

		@Override
		public void playPause() {
		}

		@Override
		public void next() {
		}

		@Override
		public void previous() {
		}

		@Override
		public String displayName() {
			return "Flipper";
		}
	}

	@TempDir
	Path cache;

	@Test
	void aSongChangeBetweenTheReadsNeverMixesTheOldSongWithTheNewPosition() throws Exception {
		LyricsService service = new LyricsService(t -> new LrclibClient.Result("[00:01.00]one\n[00:05.00]two", null, false), cache);
		MediaTracker tracker = new MediaTracker(new Flipper(), () -> false);
		try {
			service.load(A);
			long until = System.nanoTime() + 20_000_000_000L;
			while (!service.snapshot().lyrics().synced()) {
				assertTrue(System.nanoTime() < until, "lyrics never loaded");
				Thread.sleep(5);
			}
			LyricsRenderer renderer = new LyricsRenderer(tracker, service, new AlbumArt());
			SkyLyricsConfig cfg = new SkyLyricsConfig();
			AtomicBoolean stop = new AtomicBoolean();
			Thread flipper = new Thread(() -> {
				while (!stop.get()) {
					tracker.refresh();
					Thread.onSpinWait();
				}
			});
			flipper.setDaemon(true);
			flipper.start();
			long worst = 0;
			long end = System.nanoTime() + 3_000_000_000L;
			while (System.nanoTime() < end) {
				worst = Math.max(worst, renderer.takeLyrics(cfg));
			}
			stop.set(true);
			assertTrue(worst < 100_000, "song A's lines were drawn at song B's position: " + worst + " ms");
		} finally {
			tracker.shutdown();
			service.shutdown();
		}
	}
}
