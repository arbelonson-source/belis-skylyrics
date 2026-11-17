package dev.beli.skylyrics.media;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.beli.skylyrics.Tr;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the playing song on Windows from its media controls, the panel that pops up when you press a media or
 * volume key. Spotify, browsers (YouTube, SoundCloud…), Media Player, Apple Music and most other players
 * report to it.
 *
 * <p>Java can't reach that Windows API by itself, so a small bundled PowerShell script (windows-media.ps1) does
 * the talking. It runs hidden for as long as the game does: we write one numbered command per line and it
 * answers each with one line of JSON carrying the same number.
 */
public final class WindowsMediaSource implements MediaSource {
	private static final Logger LOG = LoggerFactory.getLogger("SkyLyrics");
	private static final String SCRIPT = "windows-media.ps1";
	/** PowerShell and the Windows media API can be slow to wake up the first time after boot. */
	private static final long START_TIMEOUT_MS = 20_000;
	/** The very first start of the session gets longer: antivirus often scans PowerShell the first time. */
	private static final long FIRST_START_TIMEOUT_MS = 45_000;
	private static final long REPLY_TIMEOUT_MS = 4_000;
	/** After the helper fails to start, wait before trying again instead of starting PowerShell on every poll: this long the first time. */
	private static final long RETRY_NS = 15_000_000_000L;
	/** The longest wait between tries. A PC that blocks PowerShell (AppLocker, Constrained Language Mode) logs an event per try. */
	private static final long MAX_RETRY_NS = 300_000_000_000L;
	/** A helper that stops this soon after starting counts as failing to start. */
	private static final long EARLY_EXIT_NS = 10_000_000_000L;
	/** Unanswered questions in a row before the helper counts as stuck and is restarted. */
	private static final int MAX_MISSED = 3;
	/** Firefox shows up under a hash of its install folder instead of a name. */
	private static final Pattern HASH_ID = Pattern.compile("[0-9A-Fa-f]{16}");
	/** Put in the queue when the helper's output ends. Compared by identity. */
	@SuppressWarnings("StringOperationCanBeSimplified")
	private static final String END = new String("end");

	/** While nothing on screen shows the song, an answer from the helper is reused this long (the Spotify Web poller's quiet pace). */
	static final long QUIET_POLL_NS = SpotifyPace.QUIET_NS;

	/** Asks the helper one question; tests swap in a stand-in. */
	interface Requester {
		JsonObject ask(String command) throws Exception;
	}

	/** How to start the helper; tests swap in a stand-in for Windows. */
	interface Launcher {
		List<String> command(Path script, Path coverDir);
	}

	private final Supplier<String> preferredPlayer;
	private final Path dir;
	private final Launcher launcher;
	private final long startTimeoutMs;
	private final long replyTimeoutMs;
	private final BooleanSupplier looking;
	/** Pacing clock; tests swap it. */
	private final LongSupplier clock;
	/** The computer's wall clock in ms; tests swap it to move the clock. Only used to take a report's age. */
	LongSupplier wallMillis = System::currentTimeMillis;
	/** A clock that only runs forward, in ns; positions are counted on it, so a clock change cannot move them. */
	LongSupplier monoNanos = System::nanoTime;
	private final Requester requester;
	private boolean wasLooking = true;
	/** Counts close() calls, so a poll in flight (which holds the lock close() must not wait for) can tell. */
	private volatile int closes;
	/** The last poll's answer, reused while nobody is looking; null when there is none worth reusing. */
	private volatile @Nullable Snapshot cached;
	private long cachedAt;

	private volatile @Nullable Process process;
	private volatile @Nullable Writer input;
	private BlockingQueue<String> replies = new LinkedBlockingQueue<>();
	private long seq;
	private int missed;
	private long retryAt;
	/** Starts that failed since a helper last answered, and the message last logged for them (so a stuck PC logs once). */
	private int failuresInARow;
	private String loggedStartError = "";
	private long startedAt;
	/** Whether a helper has been ready yet this session. */
	private boolean everReady;
	private String startError = "";
	/** The followed player: {@link Session#key()} for choosing, the rest for the controls. */
	private @Nullable String current;
	private @Nullable Session currentSession;
	/** What the last poll said about each player, to tell a player that just started playing from one that has been. */
	private Map<String, Seen> seen = new HashMap<>();
	private String currentDisplayName = "";

