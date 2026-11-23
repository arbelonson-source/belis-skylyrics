package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.freedesktop.dbus.types.Variant;
import org.junit.jupiter.api.Test;

/**
 * The followed Linux player blinks between songs, while a browser tab plays too (a muted video, a tab in the
 * background). Some players blink by saying Stopped for a moment, or Paused with no song: that must not hand the
 * lyrics to the browser, which would then keep them once the player's next song starts (the current player is kept
 * while it plays), as a blink with no answer at all doesn't.
 */
class MprisStopBlinkTest {
	private static final String A = "org.mpris.MediaPlayer2.spotify";
	private static final String B = "org.mpris.MediaPlayer2.firefox";

	private static Map<String, Variant<?>> properties(String status, String title) {
		Map<String, Variant<?>> meta = title.isEmpty() ? Map.of() : Map.of(
				"xesam:title", new Variant<>(title),
				"xesam:artist", new Variant<>(new String[] {"Made Up Band"}, "as"),
				"mpris:length", new Variant<>(200_000_000L),
				"mpris:trackid", new Variant<>(new org.freedesktop.dbus.DBusPath("/song/" + title.length())));
		return Map.of("PlaybackStatus", new Variant<>(status), "Position", new Variant<>(5_000_000L),
				"Metadata", new Variant<>(meta, "a{sv}"));
	}

	/** Player A says {@code a} (status and title), the browser B always plays its video. */
	private static MprisSource twoPlayers(AtomicReference<String[]> a) {
		return new MprisSource(() -> "", new MprisSource.Reader() {
			@Override
			public List<String> names() {
				return List.of(A, B);
			}

			@Override
			public MprisSource.Reading read(String busName) {
				String[] said = busName.equals(A) ? a.get() : new String[] {"Playing", "Browser Video"};
				return MprisSource.reading(busName, properties(said[0], said[1]), () -> {
					throw new IllegalStateException("Position is in GetAll");
				});
			}
		});
	}

	private static String pollTitle(MprisSource source) throws Exception {
		Snapshot s = source.poll();
		long giveUp = System.nanoTime() + 20_000_000_000L;
		while (s.track().isNone() && System.nanoTime() < giveUp) {
			Thread.sleep(20);
			s = source.poll();
		}
		return s.track().title();
	}

	private static void blink(String status) throws Exception {
		AtomicReference<String[]> a = new AtomicReference<>(new String[] {"Playing", "First Song"});
		MprisSource source = twoPlayers(a);
		try {
			assertEquals("First Song", pollTitle(source));
			a.set(new String[] {status, ""});
			assertEquals("First Song", source.poll().track().title(), "a blink (" + status + ") handed the lyrics to the browser");
			a.set(new String[] {"Playing", "Second Song"});
			assertEquals("Second Song", pollTitle(source), "the player's next song went to the browser");
		} finally {
			source.close();
		}
	}

	@Test
	void aStoppedBlinkBetweenSongsKeepsThePlayer() throws Exception {
		blink("Stopped");
	}

	@Test
	void aPausedBlinkWithNoSongKeepsThePlayer() throws Exception {
		blink("Paused");
	}
}
