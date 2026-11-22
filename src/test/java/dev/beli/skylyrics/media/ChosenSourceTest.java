package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Which source ChosenSource asks: the computer's own player, or the Spotify account when "spotify-web" is picked. */
class ChosenSourceTest {
	private static final Snapshot LOCAL_SNAPSHOT = new Snapshot(new Track("Local", "A", "B", 1000, "", "mpv"), 5, true, 1);

	private static class Local implements MediaSource {
		final List<String> calls = new ArrayList<>();
		boolean closed;
		int standBys;
		boolean failOnClose;
		Throwable failure;

		@Override
		public Throwable failing() {
			return failure;
		}

		@Override
		public Snapshot poll() {
			calls.add("poll");
			return LOCAL_SNAPSHOT;
		}

		@Override
		public void playPause() {
			calls.add("playPause");
		}

		@Override
		public void next() {
			calls.add("next");
		}

		@Override
		public void previous() {
			calls.add("previous");
		}

		@Override
		public void seek(long positionMs) {
			calls.add("seek " + positionMs);
		}

		@Override
		public String displayName() {
			return "Local";
		}

		/** No @Override: the test also builds against a MediaSource without it, to see it fail there. */
		public void standBy() {
			standBys++;
		}

		@Override
		public void close() {
			closed = true;
			if (failOnClose) {
				throw new IllegalStateException("boom");
			}
		}
	}

	@TempDir
	Path dir;

	private SpotifyWebSource web() {
		return new SpotifyWebSource(() -> "", dir.resolve("login.json"));
	}

	private static ChosenSource chosen(Local local, SpotifyWebSource web, String preferred) {
		return new ChosenSource(local, web, () -> preferred);
	}

	@Test
	void anyPlayerNameOrNothingUsesTheComputersOwnPlayer() throws Exception {
		for (String preferred : new String[] {"", "mpv", "spotify", "spotify-web ", "SPOTIFY-WEB", "spotify-web2", null}) {
			Local local = new Local();
			ChosenSource source = chosen(local, web(), preferred);
			assertSame(LOCAL_SNAPSHOT, source.poll(), "[" + preferred + "]");
			source.playPause();
			source.next();
			source.previous();
			source.seek(1234);
			assertEquals(List.of("poll", "playPause", "next", "previous", "seek 1234"), local.calls, "[" + preferred + "]");
			assertEquals("Local", source.displayName(), "[" + preferred + "]");
		}
	}

	@Test
	void spotifyWebNeverTouchesTheLocalPlayer() throws Exception {
		Local local = new Local();
		ChosenSource source = chosen(local, web(), SpotifyWebSource.KEY);
		assertEquals("Spotify", source.displayName());
		try {
			source.poll(); // signed out: the web source answers (or says to sign in), the local one is not asked
		} catch (java.io.IOException expected) {
			// the web source's own message
		}
		assertTrue(local.calls.isEmpty(), local.calls.toString());
	}

	@Test
	void theChoiceIsReadOnEveryCallSoSwitchingNeedsNoRestart() throws Exception {
		Local local = new Local();
		AtomicReference<String> setting = new AtomicReference<>(SpotifyWebSource.KEY);
		ChosenSource source = new ChosenSource(local, web(), setting::get);
		assertEquals("Spotify", source.displayName());
		setting.set("mpv");
		assertEquals("Local", source.displayName());
		assertSame(LOCAL_SNAPSHOT, source.poll());
		setting.set(SpotifyWebSource.KEY);
		assertEquals("Spotify", source.displayName());
		setting.set("");
		source.playPause();
		assertEquals(List.of("poll", "playPause"), local.calls);
	}

	@Test
	void aFailingStreakIsTheChosenSourcesOwn() {
		Local local = new Local();
		local.failure = new java.io.IOException("can't read the player");
		AtomicReference<String> setting = new AtomicReference<>("");
		ChosenSource source = new ChosenSource(local, web(), setting::get);
		// without this the tracker saw every quiet retry between failures as a working read
		assertSame(local.failure, source.failing());
		local.failure = null;
		assertNull(source.failing());
		local.failure = new java.io.IOException("still failing");
		setting.set(SpotifyWebSource.KEY);
		assertNull(source.failing(), "Spotify Web has its own streak, not the local player's");
	}