	/** @param dir where the helper script and the covers it saves go */
	public WindowsMediaSource(Supplier<String> preferredPlayer, Path dir) {
		this(preferredPlayer, dir, () -> true);
	}

	/** @param looking whether anything on screen shows the song; when not, the helper is asked far less often */
	public WindowsMediaSource(Supplier<String> preferredPlayer, Path dir, BooleanSupplier looking) {
		this(preferredPlayer, dir, WindowsMediaSource::powershell, START_TIMEOUT_MS, REPLY_TIMEOUT_MS, looking, System::nanoTime, null);
	}

	/** Tests use a stand-in launcher, and longer timeouts for slow test machines. */
	WindowsMediaSource(Supplier<String> preferredPlayer, Path dir, Launcher launcher, long startTimeoutMs, long replyTimeoutMs) {
		this(preferredPlayer, dir, launcher, startTimeoutMs, replyTimeoutMs, () -> true, System::nanoTime, null);
	}

	/** @param requester how the helper is asked; null means the real helper */
	WindowsMediaSource(Supplier<String> preferredPlayer, Path dir, Launcher launcher, long startTimeoutMs, long replyTimeoutMs,
			BooleanSupplier looking, LongSupplier clock, @Nullable Requester requester) {
		this.looking = looking;
		this.clock = clock;
		this.requester = requester != null ? requester : this::request;
		this.preferredPlayer = preferredPlayer;
		this.dir = dir;
		this.launcher = launcher;
		this.startTimeoutMs = startTimeoutMs;
		this.replyTimeoutMs = replyTimeoutMs;
	}

	/**
	 * One player as the helper reports it.
	 *
	 * @param index its place in the helper's list; browsers give every tab the same {@code id}, so this tells them apart
	 */
	record Session(int index, String id, Track track, long positionMs, boolean playing, double rate) {
		String key() {
			return id + "#" + index;
		}
	}

	@Override
	public synchronized Snapshot poll() throws Exception {
		boolean lookingNow = looking.getAsBoolean();
		boolean returned = lookingNow && !wasLooking; // the song may have changed while nobody looked: ask at once
		wasLooking = lookingNow;
		Snapshot reuse = cached;
		// The snapshot carries the moment it was read, so the tracker's clock keeps counting from there
		if (reuse != null && !lookingNow && !returned && clock.getAsLong() - cachedAt < QUIET_POLL_NS) {
			return reuse;
		}
		cached = null; // a failed ask must not leave an old answer to reuse
		int generation = closes;
		Snapshot fresh = ask();
		if (generation == closes) { // a close() during the ask means this answer is from before it: use it once, don't keep it
			cached = fresh;
			cachedAt = clock.getAsLong();
		}
		return fresh;
	}

	private Snapshot ask() throws Exception {
		Map<String, Seen> next = new HashMap<>();
		List<Session> sessions = sessions(requester.ask("poll"), wallMillis.getAsLong(), monoNanos.getAsLong() / 1_000_000, seen, next);
		seen = next;
		Session chosen = PlayerPicker.choose(sessions, Session::key, s -> s.track().player(), Session::playing,
				preferredPlayer.get(), followKey(sessions, current));
		currentSession = chosen;
		if (chosen == null) {
			current = null;
			return Snapshot.NOTHING;
		}
		current = chosen.key();
		currentDisplayName = prettyName(chosen.track().player());
		return new Snapshot(chosen.track(), chosen.positionMs(), chosen.playing(), System.nanoTime(), chosen.rate());
	}

