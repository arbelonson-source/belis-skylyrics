package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Everything here runs without a Mac: osascript's answers are made up. */
class MacMediaSourceTest {
	/**
	 * On a Mac (CI), the real thing: the AppleScript must compile and run. Spotify and Music aren't open on a
	 * test machine, so nothing is playing and nothing gets launched.
	 */
	@Test
	void runsTheRealAppleScript() throws Exception {
		assumeTrue(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac"), "macOS only");
		MacMediaSource source = new MacMediaSource(() -> "");
		try {
			Snapshot s = null;
			// A cold start can take longer than one poll waits; the poll says so and the next one picks it up
			for (int i = 0; s == null; i++) {
				try {
					s = source.poll();
				} catch (IOException e) {
					if (i >= 10 || !e.getMessage().startsWith("Waiting")) {
						throw e;
					}
				}
			}
			assertSame(Snapshot.NOTHING, s);
		} finally {
			source.close();
		}
	}

	private static final String SPOTIFY_PLAYING = "Spotify\tplaying\t215000\t61.25\tSong One\tMade Up Band\tMade Up Album\thttps://i.scdn.co/image/abc\n";
	private static final String MUSIC_PAUSED = "Music\tpaused\t245.5\t12.5\tSong Two\tOther Band\tOther Album\t\n";

	@Test
	void readsBothApps() {
		MacMediaSource.Answer a = MacMediaSource.parse(SPOTIFY_PLAYING + MUSIC_PAUSED);
		assertEquals(2, a.players().size());
		assertTrue(a.failures().isEmpty());

		MacMediaSource.Reading spotify = a.players().get(0);
		assertSame(MacMediaSource.SPOTIFY, spotify.app());
		assertTrue(spotify.playing());
		assertEquals(new Track("Song One", "Made Up Band", "Made Up Album", 215_000, "https://i.scdn.co/image/abc", "spotify"), spotify.track());
		assertEquals(61_250, spotify.positionMs(), "Spotify's position is in seconds");

		MacMediaSource.Reading music = a.players().get(1);
		assertSame(MacMediaSource.MUSIC, music.app());
		assertFalse(music.playing(), "paused");
		assertEquals(245_500, music.track().lengthMs(), "Music's duration is in seconds, Spotify's in milliseconds");
		assertEquals(12_500, music.positionMs());
		assertEquals("apple music", music.track().player());
		assertEquals("", music.track().artUrl());
	}

	@Test
	void onlyOneAppRunning() {
		MacMediaSource.Answer a = MacMediaSource.parse(MUSIC_PAUSED);
		assertEquals(1, a.players().size());
		assertEquals("Song Two", a.players().getFirst().track().title());
	}

	@Test
	void nothingRunningOrStopped() {
		assertTrue(MacMediaSource.parse("").players().isEmpty());
		assertTrue(MacMediaSource.parse("\n").players().isEmpty());
		MacMediaSource.Answer a = MacMediaSource.parse("Spotify\tstopped\nMusic\tstopped\n");
		assertTrue(a.players().isEmpty());
		assertTrue(a.failures().isEmpty());
		// No title (between songs, say), an app we didn't ask about, a button press's answer and junk are skipped
		assertTrue(MacMediaSource.parse("Spotify\tplaying\t1000\t0\t\tA\tB\t\nVLC\tplaying\t1\t1\tX\tY\tZ\t\nSpotify\tdone\njunk\n")
				.players().isEmpty());
	}

	@Test
	void oddCharactersInTitles() {
		// The script turns tabs and line breaks inside titles into spaces before they get here
		String out = "Music\tplaying\t200\t1\tשיר  \"Quoted\" \\ back/slash 🎵 & <b>\tBjörk, Sigur Rós\tÀlbum: Part 2\t\r\n";
		MacMediaSource.Reading r = MacMediaSource.parse(out).players().getFirst();
		assertEquals("שיר  \"Quoted\" \\ back/slash 🎵 & <b>", r.track().title());
		assertEquals("Björk, Sigur Rós", r.track().artist());
		assertEquals("Àlbum: Part 2", r.track().album());
		assertEquals("", r.track().artUrl(), "the carriage return at the end is dropped");

		// Windows-style line ends and a stray control character
		r = MacMediaSource.parse("Spotify\tpaused\t1000\t0\tSo\u0007ng\tA\tB\tC\r\n").players().getFirst();
		assertEquals("Song", r.track().title());
		assertEquals("C", r.track().artUrl());
	}

	@Test
	void numbersTheMacWay() {
		assertEquals(12.5, MacMediaSource.number("12.5"));
		assertEquals(12.5, MacMediaSource.number("12,5"), "many languages write a decimal comma");
		assertEquals(1.0e9, MacMediaSource.number("1.0E+9"));
		assertEquals(0, MacMediaSource.number(""));
		assertEquals(0, MacMediaSource.number("missing value"));
		assertEquals(-1743, MacMediaSource.number("-1743"));

		MacMediaSource.Reading r = MacMediaSource.parse("Music\tplaying\t180,25\t61,5\tSong\tA\tB\t\n").players().getFirst();
		assertEquals(180_250, r.track().lengthMs());
		assertEquals(61_500, r.positionMs());
		// A stream without a length: position is kept as it is; past the end of a song it stops at the end
		r = MacMediaSource.parse("Music\tplaying\t\t95.0\tRadio\t\t\t\n").players().getFirst();
		assertEquals(0, r.track().lengthMs());
		assertEquals(95_000, r.positionMs());
		r = MacMediaSource.parse("Spotify\tplaying\t1000\t5\tShort\tA\tB\t\n").players().getFirst();
		assertEquals(1_000, r.positionMs());
	}

	@Test
	void readsErrors() {
		MacMediaSource.Answer a = MacMediaSource.parse(
				"Spotify\terror\t-1743\tNot authorized to send Apple events to Spotify.\n" + MUSIC_PAUSED);
		assertEquals(1, a.players().size());
		assertEquals(List.of(new MacMediaSource.Failure(MacMediaSource.SPOTIFY, -1743, "Not authorized to send Apple events to Spotify.")),
				a.failures());
	}

	@Test
	void followsAppsAndSendsControls() throws Exception {
		FakeRunner runner = new FakeRunner();
		String[] preferred = {""};
		MacMediaSource source = new MacMediaSource(() -> preferred[0], runner, 0);

		runner.answer(SPOTIFY_PLAYING + MUSIC_PAUSED);
		Snapshot s = source.poll();
		assertEquals("Song One", s.track().title(), "follows the app that is playing");
		assertTrue(s.playing());
		assertEquals(61_250, s.positionMs());
		assertEquals("Spotify", source.displayName());

		List<String> command = runner.commands.getLast();
		assertEquals("/usr/bin/osascript", command.getFirst());
		assertEquals("-e", command.get(1));
		assertEquals("Spotify", command.get(command.size() - 4), "app name, then its part of the script");
		assertTrue(command.get(command.size() - 3).contains("tell application \"Spotify\""));
		assertTrue(command.get(command.size() - 3).contains("artwork url"));
		assertEquals("Music", command.get(command.size() - 2));

		runner.answer("Spotify\tdone\n");
		source.playPause();
		assertEquals(List.of("Spotify", "tell application \"Spotify\" to playpause\nreturn {\"done\"}"), args(runner.commands.getLast()));
		runner.answer("Spotify\tdone\n");
		source.seek(65_432);
		assertTrue(args(runner.commands.getLast()).get(1).startsWith("tell application \"Spotify\" to set player position to (65432 / 1000)\n"));

		preferred[0] = "music";
		runner.answer(SPOTIFY_PLAYING + MUSIC_PAUSED);
		s = source.poll();
		assertEquals("Song Two", s.track().title(), "the chosen player wins even when paused");
		assertFalse(s.playing());
		assertEquals("Apple Music", source.displayName());
		runner.answer("Music\tdone\n");
		source.next();
		assertEquals("tell application \"Music\" to next track\nreturn {\"done\"}", args(runner.commands.getLast()).get(1));
		runner.answer("Music\tdone\n");
		source.previous();
		assertTrue(args(runner.commands.getLast()).get(1).contains("previous track"));

		runner.answer("");
		assertSame(Snapshot.NOTHING, source.poll());
		int before = runner.commands.size();
		source.playPause();
		assertEquals(before, runner.commands.size(), "nothing to control once both apps have quit");
	}

	@Test
	void reusesAReadingForAMoment() throws Exception {
		FakeRunner runner = new FakeRunner();
		MacMediaSource source = new MacMediaSource(() -> "", runner, 60_000_000_000L);
		runner.answer(SPOTIFY_PLAYING);
		Snapshot first = source.poll();
		Snapshot again = source.poll();
		assertEquals(1, runner.commands.size());
		assertEquals(first, again);
		runner.answer("Spotify\tdone\n");
		source.playPause();
		runner.answer(SPOTIFY_PLAYING.replace("playing", "paused"));
		assertFalse(source.poll().playing(), "a button press reads again at once");
	}

	@Test
	void explainsMissingPermission() {
		FakeRunner runner = new FakeRunner();
		MacMediaSource source = new MacMediaSource(() -> "", runner, 0);
		runner.answer("Spotify\terror\t-1743\tNot authorized to send Apple events to Spotify.\n");
		IOException e = assertThrows(IOException.class, source::poll);
		assertTrue(e.getMessage().contains("Spotify") && e.getMessage().contains("Automation"), e.getMessage());

		// Another app still playing is followed regardless
		runner.answer("Spotify\terror\t-1743\tNot authorized to send Apple events to Spotify.\n" + MUSIC_PAUSED);
		assertDoesNotThrow(source);

		// An app that quit as we asked is just nothing playing; other errors are shown
		runner.answer("Music\terror\t-600\tApplication isn't running.\n");
		assertDoesNotThrow(source);
		runner.answer("Music\terror\t-10000\tSomething odd.\n");
		e = assertThrows(IOException.class, source::poll);
		assertTrue(e.getMessage().contains("Something odd"), e.getMessage());
	}

	@Test
	void backsOffWhenOsascriptIsMissing() {
		FakeRunner runner = new FakeRunner();
		MacMediaSource source = new MacMediaSource(() -> "", runner, 0);
		runner.plans.add(new IOException("No such file"));
		IOException first = assertThrows(IOException.class, source::poll);
		assertTrue(first.getMessage().contains("osascript"), first.getMessage());
		IOException again = assertThrows(IOException.class, source::poll);
		assertEquals(first.getMessage(), again.getMessage());
		assertEquals(1, runner.starts, "waits before trying again");

		source.close(); // like Reconnect: try again at once
		runner.answer(SPOTIFY_PLAYING);
		assertDoesNotThrow(source);
		assertEquals(2, runner.starts);
	}

	@Test
	void backsOffWhenOsascriptKeepsFailing() {
		FakeRunner runner = new FakeRunner();
		MacMediaSource source = new MacMediaSource(() -> "", runner, 0);
		for (int i = 0; i < 3; i++) {
			runner.plans.add(new FakeRun(new MacMediaSource.Result(1, "", "syntax error: Expected end of line (-2741)\nmore"), 0));
			IOException e = assertThrows(IOException.class, source::poll);
			assertTrue(e.getMessage().contains("-2741") && !e.getMessage().contains("more"), e.getMessage());
		}
		assertThrows(IOException.class, source::poll);
		assertEquals(3, runner.starts, "stops trying for a while after three failures in a row");
	}

	/** The permission question keeps osascript waiting; it is left alone rather than killed and asked again. */
	@Test
	void waitsForThePermissionQuestion() throws Exception {
		FakeRunner runner = new FakeRunner();
		MacMediaSource source = new MacMediaSource(() -> "", runner, 0);
		runner.answer(MUSIC_PAUSED);
		source.poll();
		FakeRun slow = new FakeRun(new MacMediaSource.Result(0, SPOTIFY_PLAYING, ""), 2);
		runner.plans.add(slow);
		IOException e = assertThrows(IOException.class, source::poll);
		assertTrue(e.getMessage().contains("click OK"), e.getMessage());
		source.playPause(); // no button presses while waiting
		assertThrows(IOException.class, source::poll);
		assertEquals("Song One", source.poll().track().title(), "the answer, once it comes");
		assertEquals(2, runner.starts);
		assertFalse(slow.killed);
	}

	@Test
	void stuckButtonPressIsKilled() throws Exception {
		FakeRunner runner = new FakeRunner();
		MacMediaSource source = new MacMediaSource(() -> "", runner, 0);
		runner.answer(SPOTIFY_PLAYING);
		source.poll();
		FakeRun stuck = new FakeRun(new MacMediaSource.Result(0, "", ""), Integer.MAX_VALUE);
		runner.plans.add(stuck);
		source.next();
		assertTrue(stuck.killed);
		runner.answer(SPOTIFY_PLAYING);
		assertEquals("Song One", source.poll().track().title(), "the next poll starts a fresh osascript");
	}

	@Test
	void closeKillsAWaitingOsascript() {
		FakeRunner runner = new FakeRunner();
		MacMediaSource source = new MacMediaSource(() -> "", runner, 0);
		FakeRun slow = new FakeRun(new MacMediaSource.Result(0, "", ""), Integer.MAX_VALUE);
		runner.plans.add(slow);
		assertThrows(IOException.class, source::poll);
		source.close();
		assertTrue(slow.killed);
	}

	private static void assertDoesNotThrow(MacMediaSource source) {
		org.junit.jupiter.api.Assertions.assertDoesNotThrow(source::poll);
	}

	/** The arguments after the main script. */
	private static List<String> args(List<String> command) {
		int i = command.lastIndexOf("-e");
		return command.subList(i + 2, command.size());
	}

	private static final class FakeRun implements MacMediaSource.Run {
		private final MacMediaSource.Result result;
		private int hangs;
		boolean killed;

		/** @param hangs how many waits it takes to finish */
		FakeRun(MacMediaSource.Result result, int hangs) {
			this.result = result;
			this.hangs = hangs;
		}

		@Override
		public MacMediaSource.@Nullable Result await(long ms) {
			if (killed) {
				return new MacMediaSource.Result(137, "", "");
			}
			if (hangs > 0) {
				hangs--;
				return null;
			}
			return result;
		}

		@Override
		public void kill() {
			killed = true;
		}
	}

	private static final class FakeRunner implements MacMediaSource.Runner {
		/** Each start takes the next: a FakeRun, or an IOException to throw. */
		final Deque<Object> plans = new ArrayDeque<>();
		final List<List<String>> commands = new ArrayList<>();
		int starts;

		void answer(String out) {
			plans.add(new FakeRun(new MacMediaSource.Result(0, out, ""), 0));
		}

		@Override
		public MacMediaSource.Run start(List<String> command) throws IOException {
			starts++;
			commands.add(command);
			Object plan = plans.poll();
			if (plan instanceof IOException e) {
				throw e;
			}
			if (plan == null) {
				throw new AssertionError("osascript started unexpectedly: " + command.getLast());
			}
			return (MacMediaSource.Run) plan;
		}
	}
}
