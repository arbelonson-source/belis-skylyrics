package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.assertFalse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.freedesktop.dbus.types.Variant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class MprisSourceTest {
	private static final String SLOW = "org.mpris.MediaPlayer2.slowapp";
	private static final String GOOD = "org.mpris.MediaPlayer2.mpv";

	private static MprisSource.Reading reading(String bus, String title, long positionMs) {
		return new MprisSource.Reading(bus, new Track(title, "Made Up Band", "", 200_000, "", bus.substring(bus.lastIndexOf('.') + 1)),
				positionMs, true, "/id");
	}

	private static Snapshot pollWithinBound(MprisSource source) throws Exception {
		long start = System.nanoTime();
		Snapshot s = source.poll();
		long tookMs = (System.nanoTime() - start) / 1_000_000;
		// Expected about ANSWER_WAIT_MS; the bound only has to stay well short of the 60 s the player is stuck for
		assertTrue(tookMs < 30_000, "poll took " + tookMs + " ms"); // generous for a busy CI machine; the stuck player holds 60 s
		return s;
	}

	@Test
	void oneStuckPlayerDoesNotHoldUpTheOthers() throws Exception {
		CountDownLatch unstick = new CountDownLatch(1);
		MprisSource source = new MprisSource(() -> "", new MprisSource.Reader() {
			@Override
			public List<String> names() {
				return List.of(SLOW, GOOD);
			}

			@Override
			public MprisSource.Reading read(String busName) throws Exception {
				if (busName.equals(SLOW)) {
					// Stuck for far longer than any poll may take, until the test is over
					unstick.await(60, TimeUnit.SECONDS);
					return reading(SLOW, "Slow Song", 0);
				}
				return reading(GOOD, "Good Song", 5000);
			}
		});
		try {
			// The first polls only start the reader threads; on a loaded machine the good answer
			// may take longer than ANSWER_WAIT_MS to arrive, so poll until it shows
			long giveUp = System.nanoTime() + 20_000L * 1_000_000L;
			Snapshot first;
			do {
				first = pollWithinBound(source);
			} while (first.track().isNone() && System.nanoTime() < giveUp);
			assertEquals("Good Song", first.track().title());
			// From here the good player has answered; the stuck one must still not hold anything up
			for (int i = 0; i < 3; i++) {
				assertEquals("Good Song", pollWithinBound(source).track().title(), "poll " + i);
			}
		} finally {
			unstick.countDown();
			source.close();
		}
	}

	@Test
	void aFailedReadKeepsTheLastGoodReadingForAMoment() throws Exception {
		AtomicBoolean failing = new AtomicBoolean();
		AtomicReference<List<String>> names = new AtomicReference<>(List.of(GOOD));
		MprisSource source = new MprisSource(() -> "", new MprisSource.Reader() {
			@Override
			public List<String> names() {
				return names.get();
			}

			@Override
			public MprisSource.Reading read(String busName) {
				if (failing.get()) {
					throw new IllegalStateException("busy");
				}
				return reading(GOOD, "Good Song", 5000);
			}
		});
		try {
			long giveUp = System.nanoTime() + 20_000L * 1_000_000L;
			Snapshot first = source.poll();
			while (first.track().isNone() && System.nanoTime() < giveUp) {
				Thread.sleep(20);
				first = source.poll();
			}
			assertEquals("Good Song", first.track().title());
			failing.set(true);
			assertEquals("Good Song", source.poll().track().title());
			// a player that left the bus is gone at once
			names.set(List.of());
			assertTrue(source.poll().track().isNone());
		} finally {
			source.close();
		}
	}

	@Test
	void aLateAnswerIsStampedWhenItWasAsked() throws Exception {
		java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.atomic.AtomicLong readerEntered = new java.util.concurrent.atomic.AtomicLong();
		MprisSource source = new MprisSource(() -> "", new MprisSource.Reader() {
			@Override
			public List<String> names() {
				return List.of(GOOD);
			}

			@Override
			public MprisSource.Reading read(String busName) throws Exception {
				readerEntered.set(System.nanoTime());
				entered.countDown();
				// Answers only once the test lets it, i.e. after the first poll has given up waiting
				release.await();
				return reading(GOOD, "Good Song", 5000);
			}
		});
		try {
			long asked = System.nanoTime();
			assertTrue(source.poll().track().isNone(), "no answer yet");
			assertTrue(entered.await(20, java.util.concurrent.TimeUnit.SECONDS), "the reader never started");
			release.countDown();
			long released = System.nanoTime();
			Snapshot s = null;
			long giveUp = System.nanoTime() + 20_000L * 1_000_000L;
			while ((s == null || s.track().isNone()) && System.nanoTime() < giveUp) {
				Thread.sleep(20);
				s = source.poll();
			}
			assertEquals("Good Song", s.track().title());
			// The answer was gathered when the read asked, before the first poll gave up, not when it was picked up
			assertTrue(readerEntered.get() != 0);
			assertTrue(s.readAtNanos() >= asked, "not before the poll");
			assertTrue(s.readAtNanos() <= readerEntered.get(), "stamped when asked, not when picked up");
			assertTrue(s.readAtNanos() < released, "stamped before the answer was let go");
		} finally {
			release.countDown();
			source.close();
		}
	}

	@Test
	void sessionBusAddressFallsBackToTheRuntimeDirSocket() {
		Set<String> there = Set.of("/run/user/1000/bus");
		// the environment's own address is left to the library
		assertEquals(null, MprisSource.sessionBusAddress(Map.of("DBUS_SESSION_BUS_ADDRESS", "unix:path=/x", "XDG_RUNTIME_DIR", "/run/user/1000"), there::contains, "1000"));
		assertEquals("unix:path=/run/user/1000/bus", MprisSource.sessionBusAddress(Map.of("XDG_RUNTIME_DIR", "/run/user/1000"), there::contains, "1000"));
		assertEquals("unix:path=/run/user/1000/bus", MprisSource.sessionBusAddress(Map.of("DBUS_SESSION_BUS_ADDRESS", " ", "XDG_RUNTIME_DIR", "/run/user/1000/"), there::contains, ""));
		// no runtime dir variable: the user's own directory
		assertEquals("unix:path=/run/user/1000/bus", MprisSource.sessionBusAddress(Map.of(), there::contains, "1000"));
		// nothing there: leave it to the library
		assertEquals(null, MprisSource.sessionBusAddress(Map.of(), p -> false, "1000"));
		assertEquals(null, MprisSource.sessionBusAddress(Map.of(), there::contains, ""));
	}

	@Test
	void aFlatpakThatHidesEveryPlayerSaysSo() throws Exception {
		MprisSource source = new MprisSource(() -> "", new MprisSource.Reader() {
			@Override
			public List<String> names() {
				return List.of(":1.5", "org.freedesktop.DBus");
			}

			@Override
			public MprisSource.Reading read(String busName) {
				return null;
			}
		}, () -> true);
		try {
			for (int i = 1; i < MprisSource.HIDDEN_POLLS; i++) {
				assertTrue(source.poll().track().isNone());
			}
			Exception e = org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class, source::poll);
			assertTrue(e.getMessage().contains("flatpak override"), e.getMessage());
		} finally {
			source.close();
		}
	}

	@Test
	void outsideAFlatpakNoPlayersIsJustNothingPlaying() throws Exception {
		MprisSource source = new MprisSource(() -> "", new MprisSource.Reader() {
			@Override
			public List<String> names() {
				return List.of();
			}

			@Override
			public MprisSource.Reading read(String busName) {
				return null;
			}
		}, () -> false);
		try {
			for (int i = 0; i < 10; i++) {
				assertTrue(source.poll().track().isNone());
			}
		} finally {
			source.close();
		}
	}

	@Test
	void aFlatpakThatCanSeeAPlayerIsNotToldAnything() throws Exception {
		AtomicBoolean visible = new AtomicBoolean();
		MprisSource source = new MprisSource(() -> "", new MprisSource.Reader() {
			@Override
			public List<String> names() {
				return visible.get() ? List.of(GOOD) : List.of();
			}

			@Override
			public MprisSource.Reading read(String busName) {
				return reading(GOOD, "Good Song", 5000);
			}
		}, () -> true);
		try {
			for (int i = 0; i < MprisSource.HIDDEN_POLLS - 1; i++) {
				source.poll();
			}
			visible.set(true);
			long giveUp = System.nanoTime() + 20_000L * 1_000_000L;
			Snapshot seen;
			do { // the fresh reader may take longer than ANSWER_WAIT_MS to answer
				seen = source.poll();
			} while (seen.track().isNone() && System.nanoTime() < giveUp);
			assertEquals("Good Song", seen.track().title());
			visible.set(false);
			// the count starts over once a player has been seen
			for (int i = 0; i < MprisSource.HIDDEN_POLLS - 1; i++) {
				source.poll();
			}
		} finally {
			source.close();
		}
	}

	@Test
	void playerIdsDropOnlyAnInstanceSuffix() {
		assertEquals("firefox", MprisSource.playerId("org.mpris.MediaPlayer2.firefox.instance_1_23"));
		assertEquals("chromium", MprisSource.playerId("org.mpris.MediaPlayer2.chromium.instance123"));
		assertEquals("io.bassi.Amberol", MprisSource.playerId("org.mpris.MediaPlayer2.io.bassi.Amberol"));
		assertEquals("org.gnome.Rhythmbox3", MprisSource.playerId("org.mpris.MediaPlayer2.org.gnome.Rhythmbox3"));
		assertEquals("spotify", MprisSource.playerId("org.mpris.MediaPlayer2.spotify"));
		assertEquals("spotifyd", MprisSource.playerId("org.mpris.MediaPlayer2.spotifyd"));
		assertEquals("vlc", MprisSource.playerId("org.mpris.MediaPlayer2.vlc"));
		assertEquals("kdeconnect", MprisSource.playerId("org.mpris.MediaPlayer2.kdeconnect.mpris_000001"));
		assertEquals("plasma-browser-integration", MprisSource.playerId("org.mpris.MediaPlayer2.plasma-browser-integration"));
		assertEquals("mpv", MprisSource.playerId("org.mpris.MediaPlayer2.mpv"));
	}

	@Test
	void playerNamesShowTheLastSegment() {
		assertEquals("Amberol", MprisSource.prettyName("io.bassi.Amberol"));
		assertEquals("Rhythmbox3", MprisSource.prettyName("org.gnome.Rhythmbox3"));
		assertEquals("Firefox", MprisSource.prettyName("firefox"));
		assertEquals("Spotify", MprisSource.prettyName("spotify"));
		assertEquals("Spotifyd", MprisSource.prettyName("spotifyd"));
		assertEquals("VLC", MprisSource.prettyName("vlc"));
		assertEquals("mpv", MprisSource.prettyName("mpv"));
		assertEquals("Kdeconnect", MprisSource.prettyName(MprisSource.playerId("org.mpris.MediaPlayer2.kdeconnect.mpris_000001")));
		assertEquals("Amberol", MprisSource.prettyName(MprisSource.playerId("org.mpris.MediaPlayer2.io.bassi.Amberol")));
	}

	@Test
	void aPreferredPlayerFindsAReverseDnsPlayerByItsLastName() {
		Track amberol = new Track("Made Up Song", "Made Up Band", "", 1000, "", MprisSource.playerId("org.mpris.MediaPlayer2.io.bassi.Amberol"));
		Track other = new Track("Other Song", "Made Up Band", "", 1000, "", "io.other.Thing");
		assertEquals(amberol, PlayerPicker.choose(List.of(other, amberol), t -> t.player(), Track::player, t -> true, "amberol", null));
	}

	@Test
	void positionIsAskedForOnItsOwnWhenGetAllLeavesItOut() {
		assertEquals(7L, MprisSource.positionMicros(Map.of("Position", new Variant<>(7L)), () -> {
			throw new AssertionError("not needed");
		}));
		assertEquals(42L, MprisSource.positionMicros(Map.of(), () -> new Variant<>(42L)));
		assertEquals(0L, MprisSource.positionMicros(Map.of(), () -> {
			throw new IllegalStateException("no such property");
		}));
	}

	private static final String SANDBOX = "[Application]\nname=org.prismlauncher.PrismLauncher\n\n[Context]\nshared=network;ipc;\nsockets=x11;wayland;pulseaudio;\n";

	@Test
	void theFlatpakHintIsOnlyForASandboxThatCannotTalkToPlayers() {
		assertTrue(MprisSource.hidesPlayers(Map.of(), SANDBOX));
		assertTrue(MprisSource.hidesPlayers(Map.of(), SANDBOX + "\n[Session Bus Policy]\norg.freedesktop.Notifications=talk\norg.mpris.MediaPlayer2.spotify=none\n"));
		// the whole session bus
		assertFalse(MprisSource.hidesPlayers(Map.of(), SANDBOX.replace("sockets=x11;", "sockets=x11;session-bus;")));
		// talking to the players' names
		assertFalse(MprisSource.hidesPlayers(Map.of(), SANDBOX + "\n[Session Bus Policy]\norg.mpris.MediaPlayer2.*=talk\n"));
		assertFalse(MprisSource.hidesPlayers(Map.of(), SANDBOX + "\n[Session Bus Policy]\norg.mpris.MediaPlayer2.vlc=own\n"));
		// no /.flatpak-info: nothing is said
		assertFalse(MprisSource.hidesPlayers(Map.of(), null));
	}

	@Test
	void aStoppedPlayerHasNoSongButAPausedOneKeepsIt() {
		assertTrue(MprisSource.stopped(Map.of("PlaybackStatus", new Variant<>("Stopped"))));
		assertFalse(MprisSource.stopped(Map.of("PlaybackStatus", new Variant<>("Paused"))));
		assertFalse(MprisSource.stopped(Map.of("PlaybackStatus", new Variant<>("Playing"))));
		assertFalse(MprisSource.stopped(Map.of()));
	}

	@Test
	void aPlayerWithNothingToShowClearsTheBar() throws Exception {
		MprisSource source = new MprisSource(() -> "", new MprisSource.Reader() {
			@Override
			public List<String> names() {
				return List.of(GOOD);
			}

			@Override
			public MprisSource.Reading read(String busName) {
				return null; // what read() gives for a Stopped player
			}
		});
		try {
			assertTrue(source.poll().track().isNone());
		} finally {
			source.close();
		}
	}


	@Test
	void spotifyDesktopWebAddressBecomesItsSongId() {
		assertEquals("spotify:track:X", MprisSource.songUri("spotify", "https://open.spotify.com/track/X"));
		assertEquals("spotify:track:X", MprisSource.songUri("spotify", "https://open.spotify.com/track/X?si=abc"));
		assertEquals("spotify:episode:E1", MprisSource.songUri("spotify", "https://open.spotify.com/episode/E1"));
		// a browser's page address changes when you click around: never a song id
		assertEquals("https://open.spotify.com/track/X", MprisSource.songUri("firefox", "https://open.spotify.com/track/X"));
		assertEquals("https://open.spotify.com/album/A", MprisSource.songUri("spotify", "https://open.spotify.com/album/A"));
		assertEquals("spotify:track:Z", MprisSource.songUri("spotify", "spotify:track:Z"));
		assertEquals("", MprisSource.songUri("spotify", ""));
	}

	@Test
	void twoSameNamedSpotifyDesktopSongsAreToldApart() {
		Track a = new Track("Interlude", "Made Up Band", "Album", 60_000, "", "spotify", MprisSource.songUri("spotify", "https://open.spotify.com/track/X"));
		Track b = new Track("Interlude", "Made Up Band", "Album", 60_000, "", "spotify", MprisSource.songUri("spotify", "https://open.spotify.com/track/Y"));
		assertFalse(a.sameSong(b));
		assertTrue(a.sameSong(new Track("Interlude", "Made Up Band", "Album", 60_000, "", "spotify", "spotify:track:X")));
	}
}