	/**
	 * The key to keep following. When another player closes, the numbers after it shift and the followed player's
	 * exact key is gone; then it is the same app (preferably the one playing) that we still mean.
	 */
	static @Nullable String followKey(List<Session> sessions, @Nullable String current) {
		if (current == null) {
			return null;
		}
		for (Session s : sessions) {
			if (s.key().equals(current)) {
				return current;
			}
		}
		int hash = current.lastIndexOf('#');
		String id = hash < 0 ? current : current.substring(0, hash);
		Session same = null;
		for (Session s : sessions) {
			if (s.id().equals(id) && (same == null || s.playing() && !same.playing())) {
				same = s;
			}
		}
		return same == null ? current : same.key();
	}

	/**
	 * What a poll said about one player: its state and reported position, when we asked, and the time its
	 * position counts from (see {@link #countFrom}).
	 */
	record Seen(String title, long lengthMs, boolean playing, long reportedMs, long polledMs, long countFromMs, long updatedRawMs) {

		/** Whether this is still the same song, so its count can go on. */
		boolean sameSong(String title, long lengthMs) {
			return this.title.equals(title) && this.lengthMs == lengthMs;
		}
	}

	/**
	 * The time a player's reported position counts from, to move it on while playing. Normally that is
	 * {@code updatedMs}, when the player wrote it. But an app can flip to Playing before it rewrites its update
	 * time: then {@code updatedMs} is from before the pause, and counting from it adds the whole pause on top of
	 * the old position. A report no newer than the poll that still saw it paused counts from now instead. A
	 * player that never says when (updatedMs 0) counts from when we first saw this position, rather than
	 * standing still. A report that changed, or an update time newer than the one we count from, starts over.
	 */
	static long countFrom(boolean playing, long reportedMs, long updatedMs, long nowMs, @Nullable Seen prev) {
		if (!playing) {
			return updatedMs;
		}
		if (prev == null) {
			return updatedMs > 0 ? updatedMs : nowMs;
		}
		if (!prev.playing()) {
			return updatedMs > prev.polledMs() ? updatedMs : nowMs;
		}
		if (reportedMs != prev.reportedMs() || updatedMs > prev.countFromMs()) {
			return updatedMs > 0 ? updatedMs : nowMs;
		}
		return prev.countFromMs();
	}

	static List<Session> sessions(JsonObject reply, long nowMs) {
		return sessions(reply, nowMs, Map.of(), new HashMap<>());
	}

	/**
	 * The players in a poll answer, with their positions moved on to {@code nowMs}.
	 *
	 * @param before what the last poll said, by {@link Session#key()}
	 * @param after  filled with what this poll says, for the next
	 */
	static List<Session> sessions(JsonObject reply, long nowMs, Map<String, Seen> before, Map<String, Seen> after) {
		return sessions(reply, nowMs, nowMs, before, after);
	}

	/**
	 * As above, with the wall clock and a forward-only clock (both ms) told apart. Windows stamps a report in wall
	 * time, so its age is taken once, when it is first seen; from then on the position runs on {@code monoMs}, which
	 * a changed computer clock or a wake from sleep cannot move.
	 */
	static List<Session> sessions(JsonObject reply, long wallMs, long monoMs, Map<String, Seen> before, Map<String, Seen> after) {
		long nowMs = monoMs;
		List<Session> out = new ArrayList<>();
		JsonElement list = reply.get("sessions");
		if (list == null || !list.isJsonArray()) {
			return out;
		}
		for (JsonElement e : list.getAsJsonArray()) {
			if (!e.isJsonObject()) {
				continue;
			}
			JsonObject o = e.getAsJsonObject();
			String title = string(o, "title");
			if (title.isEmpty()) {
				continue;
			}
			String id = string(o, "id");
			boolean playing = "Playing".equalsIgnoreCase(string(o, "status"));
			long start = number(o, "startMs");
			long length = Math.max(0, number(o, "endMs") - start);
			double rate = rate(o);
			long reported = number(o, "positionMs") - start;
			long updated = number(o, "updatedMs");
			String key = id + "#" + (int) number(o, "index");
			Seen prev = before.get(key);
			// Another song on the same player starts its own count
			Seen same = prev != null && prev.sameSong(title, length) ? prev : null;
			long from;
			if (same != null && playing && same.playing() && same.reportedMs() == reported && same.updatedRawMs() == updated) {
				from = same.countFromMs(); // the same report as last time: keep counting, whatever the wall clock did
			} else {
				// A first sight of this report: its age on the wall clock, once, then on the steady clock
				long age = updated > 0 ? Math.max(0, wallMs - updated) : 0;
				if (length > 0) {
					age = Math.min(age, length);
				}
				from = countFrom(playing, reported, updated > 0 ? monoMs - age : 0, nowMs, same);
			}
			after.put(key, new Seen(title, length, playing, reported, nowMs, from, updated));
			long position = position(reported, from, playing, length, nowMs, rate);
			Track track = new Track(title, string(o, "artist"), string(o, "album"), length, coverUrl(string(o, "cover")), playerKey(id));
			out.add(new Session((int) number(o, "index"), id, TitleCleaner.clean(track), position, playing, rate));
		}
		return out;
	}

