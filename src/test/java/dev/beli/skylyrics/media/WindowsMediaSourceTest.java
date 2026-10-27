package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

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
	}

	@Test
	void positionMovesOnWhilePlaying() {
		// Reported 61 s in, 2 s ago
		assertEquals(63_000, WindowsMediaSource.position(61_000, 1_000_000, true, 200_000, 1_002_000));
		// Paused: stays put
		assertEquals(61_000, WindowsMediaSource.position(61_000, 1_000_000, false, 200_000, 1_002_000));
		// Never past the end, never below zero, and no guessing without a timestamp
		assertEquals(200_000, WindowsMediaSource.position(199_000, 1_000_000, true, 200_000, 1_500_000));
		assertEquals(0, WindowsMediaSource.position(-50, 0, true, 0, 1_000));
		assertEquals(61_000, WindowsMediaSource.position(61_000, 0, true, 200_000, 1_002_000));
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

	/** Away from Windows the helper can't load the Windows media API; the error says so, and we don't retry at once. */
	@Test
	@Timeout(300)
	void explainsWhenWindowsMediaIsMissing() {
		String shell = powershell();
		assumeTrue(shell != null && !WINDOWS, "needs PowerShell on a system without the Windows media API");
		// Started the way the game starts it on Windows
		WindowsMediaSource source = new WindowsMediaSource(() -> "", dir,
				(script, covers) -> WindowsMediaSource.command(shell, script, covers), START_MS, REPLY_MS);
		IOException first = assertThrows(IOException.class, source::poll);
		assertTrue(first.getMessage().contains("media controls aren't available"), first.getMessage());
		long before = System.nanoTime();
		IOException again = assertThrows(IOException.class, source::poll);
		assertEquals(first.getMessage(), again.getMessage());
		assertTrue(System.nanoTime() - before < 500_000_000L, "waits before starting PowerShell again");
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
}
