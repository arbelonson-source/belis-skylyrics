package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class WindowsMediaSourceTest {
	private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
	/**
	 * Test machines can be slow to start PowerShell (antivirus scans, cold caches), and the build tests every
	 * Minecraft version at once, so four start together; the game uses 20 s and 4 s.
	 */
	private static final long START_MS = 150_000, REPLY_MS = 15_000;

	@TempDir
	Path dir;

	/** The helper is rewritten through a temp file of its own, tried again while Windows holds the file, and no temp file stays. */
	@Test
	void helperScriptIsWrittenThroughAUniqueTempFileWithRetries() throws Exception {
		Path script = dir.resolve("windows-media.ps1");
		Files.writeString(script, "old");
		int[] calls = {0};
		java.util.List<Path> temps = new java.util.ArrayList<>();
		WindowsMediaSource.writeScript(script, "new".getBytes(java.nio.charset.StandardCharsets.UTF_8), (from, to, opts) -> {
			temps.add(from);
			if (++calls[0] <= 2) {
				throw new java.nio.file.AccessDeniedException(to.toString());
			}
			Files.move(from, to, opts);
		});
		assertEquals(3, calls[0]);
		assertEquals("new", Files.readString(script));
		assertFalse(temps.get(0).getFileName().toString().equals("windows-media.ps1.tmp"), "a unique name, not the fixed one");
		try (var left = Files.list(dir)) {
			assertEquals(List.of(script), left.toList(), "no temp file left behind");
		}
	}

	@Test
	void playerNamesFromWindowsAppIds() {
		assertEquals("spotify", WindowsMediaSource.playerKey("Spotify.exe"));
		assertEquals("spotify", WindowsMediaSource.playerKey("SpotifyAB.SpotifyMusic_zpdnekdrzrea0!Spotify"));
		assertEquals("edge", WindowsMediaSource.playerKey("MSEdge"));
		assertEquals("chrome", WindowsMediaSource.playerKey("Chrome"));
		assertEquals("firefox", WindowsMediaSource.playerKey("308046B0AF4A39CB"));
		assertEquals("media player", WindowsMediaSource.playerKey("Microsoft.ZuneMusic_8wekyb3d8bbwe!Microsoft.ZuneMusic"));
		assertEquals("apple music", WindowsMediaSource.playerKey("AppleInc.AppleMusicWin_nzyj5cx40ttqa!App"));
		assertEquals("vlc", WindowsMediaSource.playerKey("vlc.exe"));
		assertEquals("someplayer", WindowsMediaSource.playerKey("C:\\Program Files\\Some Player\\SomePlayer.exe"));
		assertEquals("Media Player", WindowsMediaSource.prettyName("media player"));
		assertEquals("Someplayer", WindowsMediaSource.prettyName("someplayer"));
	}

	@Test
	void pathsAreQuotedForPowerShell() {
		assertEquals("'C:\\Users\\O''Brien\\[modpack] 1.21'", WindowsMediaSource.quote(Path.of("C:\\Users\\O'Brien\\[modpack] 1.21")));
		// PowerShell ends a single-quoted string at curly quotes too, so a folder like "Beli’s Pack" needs them doubled
		assertEquals("'D:\\Games\\Beli\u2019\u2019s Pack\\\u2018\u2018x\u2018\u2018\u201A\u201A\u201B\u201B'",
				WindowsMediaSource.quote(Path.of("D:\\Games\\Beli\u2019s Pack\\\u2018x\u2018\u201A\u201B")));
	}

	@Test
	void positionMovesOnWhilePlaying() {
		// Reported 61 s in, 2 s ago
		assertEquals(63_000, WindowsMediaSource.position(61_000, 1_000_000, true, 200_000, 1_002_000));
		// Paused: stays put
		assertEquals(61_000, WindowsMediaSource.position(61_000, 1_000_000, false, 200_000, 1_002_000));
		// Never past the end, never below zero, and no guessing without a timestamp
		assertEquals(200_000, WindowsMediaSource.position(199_000, 1_000_000, true, 200_000, 1_500_000));
		assertEquals(0, WindowsMediaSource.position(-50, WindowsMediaSource.NO_TIME, true, 0, 1_000));
		assertEquals(61_000, WindowsMediaSource.position(61_000, WindowsMediaSource.NO_TIME, true, 200_000, 1_002_000));
	}

	@Test
	void positionFollowsPlaybackSpeed() {
		// Reported 61 s in, 2 s ago, at 1.5x: 3 s of song have passed
		assertEquals(64_000, WindowsMediaSource.position(61_000, 1_000_000, true, 200_000, 1_002_000, 1.5));
		assertEquals(65_000, WindowsMediaSource.position(61_000, 1_000_000, true, 200_000, 1_002_000, 2.0));
		// Paused: speed doesn't matter
		assertEquals(61_000, WindowsMediaSource.position(61_000, 1_000_000, false, 200_000, 1_002_000, 2.0));
		// Silly speeds count as normal
		for (double silly : new double[] {0, -1, 4.5, 100, Double.NaN, Double.POSITIVE_INFINITY}) {
			assertEquals(63_000, WindowsMediaSource.position(61_000, 1_000_000, true, 200_000, 1_002_000, silly), "rate " + silly);
		}
		assertEquals(4.0, WindowsMediaSource.saneRate(4.0));
	}

	@Test
	void readsPlaybackSpeedFromPollAnswers() {
		JsonObject reply = JsonParser.parseString("""
				{"seq":3,"ok":true,"sessions":[
				 {"id":"MSEdge","title":"Fast","status":"Playing","positionMs":10000,"startMs":0,"endMs":0,"updatedMs":1000,"rate":1.5},
				 {"id":"Spotify.exe","title":"Plain","status":"Playing","positionMs":10000,"startMs":0,"endMs":0,"updatedMs":1000},
				 {"id":"Chrome","title":"Odd","status":"Playing","positionMs":10000,"startMs":0,"endMs":0,"updatedMs":1000,"rate":"fast"}
				]}""").getAsJsonObject();
		List<WindowsMediaSource.Session> s = WindowsMediaSource.sessions(reply, 3000);
		assertEquals(1.5, s.get(0).rate());
		assertEquals(13_000, s.get(0).positionMs());
		assertEquals(1.0, s.get(1).rate());
		assertEquals(12_000, s.get(1).positionMs());
		assertEquals(1.0, s.get(2).rate());
	}

	@Test
	void helperErrorsComeFromCodes() {
		assertEquals("Windows' media controls aren't available (boom)", WindowsMediaSource.helperError(
				JsonParser.parseString("{\"ready\":false,\"code\":\"unavailable\",\"detail\":\"boom\",\"error\":\"unavailable: boom\"}").getAsJsonObject()));
		assertEquals("Windows' media controls stopped answering (late)", WindowsMediaSource.helperError(
				JsonParser.parseString("{\"ok\":false,\"code\":\"stopped\",\"detail\":\"late\"}").getAsJsonObject()));
		assertEquals("Nothing is playing", WindowsMediaSource.helperError(
				JsonParser.parseString("{\"ok\":false,\"error\":\"Nothing is playing\"}").getAsJsonObject()));
		assertEquals("", WindowsMediaSource.helperError(JsonParser.parseString("{\"ok\":false}").getAsJsonObject()));
	}

	@Test
	void keepsFollowingTheSameAppWhenAnEarlierPlayerCloses() {
		Track t = new Track("A", "", "", 0, "", "chrome");
		// Followed Spotify#2; the player before it closed, so Spotify is #1 now and Edge (playing) is #0
		List<WindowsMediaSource.Session> sessions = List.of(
				new WindowsMediaSource.Session(0, "MSEdge", t, 0, true, 1.0),
				new WindowsMediaSource.Session(1, "Spotify.exe", t, 0, true, 1.0));
		assertEquals("Spotify.exe#1", WindowsMediaSource.followKey(sessions, "Spotify.exe#2"));
		// The exact key is still there: unchanged
		assertEquals("MSEdge#0", WindowsMediaSource.followKey(sessions, "MSEdge#0"));
		// Two tabs of one app: prefer the one playing
		List<WindowsMediaSource.Session> tabs = List.of(
				new WindowsMediaSource.Session(0, "Chrome", t, 0, false, 1.0),
				new WindowsMediaSource.Session(1, "Chrome", t, 0, true, 1.0));
		assertEquals("Chrome#1", WindowsMediaSource.followKey(tabs, "Chrome#5"));
		// The app is gone, or nothing followed yet
		assertEquals("Gone.exe#0", WindowsMediaSource.followKey(sessions, "Gone.exe#0"));
		assertEquals(null, WindowsMediaSource.followKey(sessions, null));
		// And the picker then sticks with it, as it does for an exact key
		assertEquals("Spotify.exe#1", PlayerPicker.choose(sessions, WindowsMediaSource.Session::key, s -> "x",
				WindowsMediaSource.Session::playing, "", WindowsMediaSource.followKey(sessions, "Spotify.exe#2")).key());
	}

	@Test
	void theFirstStartMayTakeLonger() {
		assertEquals(45_000, WindowsMediaSource.startLimitMs(20_000, false));
		assertEquals(20_000, WindowsMediaSource.startLimitMs(20_000, true));
		assertEquals(150_000, WindowsMediaSource.startLimitMs(150_000, false), "never shortens a longer limit");
	}

	@Test
	void readsPollAnswers() {
		JsonObject reply = JsonParser.parseString("""
				{"seq":3,"ok":true,"sessions":[
				 {"id":"Spotify.exe","title":"Song One","artist":"Made Up Band","album":"Made Up Album","status":"Playing",
				  "positionMs":11000,"startMs":1000,"endMs":201000,"updatedMs":5000,"cover":""},
				 {"id":"MSEdge","title":"Made Up Band - Video Song (Official Video)","artist":"Made Up Band","status":"Paused",
				  "positionMs":5000,"startMs":0,"endMs":0,"updatedMs":0,"cover":"/covers/cover-1.img"},
				 {"id":"Silent.exe","title":"","status":"Playing"},
				 "junk"
				]}""").getAsJsonObject();
		List<WindowsMediaSource.Session> sessions = WindowsMediaSource.sessions(reply, 6000);
		assertEquals(2, sessions.size());

		WindowsMediaSource.Session spotify = sessions.get(0);
		assertTrue(spotify.playing());
		assertEquals(11_000, spotify.positionMs(), "counted from the start time, plus the second since the report");
		assertEquals(new Track("Song One", "Made Up Band", "Made Up Album", 200_000, "", "spotify"), spotify.track());

		WindowsMediaSource.Session edge = sessions.get(1);
		assertFalse(edge.playing());
		assertEquals("Video Song", edge.track().title(), "browser titles are cleaned like on Linux");
		assertEquals("edge", edge.track().player());
		assertTrue(edge.track().artUrl().startsWith("file:"), edge.track().artUrl());
		assertEquals(0, edge.track().lengthMs());
	}

	@Test
	void backsOffWhenTheHelperIsGoneSoonAfterStarting() {
		long s = 1_000_000_000L;
		assertTrue(WindowsMediaSource.shouldBackOff(false, s, s + 2_000_000_000L), "died after 2 s");
		assertFalse(WindowsMediaSource.shouldBackOff(false, s, s + 11_000_000_000L), "ran for a while first");
		assertFalse(WindowsMediaSource.shouldBackOff(true, s, s + 2_000_000_000L), "still running");
	}

	@Test
	void retryDelayGrowsAndIsCapped() {
		long s = 1_000_000_000L;
		assertEquals(15 * s, WindowsMediaSource.retryDelayNs(0));
		assertEquals(15 * s, WindowsMediaSource.retryDelayNs(1));
		assertEquals(30 * s, WindowsMediaSource.retryDelayNs(2));
		assertEquals(60 * s, WindowsMediaSource.retryDelayNs(3));
		assertEquals(120 * s, WindowsMediaSource.retryDelayNs(4));
		assertEquals(240 * s, WindowsMediaSource.retryDelayNs(5));
		assertEquals(300 * s, WindowsMediaSource.retryDelayNs(6));
		assertEquals(300 * s, WindowsMediaSource.retryDelayNs(7));
		assertEquals(300 * s, WindowsMediaSource.retryDelayNs(1000));
		assertEquals(300 * s, WindowsMediaSource.retryDelayNs(Integer.MAX_VALUE));
	}

	@Test
	void lastLogLineIsTheLastNonEmptyLine() throws Exception {
		Path log = dir.resolve("windows-media.log");
		assertEquals("", WindowsMediaSource.lastLogLine(log), "no file");
		Files.writeString(log, "");
		assertEquals("", WindowsMediaSource.lastLogLine(log), "empty file");
		Files.writeString(log, "first\r\nCannot run: blocked by policy  \r\n\r\n   \n");
		assertEquals(" (Cannot run: blocked by policy)", WindowsMediaSource.lastLogLine(log));
		Files.write(log, "\uFEFFh\0e\0y\0\n\0".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		assertEquals(" (hey)", WindowsMediaSource.lastLogLine(log), "UTF-16 look-alike");
	}

	/** A new helper must not inherit the old one's unanswered questions, or it would be thrown away after one miss. */
	@Test
	@Timeout(60)
	void missedRepliesDoNotCarryOverToANewHelper() throws Exception {
		assumeTrue(!WINDOWS && Files.isExecutable(Path.of("/bin/sh")), "needs a POSIX shell for the stand-in helper");
		int[] starts = {0};
		String body = "printf '{\"ready\":true}\\n'; while read l; do :; done";
		WindowsMediaSource source = new WindowsMediaSource(() -> "", dir, (script, covers) -> {
			starts[0]++;
			return List.of("/bin/sh", "-c", body);
		}, START_MS, 300);
		try {
			assertThrows(IOException.class, source::poll);
			assertThrows(IOException.class, source::poll); // two misses on the first helper
			source.close();
			assertThrows(IOException.class, source::poll); // one miss on the second
			assertThrows(IOException.class, source::poll); // two: must still be the same helper
			assertEquals(2, starts[0]);
		} finally {
			source.close();
		}
	}

	/**
	 * A helper PowerShell stopped at once says why in windows-media.log, and the error carries that last line; failed
	 * starts are counted, and a helper that answers resets the count.
	 */
	@Test
	@Timeout(60)
	void aBlockedHelperIsNamedInTheErrorAndCountedUntilOneAnswers() throws Exception {
		assumeTrue(!WINDOWS && Files.isExecutable(Path.of("/bin/sh")), "needs a POSIX shell for the stand-in helper");
		int[] starts = {0};
		String blocked = "echo blocked >&2; exit 1";
		String good = "printf '{\"ready\":true}\\n'; while read n cmd rest; do "
				+ "printf '{\"seq\":%s,\"ok\":true,\"sessions\":[{\"index\":0,\"id\":\"Spotify.exe\",\"title\":\"Song\",\"status\":\"Playing\",\"positionMs\":1000,\"startMs\":0,\"endMs\":0,\"updatedMs\":0}]}\\n' \"$n\"; done";
		WindowsMediaSource source = new WindowsMediaSource(() -> "", dir, (script, covers) -> {
			return List.of("/bin/sh", "-c", ++starts[0] <= 2 ? blocked : good);
		}, START_MS, REPLY_MS);
		try {
			IOException first = assertThrows(IOException.class, source::poll);
			assertTrue(first.getMessage().contains("(blocked)"), first.getMessage());
			assertEquals(1, source.failuresInARow());
			source.retryNow();
			IOException second = assertThrows(IOException.class, source::poll);
			assertTrue(second.getMessage().contains("(blocked)"), second.getMessage());
			assertEquals(2, source.failuresInARow());
			source.retryNow();
			assertEquals("Song", source.poll().track().title());
			assertEquals(0, source.failuresInARow(), "a helper that answers starts the count over");
			assertEquals(3, starts[0]);
		} finally {
			source.close();
		}
	}

	/** A helper that answers "ok":false and exits at once must not be started again on every poll. */
	@Test
	@Timeout(60)
	void doesNotRestartAHelperThatKeepsExiting() throws Exception {
		assumeTrue(!WINDOWS && Files.isExecutable(Path.of("/bin/sh")), "needs a POSIX shell for the stand-in helper");
		int[] starts = {0};
		String body = "printf '{\"ready\":true}\\n'; read line; printf '{\"seq\":1,\"ok\":false,\"error\":\"gone\"}\\n'; exit 2";
		WindowsMediaSource source = new WindowsMediaSource(() -> "", dir, (script, covers) -> {
			starts[0]++;
			return List.of("/bin/sh", "-c", body);
		}, 20_000, 5_000);
		try {
			assertThrows(IOException.class, source::poll);
			for (int i = 0; i < 4; i++) {
				assertThrows(IOException.class, source::poll);
			}
			assertEquals(1, starts[0], "started once, then waited");
		} finally {
			source.close();
		}
	}

	/** Runs the real helper script against a made-up media manager (windows-media-fake.ps1). */
	@Test
	@Timeout(300)
	void talksToTheHelper() throws Exception {
		String shell = powershell();
		assumeTrue(shell != null, "PowerShell isn't installed");
		Path fake = Path.of("src/test/resources/windows-media-fake.ps1").toAbsolutePath();
		String[] preferred = {""};
		WindowsMediaSource source = new WindowsMediaSource(() -> preferred[0], dir, (script, covers) -> List.of(
				shell, "-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
				"-File", fake.toString(), "-Script", script.toString(), "-CoverDir", covers.toString()), START_MS, REPLY_MS);
		try {
			long before = System.currentTimeMillis();
			Snapshot s = source.poll();
			long took = System.currentTimeMillis() - before;
			assertEquals("Song One", s.track().title(), "follows the player that is playing");
			assertTrue(s.playing());
			// The fake's song was at 61 s when PowerShell started it, and has kept playing since
			assertTrue(s.positionMs() >= 61_000 && s.positionMs() <= 61_000 + took + 1_000, "position " + s.positionMs() + " after " + took + " ms");
			assertEquals("Spotify", source.displayName());
			assertTrue(Files.isRegularFile(dir.resolve("windows-media.ps1")));

			source.playPause();
			s = source.poll();
			assertEquals("Song One", s.track().title(), "keeps following it once paused");
			assertFalse(s.playing());

			source.seek(5_000);
			assertEquals(5_000, source.poll().positionMs());

			preferred[0] = "edge";
			s = source.poll();
			assertEquals("Video Song", s.track().title());
			assertEquals("Edge", source.displayName());
			// Both Edge tabs are called "MSEdge"; this must skip the song in the tab being followed, not the other one
			source.next();
			assertEquals("Song Two", source.poll().track().title());

			// The helper went away (say, killed by hand): the next poll starts a fresh one
			source.close();
			assertEquals("Video Song", source.poll().track().title());
		} finally {
			source.close();
		}
	}

	/** A source running the real script against the made-up manager in one of its scenarios. */
	private WindowsMediaSource fakeSource(String shell, String scenario, Path flag, long replyMs) {
		Path fake = Path.of("src/test/resources/windows-media-fake.ps1").toAbsolutePath();
		return new WindowsMediaSource(() -> "", dir, (script, covers) -> List.of(
				shell, "-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
				"-File", fake.toString(), "-Script", script.toString(), "-CoverDir", covers.toString(),
				"-Scenario", scenario, "-Flag", flag.toString()), START_MS, replyMs);
	}

	/**
	 * Covers that never open or never finish copying must not make a poll late: the game gives up on a helper
	 * after three polls that take over 4 s. The limits here are looser than the game's (10 s to answer, 6 s per poll)
	 * because GitHub's Windows machine runs this test for all nine versions at once and a poll there once took 3.8 s;
	 * a cover without its time limit would still hold a poll for ever and fail.
	 */
	@Test
	@Timeout(300)
	void coversThatHangNeverMakeAPollLate() throws Exception {
		String shell = powershell();
		assumeTrue(shell != null, "PowerShell isn't installed");
		WindowsMediaSource source = fakeSource(shell, "slowcovers", dir.resolve("none"), 10_000);
		try {
			source.poll(); // the helper starts here
			long end = System.currentTimeMillis() + 9_000; // long enough for every cover to be tried and given up on
			int polls = 0;
			while (System.currentTimeMillis() < end || polls < 3) {
				long before = System.currentTimeMillis();
				Snapshot s = source.poll();
				long took = System.currentTimeMillis() - before;
				assertEquals("Slow Song A", s.track().title());
				assertTrue(took < 30_000, "a poll took " + took + " ms");
				polls++;
				Thread.sleep(300);
			}
		} finally {
			source.close();
		}
	}

	/** A player that is slow to answer once keeps showing its last reading for that one poll, and no longer. */
	@Test
	@Timeout(300)
	void aSlowPlayerIsShownOnceMoreThenDropped() throws Exception {
		String shell = powershell();
		assumeTrue(shell != null, "PowerShell isn't installed");
		WindowsMediaSource source = fakeSource(shell, "slowprops", dir.resolve("none"), REPLY_MS);
		try {
			assertEquals("Slow Props Song", source.poll().track().title());
			assertEquals("Slow Props Song", source.poll().track().title(), "late once: the last reading is kept");
			assertSame(Snapshot.NOTHING, source.poll(), "late twice in a row: gone");
		} finally {
			source.close();
		}
	}

	/** A cover read right after the song changed can be the old song's; it is read again a few seconds later. */
	@Test
	@Timeout(300)
	void aCoverIsReadAgainAfterAFewSeconds() throws Exception {
		String shell = powershell();
		assumeTrue(shell != null, "PowerShell isn't installed");
		WindowsMediaSource source = fakeSource(shell, "newcover", dir.resolve("none"), REPLY_MS);
		try {
			String first = null, latest = null;
			long end = System.currentTimeMillis() + 90_000;
			while (System.currentTimeMillis() < end && !"new cover".equals(latest)) {
				String art = source.poll().track().artUrl();
				if (!art.isEmpty()) {
					latest = Files.readString(Path.of(java.net.URI.create(art)));
					if (first == null) {
						first = latest;
					}
				}
				Thread.sleep(400);
			}
			assertEquals("old cover", first, "the first read");
			assertEquals("new cover", latest, "the second read replaces it");
		} finally {
			source.close();
		}
	}

	/** When the followed app is gone, a pause must not land on another app. */
	@Test
	@Timeout(300)
	void controlsNeverReachAnotherApp() throws Exception {
		String shell = powershell();
		assumeTrue(shell != null, "PowerShell isn't installed");
		Path flag = dir.resolve("followed-is-gone");
		WindowsMediaSource source = fakeSource(shell, "vanish", flag, REPLY_MS);
		try {
			assertEquals("Followed Song", source.poll().track().title());
			Files.writeString(flag, "gone");
			source.playPause(); // would have toggled Windows' pick, the other app
			source.next();
			Snapshot s = source.poll();
			assertEquals("Other Song", s.track().title());
			assertFalse(s.playing(), "the other app was left alone");
			assertEquals(2_000, s.positionMs());
		} finally {
			source.close();
		}
	}

	/**
	 * The helper answering "ok":false to a button press (the followed app is gone) is not an error worth showing,
	 * and doesn't count as the helper being stuck or make it restart.
	 */
	@Test
	@Timeout(60)
	void aRefusedControlIsQuietAndKeepsTheHelper() throws Exception {
		assumeTrue(!WINDOWS && Files.isExecutable(Path.of("/bin/sh")), "needs a POSIX shell for the stand-in helper");
		int[] starts = {0};
		String body = "printf '{\"ready\":true}\\n'; while read n cmd rest; do "
				+ "case \"$cmd\" in poll) printf '{\"seq\":%s,\"ok\":true,\"sessions\":[{\"index\":0,\"id\":\"Spotify.exe\",\"title\":\"Song\",\"status\":\"Playing\",\"positionMs\":1000,\"startMs\":0,\"endMs\":0,\"updatedMs\":0}]}\\n' \"$n\";; "
				+ "*) printf '{\"seq\":%s,\"ok\":false,\"error\":\"That player is not there any more\"}\\n' \"$n\";; esac; done";
		WindowsMediaSource source = new WindowsMediaSource(() -> "", dir, (script, covers) -> {
			starts[0]++;
			return List.of("/bin/sh", "-c", body);
		}, 20_000, 5_000);
		try {
			assertEquals("Song", source.poll().track().title());
			for (int i = 0; i < 5; i++) {
				source.playPause();
				source.next();
				source.previous();
				source.seek(1000);
			}
			assertEquals("Song", source.poll().track().title(), "still the same helper, still answering");
			assertEquals(1, starts[0]);
		} finally {
			source.close();
		}
	}

	/** Away from Windows the helper can't load the Windows media API; the error says so, and we don't retry at once. */
	@Test
	@Timeout(300)
	void explainsWhenWindowsMediaIsMissing() {
		String shell = powershell();
		assumeTrue(shell != null && !WINDOWS, "needs PowerShell on a system without the Windows media API");
		AtomicInteger starts = new AtomicInteger();
		// Started the way the game starts it on Windows
		WindowsMediaSource source = new WindowsMediaSource(() -> "", dir,
				(script, covers) -> {
					starts.incrementAndGet();
					return WindowsMediaSource.command(shell, script, covers);
				}, START_MS, REPLY_MS);
		IOException first = assertThrows(IOException.class, source::poll);
		assertTrue(first.getMessage().contains("media controls aren't available"), first.getMessage());
		IOException again = assertThrows(IOException.class, source::poll);
		assertEquals(first.getMessage(), again.getMessage());
		assertEquals(1, starts.get(), "started once, then waited instead of starting PowerShell again");
		source.close();
	}

	/** On Windows itself (CI), the real thing: starts the helper and asks what's playing. */
	@Test
	@Timeout(300)
	void readsTheRealWindowsMediaControls() throws Exception {
		assumeTrue(WINDOWS, "Windows only");
		WindowsMediaSource source = new WindowsMediaSource(() -> "", dir, WindowsMediaSource::powershell, START_MS, REPLY_MS);
		try {
			source.poll(); // nothing may be playing; it just must not fail
		} finally {
			source.close();
		}
	}

	private static @Nullable String powershell() {
		String fromEnv = System.getenv("SKYLYRICS_PWSH");
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv;
		}
		List<String> names = WINDOWS ? List.of("powershell.exe", "pwsh.exe") : List.of("pwsh");
		for (String name : names) {
			for (String entry : System.getenv().getOrDefault("PATH", "").split(File.pathSeparator)) {
				Path exe = Path.of(entry).resolve(name);
				if (!entry.isEmpty() && Files.isExecutable(exe)) {
					return exe.toString();
				}
			}
		}
		return null;
	}

	/** One poll answer with one player. */
	private static JsonObject poll(String status, long positionMs, long updatedMs) {
		return JsonParser.parseString("""
				{"seq":1,"ok":true,"sessions":[{"id":"Spotify.exe","title":"Song","status":"%s","positionMs":%d,"startMs":0,"endMs":200000,"updatedMs":%d}]}"""
				.formatted(status, positionMs, updatedMs)).getAsJsonObject();
	}

	/** One poll answer with one player that never says when it updated, playing the given song. */
	private static JsonObject pollSong(String title, long positionMs) {
		return JsonParser.parseString("""
				{"seq":1,"ok":true,"sessions":[{"id":"Browser","title":"%s","status":"Playing","positionMs":%d,"startMs":0,"endMs":200000,"updatedMs":0}]}"""
				.formatted(title, positionMs)).getAsJsonObject();
	}

	/** Runs polls through sessions() the way poll() does, and returns the position each one gives. */
	private static long[] positions(Object... pollsAndTimes) {
		Map<String, WindowsMediaSource.Seen> seen = new HashMap<>();
		long[] out = new long[pollsAndTimes.length / 2];
		for (int i = 0; i < out.length; i++) {
			Map<String, WindowsMediaSource.Seen> next = new HashMap<>();
			out[i] = WindowsMediaSource.sessions((JsonObject) pollsAndTimes[2 * i], (Long) pollsAndTimes[2 * i + 1], seen, next).getFirst().positionMs();
			seen = next;
		}
		return out;
	}

	@Test
	void aReportOlderThanTheSteadyClockStillMovesTheSong() {
		// Just after boot the steady clock is small: a 60 s old report puts the count start below zero
		Map<String, WindowsMediaSource.Seen> next = new HashMap<>();
		long wall = 1_700_000_000_000L;
		long pos = WindowsMediaSource.sessions(poll("Playing", 60_000, wall - 60_000), wall, 10_000L, Map.of(), next).getFirst().positionMs();
		assertEquals(120_000, pos, "60 s reported, 60 s old");
		Map<String, WindowsMediaSource.Seen> after = new HashMap<>();
		long later = WindowsMediaSource.sessions(poll("Playing", 60_000, wall - 60_000), wall + 2_000, 12_000L, next, after).getFirst().positionMs();
		assertEquals(122_000, later, "keeps moving on the next poll");
	}

	@Test
	void resumingBeforeTheUpdateTimeIsRewrittenDoesNotAddThePause() {
		// Paused at 60 s since t=1000 (updatedMs); polls at 40 s and 41 s; resumes with updatedMs still 1000
		long[] p = positions(
				poll("Paused", 60_000, 1_000), 40_000L,
				poll("Paused", 60_000, 1_000), 41_000L,
				poll("Playing", 60_000, 1_000), 42_000L, // flipped, update time still old: must not jump to 101 s
				poll("Playing", 60_000, 1_000), 43_000L); // keeps counting from the flip
		assertEquals(60_000, p[1]);
		assertEquals(60_000, p[2], "the pause is not added");
		assertEquals(61_000, p[3]);
	}

	@Test
	void aResumeThatRewritesTheUpdateTimeCountsFromIt() {
		long[] p = positions(
				poll("Paused", 60_000, 1_000), 40_000L,
				poll("Playing", 60_000, 41_500), 42_000L, // written 500 ms before this poll, after the one that saw it paused
				poll("Playing", 60_000, 41_500), 43_000L);
		assertEquals(60_500, p[1]);
		assertEquals(61_500, p[2]);
	}

	@Test
	void aPlayerWithNoUpdateTimeIsCountedFromWhenWeSawThePosition() {
		long[] p = positions(
				poll("Playing", 60_000, 0), 10_000L,
				poll("Playing", 60_000, 0), 12_000L,
				poll("Playing", 60_000, 0), 13_500L,
				poll("Playing", 90_000, 0), 14_000L); // the player moved it (seek): start over
		assertEquals(60_000, p[0]);
		assertEquals(62_000, p[1], "was frozen at 60 s");
		assertEquals(63_500, p[2]);
		assertEquals(90_000, p[3]);
	}

	@Test
	void aNewSongOnAPlayerWithNoUpdateTimeStartsItsOwnCount() {
		long[] p = positions(
				pollSong("First", 0), 10_000L,
				pollSong("First", 0), 70_000L,
				pollSong("Second", 0), 71_000L, // the next song, and the player still says 0
				pollSong("Second", 0), 72_000L);
		assertEquals(60_000, p[1]);
		assertEquals(0, p[2], "the new song starts at its beginning");
		assertEquals(1_000, p[3]);
	}

	@Test
	void normalPlayingStillCountsFromTheUpdateTime() {
		long[] p = positions(
				poll("Playing", 60_000, 5_000), 6_000L,
				poll("Playing", 60_000, 5_000), 8_000L);
		assertEquals(61_000, p[0]);
		assertEquals(63_000, p[1]);
	}

	@Test
	void onlyACoverWhoseHelperIsGoneIsStale() {
		java.util.function.LongFunction<String> runs = pid -> pid == 4242 ? "C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\PowerShell.EXE" : null;
		assertFalse(WindowsMediaSource.staleCover("cover-4242-3.img", runs, 0), "another game's helper still runs");
		assertTrue(WindowsMediaSource.staleCover("cover-777-1.img", runs, 0), "its helper is gone");
		assertFalse(WindowsMediaSource.staleCover("other.img", runs, 60_000), "not ours and not old: left alone");
		assertTrue(WindowsMediaSource.staleCover("other.img", runs, 25L * 3600 * 1000), "a day old");
		assertFalse(WindowsMediaSource.staleCover("cover-99999999999999999999-1.img", runs, 1000));
	}

	@Test
	void aReusedProcessIdThatIsNotPowerShellDoesNotKeepACover() {
		assertTrue(WindowsMediaSource.staleCover("cover-4242-3.img", pid -> "C:\\Program Files\\Chrome\\chrome.exe", 0));
		assertFalse(WindowsMediaSource.staleCover("cover-4242-3.img", pid -> "C:\\x\\pwsh.exe", 0));
		assertFalse(WindowsMediaSource.staleCover("cover-4242-3.img", pid -> "", 60_000), "program unknown: kept while fresh");
		assertTrue(WindowsMediaSource.staleCover("cover-4242-3.img", pid -> "", 25L * 3600 * 1000), "program unknown: gone after a day");
	}

	private static JsonObject playingReply() {
		return JsonParser.parseString("{\"ok\":true,\"sessions\":[{\"index\":0,\"id\":\"Spotify.exe\",\"title\":\"Song\",\"status\":\"Playing\","
				+ "\"positionMs\":1000,\"startMs\":0,\"endMs\":0,\"updatedMs\":0}]}").getAsJsonObject();
	}

	/** Polls every 500 ms for the given seconds on a fake clock; returns how many questions the helper was asked. */
	private int asksOver(boolean[] looking, long seconds) throws Exception {
		int[] asks = {0};
		long[] now = {1_000_000_000L};
		WindowsMediaSource source = new WindowsMediaSource(() -> "", dir, WindowsMediaSource::powershell, START_MS, REPLY_MS,
				() -> looking[0], () -> now[0], cmd -> {
					asks[0]++;
					return playingReply();
				});
		for (long t = 0; t < seconds * 2; t++) {
			assertEquals("Song", source.poll().track().title());
			now[0] += 500_000_000L;
		}
		return asks[0];
	}

	@Test
	void aReadThatFinishesAfterCloseIsNotKeptForTheNextPolls() throws Exception {
		int[] asks = {0};
		long[] now = {1_000_000_000L};
		WindowsMediaSource[] box = new WindowsMediaSource[1];
		WindowsMediaSource source = new WindowsMediaSource(() -> "", dir, WindowsMediaSource::powershell, START_MS, REPLY_MS,
				() -> false, () -> now[0], cmd -> {
					if (asks[0]++ == 0) {
						box[0].close(); // the game closes the source while the first question is still being answered
					}
					return playingReply();
				});
		box[0] = source;
		source.poll();
		now[0] += 500_000_000L;
		source.poll(); // not looking, 0.5 s on: would reuse the 15 s reading, but that one began before the close
		assertEquals(2, asks[0]);
	}

	@Test
	void notLookingAsksTheHelperOncePer15Seconds() throws Exception {
		assertEquals(4, asksOver(new boolean[] {false}, 60));
	}

	@Test
	void lookingAsksOnEveryPoll() throws Exception {
		assertEquals(60, asksOver(new boolean[] {true}, 30));
	}

	@Test
	void notLookingReturnsTheSameReading() throws Exception {
		long[] now = {1_000_000_000L};
		WindowsMediaSource source = new WindowsMediaSource(() -> "", dir, WindowsMediaSource::powershell, START_MS, REPLY_MS,
				() -> false, () -> now[0], cmd -> playingReply());
		Snapshot first = source.poll();
		now[0] += 500_000_000L;
		assertSame(first, source.poll(), "the reading keeps its own time stamp, so the position is not counted twice");
	}

	@Test
	void lookingAgainAsksAtOnce() throws Exception {
		int[] asks = {0};
		long[] now = {1_000_000_000L};
		boolean[] looking = {false};
		WindowsMediaSource source = new WindowsMediaSource(() -> "", dir, WindowsMediaSource::powershell, START_MS, REPLY_MS,
				() -> looking[0], () -> now[0], cmd -> {
					asks[0]++;
					return playingReply();
				});
		source.poll();
		now[0] += 500_000_000L;
		source.poll();
		assertEquals(1, asks[0]);
		looking[0] = true;
		now[0] += 500_000_000L;
		source.poll();
		assertEquals(2, asks[0], "the first poll after looking turns on asks again");
	}

	@Test
	void aFailedAskIsTriedAgainOnTheNextPollEvenWhileNotLooking() throws Exception {
		int[] asks = {0};
		long[] now = {1_000_000_000L};
		WindowsMediaSource source = new WindowsMediaSource(() -> "", dir, WindowsMediaSource::powershell, START_MS, REPLY_MS,
				() -> false, () -> now[0], cmd -> {
					if (++asks[0] <= 2) {
						throw new IOException("no helper");
					}
					return playingReply();
				});
		assertThrows(IOException.class, source::poll);
		now[0] += 500_000_000L;
		assertThrows(IOException.class, source::poll);
		now[0] += 500_000_000L;
		assertEquals("Song", source.poll().track().title());
		assertEquals(3, asks[0], "a failure is not reused: the helper's own back-off decides how often it really starts");
	}
}