	/**
	 * Players only report their position now and then (on play, pause, seek), together with when they did.
	 * While playing, the song has moved on since.
	 */
	static long position(long positionMs, long updatedMs, boolean playing, long lengthMs, long nowMs) {
		return position(positionMs, updatedMs, playing, lengthMs, nowMs, 1.0);
	}

	/** As above, for a player running at {@code rate} (1.5 = song time passes 1.5 times faster than the clock). */
	static long position(long positionMs, long updatedMs, boolean playing, long lengthMs, long nowMs, double rate) {
		long pos = positionMs;
		if (playing && updatedMs > 0 && nowMs > updatedMs) {
			pos += (long) ((nowMs - updatedMs) * saneRate(rate));
		}
		if (lengthMs > 0) {
			pos = Math.min(pos, lengthMs);
		}
		return Math.max(0, pos);
	}

	/** Speeds that make no sense (zero, negative, over 4x, not a number) count as normal speed. */
	static double saneRate(double rate) {
		return rate > 0 && rate <= 4 ? rate : 1.0;
	}

	private static double rate(JsonObject o) {
		JsonElement e = o.get("rate");
		try {
			return e == null || !e.isJsonPrimitive() ? 1.0 : saneRate(e.getAsDouble());
		} catch (NumberFormatException ex) {
			return 1.0;
		}
	}

	private static String coverUrl(String file) {
		if (file.isEmpty()) {
			return "";
		}
		try {
			return Path.of(file).toUri().toString();
		} catch (RuntimeException e) {
			return "";
		}
	}

	/** A short player name from a Windows app id like "Spotify.exe", "MSEdge" or "Microsoft.ZuneMusic_8wekyb3d8bbwe!Microsoft.ZuneMusic". */
	static String playerKey(String id) {
		String a = id.toLowerCase(Locale.ROOT);
		if (a.contains("spotify")) return "spotify";
		if (a.contains("msedge") || a.contains("microsoftedge")) return "edge";
		if (a.contains("chrome")) return "chrome";
		if (a.contains("firefox") || HASH_ID.matcher(id).matches()) return "firefox";
		if (a.contains("zunemusic")) return "media player";
		if (a.contains("applemusic")) return "apple music";
		for (String name : List.of("brave", "opera", "vivaldi", "vlc", "tidal", "deezer", "foobar2000", "musicbee", "aimp")) {
			if (a.contains(name)) {
				return name;
			}
		}
		String name = a.substring(a.indexOf('!') + 1); // packaged apps: "Publisher.App_hash!App"
		name = name.substring(name.lastIndexOf('\\') + 1);
		return name.endsWith(".exe") ? name.substring(0, name.length() - 4) : name;
	}

	static String prettyName(String player) {
		return switch (player) {
			case "spotify" -> "Spotify";
			case "edge" -> "Edge";
			case "chrome" -> "Chrome";
			case "firefox" -> "Firefox";
			case "media player" -> "Media Player";
			case "apple music" -> "Apple Music";
			case "vlc" -> "VLC";
			case "tidal" -> "TIDAL";
			case "musicbee" -> "MusicBee";
			case "aimp" -> "AIMP";
			case "" -> "";
			default -> Character.toUpperCase(player.charAt(0)) + player.substring(1);
		};
	}

