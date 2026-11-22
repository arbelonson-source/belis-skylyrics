package dev.beli.skylyrics.media;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
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
 * {@link #parse}). The first time, macOS asks whether the launcher (the app that started the game, such as Minecraft or Prism Launcher) may control the app, and osascript waits until
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
	/**
	 * How long "is the app open" is remembered. Walking every process is the costly part of a poll. A wrong answer
	 * is harmless: an app that quit meanwhile is skipped by the script's own {@code running of application} check
	 * (which never launches anything), and one that just opened is noticed a moment later.
	 */
	private static final long RUNNING_TTL_NS = 3_000_000_000L;
	/** The same, while nothing on screen shows the song: nobody notices an app being noticed 15 s late. */
	static final long RUNNING_QUIET_TTL_NS = 15_000_000_000L;
	/** Every read starts a process; the tracker's smooth clock fills the gaps, so about once a second is plenty. */
	private static final long MIN_POLL_NS = 900_000_000L;
	/** While nothing on screen shows the song, a reading is reused this long (the Spotify Web poller's quiet pace). */
	static final long QUIET_POLL_NS = SpotifyPace.QUIET_NS;
	/** errAEEventNotPermitted / errAEEventWouldRequireUserConsent: Automation permission was refused. */
	private static final int NOT_ALLOWED = -1743;
	private static final int NEEDS_CONSENT = -1744;
	/** errAETimeout: the app didn't answer within {@link #ANSWER_LIMIT_S}. */
	private static final int TIMED_OUT = -1712;
	/**
	 * Longest one question may take an app that has answered before, while the other app is asked in the same run, so a
	 * frozen app holds the other one up only this long instead of until the poll gives up on the whole run. Not for an
	 * app that hasn't answered yet: macOS may be asking the player whether it may be controlled, and that waits for them.
	 */
	static final int ANSWER_LIMIT_S = 2;
	/** An app that ran out of time is left out this long, so it can't keep holding the other one up. */
	private static final long FROZEN_NS = 5_000_000_000L;
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

	/**
	 * Spotify: duration in milliseconds, position in seconds; the last field is the song's spotify: address ("spotify:ad:..." for an ad). Each field is read on its own, so one that is missing (the cover of a local file or podcast) comes back empty and the rest still arrives.
	 *
	 * <p>The first line checks again that the app runs (it answers "quit" if not): the main script's check comes before
	 * this part is compiled, and an app that quit meanwhile would be opened again by the first question.
	 */
	private static final String SPOTIFY_READ = """
			if not (running of application "Spotify") then return {"quit"}
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
				set d to ""
				set pos to ""
				set n to ""
				set ar to ""
				set al to ""
				set art to ""
				set u to ""
				try
					set d to duration of t
				end try
				try
					set pos to player position
				end try
				try
					set n to name of t
				end try
				try
					set ar to artist of t
				end try
				try
					set al to album of t
				end try
				try
					set art to artwork url of t
				end try
				try
					set u to spotify url of t
				end try
				return {s, d, pos, n, ar, al, art, u}
			end tell""";

	/**
	 * Music: duration and position in seconds; streams may have neither. No cover link, only image data and no
	 * address, so those two fields are empty. The last field is an internet radio station's "Artist - Song" line. The
	 * first line checks again that Music runs, as in Spotify's part.
	 */
	private static final String MUSIC_READ = """
			if not (running of application "Music") then return {"quit"}
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
				set n to ""
				set ar to ""
				set al to ""
				try
					set n to name of t
				end try
				try
					set ar to artist of t
				end try
				try
					set al to album of t
				end try
				set streamLine to ""
				try
					set streamLine to current stream title
				end try
				return {s, d, pos, n, ar, al, "", "", streamLine}
			end tell""";

	/** An app part with {@link #ANSWER_LIMIT_S} on every question it asks (AppleScript's own limit is two minutes). */
	static String limited(String part) {
		return "with timeout of " + ANSWER_LIMIT_S + " seconds\n" + part + "\nend timeout";
	}

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

	/**
	 * @param ads      apps playing something that is no song (a Spotify ad): no title, but that app is still the one playing
	 * @param answered apps that said what they play (or that they are stopped)
	 */
	record Answer(List<Reading> players, List<Failure> failures, List<Reading> ads, Set<App> answered) {
		static final Answer EMPTY = new Answer(List.of(), List.of(), List.of(), Set.of());
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
	private final BooleanSupplier looking;
	/** Pacing clock; tests swap it. */
	private final LongSupplier clock;
	private boolean wasLooking = true;

	/** The osascript that hasn't finished yet, if any. */
	private volatile @Nullable Run pending;
	private long pendingSince;
	/** When the osascript that gave the last answer started and when it finished ({@link System#nanoTime()}). */
	private long ranFromNs;
	private long ranToNs;
	/** The time the last answer's positions were read at, see {@link #readStamp}. */
	private long lastReadAt;
	private volatile int fails;
	private volatile long retryAt;
	/** Apps macOS refused to let us control, and when to ask again. */
	private final Map<App, Long> deniedUntil = new ConcurrentHashMap<>();
	/** Apps that answered since they were last seen closed: macOS has settled whether we may control them. */
	private final Set<App> answered = ConcurrentHashMap.newKeySet();
	/** Apps that ran out of {@link #ANSWER_LIMIT_S}, and when to ask them again. */
	private final Map<App, Long> frozenUntil = new ConcurrentHashMap<>();
	private String retryError = "";
	/** The last message put in the log, so the same one isn't repeated every retry. */
	private String loggedError = "";
	private Answer last = Answer.EMPTY;
	private long lastAt;
	private volatile boolean haveLast;
	private @Nullable App current;
	private volatile String currentDisplayName = "";

	/** @param looking whether anything on screen shows the song; when not, osascript runs far less often */
	public MacMediaSource(Supplier<String> preferredPlayer, BooleanSupplier looking) {
		this(preferredPlayer, MacMediaSource::startProcess, MIN_POLL_NS,
				scanned(MacMediaSource::runningApps, System::nanoTime, () -> ttlNs(looking.getAsBoolean())),
				looking, System::nanoTime);
	}

	static long ttlNs(boolean looking) {
		return looking ? RUNNING_TTL_NS : RUNNING_QUIET_TTL_NS;
	}

	/** How long a reading is reused for. */
	static long gapNs(boolean looking, long minPollNs) {
		return looking ? minPollNs : Math.max(minPollNs, QUIET_POLL_NS);
	}

	/** @param minPollNs how long a reading is reused for; tests use 0 */
	MacMediaSource(Supplier<String> preferredPlayer, Runner runner, long minPollNs) {
		this(preferredPlayer, runner, minPollNs, app -> true);
	}

	MacMediaSource(Supplier<String> preferredPlayer, Runner runner, long minPollNs, Predicate<App> running) {
		this(preferredPlayer, runner, minPollNs, running, () -> true, System::nanoTime);
	}

	MacMediaSource(Supplier<String> preferredPlayer, Runner runner, long minPollNs, Predicate<App> running,
			BooleanSupplier looking, LongSupplier clock) {
		this.looking = looking;
		this.clock = clock;
		this.preferredPlayer = preferredPlayer;
		this.runner = runner;
		this.minPollNs = minPollNs;
		this.running = running;
	}

	/** Remembers {@code inner}'s answer for each app for {@code ttlNs}. */
	static Predicate<App> cached(Predicate<App> inner, LongSupplier nanos, long ttlNs) {
		Map<App, Boolean> answers = new HashMap<>();
		Map<App, Long> at = new HashMap<>();
		return app -> {
			synchronized (answers) {
				long now = nanos.getAsLong();
				Long when = at.get(app);
				if (when == null || now - when >= ttlNs) {
					answers.put(app, inner.test(app));
					at.put(app, now);
				}
				return answers.get(app);
			}
		};
	}

	/**
	 * Like {@link #cached} but one walk of the process table answers both apps, and the lifetime of the answer
	 * is asked for each time (it is longer while nobody is looking).
	 */
	static Predicate<App> scanned(Supplier<Set<String>> scan, LongSupplier nanos, LongSupplier ttlNs) {
		Object lock = new Object();
		java.util.concurrent.atomic.AtomicReference<Set<String>> found = new java.util.concurrent.atomic.AtomicReference<>();
		long[] at = {0};
		return app -> {
			synchronized (lock) {
				long now = nanos.getAsLong();
				if (found.get() == null || now - at[0] >= ttlNs.getAsLong()) {
					found.set(scan.get());
					at[0] = now;
				}
				return found.get().contains(app.name());
			}
		};
	}

	/** The names of the apps among this user's processes, from one walk of the process table. */
	static Set<String> runningApps() {
		Set<String> out = new java.util.HashSet<>();
		ProcessHandle.allProcesses().forEach(p -> {
			String c = p.info().command().orElse("");
			for (App app : APPS) {
				if (c.endsWith("/" + app.name() + ".app/Contents/MacOS/" + app.name())) {
					out.add(app.name());
				}
			}
		});
		return out;
	}

	/** The most a reading is believed to be older than the moment osascript finished. */
	static final long MAX_LAG_NS = 500_000_000L;

	/**
	 * When the positions in an answer were read, given when its osascript started and finished. The script reads
	 * after it starts up and before its output gets back, so the midpoint is within half the run time of the
	 * truth; stamping the finish (as this used to) made lyrics run late by the whole lag, which MediaTracker
	 * never corrects. A run that took long (macOS was asking for permission) read near its end, not its middle,
	 * so the stamp is never more than {@link #MAX_LAG_NS} before the finish.
	 */
	static long readStamp(long startNs, long endNs) {
		long mid = startNs + (endNs - startNs) / 2;
		return Math.max(mid, endNs - MAX_LAG_NS);
	}

	/** Whether this answer means macOS refused the Automation permission. */
	static boolean isDenied(int code) {
		return code == NOT_ALLOWED || code == NEEDS_CONSENT;
	}

	@Override
	public synchronized Snapshot poll() throws Exception {
		boolean lookingNow = looking.getAsBoolean();
		boolean returned = lookingNow && !wasLooking; // the song may have changed while nobody looked: read at once
		wasLooking = lookingNow;
		if (!haveLast || returned || clock.getAsLong() - lastAt >= gapNs(lookingNow, minPollNs)) {
			List<App> asked = new ArrayList<>();
			List<Failure> denied = new ArrayList<>();
			long now = System.nanoTime();
			for (App app : APPS) {
				Long until = deniedUntil.get(app);
				Long frozen = frozenUntil.get(app);
				if (until != null && now < until) {
					// macOS said no a moment ago and osascript exits normally with that answer, so nothing else
					// would slow us down: leave the app alone until the retry time
					denied.add(new Failure(app, NOT_ALLOWED, ""));
				} else if (frozen != null && now < frozen) {
					// it didn't answer in time a moment ago: left out, so it can't hold the other app up
				} else if (running.test(app)) {
					asked.add(app);
				} else {
					answered.remove(app); // just opened again, it can be slow to answer at first
				}
			}
			List<String> args = new ArrayList<>();
			for (App app : asked) {
				args.add(app.name());
				args.add(asked.size() > 1 && answered.contains(app) ? limited(app.readScript()) : app.readScript());
			}
			boolean late = pending != null;
			if (args.isEmpty() && !late) {
				last = denied.isEmpty() ? Answer.EMPTY : new Answer(List.of(), denied, List.of(), Set.of()); // nothing to ask, no osascript to start
				lastAt = clock.getAsLong();
				lastReadAt = System.nanoTime();
				haveLast = true;
			} else {
				String out = osascript(args, true);
				Answer answer = parse(out);
				List<Failure> failures = new ArrayList<>(answer.failures());
				boolean timedOut = false;
				for (Failure f : answer.failures()) {
					if (isDenied(f.code())) {
						deniedUntil.put(f.app(), System.nanoTime() + RETRY_NS);
						answered.remove(f.app());
					} else if (f.code() == TIMED_OUT) {
						frozenUntil.put(f.app(), System.nanoTime() + FROZEN_NS);
						timedOut = true;
					}
				}
				answered.addAll(answer.answered());
				failures.addAll(denied);
				if (timedOut) {
					// The other app's answer waited behind the frozen one, so when it was read is unknown: keep the
					// last answer for now and read again at once, without the frozen app
					haveLast = false;
				} else {
					last = new Answer(answer.players(), failures, answer.ads(), answer.answered());
					lastAt = clock.getAsLong();
					lastReadAt = readStamp(ranFromNs, ranToNs);
					haveLast = !late; // a late answer may be old: read again next time
				}
			}
		}
		// An ad comes after the songs: it only keeps the app it plays in followed (or picked), it never beats a playing song
		List<Reading> candidates = new ArrayList<>(last.players());
		candidates.addAll(last.ads());
		Reading chosen = PlayerPicker.choose(candidates, r -> r.app().name(), r -> r.track().player(), Reading::playing,
				preferredPlayer.get(), current == null ? null : current.name());
		if (chosen == null) {
			current = null;
			for (Failure f : last.failures()) {
				if (isDenied(f.code())) {
					throw new IOException(Tr.t("Allow your launcher (Minecraft, Prism Launcher…) to control %s in System Settings → Privacy & Security → Automation", f.app().name()));
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
		if (chosen.track().isNone()) {
			return Snapshot.NOTHING; // an ad: no song to show, but the buttons still reach the app that plays it
		}
		return new Snapshot(chosen.track(), chosen.positionMs(), chosen.playing(), lastReadAt);
	}

	/**
	 * Reads what the script prints. Each line is one running app: its name and state, then for a song
	 * duration, position, title, artist, album, cover link, song address (Spotify) and the stream's current title (Music radio),
	 * separated by tabs (the last two may be missing in an older reply); or its name, "error",
	 * the AppleScript error number and message.
	 */
	static Answer parse(String output) {
		List<Reading> players = new ArrayList<>();
		List<Failure> failures = new ArrayList<>();
		List<Reading> ads = new ArrayList<>();
		Set<App> answered = new java.util.HashSet<>();
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
			if (playing || state.equals("paused") || state.equals("stopped")) {
				answered.add(app);
			}
			if (!playing && !state.equals("paused")) {
				continue; // stopped, quit just as we asked, or the answer to a button press
			}
			String uri = field(f, 8);
			if (uri.startsWith("spotify:ad:")) {
				// An ad is not a song, but Spotify still plays: kept apart, so Music's paused song doesn't take over for it
				ads.add(new Reading(app, new Track("", "", "", 0, "", app.key(), uri), 0, playing));
				continue;
			}
			String title = field(f, 4);
			String artist = field(f, 5);
			String album = field(f, 6);
			String stream = field(f, 9);
			if (artist.isEmpty() && !stream.isEmpty()) {
				// Internet radio: the "song" is the station and the artist is empty; the stream title is "Artist - Song"
				int dash = stream.indexOf(" - ");
				if (dash > 0 && dash + 3 < stream.length()) {
					artist = stream.substring(0, dash).strip();
					title = stream.substring(dash + 3).strip();
				} else {
					title = stream;
				}
				album = ""; // the station name must not go into the lyrics lookup
			}
			if (title.isEmpty()) {
				continue;
			}
			long length = Math.max(0, Math.round(number(field(f, 2)) * app.msPerDurationUnit()));
			long position = Math.max(0, Math.round(number(field(f, 3)) * 1000));
			if (length > 0) {
				position = Math.min(position, length);
			}
			Track track = new Track(title, artist, album, length, field(f, 7), app.key(), uri);
			players.add(new Reading(app, TitleCleaner.clean(track), position, playing));
		}
		return new Answer(players, failures, ads, answered);
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
		// Composed form, so accents typed as letter + combining mark (old Music libraries) match LRCLIB and the caches
		return i < f.length ? Normalizer.normalize(f[i].replaceAll("\\p{Cntrl}", "").strip(), Normalizer.Form.NFC) : "";
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
				throw backOff(Tr.t("Couldn't start osascript: %s", e.getMessage() == null ? e.toString() : e.getMessage()));
			}
			pending = run;
			pendingSince = System.nanoTime();
		}
		Result result = run.await(WAIT_MS);
		long finished = System.nanoTime();
		if (result == null) {
			if (!mayWait || System.nanoTime() - pendingSince > GIVE_UP_NS) {
				run.kill();
				pending = null;
				throw failed(Tr.t("osascript didn't answer in time"));
			}
			throw new IOException(Tr.t("Waiting for Spotify or Music. If macOS asks to let your launcher control them, click OK"));
		}
		pending = null;
		ranFromNs = pendingSince; // of the run that answered, which may be an older one than this call started
		ranToNs = finished;
		if (result.exitCode() != 0) {
			String err = result.err().strip();
			int newline = err.indexOf('\n');
			throw failed(Tr.t("osascript failed: %s", newline > 0 ? err.substring(0, newline) : err.isEmpty() ? Tr.t("exit code %s", result.exitCode()) : err));
		}
		fails = 0;
		loggedError = "";
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
		if (!message.equals(loggedError)) { // osascript failing keeps repeating the same line
			loggedError = message;
			LOG.warn("Couldn't read Spotify or Music: {}", message);
		}
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

	/** Starts osascript the way every poll and button press does (the tests on a Mac run the real scripts through it). */
	static Run startProcess(List<String> command) throws IOException {
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

	/**
	 * Spotify Web or the demo song is followed for now: an osascript still waiting (on macOS's permission question or a
	 * frozen app) is stopped instead of being left running for nobody.
	 */
	@Override
	public void standBy() {
		close();
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
		deniedUntil.clear();
		frozenUntil.clear();
		haveLast = false;
	}
}
