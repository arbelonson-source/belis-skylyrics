package dev.beli.skylyrics.media;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;

import dev.beli.skylyrics.Tr;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the playing song on macOS straight from the Spotify and Music apps.
 *
 * <p>macOS has no public "what's playing" API (the private MediaRemote one is closed to other apps since 15.4), so
 * we ask the two players that matter most ourselves, with AppleScript through osascript. Browsers and other players
 * can't be seen this way. An app that isn't running is never asked anything, because asking would open it; and
 * whether it runs is checked from Java, since even asking AppleScript about an app that isn't installed can pop up
 * a "Where is Spotify?" window.
 *
 * <p>One osascript run per poll reads both apps and prints one tab-separated line per running app (see
 * {@link #parse}). The first time, macOS asks whether Minecraft may control the app, and osascript waits until
 * the player answers that question.
 */
public final class MacMediaSource implements MediaSource {
	private static final Logger LOG = LoggerFactory.getLogger("SkyLyrics");
	private static final String OSASCRIPT = "/usr/bin/osascript";
	/** Longest a poll or a button press waits for osascript. */
	private static final long WAIT_MS = 3_000;
	/**
	 * A poll that takes longer is usually macOS asking for permission. Killing osascript then could take the
	 * question away before the player can click OK, so it is left to finish, up to this long.
	 */
	private static final long GIVE_UP_NS = 60_000_000_000L;
	/** After osascript is missing or keeps failing, wait before trying again. */
	private static final long RETRY_NS = 15_000_000_000L;
	private static final int MAX_FAILS = 3;
	/** Every read starts a process; the tracker's smooth clock fills the gaps, so about once a second is plenty. */
	private static final long MIN_POLL_NS = 900_000_000L;
	/** errAEEventNotPermitted / errAEEventWouldRequireUserConsent: Automation permission was refused. */
	private static final int NOT_ALLOWED = -1743;
	private static final int NEEDS_CONSENT = -1744;
	/** The app quit, lost its connection or had no track just as we asked: nothing worth showing. */
	private static final List<Integer> HARMLESS = List.of(-600, -609, -1712, -1728);

	/**
	 * Runs the app parts given as (app name, script text) pairs in its arguments, each only if that app is
	 * running. They are compiled with {@code run script} on the spot, because a script naming Spotify's
	 * own words doesn't even compile on a Mac without Spotify. Tabs and line breaks in titles become spaces.
	 */
	private static final List<String> MAIN = List.of(
			"on run argv",
			"	set out to \"\"",
			"	repeat with i from 1 to (count of argv) by 2",
			"		set appName to item i of argv",
			"		set isOpen to false",
			"		try",
			"			set isOpen to running of application appName",
			"		end try",
			"		if isOpen then",
			"			set rec to appName",
			"			try",
			"				set fields to run script (item (i + 1) of argv)",
			"				repeat with v in fields",
			"					set rec to rec & tab & my clean(contents of v)",
			"				end repeat",
			"			on error msg number num",
			"				set rec to appName & tab & \"error\" & tab & num & tab & my clean(msg)",
			"			end try",
			"			set out to out & rec & linefeed",
			"		end if",
			"	end repeat",
			"	return out",
			"end run",
			"on clean(v)",
			"	if v is missing value then return \"\"",
			"	set v to v as text",
			"	set saved to AppleScript's text item delimiters",
			"	repeat with c in {tab, return, linefeed}",
			"		set AppleScript's text item delimiters to contents of c",
			"		set parts to text items of v",
			"		set AppleScript's text item delimiters to \" \"",
			"		set v to parts as text",
			"	end repeat",
			"	set AppleScript's text item delimiters to saved",
			"	return v",
			"end clean");

	/** Spotify: duration in milliseconds, position in seconds. */
	private static final String SPOTIFY_READ = """
			tell application "Spotify"
				set p to player state
				if p is playing then
					set s to "playing"
				else if p is paused then
					set s to "paused"
				else
					return {"stopped"}
				end if
				set t to current track
				return {s, duration of t, player position, name of t, artist of t, album of t, artwork url of t}
			end tell""";

	/** Music: duration and position in seconds; streams may have neither. No cover link, only image data. */
	private static final String MUSIC_READ = """
			tell application "Music"
				set p to player state
				if p is paused then
					set s to "paused"
				else if p is stopped then
					return {"stopped"}
				else
					set s to "playing"
				end if
				set t to current track
				set d to 0
				try
					set d to duration of t
				end try
				set pos to 0
				try
					set pos to player position
				end try
				return {s, d, pos, name of t, artist of t, album of t, ""}
			end tell""";

	/** An app we can ask. {@code name} is the app's own name, as AppleScript and System Settings use it. */
	record App(String name, String key, String displayName, long msPerDurationUnit, String readScript) {
	}

	static final App SPOTIFY = new App("Spotify", "spotify", "Spotify", 1, SPOTIFY_READ);
	static final App MUSIC = new App("Music", "apple music", "Apple Music", 1000, MUSIC_READ);
	private static final List<App> APPS = List.of(SPOTIFY, MUSIC);

	/** One app's answer. */
	record Reading(App app, Track track, long positionMs, boolean playing) {
	}

	/** An app that was running but answered with an AppleScript error. */
	record Failure(App app, int code, String message) {
	}

	record Answer(List<Reading> players, List<Failure> failures) {
		static final Answer EMPTY = new Answer(List.of(), List.of());
	}

	/** What osascript said when it finished. */
	record Result(int exitCode, String out, String err) {
	}

	/** A started osascript. */
	interface Run {
		/** The result, or null if it hasn't finished within {@code ms}. */
		@Nullable Result await(long ms) throws InterruptedException;

		void kill();
	}

	/** How to start osascript; tests swap in a fake so they don't need a Mac. */
	interface Runner {
		Run start(List<String> command) throws IOException;
	}

	private final Supplier<String> preferredPlayer;
	private final Runner runner;
	private final long minPollNs;
	private final Predicate<App> running;

	/** The osascript that hasn't finished yet, if any. */
	private volatile @Nullable Run pending;
	private long pendingSince;
	private volatile int fails;
	private volatile long retryAt;
	private String retryError = "";
	private Answer last = Answer.EMPTY;
	private long lastAt;
	private volatile boolean haveLast;
	private @Nullable App current;
	private volatile String currentDisplayName = "";

	public MacMediaSource(Supplier<String> preferredPlayer) {
		this(preferredPlayer, MacMediaSource::startProcess, MIN_POLL_NS, MacMediaSource::isRunning);
	}

	/** @param minPollNs how long a reading is reused for; tests use 0 */
	MacMediaSource(Supplier<String> preferredPlayer, Runner runner, long minPollNs) {
		this(preferredPlayer, runner, minPollNs, app -> true);
	}

	MacMediaSource(Supplier<String> preferredPlayer, Runner runner, long minPollNs, Predicate<App> running) {
		this.preferredPlayer = preferredPlayer;
		this.runner = runner;
		this.minPollNs = minPollNs;
		this.running = running;
	}

	/** Looks for the app's program among this user's processes; needs no permission and opens nothing. */
	static boolean isRunning(App app) {
		String program = "/" + app.name() + ".app/Contents/MacOS/" + app.name();
		return ProcessHandle.allProcesses().anyMatch(p -> p.info().command().map(c -> c.endsWith(program)).orElse(false));
	}

	@Override
	public synchronized Snapshot poll() throws Exception {
		if (!haveLast || System.nanoTime() - lastAt >= minPollNs) {
			List<String> args = new ArrayList<>();
			for (App app : APPS) {
				if (running.test(app)) {
					args.add(app.name());
					args.add(app.readScript());
				}
			}
			boolean late = pending != null;
			if (args.isEmpty() && !late) {
				last = Answer.EMPTY; // neither app is open: nothing to ask, no osascript to start
				lastAt = System.nanoTime();
				haveLast = true;
			} else {
				String out = osascript(args, true);
				last = parse(out);
				lastAt = System.nanoTime();
				haveLast = !late; // a late answer may be old: read again next time
			}
		}
		Reading chosen = PlayerPicker.choose(last.players(), r -> r.app().name(), r -> r.track().player(), Reading::playing,
				preferredPlayer.get(), current == null ? null : current.name());
		if (chosen == null) {
			current = null;
			for (Failure f : last.failures()) {
				if (f.code() == NOT_ALLOWED || f.code() == NEEDS_CONSENT) {
					throw new IOException(Tr.t("Allow Minecraft to control %s in System Settings → Privacy & Security → Automation", f.app().name()));
				}
			}
			for (Failure f : last.failures()) {
				if (!HARMLESS.contains(f.code())) {
					throw new IOException(f.app().name() + ": " + f.message() + " (" + f.code() + ")");
				}
			}
			return Snapshot.NOTHING;
		}
		current = chosen.app();
		currentDisplayName = chosen.app().displayName();
		return new Snapshot(chosen.track(), chosen.positionMs(), chosen.playing(), lastAt);
	}

	/**
	 * Reads what the script prints. Each line is one running app: its name and state, then for a song
	 * duration, position, title, artist, album and cover link, separated by tabs; or its name, "error",
	 * the AppleScript error number and message.
	 */
	static Answer parse(String output) {
		List<Reading> players = new ArrayList<>();
		List<Failure> failures = new ArrayList<>();
		for (String line : output.split("\n")) {
			String[] f = line.split("\t", -1);
			if (f.length < 2) {
				continue;
			}
			App app = app(field(f, 0));
			if (app == null) {
				continue;
			}
			String state = field(f, 1);
			if (state.equals("error")) {
				failures.add(new Failure(app, (int) number(field(f, 2)), field(f, 3)));
				continue;
			}
			boolean playing = state.equals("playing");
			if (!playing && !state.equals("paused")) {
				continue; // stopped, or the answer to a button press
			}
			String title = field(f, 4);
			if (title.isEmpty()) {
				continue;
			}
			long length = Math.max(0, Math.round(number(field(f, 2)) * app.msPerDurationUnit()));
			long position = Math.max(0, Math.round(number(field(f, 3)) * 1000));
			if (length > 0) {
				position = Math.min(position, length);
			}
			Track track = new Track(title, field(f, 5), field(f, 6), length, field(f, 7), app.key());
			players.add(new Reading(app, TitleCleaner.clean(track), position, playing));
		}
		return new Answer(players, failures);
	}

	private static @Nullable App app(String name) {
		for (App app : APPS) {
			if (app.name().equals(name)) {
				return app;
			}
		}
		return null;
	}

	/** A field without stray control characters (the script already turned tabs and line breaks into spaces). */
	private static String field(String[] f, int i) {
		return i < f.length ? f[i].replaceAll("\\p{Cntrl}", "").strip() : "";
	}

	/** AppleScript writes numbers the Mac's way: "12.5", "12,5" in many languages, "1.0E+9" when big. */
	static double number(String s) {
		s = s.strip();
		if (s.indexOf('.') < 0) {
			s = s.replace(',', '.');
		}
		try {
			double d = Double.parseDouble(s);
			return Double.isFinite(d) ? d : 0;
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/**
	 * Runs osascript with {@link #MAIN} and these arguments and returns what it printed.
	 *
	 * @param mayWait a poll that is slow is probably waiting on the permission question, so keep it; a slow
	 *                button press is just stuck
	 */
	private String osascript(List<String> args, boolean mayWait) throws IOException, InterruptedException {
		Run run = pending;
		if (run == null) {
			if (System.nanoTime() < retryAt) {
				throw new IOException(retryError);
			}
			try {
				run = runner.start(command(args));
			} catch (IOException | RuntimeException e) {
				throw backOff("Couldn't start osascript: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
			}
			pending = run;
			pendingSince = System.nanoTime();
		}
		Result result = run.await(WAIT_MS);
		if (result == null) {
			if (!mayWait || System.nanoTime() - pendingSince > GIVE_UP_NS) {
				run.kill();
				pending = null;
				throw failed("osascript didn't answer in time");
			}
			throw new IOException(Tr.t("Waiting for Spotify or Music. If macOS asks to let Minecraft control them, click OK"));
		}
		pending = null;
		if (result.exitCode() != 0) {
			String err = result.err().strip();
			int newline = err.indexOf('\n');
			throw failed("osascript failed: " + (newline > 0 ? err.substring(0, newline) : err.isEmpty() ? "exit code " + result.exitCode() : err));
		}
		fails = 0;
		return result.out();
	}

	private IOException failed(String message) {
		if (++fails < MAX_FAILS) {
			return new IOException(message);
		}
		return backOff(message);
	}

	private IOException backOff(String message) {
		fails = 0;
		retryError = message;
		retryAt = System.nanoTime() + RETRY_NS;
		LOG.warn("Couldn't read Spotify or Music: {}", message);
		return new IOException(message);
	}

	static List<String> command(List<String> args) {
		List<String> command = new ArrayList<>();
		command.add(OSASCRIPT);
		for (String line : MAIN) {
			command.add("-e");
			command.add(line);
		}
		command.addAll(args);
		return command;
	}

	/** Runs the main script with no app to ask, to check that it works on this Mac. Tests only. */
	String checkScript() throws IOException, InterruptedException {
		return osascript(List.of(), false);
	}

	private static Run startProcess(List<String> command) throws IOException {
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.environment().put("LANG", "en_US.UTF-8"); // so titles come out as UTF-8
		Process p = builder.start();
		p.getOutputStream().close();
		CompletableFuture<Result> done = new CompletableFuture<>();
		Thread reader = new Thread(() -> {
			try {
				String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
				String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
				done.complete(new Result(p.waitFor(), out, err));
			} catch (IOException | InterruptedException e) {
				done.complete(new Result(-1, "", e.toString()));
			}
		}, "SkyLyrics osascript");
		reader.setDaemon(true);
		reader.start();
		return new Run() {
			@Override
			public @Nullable Result await(long ms) throws InterruptedException {
				try {
					return done.get(ms, TimeUnit.MILLISECONDS);
				} catch (TimeoutException e) {
					return null;
				} catch (ExecutionException e) {
					return new Result(-1, "", e.toString());
				}
			}

			@Override
			public void kill() {
				p.destroyForcibly();
			}
		};
	}

	/** Sends a command to the app being followed; the app is left alone if it has quit since. */
	private synchronized void control(String command) {
		App app = current;
		if (app == null || pending != null) {
			return;
		}
		try {
			osascript(List.of(app.name(), "tell application \"" + app.name() + "\" to " + command + "\nreturn {\"done\"}"), false);
		} catch (Exception e) {
			LOG.debug("Player control failed: {}", e.toString());
		}
		haveLast = false; // show the change on the next poll
	}

	@Override
	public void playPause() {
		control("playpause");
	}

	@Override
	public void next() {
		control("next track");
	}

	@Override
	public void previous() {
		control("previous track");
	}

	@Override
	public void seek(long positionMs) {
		// Whole numbers and a division, so the script holds no decimal point to get wrong
		control(String.format(Locale.ROOT, "set player position to (%d / 1000)", Math.max(0, positionMs)));
	}

	@Override
	public String displayName() {
		return currentDisplayName;
	}

	/** Stops a waiting osascript; the next poll asks again straight away. Safe from any thread. */
	@Override
	public void close() {
		Run run = pending;
		pending = null;
		if (run != null) {
			run.kill();
		}
		retryAt = 0;
		fails = 0;
		haveLast = false;
	}
}