	private JsonObject request(String command) throws IOException, InterruptedException {
		ensureStarted();
		long id = ++seq;
		BlockingQueue<String> queue = replies;
		Writer w = input;
		try {
			if (w == null) {
				throw new IOException("closed");
			}
			w.write(id + " " + command + "\n");
			w.flush();
		} catch (IOException e) {
			stop();
			backOffIfEarly();
			throw new IOException(Tr.t("The Windows music reader stopped"));
		}
		long deadline = System.nanoTime() + replyTimeoutMs * 1_000_000;
		while (true) {
			long left = deadline - System.nanoTime();
			String line = left > 0 ? queue.poll(left, TimeUnit.NANOSECONDS) : null;
			if (line == END) {
				stop();
				backOffIfEarly();
				throw new IOException(Tr.t("The Windows music reader stopped"));
			}
			if (line == null) {
				if (++missed >= MAX_MISSED) {
					stop(); // also clears missed
				}
				throw new IOException(Tr.t("Windows didn't say what's playing in time"));
			}
			JsonObject reply = parse(line);
			if (reply == null || number(reply, "seq") != id) {
				continue; // a late answer to an earlier question
			}
			missed = 0;
			JsonElement ok = reply.get("ok");
			if (ok != null && ok.isJsonPrimitive() && ok.getAsBoolean()) {
				failuresInARow = 0; // a helper that answers works: the next failure starts the waits over
				loggedStartError = "";
			}
			if (ok == null || !ok.isJsonPrimitive() || !ok.getAsBoolean()) {
				String error = helperError(reply);
				throw new IOException(error.isEmpty() ? Tr.t("The Windows music reader sent something unexpected") : error);
			}
			return reply;
		}
	}

	private void ensureStarted() throws IOException, InterruptedException {
		Process p = process;
		if (p != null && p.isAlive()) {
			return;
		}
		boolean died = p != null;
		stop();
		if (died) {
			// It said what was wrong and exited (the script does that when Windows' media controls fail for good)
			backOffIfEarly();
		}
		if (System.nanoTime() < retryAt) {
			throw new IOException(startError);
		}
		try {
			start();
		} catch (IOException | RuntimeException e) {
			stop();
			startError = detail(e);
			failuresInARow++;
			retryAt = System.nanoTime() + retryDelayNs(failuresInARow);
			// A PC that can never run PowerShell fails the same way every time: say it once (and again only when it changes)
			if (!startError.equals(loggedStartError)) {
				loggedStartError = startError;
				LOG.warn("Couldn't read Windows' media controls: {}", dev.beli.skylyrics.util.LogText.redact(startError, System.getProperty("user.home")));
			}
			throw new IOException(startError, e);
		}
	}

	/** How many helper starts failed since one last answered (for tests). */
	synchronized int failuresInARow() {
		return failuresInARow;
	}

	/** Lets the next poll start the helper without waiting out the back-off (for tests). */
	synchronized void retryNow() {
		retryAt = 0;
	}

	/** How long to wait for a helper to say it is ready: longer until one has managed it once. */
	static long startLimitMs(long normalMs, boolean everReady) {
		return everReady ? normalMs : Math.max(normalMs, FIRST_START_TIMEOUT_MS);
	}

	private static String detail(Exception e) {
		return e.getMessage() == null || e.getMessage().isEmpty() ? e.toString() : e.getMessage();
	}

	/**
	 * What the helper's error says, in the player's language. The script sends a stable {@code code} (and the
	 * technical part as {@code detail}); anything else it says is shown as it is.
	 */
	static String helperError(JsonObject reply) {
		String detail = string(reply, "detail");
		return switch (string(reply, "code")) {
			case "unavailable" -> Tr.t("Windows' media controls aren't available (%s)", detail);
			case "stopped" -> Tr.t("Windows' media controls stopped answering (%s)", detail);
			case "no_web" -> Tr.t("Windows is missing a part the music reader needs (System.Web)");
			default -> string(reply, "error");
		};
	}

