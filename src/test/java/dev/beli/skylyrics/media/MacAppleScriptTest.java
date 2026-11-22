package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * On a Mac (GitHub's macOS build), the real scripts: the main script with its {@code clean} handler, the app parts and
 * the button presses go through the real /usr/bin/osascript, started exactly as a poll starts it ({@link
 * MacMediaSource#command}, {@link MacMediaSource#startProcess}). A script that doesn't compile or reads wrong fails the
 * build here. Spotify isn't installed on a test Mac and Music is closed, so Finder (always open on a Mac) stands in for a
 * running app, with made-up parts. Everything here skips itself off a Mac.
 */
class MacAppleScriptTest {
	private static final String FINDER = "Finder";
	/** A stand-in part starts like the real ones: an app that quit meanwhile is not asked anything. */
	private static final String FINDER_STILL_OPEN = "if not (running of application \"Finder\") then return {\"quit\"}\n";

	private static void assumeMac() {
		assumeTrue(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac"), "macOS only");
	}

	private static void assumeMusicClosed() {
		assumeTrue(!MacMediaSource.runningApps().contains("Music"), "Music is open on this Mac");
	}

	private static void assumeFinderOpen() {
		boolean open = ProcessHandle.allProcesses()
				.anyMatch(p -> p.info().command().orElse("").endsWith("/Finder.app/Contents/MacOS/Finder"));
		assumeTrue(open, "Finder isn't running on this Mac");
	}

	/** Runs the main script with these (app name, part) pairs, as a poll does, and waits for it. */
	private static MacMediaSource.Result osascript(List<String> args) throws Exception {
		MacMediaSource.Run run = MacMediaSource.startProcess(MacMediaSource.command(args));
		MacMediaSource.Result result = run.await(60_000);
		if (result == null) {
			run.kill();
			throw new AssertionError("osascript didn't finish within a minute");
		}
		assertEquals(0, result.exitCode(), "osascript failed: " + result.err());
		return result;
	}

	/** The lines the main script printed (one per running app), without the empty line osascript ends with. */
	private static List<String> lines(MacMediaSource.Result result) {
		return Arrays.stream(result.out().split("\n")).filter(l -> !l.isEmpty()).toList();
	}

	/** A line from the stand-in app, as {@link MacMediaSource#parse} would read it from Music. */
	private static MacMediaSource.Answer parseAsMusic(String finderLine) {
		assertTrue(finderLine.startsWith(FINDER + "\t"), finderLine);
		return MacMediaSource.parse("Music" + finderLine.substring(FINDER.length()) + "\n");
	}

	@Test
	void mainWithNoAppsPrintsNothing() throws Exception {
		assumeMac();
		MacMediaSource.Result result = osascript(List.of());
		assertEquals("", result.out().strip(), "nothing to ask, nothing printed");
	}

	/**
	 * Music's real part, plain and with the time limit: while Music is closed the main script doesn't run it at all, and
	 * run for an app that is open (Finder) the part itself sees that Music is closed. Either way Music stays closed.
	 */
	@Test
	void mainLeavesAClosedMusicClosed() throws Exception {
		assumeMac();
		assumeMusicClosed();
		assumeFinderOpen();
		for (String part : List.of(MacMediaSource.MUSIC.readScript(), MacMediaSource.limited(MacMediaSource.MUSIC.readScript()))) {
			assertEquals(List.of(), lines(osascript(List.of("Music", part))), "Music is closed: its part isn't run");
			assertEquals(List.of("Finder\tquit"), lines(osascript(List.of(FINDER, part))), "the part checks again itself");
		}
		assertFalse(MacMediaSource.runningApps().contains("Music"), "Music was opened");
	}

	/** A whole poll, as the game makes it, with Music taken for open although it is closed: nothing, and no error. */
	@Test
	void aRealPollWhileMusicIsClosedShowsNothing() throws Exception {
		assumeMac();
		assumeMusicClosed();
		MacMediaSource source = new MacMediaSource(() -> "", MacMediaSource::startProcess, 0, app -> app == MacMediaSource.MUSIC);
		try {
			Snapshot s = null;
			// A cold start can take longer than one poll waits; the poll says so and the next one picks it up
			for (int i = 0; s == null; i++) {
				try {
					s = source.poll();
				} catch (IOException e) {
					if (i >= 20 || !e.getMessage().startsWith("Waiting")) {
						throw e;
					}
				}
			}
			assertSame(Snapshot.NOTHING, s);
		} finally {
			source.close();
		}
		assertFalse(MacMediaSource.runningApps().contains("Music"), "Music was opened");
	}

	/**
	 * What a part returns comes back as one tab-separated line: tabs and line breaks inside a field become spaces, missing
	 * value becomes empty, decimals are written the Mac's way (a dot or a comma, as this Mac's language has it), and
	 * Hebrew and emoji arrive whole. Made inside the script, as a player's titles are, not passed in from Java.
	 */
	@Test
	void mainCleansTheFieldsAPartReturns() throws Exception {
		assumeMac();
		assumeFinderOpen();
		String part = FINDER_STILL_OPEN + "return {\"playing\", 245.5, 61.25, "
				+ "\"Tab\" & tab & \"and\" & linefeed & \"line\" & return & \"breaks\", "
				+ "(character id 1513) & (character id 1497) & (character id 1512) & \" \" & (character id 127925), "
				+ "\"Album\" & return & linefeed & \"Two\", missing value, \"\", \"\"}";
		List<String> lines = lines(osascript(List.of(FINDER, part)));
		assertEquals(1, lines.size(), "one line for the one app: " + lines);
		String line = lines.getFirst();
		String[] f = line.split("\t", -1);
		assertEquals(10, f.length, "the app's name and the 9 fields, no tab left inside one: " + line);
		assertEquals(FINDER, f[0]);
		assertEquals("playing", f[1]);
		assertTrue(f[2].equals("245.5") || f[2].equals("245,5"), f[2]);
		assertTrue(f[3].equals("61.25") || f[3].equals("61,25"), f[3]);
		assertEquals("Tab and line breaks", f[4]);
		assertEquals("שיר 🎵", f[5], "Hebrew and an emoji, read as UTF-8");
		assertEquals("Album  Two", f[6], "a return and a line feed: a space each");
		assertEquals("", f[7], "missing value");
		assertEquals("", f[8]);
		assertEquals("", f[9]);

		MacMediaSource.Answer answer = parseAsMusic(line);
		assertEquals(List.of(), answer.failures());
		assertEquals(1, answer.players().size());
		MacMediaSource.Reading r = answer.players().getFirst();
		assertTrue(r.playing());
		assertEquals(245_500, r.track().lengthMs(), "Music's seconds, with the decimals");
		assertEquals(61_250, r.positionMs());
		assertEquals("Tab and line breaks", r.track().title());
		assertEquals("שיר 🎵", r.track().artist());
		assertEquals("Album Two", r.track().album());
		assertEquals("", r.track().artUrl());
	}

	/** A part that throws, or doesn't even compile (as Music's did with "st"), comes back as an error line, not as nothing. */
	@Test
	void aPartThatFailsComesBackAsAnErrorLine() throws Exception {
		assumeMac();
		assumeFinderOpen();
		String boom = FINDER_STILL_OPEN + "error (\"boom\" & tab & \"and\" & linefeed & \"more\") number -1712";
		List<String> lines = lines(osascript(List.of(FINDER, boom)));
		assertEquals(List.of("Finder\terror\t-1712\tboom and more"), lines);
		MacMediaSource.Answer answer = parseAsMusic(lines.getFirst());
		assertEquals(List.of(new MacMediaSource.Failure(MacMediaSource.MUSIC, -1712, "boom and more")), answer.failures());
		assertEquals(List.of(), answer.players());

		lines = lines(osascript(List.of(FINDER, FINDER_STILL_OPEN + "return {\"playing\", }")));
		assertEquals(1, lines.size(), lines.toString());
		String[] f = lines.getFirst().split("\t", -1);
		assertEquals(4, f.length, lines.getFirst());
		assertEquals("error", f[1], lines.getFirst());
		assertEquals("-2741", f[2], "a syntax error: " + lines.getFirst());
		MacMediaSource.Failure failure = parseAsMusic(lines.getFirst()).failures().getFirst();
		assertEquals(-2741, failure.code());
		assertFalse(failure.message().isEmpty());
	}

	/**
	 * The time limit ({@link MacMediaSource#limited}) on a real Mac: an app that answers is read as usual; an app that
	 * takes longer than the limit to answer comes back as error -1712 once the limit is up, which the source takes for a
	 * frozen app. A {@code delay} is no slow answer: since Mac OS X 10.6 osascript runs it itself even inside a
	 * {@code tell} (Apple's AppleScript release notes), so the limit never cuts it; a dialog shown by the app is one.
	 *
	 * <p>GitHub's Mac only: it shows a dialog in Finder for a few seconds. One at a time across the 9 versions' test runs,
	 * since Finder answers nothing else while its dialog is up.
	 */
	@Test
	void theTimeLimitCutsAnAppThatIsSlowToAnswer() throws Exception {
		assumeMac();
		assumeTrue("true".equals(System.getenv("CI")), "shows a dialog in Finder: on GitHub's Mac only");
		assumeFinderOpen();
		String answers = FINDER_STILL_OPEN + "tell application \"Finder\"\n\tset n to name\nend tell\nreturn {n}";
		String delays = FINDER_STILL_OPEN + "tell application \"Finder\"\n\tdelay 2.5\nend tell\nreturn {\"stopped\"}";
		String slow = FINDER_STILL_OPEN + "tell application \"Finder\"\n"
				+ "\tdisplay dialog \"SkyLyrics test: this closes by itself\" buttons {\"OK\"} giving up after 3\n"
				+ "end tell\nreturn {\"stopped\"}";
		withFinderToMyself(() -> {
			try {
				assertEquals(List.of("Finder\tFinder"), lines(osascript(List.of(FINDER, MacMediaSource.limited(answers)))),
						"Finder answers within the limit (and may be asked at all)");
				assertAll(
						() -> {
							long start = System.nanoTime();
							List<String> out = lines(osascript(List.of(FINDER, MacMediaSource.limited(delays))));
							assertEquals(List.of("Finder\tstopped"), out, "a delay inside a tell, after "
									+ TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) + " ms");
						},
						() -> {
							long start = System.nanoTime();
							List<String> out = lines(osascript(List.of(FINDER, MacMediaSource.limited(slow))));
							long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
							assertEquals(1, out.size(), out.toString());
							assertTrue(out.getFirst().startsWith("Finder\terror\t-1712\t"), "after " + ms + " ms: " + out);
							MacMediaSource.Failure failure = parseAsMusic(out.getFirst()).failures().getFirst();
							assertEquals(-1712, failure.code());
						});
			} finally {
				waitUntilFinderAnswers(answers);
			}
			return null;
		});
	}

	/** Finder's dialog stays up a moment after the limit: wait (a while at most) until Finder answers again. */
	private static void waitUntilFinderAnswers(String answers) throws InterruptedException {
		long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
		while (System.nanoTime() < until) {
			try {
				if (lines(osascript(List.of(FINDER, MacMediaSource.limited(answers)))).equals(List.of("Finder\tFinder"))) {
					return;
				}
			} catch (Exception | AssertionError e) {
				// not yet
			}
			Thread.sleep(200);
		}
	}

	/** Holds a lock shared by every test JVM on this Mac while {@code body} talks to Finder. */
	private static void withFinderToMyself(Callable<Void> body) throws Exception {
		Path lockFile = Path.of(System.getProperty("java.io.tmpdir"), "skylyrics-finder-test.lock");
		try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
				FileLock lock = channel.lock()) {
			body.call();
		}
	}

	/**
	 * The button presses, built by the source itself for Music: each compiles against Music's own words (osacompile, which
	 * doesn't open Music), and run as a button press runs them while Music is closed, they ask nothing and leave Music
	 * closed. Spotify's are the same words for another app, and can't be compiled on a Mac without Spotify.
	 */
	@Test
	void theButtonScriptsCompileAndLeaveAClosedMusicClosed() throws Exception {
		assumeMac();
		assumeMusicClosed();
		List<List<String>> commands = new ArrayList<>();
		Deque<String> answers = new ArrayDeque<>(List.of("Music\tpaused\t245.5\t12.5\tSong\tBand\tAlbum\t\n"));
		MacMediaSource.Runner runner = command -> {
			commands.add(command);
			String out = answers.isEmpty() ? "Music\tdone\n" : answers.poll();
			return new MacMediaSource.Run() {
				@Override
				public MacMediaSource.Result await(long ms) {
					return new MacMediaSource.Result(0, out, "");
				}

				@Override
				public void kill() {
				}
			};
		};
		MacMediaSource source = new MacMediaSource(() -> "", runner, 0, app -> app == MacMediaSource.MUSIC);
		assertEquals("Song", source.poll().track().title(), "Music is followed");
		commands.clear();
		source.playPause();
		source.next();
		source.previous();
		source.seek(61_250);
		assertEquals(4, commands.size(), "one osascript per button press");

		Path dir = Files.createTempDirectory("skylyrics-osacompile");
		Path compiled = dir.resolve("button.scpt");
		try {
			for (List<String> command : commands) {
				List<String> args = command.subList(command.size() - 2, command.size());
				assertEquals("Music", args.getFirst());
				String part = args.get(1);
				List<String> compile = new ArrayList<>(List.of("/usr/bin/osacompile", "-o", compiled.toString()));
				for (String line : part.split("\n")) {
					compile.add("-e");
					compile.add(line);
				}
				Process p = new ProcessBuilder(compile).redirectErrorStream(true).start();
				p.getOutputStream().close();
				if (!p.waitFor(60, TimeUnit.SECONDS)) {
					p.destroyForcibly();
					throw new AssertionError("osacompile didn't finish: " + part);
				}
				String said = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
				assertEquals(0, p.exitValue(), "doesn't compile: " + part + "\n" + said);

				MacMediaSource.Result result = osascript(args);
				assertEquals(List.of(), lines(result), "Music is closed: the button press asks nothing");
			}
		} finally {
			Files.deleteIfExists(compiled);
			Files.deleteIfExists(dir);
		}
		assertFalse(MacMediaSource.runningApps().contains("Music"), "Music was opened");
	}
}
