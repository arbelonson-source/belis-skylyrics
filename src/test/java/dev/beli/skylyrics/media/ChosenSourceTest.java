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

	@Test
	void closingClosesTheLocalSourceAndPassesOnItsFailure() {
		Local local = new Local();
		local.failOnClose = true;
		ChosenSource source = chosen(local, web(), "");
		assertThrows(IllegalStateException.class, source::close);
		assertTrue(local.closed);
	}
}