	private void backOffIfEarly() {
		if (shouldBackOff(false, startedAt, System.nanoTime())) {
			// Starting it again straight away would most likely end the same way
			startError = Tr.t("The Windows music reader keeps stopping (see windows-media.log)");
			failuresInARow++;
			retryAt = System.nanoTime() + retryDelayNs(failuresInARow);
		}
	}

	/** How long to wait before starting the helper again after this many failed starts in a row: 15 s, 30 s, 60 s, ... up to 5 minutes. */
	static long retryDelayNs(int failuresInARow) {
		int doublings = Math.max(0, Math.min(failuresInARow, 31) - 1);
		return Math.min(MAX_RETRY_NS, RETRY_NS << Math.min(doublings, 10));
	}

	/** A helper that is gone soon after it started would most likely stop again, so wait before starting another. */
	static boolean shouldBackOff(boolean alive, long startedAtNanos, long nowNanos) {
		return !alive && nowNanos - startedAtNanos < EARLY_EXIT_NS;
	}

	/** Writes the helper through a temp file of its own (two games may start at once) and moves it in, trying again when Windows briefly holds the file. */
	static void writeScript(Path script, byte[] bytes, dev.beli.skylyrics.util.SafeFile.Mover mover) throws IOException {
		Path tmp = Files.createTempFile(script.getParent(), script.getFileName() + ".", ".tmp");
		try {
			Files.write(tmp, bytes);
		} catch (IOException | RuntimeException e) {
			Files.deleteIfExists(tmp);
			throw e;
		}
		dev.beli.skylyrics.util.SafeFile.replace(tmp, script, mover, dev.beli.skylyrics.util.SafeFile.TRIES,
				dev.beli.skylyrics.util.SafeFile.PAUSE_MS);
	}

