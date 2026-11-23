package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.freedesktop.dbus.types.Variant;
import org.junit.jupiter.api.Test;

/**
 * Stop in a Linux player: the reader keeps the song for a couple of polls (a player can blink between songs), and the
 * tracker holds it a few more, about 2.5 s in all. Kept as playing, the lyrics ran on through all of it; a player that
 * says Stopped is not playing, so what is kept stands still where the song stopped.
 */
class MprisStopTest {
	private static final String BUS = "org.mpris.MediaPlayer2.mpv";

	private static Map<String, Variant<?>> properties(String status) {
		Map<String, Variant<?>> meta = Map.of(
				"xesam:title", new Variant<>("A Made-Up Song"),
				"xesam:artist", new Variant<>(new String[] {"Made Up Band"}, "as"),
				"mpris:length", new Variant<>(200_000_000L),
				"mpris:trackid", new Variant<>(new org.freedesktop.dbus.DBusPath("/song/1")));
		return Map.of("PlaybackStatus", new Variant<>(status), "Position", new Variant<>(60_000_000L),
				"Metadata", new Variant<>(meta, "a{sv}"));
	}

	private static MprisSource source(AtomicReference<String> status) {
		return new MprisSource(() -> "", new MprisSource.Reader() {
			@Override
			public List<String> names() {
				return List.of(BUS);
			}

			@Override
			public MprisSource.Reading read(String busName) {
				return MprisSource.reading(busName, properties(status.get()), () -> {
					throw new IllegalStateException("Position is in GetAll");
				});
			}
		});
	}

	@Test
	void afterStopTheKeptSongStandsStill() throws Exception {
		AtomicReference<String> status = new AtomicReference<>("Playing");
		MprisSource source = source(status);
		try {
			Snapshot playing = source.poll();
			long giveUp = System.nanoTime() + 20_000_000_000L;
			while (playing.track().isNone() && System.nanoTime() < giveUp) {
				Thread.sleep(20);
				playing = source.poll();
			}
			assertTrue(playing.playing());
			assertEquals("A Made-Up Song", playing.track().title());

			status.set("Stopped");
			Thread.sleep(300); // the song played on until the Stop
			Snapshot first = source.poll();
			// Still the song (a blink between songs looks the same), but no longer running
			assertEquals("A Made-Up Song", first.track().title());
			assertFalse(first.playing(), "the player said Stopped, the reader still said it plays");
			long stoppedAt = first.positionMs();
			assertTrue(stoppedAt >= 60_250 && stoppedAt <= 60_000 + 5_000, "stands where the song got to: " + stoppedAt);

			Thread.sleep(300);
			Snapshot second = source.poll();
			if (!second.track().isNone()) {
				assertFalse(second.playing());
				assertEquals(stoppedAt, second.positionMs(), "it doesn't move on while kept");
			}
			Snapshot gone = second;
			for (int i = 0; i < MprisSource.EMPTY_GRACE_POLLS + 1 && !gone.track().isNone(); i++) {
				gone = source.poll();
			}
			assertTrue(gone.track().isNone(), "a stopped player's song goes after the grace polls");

			status.set("Playing");
			Snapshot again = source.poll();
			giveUp = System.nanoTime() + 20_000_000_000L;
			while (again.track().isNone() && System.nanoTime() < giveUp) {
				Thread.sleep(20);
				again = source.poll();
			}
			assertTrue(again.playing(), "playing again after a Stop");
		} finally {
			source.close();
		}
	}
}
