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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
	private static final long REPLY_TIMEOUT_MS = 4_000;
	/** After the helper fails to start, wait before trying again instead of starting PowerShell on every poll. */
	private static final long RETRY_NS = 15_000_000_000L;
	/** A helper that stops this soon after starting counts as failing to start. */
	private static final long EARLY_EXIT_NS = 10_000_000_000L;
	/** Unanswered questions in a row before the helper counts as stuck and is restarted. */
	private static final int MAX_MISSED = 3;
	/** Firefox shows up under a hash of its install folder instead of a name. */
	private static final Pattern HASH_ID = Pattern.compile("[0-9A-Fa-f]{16}");
	/** Put in the queue when the helper's output ends. Compared by identity. */
	@SuppressWarnings("StringOperationCanBeSimplified")
	private static final String END = new String("end");

	/** How to start the helper; tests swap in a stand-in for Windows. */
	interface Launcher {
		List<String> command(Path script, Path coverDir);
	}

	private final Supplier<String> preferredPlayer;
	private final Path dir;
	private final Launcher launcher;
	private final long startTimeoutMs;
	private final long replyTimeoutMs;

	private volatile @Nullable Process process;
	private volatile @Nullable Writer input;
	private BlockingQueue<String> replies = new LinkedBlockingQueue<>();
	private long seq;
	private int missed;
	private long retryAt;
	private long startedAt;
	private String startError = "";
	/** The followed player: {@link Session#key()} for choosing, the rest for the controls. */
	private @Nullable String current;
	private @Nullable Session currentSession;
	private String currentDisplayName = "";

	/** @param dir where the helper script and the covers it saves go */
	public WindowsMediaSource(Supplier<String> preferredPlayer, Path dir) {
		this(preferredPlayer, dir, WindowsMediaSource::powershell, START_TIMEOUT_MS, REPLY_TIMEOUT_MS);
	}

	/** Tests use a stand-in launcher, and longer timeouts for slow test machines. */
	WindowsMediaSource(Supplier<String> preferredPlayer, Path dir, Launcher launcher, long startTimeoutMs, long replyTimeoutMs) {
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
	record Session(int index, String id, Track track, long positionMs, boolean playing) {
		String key() {
			return id + "#" + index;
		}
	}

	@Override
	public synchronized Snapshot poll() throws Exception {
		List<Session> sessions = sessions(request("poll"), System.currentTimeMillis());
		Session chosen = PlayerPicker.choose(sessions, Session::key, s -> s.track().player(), Session::playing,
				preferredPlayer.get(), current);
		currentSession = chosen;
		if (chosen == null) {
			current = null;
			return Snapshot.NOTHING;
		}
		current = chosen.key();
		currentDisplayName = prettyName(chosen.track().player());
		return new Snapshot(chosen.track(), chosen.positionMs(), chosen.playing(), System.nanoTime());
	}

	/** The players in a poll answer, with their positions moved on to {@code nowMs}. */
	static List<Session> sessions(JsonObject reply, long nowMs) {
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
			long position = position(number(o, "positionMs") - start, number(o, "updatedMs"), playing, length, nowMs);
			Track track = new Track(title, string(o, "artist"), string(o, "album"), length, coverUrl(string(o, "cover")), playerKey(id));
			out.add(new Session((int) number(o, "index"), id, TitleCleaner.clean(track), position, playing));
		}
		return out;
	}

	/**
	 * Players only report their position now and then (on play, pause, seek), together with when they did.
	 * While playing, the song has moved on since.
	 */
	static long position(long positionMs, long updatedMs, boolean playing, long lengthMs, long nowMs) {
		long pos = positionMs;
		if (playing && updatedMs > 0 && nowMs > updatedMs) {
			pos += nowMs - updatedMs;
		}
		if (lengthMs > 0) {
			pos = Math.min(pos, lengthMs);
		}
		return Math.max(0, pos);
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
			throw new IOException("The Windows media helper stopped");
		}
		long deadline = System.nanoTime() + replyTimeoutMs * 1_000_000;
		while (true) {
			long left = deadline - System.nanoTime();
			String line = left > 0 ? queue.poll(left, TimeUnit.NANOSECONDS) : null;
			if (line == END) {
				stop();
				if (System.nanoTime() - startedAt < EARLY_EXIT_NS) {
					// Starting it again straight away would most likely end the same way
					startError = "The Windows media helper keeps stopping (see windows-media.log)";
					retryAt = System.nanoTime() + RETRY_NS;
				}
				throw new IOException("The Windows media helper stopped");
			}
			if (line == null) {
				if (++missed >= MAX_MISSED) {
					stop();
					missed = 0;
				}
				throw new IOException("Windows didn't say what's playing in time");
			}
			JsonObject reply = parse(line);
			if (reply == null || number(reply, "seq") != id) {
				continue; // a late answer to an earlier question
			}
			missed = 0;
			JsonElement ok = reply.get("ok");
			if (ok == null || !ok.isJsonPrimitive() || !ok.getAsBoolean()) {
				String error = string(reply, "error");
				throw new IOException(error.isEmpty() ? "The Windows media helper sent something odd" : error);
			}
			return reply;
		}
	}

	private void ensureStarted() throws IOException, InterruptedException {
		Process p = process;
		if (p != null && p.isAlive()) {
			return;
		}
		stop();
		if (System.nanoTime() < retryAt) {
			throw new IOException(startError);
		}
		try {
			start();
		} catch (IOException | RuntimeException e) {
			stop();
			startError = e.getMessage() == null || e.getMessage().isEmpty() ? e.toString() : e.getMessage();
			retryAt = System.nanoTime() + RETRY_NS;
			LOG.warn("Couldn't read Windows' media controls: {}", startError);
			throw new IOException(startError, e);
		}
	}

	private void start() throws IOException, InterruptedException {
		Path covers = dir.resolve("covers");
		Files.createDirectories(covers);
		deleteFilesIn(covers); // left over from the last game
		Path script = dir.resolve(SCRIPT);
		byte[] bytes;
		try (InputStream in = WindowsMediaSource.class.getResourceAsStream("/skylyrics/" + SCRIPT)) {
			if (in == null) {
				throw new IOException(SCRIPT + " is missing from the mod");
			}
			bytes = in.readAllBytes();
		}
		// Rewriting an unchanged file can collide with an antivirus scanning it
		if (!Files.isRegularFile(script) || !Arrays.equals(Files.readAllBytes(script), bytes)) {
			Path tmp = dir.resolve(SCRIPT + ".tmp");
			Files.write(tmp, bytes);
			Files.move(tmp, script, StandardCopyOption.REPLACE_EXISTING);
		}

		ProcessBuilder builder = new ProcessBuilder(launcher.command(script, covers));
		builder.redirectError(dir.resolve("windows-media.log").toFile());
		// A game started from PowerShell 7 hands down its module path, which confuses Windows PowerShell 5.1
		builder.environment().remove("PSModulePath");
		Process p;
		try {
			p = builder.start();
		} catch (IOException e) {
			throw new IOException("Couldn't start PowerShell: " + e.getMessage(), e);
		}
		BlockingQueue<String> queue = new LinkedBlockingQueue<>();
		replies = queue;
		process = p;
		input = new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8));
		Thread reader = new Thread(() -> read(p, queue), "SkyLyrics Windows media");
		reader.setDaemon(true);
		reader.start();
		startedAt = System.nanoTime();

		String first = queue.poll(startTimeoutMs, TimeUnit.MILLISECONDS);
		JsonObject hello = first == null || first == END ? null : parse(first);
		if (hello == null || !hello.has("ready") || !hello.get("ready").getAsBoolean()) {
			String error = hello == null ? "" : string(hello, "error");
			throw new IOException(!error.isEmpty() ? error
					: first == null ? "PowerShell took too long to start" : "PowerShell stopped right away");
		}
		LOG.info("Reading music from Windows' media controls");
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

	private static void deleteFilesIn(Path dir) {
		try (Stream<Path> files = Files.list(dir)) {
			files.forEach(f -> {
				try {
					Files.deleteIfExists(f);
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
		String run = "& ([scriptblock]::Create([IO.File]::ReadAllText(" + quote(script) + "))) -CoverDir " + quote(coverDir);
		return List.of(exe, "-NoLogo", "-NoProfile", "-NonInteractive", "-Command", run);
	}

	/** A PowerShell string literal: single quotes, with any ' inside doubled. */
	static String quote(Path path) {
		return "'" + path.toString().replace("'", "''") + "'";
	}

	private synchronized void control(String command) {
		Session s = currentSession;
		if (s == null) {
			return;
		}
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