	private void start() throws IOException, InterruptedException {
		Path covers = dir.resolve("covers");
		Path script = dir.resolve(SCRIPT);
		try {
			Files.createDirectories(covers);
			deleteStaleCovers(covers); // left over from a game that has closed
			dev.beli.skylyrics.util.Leftovers.cleanTemps(dir, SCRIPT, System.currentTimeMillis()); // a killed game's script temp
			byte[] bytes;
			try (InputStream in = WindowsMediaSource.class.getResourceAsStream("/skylyrics/" + SCRIPT)) {
				if (in == null) {
					throw new IOException(SCRIPT + " is missing from the mod");
				}
				bytes = in.readAllBytes();
			}
			// Rewriting an unchanged file can collide with an antivirus scanning it
			if (!Files.isRegularFile(script) || !Arrays.equals(Files.readAllBytes(script), bytes)) {
				writeScript(script, bytes, Files::move);
			}
		} catch (IOException e) {
			throw new IOException(Tr.t("Couldn't prepare the Windows music reader (%s)", detail(e)), e);
		}

		ProcessBuilder builder = new ProcessBuilder(launcher.command(script, covers));
		builder.redirectError(dir.resolve("windows-media.log").toFile());
		// A game started from PowerShell 7 hands down its module path, which confuses Windows PowerShell 5.1
		builder.environment().remove("PSModulePath");
		Process p;
		try {
			p = builder.start();
		} catch (IOException e) {
			throw new IOException(Tr.t("Couldn't start the Windows music reader. Check that Windows PowerShell is installed, then reopen the game. (%s)", e.getMessage()), e);
		}
		BlockingQueue<String> queue = new LinkedBlockingQueue<>();
		replies = queue;
		process = p;
		input = new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8));
		Thread reader = new Thread(() -> read(p, queue), "SkyLyrics Windows media");
		reader.setDaemon(true);
		reader.start();
		startedAt = System.nanoTime();

		String first = queue.poll(startLimitMs(startTimeoutMs, everReady), TimeUnit.MILLISECONDS);
		JsonObject hello = first == null || first == END ? null : parse(first);
		if (hello == null || !hello.has("ready") || !hello.get("ready").getAsBoolean()) {
			String error = hello == null ? "" : helperError(hello);
			throw new IOException(!error.isEmpty() ? error
					: first == null ? Tr.t("The Windows music reader took too long to start. Reopen the game and give it a moment.")
					: Tr.t("The Windows music reader stopped right away. Check that Windows PowerShell is installed, then reopen the game.")
							+ lastLogLine(dir.resolve("windows-media.log")));
		}
		everReady = true;
		LOG.info("Reading music from Windows' media controls");
	}

	/**
	 * What the helper's own log says last, as " (that line)", or "" when there is none. The helper's error output goes to
	 * windows-media.log, which is started empty for every helper run, so this is what this run said (PowerShell's own message
	 * when, say, Constrained Language Mode or AppLocker stops the script).
	 */
	static String lastLogLine(Path log) {
		try {
			if (!Files.isRegularFile(log) || Files.size(log) > 1_000_000) {
				return "";
			}
			// Some Windows setups write it as UTF-16: dropping the zero bytes leaves the ASCII text readable
			String text = new String(Files.readAllBytes(log), StandardCharsets.UTF_8).replace("\0", "").replace("\uFEFF", "");
			String last = "";
			for (String line : text.split("\\R")) {
				if (!line.isBlank()) {
					last = line.strip();
				}
			}
			return last.isEmpty() ? "" : " (" + (last.length() > 200 ? last.substring(0, 200) + "…" : last) + ")";
		} catch (IOException | RuntimeException e) {
			return "";
		}
	}

	private static void read(Process p, BlockingQueue<String> queue) {
		try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
			for (String line; (line = r.readLine()) != null; ) {
				line = line.strip();
				if (line.startsWith("﻿")) {
					line = line.substring(1);
				}
				if (line.startsWith("{")) {
					queue.offer(line); // anything else is PowerShell talking to itself
				}
			}
		} catch (IOException e) {
			// Closed because the helper stopped
		}
		queue.offer(END);
	}

	/** Ends the helper and waits for it, so its log file is let go of (Windows can't delete open files). */
	private void stop() {
		Process p = process;
		Writer w = input;
		process = null;
		input = null;
		missed = 0; // a new helper must not inherit the old one's unanswered questions
		if (p == null) {
			return;
		}
		try {
			if (w != null) {
				w.close(); // the script ends by itself when its input closes
			}
		} catch (IOException e) {
			// Already gone
		}
		try {
			if (!p.waitFor(500, TimeUnit.MILLISECONDS)) {
				p.destroyForcibly();
				p.waitFor(2, TimeUnit.SECONDS);
			}
		} catch (InterruptedException e) {
			p.destroyForcibly();
			Thread.currentThread().interrupt();
		}
	}

	private static final java.util.regex.Pattern COVER_NAME = java.util.regex.Pattern.compile("cover-(\\d+)-\\d+\\.img");
	private static final long STRANGE_FILE_AGE_MS = 24L * 60 * 60 * 1000;

	/**
	 * Whether a file in the covers folder is left over and can go. The helper names its covers {@code cover-<its process id>-n.img},
	 * so one whose helper still runs belongs to another game sharing this folder. Windows reuses process ids fast, so a process
	 * with that id only counts when it is a PowerShell: {@code commandOf} gives the process's program path, null when no such
	 * process runs, and "" when it runs but its program is unknown (then the cover stays until it is a day old). Any other file goes
	 * once it is a day old.
	 */
	static boolean staleCover(String name, java.util.function.LongFunction<String> commandOf, long ageMs) {
		var m = COVER_NAME.matcher(name);
		if (m.matches()) {
			try {
				String command = commandOf.apply(Long.parseLong(m.group(1)));
				if (command == null) {
					return true;
				}
				if (command.isEmpty()) {
					return ageMs > STRANGE_FILE_AGE_MS;
				}
				String lower = command.toLowerCase(Locale.ROOT);
				return !(lower.endsWith("powershell.exe") || lower.endsWith("pwsh.exe"));
			} catch (NumberFormatException e) {
				return ageMs > STRANGE_FILE_AGE_MS;
			}
		}
		return ageMs > STRANGE_FILE_AGE_MS;
	}

	private static String commandOf(long pid) {
		return ProcessHandle.of(pid).map(h -> h.info().command().orElse("")).orElse(null);
	}

	/** Removes the covers a helper that is gone left behind; never another running game's. */
	private static void deleteStaleCovers(Path dir) {
		try (Stream<Path> files = Files.list(dir)) {
			long now = System.currentTimeMillis();
			files.forEach(f -> {
				try {
					long age = now - Files.getLastModifiedTime(f).toMillis();
					if (staleCover(f.getFileName().toString(), WindowsMediaSource::commandOf, age)) {
						Files.deleteIfExists(f);
					}
				} catch (IOException e) {
					// Still open somewhere; it'll go next time
				}
			});
		} catch (IOException e) {
			LOG.debug("Couldn't tidy up old covers: {}", e.toString());
		}
	}

	/**
	 * Windows PowerShell 5.1 comes with every Windows 10 and 11. The script is read in and run as a command
	 * rather than with -File: school and work PCs often only allow signed script files, and -File also trips
	 * over folder names with [brackets], which modpacks like.
	 */
	static List<String> powershell(Path script, Path coverDir) {
		String exe = "powershell.exe";
		String root = System.getenv("SystemRoot");
		if (root != null) {
			Path full = Path.of(root, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
			if (Files.isRegularFile(full)) {
				exe = full.toString();
			}
		}
		return command(exe, script, coverDir);
	}

	static List<String> command(String exe, Path script, Path coverDir) {
		// Dot-sourced: with "&" the script's own variables and $script: would be two different scopes
		String run = ". ([scriptblock]::Create([IO.File]::ReadAllText(" + quote(script) + "))) -CoverDir " + quote(coverDir);
		return List.of(exe, "-NoLogo", "-NoProfile", "-NonInteractive", "-Command", run);
	}

	/**
	 * A PowerShell string literal: single quotes, with any quote inside doubled. PowerShell also ends a single-quoted
	 * string at the curly quotes ‘ ’ ‚ ‛, so a folder like "Beli’s Pack" would stop the helper from starting.
	 */
	static String quote(Path path) {
		StringBuilder out = new StringBuilder("'");
		for (char c : path.toString().toCharArray()) {
			out.append(c);
			if (c == '\'' || c == '‘' || c == '’' || c == '‚' || c == '‛') {
				out.append(c);
			}
		}
		return out.append('\'').toString();
	}

	private synchronized void control(String command) {
		Session s = currentSession;
		if (s == null) {
			return;
		}
		cached = null; // the player is about to change: the next poll must ask
		try {
			request(command + " " + s.index() + " " + s.id());
		} catch (Exception e) {
			LOG.debug("Player control failed: {}", e.toString());
		}
	}

	@Override
	public void playPause() {
		control("toggle");
	}

	@Override
	public void next() {
		control("next");
	}

	@Override
	public void previous() {
		control("prev");
	}

	@Override
	public void seek(long positionMs) {
		control("seek " + Math.max(0, positionMs));
	}

	@Override
	public String displayName() {
		return currentDisplayName;
	}

	/** Stops the helper; the next poll starts it again straight away. Safe from any thread. */
	@Override
	public void close() {
		closes++;
		cached = null;
		stop();
		retryAt = 0;
	}

	private static @Nullable JsonObject parse(String line) {
		try {
			JsonElement e = JsonParser.parseString(line);
			return e.isJsonObject() ? e.getAsJsonObject() : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static String string(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e == null || !e.isJsonPrimitive() ? "" : e.getAsString().strip();
	}

	private static long number(JsonObject o, String key) {
		JsonElement e = o.get(key);
		try {
			return e == null || !e.isJsonPrimitive() ? 0 : e.getAsLong();
		} catch (NumberFormatException ex) {
			return 0;
		}
	}
}
