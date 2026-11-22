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
	void notRunningAppsAreNeverAsked() throws Exception {
		FakeRunner runner = new FakeRunner();
		MacMediaSource source = new MacMediaSource(() -> "", runner, 0, app -> false);
		assertSame(Snapshot.NOTHING, source.poll());
		assertTrue(runner.commands.isEmpty(), "no osascript when neither app is open");
	}

	@Test
	void runsTheRealAppleScript() throws Exception {
		assumeTrue(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac"), "macOS only");
		MacMediaSource source = new MacMediaSource(() -> "", () -> true);
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
			// And the AppleScript itself compiles and runs here
			assertEquals("", source.checkScript().strip());
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
		// The double space after the Hebrew word is now one space: a Track collapses runs of space
		assertEquals("שיר \"Quoted\" \\ back/slash 🎵 & <b>", r.track().title());
		assertEquals("Björk, Sigur Rós", r.track().artist());
		assertEquals("Àlbum: Part 2", r.track().album());
		assertEquals("", r.track().artUrl(), "the carriage return at the end is dropped");

		// Windows-style line ends and a stray control character
		r = MacMediaSource.parse("Spotify\tpaused\t1000\t0\tSo\u0007ng\tA\tB\tC\r\n").players().getFirst();
		assertEquals("Song", r.track().title());
		assertEquals("C", r.track().artUrl());
	}

	@Test
	void aMissingFieldStillLeavesTheRestOfTheReading() {
		// A local file or podcast has no cover link; the script now sends it empty instead of failing the whole reading
		MacMediaSource.Reading r = MacMediaSource.parse("Spotify\tplaying\t215000\t61.25\tSong One\tMade Up Band\t\t\n").players().getFirst();
		assertEquals("Song One", r.track().title());
		assertEquals("Made Up Band", r.track().artist());
		assertEquals("", r.track().artUrl());
		assertEquals(215_000, r.track().lengthMs());
		// No duration or position either: still a song, with unknown length
		r = MacMediaSource.parse("Music\tplaying\t\t\tSong Two\t\t\t\n").players().getFirst();
		assertEquals("Song Two", r.track().title());
		assertEquals(0, r.track().lengthMs());
	}

	@Test
	void spotifyAddressIsReadWhenThere() {
		MacMediaSource.Reading r = MacMediaSource.parse(
				"Spotify\tplaying\t215000\t61.25\tSong One\tMade Up Band\tMade Up Album\thttps://x/a\tspotify:track:abc\n").players().getFirst();
		assertEquals("spotify:track:abc", r.track().uri());
		assertEquals("Song One", r.track().title());
		// An old reply without the field is read as before
		r = MacMediaSource.parse(SPOTIFY_PLAYING).players().getFirst();
		assertEquals("", r.track().uri());
		assertEquals("Song One", r.track().title());
		// Two songs with the same text are told apart by their address
		Track a = MacMediaSource.parse("Spotify\tplaying\t1000\t0\tInterlude\tBand\tAlbum\t\tspotify:track:1\n").players().getFirst().track();
		Track b = MacMediaSource.parse("Spotify\tplaying\t1000\t0\tInterlude\tBand\tAlbum\t\tspotify:track:2\n").players().getFirst().track();
		assertTrue(a.otherUri(b));
	}

	@Test
	void anAdIsNoSong() {
		assertTrue(MacMediaSource.parse("Spotify\tplaying\t30000\t1\tAdvertisement\tSpotify\t\t\tspotify:ad:0000\n").players().isEmpty());
		// the other app is still read
		assertEquals(1, MacMediaSource.parse("Spotify\tplaying\t30000\t1\tAdvertisement\tSpotify\t\t\tspotify:ad:0000\n" + MUSIC_PAUSED).players().size());
		// a local file's address is not an ad
		assertEquals(1, MacMediaSource.parse("Spotify\tplaying\t30000\t1\tSong\tA\t\t\tspotify:local:a:b:c:1\n").players().size());
	}

	private static final String SPOTIFY_AD = "Spotify\tplaying\t30000\t1\tAdvertisement\tSpotify\t\t\tspotify:ad:0000\n";

	/** While Spotify plays an ad, Spotify is still the app that plays: Music's paused song doesn't take over for it. */
	@Test
	void anAdKeepsSpotifyFollowed() throws Exception {
		FakeRunner runner = new FakeRunner();
		MacMediaSource source = new MacMediaSource(() -> "", runner, 0);
		runner.answer(SPOTIFY_PLAYING + MUSIC_PAUSED);
		assertEquals("Song One", source.poll().track().title());

		runner.answer(SPOTIFY_AD + MUSIC_PAUSED);
		assertSame(Snapshot.NOTHING, source.poll(), "no song during the ad, and not Music's paused one");
		assertEquals("Spotify", source.displayName());
		runner.answer("Spotify\tdone\n");
		source.playPause();
		assertEquals("Spotify", args(runner.commands.getLast()).getFirst(), "pausing during the ad pauses Spotify, not Music");

		runner.answer(SPOTIFY_PLAYING.replace("Song One", "Song Three") + MUSIC_PAUSED);
		assertEquals("Song Three", source.poll().track().title(), "the next song after the ad");
	}

	/** With only Spotify open, the buttons still reach it during an ad (ads can be paused). */
	@Test
	void theButtonsReachSpotifyDuringAnAd() throws Exception {
		FakeRunner runner = new FakeRunner();
		MacMediaSource source = new MacMediaSource(() -> "", runner, 0);
		runner.answer(SPOTIFY_AD);
		assertSame(Snapshot.NOTHING, source.poll());
		runner.answer("Spotify\tdone\n");
		source.playPause();
		assertEquals(2, runner.commands.size(), "the button press ran");
		assertEquals(List.of("Spotify", "tell application \"Spotify\" to playpause\nreturn {\"done\"}"), args(runner.commands.getLast()));
	}

	/** Spotify picked in the settings: during its ad, Music's paused song isn't shown instead. */
	@Test
	void thePickedSpotifyKeepsItsAd() throws Exception {
		FakeRunner runner = new FakeRunner();
		MacMediaSource source = new MacMediaSource(() -> "spotify", runner, 0);
		runner.answer(SPOTIFY_AD + MUSIC_PAUSED);
		assertSame(Snapshot.NOTHING, source.poll());
		assertEquals("Spotify", source.displayName());
	}

	/** An ad only keeps Spotify followed: a song playing in Music still wins when nothing was followed yet, or Music is picked. */
	@Test
	void aPlayingSongBeatsAnAd() throws Exception {
		String musicPlaying = MUSIC_PAUSED.replace("paused", "playing");
		FakeRunner runner = new FakeRunner();
		MacMediaSource source = new MacMediaSource(() -> "", runner, 0);
		runner.answer(SPOTIFY_AD + musicPlaying);
		assertEquals("Song Two", source.poll().track().title());
		runner.answer(SPOTIFY_AD + musicPlaying);
		assertEquals("Song Two", source.poll().track().title(), "and stays with it");

		runner = new FakeRunner();
		source = new MacMediaSource(() -> "music", runner, 0);
		runner.answer(SPOTIFY_AD + MUSIC_PAUSED);
		assertEquals("Song Two", source.poll().track().title());
	}

	@Test
	void radioStreamTitleBecomesTheSong() {
		MacMediaSource.Reading r = MacMediaSource.parse("Music\tplaying\t\t\tKEXP 90.3\t\tKEXP\t\t\tBjork - Hyperballad - Live\n").players().getFirst();
		assertEquals("Bjork", r.track().artist());
		assertEquals("Hyperballad - Live", r.track().title());
		assertEquals("", r.track().album(), "the station stays out of the lookup");
		// no " - ": the whole line is the title
		r = MacMediaSource.parse("Music\tplaying\t\t\tStation\t\t\t\t\tJust A Title\n").players().getFirst();
		assertEquals("Just A Title", r.track().title());
		assertEquals("", r.track().artist());
		// "-" without spaces is not a separator
		assertEquals("Jay-Z", MacMediaSource.parse("Music\tplaying\t\t\tStation\t\t\t\t\tJay-Z\n").players().getFirst().track().title());
		// without a stream title, and with an artist, nothing changes
		r = MacMediaSource.parse("Music\tplaying\t\t\tStation\t\t\t\t\t\n").players().getFirst();
		assertEquals("Station", r.track().title());
		r = MacMediaSource.parse("Music\tpaused\t245.5\t12.5\tSong Two\tOther Band\tOther Album\t\t\tX - Y\n").players().getFirst();
		assertEquals("Song Two", r.track().title());
		assertEquals("Other Band", r.track().artist());
		assertEquals("Other Album", r.track().album());
	}

	@Test
	void scriptsReadTheNewFieldsOnTheirOwn() {
		String sp = MacMediaSource.SPOTIFY.readScript();
		assertTrue(sp.contains("set u to spotify url of t"));
		assertTrue(sp.contains("return {s, d, pos, n, ar, al, art, u}"));
		String mu = MacMediaSource.MUSIC.readScript();
		assertTrue(mu.contains("set streamLine to current stream title"));
		assertTrue(mu.contains("return {s, d, pos, n, ar, al, \"\", \"\", streamLine}"));
		// each read sits inside its own try
		assertTrue(sp.replace("\t", "").contains("try\nset u to spotify url of t\nend try"));
		assertTrue(mu.replace("\t", "").contains("try\nset streamLine to current stream title\nend try"));
	}

	/**
	 * The main script checks that the app runs, then compiles its part; an app that quits during that compile would be
	 * opened again by the first question ({@code tell application} launches an app that isn't running). So each part
	 * checks again itself, right before it asks anything.
	 */
	@Test
	void eachAppPartChecksTheAppStillRunsRightBeforeAsking() {
		for (MacMediaSource.App app : List.of(MacMediaSource.SPOTIFY, MacMediaSource.MUSIC)) {
			List<String> lines = app.readScript().lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
			assertEquals("if not (running of application \"" + app.name() + "\") then return {\"quit\"}", lines.getFirst());
			assertEquals("tell application \"" + app.name() + "\"", lines.get(1));
		}
		// what it prints then is no song and no error
		MacMediaSource.Answer a = MacMediaSource.parse("Spotify\tquit\n" + MUSIC_PAUSED);
		assertEquals(1, a.players().size());
		assertTrue(a.failures().isEmpty());
	}

	/** On a Mac (CI): Music's part, run while Music is closed, answers "quit" and leaves Music closed. */
	@Test
	void musicPartLeavesAClosedMusicClosed() throws Exception {
		assumeTrue(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac"), "macOS only");
		assumeTrue(!MacMediaSource.runningApps().contains("Music"), "Music is open on this Mac");
		assertEquals("quit", runPart(MacMediaSource.MUSIC.readScript()));
		assertFalse(MacMediaSource.runningApps().contains("Music"), "Music was opened");
	}

	/** The part the main script runs for this app, from one osascript command. */
	private static String part(List<String> command, String app) {
		List<String> args = args(command);
		return args.get(args.indexOf(app) + 1);
	}

	/**
	 * One osascript run reads Spotify, then Music. A frozen Spotify used to hold Music up until the poll gave up on the
	 * whole run (a minute), again and again: Music's song never showed. Now an app that has answered before gets a
	 * short time limit while the other app is asked too, and one that runs out of time is left out for a moment.
	 */
	@Test
	void aFrozenAppDoesNotHoldUpTheOtherOne() throws Exception {
		FakeRunner runner = new FakeRunner();
		MacMediaSource source = new MacMediaSource(() -> "", runner, 0);
		runner.answer(SPOTIFY_PLAYING + MUSIC_PAUSED);
		assertEquals("Song One", source.poll().track().title());

		runner.answer("Spotify\terror\t-1712\tSpotify got an error: AppleEvent timed out.\n" + MUSIC_PAUSED);
		Snapshot s = source.poll();
		String spotify = part(runner.commands.getLast(), "Spotify");
		assertTrue(spotify.startsWith("with timeout of " + MacMediaSource.ANSWER_LIMIT_S + " seconds\n") && spotify.endsWith("\nend timeout"), spotify);
		assertTrue(spotify.contains(MacMediaSource.SPOTIFY.readScript()));
		assertTrue(part(runner.commands.getLast(), "Music").startsWith("with timeout"));
		// Music's answer waited behind Spotify's, so when it was read is unknown: the last reading stays for now
		assertEquals("Song One", s.track().title());

		for (int i = 0; i < 3; i++) {
			runner.answer(MUSIC_PAUSED);
			assertEquals("Song Two", source.poll().track().title(), "Music, read on its own");
			assertFalse(args(runner.commands.getLast()).contains("Spotify"), "the frozen app is left out for a moment");
		}
		source.close(); // like Reconnect: everything is asked again at once
		runner.answer(SPOTIFY_PLAYING + MUSIC_PAUSED);
		assertEquals("Song One", source.poll().track().title());
	}

	/**
	 * No limit for an app that hasn't answered yet (macOS may be asking whether it may be controlled, and that waits
	 * for the player), nor for an app asked on its own (it holds nothing else up).
	 */
	@Test
	void noTimeLimitWhileMacOSMayAskOrForOneApp() throws Exception {
		FakeRunner runner = new FakeRunner();
		MacMediaSource source = new MacMediaSource(() -> "", runner, 0);
		runner.answer(SPOTIFY_PLAYING);
		source.poll();
		assertEquals(MacMediaSource.SPOTIFY.readScript(), part(runner.commands.getLast(), "Spotify"), "nothing answered yet");
		assertEquals(MacMediaSource.MUSIC.readScript(), part(runner.commands.getLast(), "Music"));
		runner.answer(SPOTIFY_PLAYING);
		source.poll();
		assertTrue(part(runner.commands.getLast(), "Spotify").startsWith("with timeout"), "Spotify answered, Music is asked too");
		assertEquals(MacMediaSource.MUSIC.readScript(), part(runner.commands.getLast(), "Music"), "Music still hasn't answered");

		runner = new FakeRunner();
		source = new MacMediaSource(() -> "", runner, 0, app -> app == MacMediaSource.SPOTIFY);
		runner.answer(SPOTIFY_PLAYING);
		source.poll();
		runner.answer(SPOTIFY_PLAYING);
		source.poll();
		assertEquals(MacMediaSource.SPOTIFY.readScript(), part(runner.commands.getLast(), "Spotify"), "alone, it holds nothing up");
	}

	/** On a Mac (CI): the time limit around an app part compiles and runs as the main script runs it. */
	@Test
	void limitedPartRunsOnAMac() throws Exception {
		assumeTrue(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac"), "macOS only");
		assumeTrue(!MacMediaSource.runningApps().contains("Music"), "Music is open on this Mac");
		assertEquals("quit", runPart(MacMediaSource.limited(MacMediaSource.MUSIC.readScript())));
		assertFalse(MacMediaSource.runningApps().contains("Music"), "Music was opened");
	}

	/** Runs an app part the way the main script does, with {@code run script}; returns what osascript printed. */
	private static String runPart(String part) throws Exception {
		Process p = new ProcessBuilder("/usr/bin/osascript", "-e", "on run argv", "-e", "return run script (item 1 of argv)",
				"-e", "end run", part).redirectErrorStream(true).start();
		p.getOutputStream().close();
		if (!p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) {
			p.destroyForcibly();
			throw new AssertionError("osascript didn't finish");
		}
		return new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip();
	}

	/** Every field the read scripts return is fetched inside its own try, so one missing field can't lose the reading. */
	@Test
	void everyFieldIsReadOnItsOwn() {
		for (String script : new String[] {MacMediaSource.SPOTIFY.readScript(), MacMediaSource.MUSIC.readScript()}) {
			String returned = script.lines().filter(l -> l.strip().startsWith("return {s,")).findFirst().orElseThrow();
			assertFalse(returned.contains(" of t"), "a field is read straight in the return list: " + returned);
		}
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
		source.close(); // forgets the refusal, as Reconnect does
		runner.answer("Music\terror\t-600\tApplication isn't running.\n");
		assertDoesNotThrow(source);
		runner.answer("Music\terror\t-10000\tSomething odd.\n");
		e = assertThrows(IOException.class, source::poll);
		assertTrue(e.getMessage().contains("Something odd"), e.getMessage());
	}

	/** macOS's refusal comes back as normal output (exit 0), so without a wait osascript would start every poll. */
	@Test
	void refusedAutomationIsNotAskedAgainForAWhile() {
		FakeRunner runner = new FakeRunner();
		MacMediaSource source = new MacMediaSource(() -> "", runner, 0, app -> app == MacMediaSource.SPOTIFY);
		runner.answer("Spotify\terror\t-1744\tNot authorized to send Apple events to Spotify.\n");
		for (int i = 0; i < 5; i++) {
			IOException e = assertThrows(IOException.class, source::poll);
			assertTrue(e.getMessage().contains("Allow") && e.getMessage().contains("Spotify"), e.getMessage());
		}
		assertEquals(1, runner.starts, "waits before asking again");

		source.close(); // like Reconnect: try again at once
		runner.answer(SPOTIFY_PLAYING);
		assertDoesNotThrow(source);
		assertEquals(2, runner.starts);
	}

	@Test
	void refusedAppDoesNotHideTheOtherOne() throws Exception {
		FakeRunner runner = new FakeRunner();
		MacMediaSource source = new MacMediaSource(() -> "", runner, 0);
		runner.answer("Spotify\terror\t-1743\tNot authorized\n" + MUSIC_PAUSED);
		assertEquals("Song Two", source.poll().track().title());
		runner.answer(MUSIC_PAUSED);
		assertEquals("Song Two", source.poll().track().title());
		assertEquals(List.of("Music"), args(runner.commands.get(1)).stream().filter(a -> a.equals("Spotify") || a.equals("Music")).toList(),
				"only the app that agreed is asked");
	}

	@Test
	void decomposedAccentsAreComposed() {
		MacMediaSource.Answer a = MacMediaSource.parse("Music\tpaused\t245.5\t12.5\tCafe\u0301\tBe\u0301yonce\u0301\tAlbum\t\n");
		Track t = a.players().get(0).track();
		assertEquals("Caf\u00e9", t.title());
		assertEquals("B\u00e9yonc\u00e9", t.artist());
	}

	@Test
	void runningAnswerIsRememberedForAMoment() {
		long[] now = {0};
		int[] asked = {0};
		java.util.function.Predicate<MacMediaSource.App> p = MacMediaSource.cached(app -> {
			asked[0]++;
			return true;
		}, () -> now[0], 3_000);
		assertTrue(p.test(MacMediaSource.SPOTIFY));
		assertTrue(p.test(MacMediaSource.SPOTIFY));
		assertEquals(1, asked[0], "one process walk per app per moment");
		assertTrue(p.test(MacMediaSource.MUSIC));
		assertEquals(2, asked[0], "each app has its own answer");
		now[0] = 3_000;
		assertTrue(p.test(MacMediaSource.SPOTIFY));
		assertEquals(3, asked[0], "asked again once it is old");
	}

	/** Polls every 500 ms for the given seconds on a fake clock; returns how many osascript runs started. */
	private static int runsOver(boolean[] looking, long seconds) throws Exception {
		FakeRunner runner = new FakeRunner();
		long[] now = {1_000_000_000L};
		MacMediaSource source = new MacMediaSource(() -> "", runner, 900_000_000L, app -> true, () -> looking[0], () -> now[0]);
		for (long t = 0; t < seconds * 2; t++) {
			runner.answer(SPOTIFY_PLAYING);
			source.poll();
			now[0] += 500_000_000L;
		}
		return runner.starts;
	}

	@Test
	void notLookingRunsOsascriptRarely() throws Exception {
		assertTrue(runsOver(new boolean[] {false}, 60) <= 6, "at most one run per 10 s while nobody looks");
	}

	@Test
	void lookingKeepsTheOldPace() throws Exception {
		// 500 ms polls, 0.9 s reuse: every second poll starts a run
		assertEquals(30, runsOver(new boolean[] {true}, 30));
	}

	@Test
	void lookingAgainReadsAtOnce() throws Exception {
		FakeRunner runner = new FakeRunner();
		long[] now = {1_000_000_000L};
		boolean[] looking = {false};
		MacMediaSource source = new MacMediaSource(() -> "", runner, 900_000_000L, app -> true, () -> looking[0], () -> now[0]);
		runner.answer(SPOTIFY_PLAYING);
		source.poll();
		now[0] += 300_000_000L;
		source.poll();
		assertEquals(1, runner.starts, "reused while not looking");
		now[0] += 300_000_000L; // still inside even the looking pace, so only the switch can cause a read
		looking[0] = true;
		runner.answer(SPOTIFY_PLAYING);
		source.poll();
		assertEquals(2, runner.starts, "the poll right after looking turns on reads again");
	}

	@Test
	void runningAnswerLivesLongerWhileNobodyLooks() {
		long[] now = {0};
		int[] scans = {0};
		boolean[] looking = {true};
		java.util.function.Predicate<MacMediaSource.App> p = MacMediaSource.scanned(() -> {
			scans[0]++;
			return java.util.Set.of("Spotify");
		}, () -> now[0], () -> MacMediaSource.ttlNs(looking[0]));
		assertTrue(p.test(MacMediaSource.SPOTIFY));
		assertFalse(p.test(MacMediaSource.MUSIC));
		assertEquals(1, scans[0], "one walk answers both apps");
		now[0] = 3_000_000_000L;
		p.test(MacMediaSource.SPOTIFY);
		assertEquals(2, scans[0], "looking: asked again after 3 s");
		looking[0] = false;
		now[0] = 3_000_000_000L + 14_000_000_000L;
		p.test(MacMediaSource.SPOTIFY);
		assertEquals(2, scans[0], "not looking: still remembered after 14 s");
		now[0] += 1_000_000_000L;
		p.test(MacMediaSource.SPOTIFY);
		assertEquals(3, scans[0], "not looking: asked again after 15 s");
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

	/**
	 * Spotify Web or the demo song is followed instead: an osascript still waiting (on macOS's permission question, or on
	 * a frozen app) is stopped instead of being left running for nobody, and the next poll asks again at once.
	 */
	@Test
	void standByStopsAWaitingOsascript() throws Exception {
		FakeRunner runner = new FakeRunner();
		MacMediaSource source = new MacMediaSource(() -> "", runner, 0);
		FakeRun slow = new FakeRun(new MacMediaSource.Result(0, "", ""), Integer.MAX_VALUE);
		runner.plans.add(slow);
		assertThrows(IOException.class, source::poll, "waiting");
		source.standBy();
		assertTrue(slow.killed, "the waiting osascript was left running");
		runner.answer(SPOTIFY_PLAYING);
		assertEquals("Song One", source.poll().track().title());
		assertEquals(2, runner.starts, "a fresh osascript");
	}

	private static void assertDoesNotThrow(MacMediaSource source) {
		org.junit.jupiter.api.Assertions.assertDoesNotThrow(source::poll);
	}

	@Test
	void readIsStampedWhenTheScriptRanNotWhenItReturned() {
		long ms = 1_000_000L;
		assertEquals(150 * ms, MacMediaSource.readStamp(100 * ms, 200 * ms), "a quick run: the middle");
		assertEquals(10_000 * ms - MacMediaSource.MAX_LAG_NS, MacMediaSource.readStamp(0, 10_000 * ms),
				"a run that waited on a permission question read near its end");
		assertEquals(5 * ms, MacMediaSource.readStamp(5 * ms, 5 * ms));
	}

	@Test
	void snapshotCarriesTheTimeTheScriptStarted() throws Exception {
		long ms = 1_000_000L;
		long[] inRun = new long[2]; // when the fake run was entered and left
		MacMediaSource.Run slowRun = new MacMediaSource.Run() {
			@Override
			public MacMediaSource.Result await(long waitMs) throws InterruptedException {
				inRun[0] = System.nanoTime();
				Thread.sleep(400); // osascript took 400 ms to start up, read and answer
				inRun[1] = System.nanoTime();
				return new MacMediaSource.Result(0, SPOTIFY_PLAYING, "");
			}

			@Override
			public void kill() {
			}
		};
		MacMediaSource source = new MacMediaSource(() -> "", command -> slowRun, 0);
		long before = System.nanoTime();
		Snapshot s = source.poll();
		long after = System.nanoTime();
		// The stamp is the midpoint of when the source saw the run start (in [before, inRun[0]]) and finish
		// (in [inRun[1], after]); these bounds need no guess about how slow the machine is.
		long lowest = (before + inRun[1]) / 2;
		// the source never stamps more than MAX_LAG_NS before the finish, so a slow run is clamped
		long highest = Math.max((inRun[0] + after) / 2, after - MacMediaSource.MAX_LAG_NS);
		assertTrue(s.readAtNanos() >= lowest, "not before the middle: " + (s.readAtNanos() - lowest) / ms + " ms");
		assertTrue(s.readAtNanos() <= highest, "not after the middle, e.g. at the end: " + (s.readAtNanos() - highest) / ms + " ms");
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