	@Test
	void aFailureBelongsToTheSourceThatWasPolledEvenIfTheSettingChangesMeanwhile() {
		AtomicReference<String> setting = new AtomicReference<>("");
		Local local = new Local() {
			@Override
			public Snapshot poll() {
				setting.set(SpotifyWebSource.KEY); // the player switches while this poll runs
				failure = new java.io.IOException("can't read the player");
				return LOCAL_SNAPSHOT;
			}
		};
		ChosenSource source = new ChosenSource(local, web(), setting::get);
		try {
			source.poll();
		} catch (Exception e) {
			throw new AssertionError(e);
		}
		assertSame(local.failure, source.failing(), "the failure of the poll that just ran is the local player's");
	}

	/** Spotify Web being picked tells the computer's own reader once, so it can stop what it keeps running. */
	@Test
	void switchingToSpotifyWebPutsTheLocalSourceOnStandByOnce() throws Exception {
		Local local = new Local();
		AtomicReference<String> setting = new AtomicReference<>("");
		ChosenSource source = new ChosenSource(local, web(), setting::get);
		source.poll();
		source.poll();
		assertEquals(0, local.standBys, "the local source is the one in use");
		setting.set(SpotifyWebSource.KEY);
		for (int i = 0; i < 3; i++) {
			try {
				source.poll();
			} catch (java.io.IOException signedOut) {
				// the web source's own message
			}
		}
		assertEquals(1, local.standBys, "once, on the switch");
		setting.set("spotify");
		source.poll();
		assertEquals(1, local.standBys);
		setting.set(SpotifyWebSource.KEY);
		try {
			source.poll();
		} catch (java.io.IOException signedOut) {
			// the web source's own message
		}
		assertEquals(2, local.standBys);
		assertEquals(List.of("poll", "poll", "poll"), local.calls, "never asked while Spotify Web is picked");
	}

	/**
	 * The Windows reader's helper (a hidden PowerShell) is stopped when Spotify Web is picked, instead of idling for the
	 * rest of the game, and a new one starts when the computer's own player is picked again. A shell stands in for it.
	 */
	@Test
	@org.junit.jupiter.api.Timeout(60)
	void switchingToSpotifyWebStopsTheWindowsHelper() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows")
				&& java.nio.file.Files.isExecutable(Path.of("/bin/sh")), "needs a POSIX shell for the stand-in helper");
		Path pidFile = dir.resolve("helper.pid");
		String body = "echo $$ > '" + pidFile + "'; printf '{\"ready\":true}\\n'; while read n cmd rest; do "
				+ "printf '{\"seq\":%s,\"ok\":true,\"sessions\":[{\"index\":0,\"id\":\"Spotify.exe\",\"title\":\"Song\",\"status\":\"Playing\",\"positionMs\":1000,\"startMs\":0,\"endMs\":0,\"updatedMs\":0}]}\\n' \"$n\"; done";
		int[] starts = {0};
		WindowsMediaSource windows = new WindowsMediaSource(() -> "", dir.resolve("windows"), (script, covers) -> {
			starts[0]++;
			return List.of("/bin/sh", "-c", body);
		}, 20_000, 5_000);
		AtomicReference<String> setting = new AtomicReference<>("");
		ChosenSource source = new ChosenSource(windows, web(), setting::get);
		try {
			assertEquals("Song", source.poll().track().title());
			long pid = Long.parseLong(java.nio.file.Files.readString(pidFile).strip());
			assertTrue(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false), "the helper runs");
			setting.set(SpotifyWebSource.KEY);
			try {
				source.poll();
			} catch (java.io.IOException signedOut) {
				// the web source's own message
			}
			assertTrue(ProcessHandle.of(pid).map(h -> !h.isAlive()).orElse(true), "no helper left running while Spotify Web is followed");
			setting.set("");
			assertEquals("Song", source.poll().track().title(), "back on the computer's own player");
			assertEquals(2, starts[0]);
		} finally {
			source.close();
		}
	}

	@Test
	void closingClosesTheLocalSourceAndPassesOnItsFailure() {
		Local local = new Local();
		local.failOnClose = true;
		ChosenSource source = chosen(local, web(), "");
		assertThrows(IllegalStateException.class, source::close);
		assertTrue(local.closed);
	}
}
