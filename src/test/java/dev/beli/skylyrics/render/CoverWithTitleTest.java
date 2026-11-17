package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.SkyLyricsClient;
import dev.beli.skylyrics.media.MediaSource;
import dev.beli.skylyrics.media.MediaTracker;
import dev.beli.skylyrics.media.Snapshot;
import dev.beli.skylyrics.media.Track;

/** On a song change the new title must never be on show next to the last song's cover. */
class CoverWithTitleTest {
	private static final Track OLD = new Track("Old Song", "Band", "", 200_000, "file:///old.png", "x");
	private static final Track NEW = new Track("New Song", "Band", "", 200_000, "file:///new.png", "x");

	private static final class Source implements MediaSource {
		volatile Track playing = OLD;

		@Override
		public Snapshot poll() {
			return new Snapshot(playing, 1000, true, System.nanoTime());
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

	@Test
	void theTitleAndTheCoverAgreeInEveryStepOfASongChange() throws Exception {
		AlbumArt art = new AlbumArt(Executors.newSingleThreadExecutor());
		art.fetcher = url -> null;
		art.load(OLD);
		art.markUploaded(1); // the old cover is on the GPU
		assertTrue(art.hasTexture());

		Source source = new Source();
		MediaTracker tracker = new MediaTracker(source, () -> false);
		List<String> disagreed = new CopyOnWriteArrayList<>();
		CountDownLatch changed = new CountDownLatch(1);
		AtomicReference<Throwable> failure = new AtomicReference<>();
		tracker.onTrackChange(track -> {
			try {
				// Each step of the change, as a frame on the render thread would see it
				Runnable frame = () -> {
					if (tracker.track().title().equals("New Song") && art.hasTexture()) {
						disagreed.add("new title with the old cover");
					}
				};
				SkyLyricsClient.trackChanged(track, () -> art.load(track), frame, frame, frame);
				frame.run();
			} catch (Throwable e) {
				failure.set(e);
			} finally {
				changed.countDown();
			}
		});
		try {
			tracker.refresh();
			long until = System.nanoTime() + 20_000_000_000L;
			while (tracker.track().isNone() && System.nanoTime() < until) {
				Thread.sleep(5);
			}
			source.playing = NEW;
			tracker.refresh();
			tracker.refresh();
			// the first change (OLD appearing) also ran the listener; wait for the NEW one
			until = System.nanoTime() + 20_000_000_000L;
			while (!tracker.track().title().equals("New Song") && System.nanoTime() < until) {
				Thread.sleep(5);
			}
			assertTrue(changed.await(20, TimeUnit.SECONDS));
			Thread.sleep(300);
		} finally {
			tracker.shutdown();
		}
		assertEquals(null, failure.get());
		assertEquals(List.of(), disagreed);
		assertFalse(art.hasTexture());
	}
}
