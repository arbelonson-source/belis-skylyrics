package dev.beli.skylyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.media.MediaSource;
import dev.beli.skylyrics.media.MediaTracker;
import dev.beli.skylyrics.media.Snapshot;
import dev.beli.skylyrics.media.Track;

/**
 * The bar shows the song's name for a few seconds when a song starts, once per song. A length, cover or artist that a
 * browser sends a moment after the title is still passed on (the lyrics are asked about again with it, the cover loads),
 * but it is the same song: it must not bring the song's name back over the line being sung.
 */
class SongFlashTest {
	private static final class Source implements MediaSource {
		private final Deque<Track> queue = new ArrayDeque<>();
		private Track last = Track.NONE;
		final AtomicInteger polls = new AtomicInteger();

		synchronized void then(Track t) {
			queue.add(t);
		}

		@Override
		public synchronized Snapshot poll() {
			if (!queue.isEmpty()) {
				last = queue.poll();
			}
			polls.incrementAndGet();
			return new Snapshot(last, 1000, true, System.nanoTime());
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
			return "Source";
		}
	}

	private final List<Track> covers = new CopyOnWriteArrayList<>();
	private final List<Track> heard = new CopyOnWriteArrayList<>();
	private final List<Track> lyrics = new CopyOnWriteArrayList<>();
	private final AtomicInteger flashes = new AtomicInteger();

	/** One poll per step, through the same wiring as the game's. */
	private void run(Track... steps) throws InterruptedException {
		Source source = new Source();
		MediaTracker tracker = new MediaTracker(source, () -> false);
		SkyLyricsClient.listen(tracker, covers::add, heard::add, lyrics::add, flashes::incrementAndGet);
		try {
			for (Track t : steps) {
				source.then(t);
				tracker.refresh();
			}
			tracker.refresh();
			long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
			while (source.polls.get() < steps.length + 1) {
				assertTrue(System.nanoTime() < until, "timed out");
				Thread.sleep(5);
			}
			// the last poll was a no-op, so every earlier poll has finished
		} finally {
			tracker.shutdown();
		}
	}

	private static Track song(String artist, long lengthMs, String art) {
		return new Track("Made Up Song", artist, "", lengthMs, art, "firefox");
	}

	@Test
	void aLateLengthIsLookedUpAgainButFlashesTheSongOnce() throws InterruptedException {
		run(song("Made Up Band", 0, ""), song("Made Up Band", 0, ""), song("Made Up Band", 215_000, ""));
		assertEquals(1, flashes.get(), "the bar showed the song's name again for its late length");
		assertEquals(2, lyrics.size(), lyrics.toString());
		assertEquals(215_000, lyrics.get(1).lengthMs());
		assertEquals(2, heard.size(), "Recently heard learns the length");
	}

	@Test
	void aLateCoverLoadsButFlashesTheSongOnce() throws InterruptedException {
		run(song("Made Up Band", 215_000, ""), song("Made Up Band", 215_000, "file:///tmp/cover.png"));
		assertEquals(1, flashes.get(), "the bar showed the song's name again for its late cover");
		assertEquals(2, covers.size(), covers.toString());
		assertEquals("file:///tmp/cover.png", covers.get(1).artUrl());
	}

	@Test
	void aLateArtistFlashesTheSongOnce() throws InterruptedException {
		// The title alone is held for one read, then announced; the artist comes a read later
		run(song("", 215_000, ""), song("", 215_000, ""), song("Made Up Band", 215_000, ""));
		assertEquals(1, flashes.get(), "the bar showed the song's name again for its late artist");
		assertEquals(2, lyrics.size(), lyrics.toString());
		assertEquals("Made Up Band", lyrics.get(1).artist());
	}

	@Test
	void aNewSongFlashesAgain() throws InterruptedException {
		run(song("Made Up Band", 215_000, ""), new Track("Another Song", "Made Up Band", "", 200_000, "", "firefox"));
		assertEquals(2, flashes.get());
		assertEquals(2, lyrics.size());
	}
}
