package dev.beli.skylyrics.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import dev.beli.skylyrics.Tr;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.interfaces.DBus;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.Variant;
import org.jspecify.annotations.Nullable;

/**
 * Reads the playing song from any Linux music player over MPRIS (D-Bus).
 * Spotify, browsers (YouTube, SoundCloud…), mpv, VLC, Rhythmbox, Strawberry and most others support it.
 */
public final class MprisSource implements MediaSource {
	private static final String PREFIX = "org.mpris.MediaPlayer2.";
	private static final String PATH = "/org/mpris/MediaPlayer2";
	private static final String PLAYER_IFACE = "org.mpris.MediaPlayer2.Player";

	@DBusInterfaceName(PLAYER_IFACE)
	public interface Player extends DBusInterface {
		void PlayPause();

		void Next();

		void Previous();

		void SetPosition(DBusPath trackId, long positionUs);

		void Seek(long offsetUs);

		void OpenUri(String uri);
	}

	private final Supplier<String> preferredPlayer;
	private @Nullable DBusConnection connection;
	private @Nullable String current;
	private @Nullable String currentTrackId;
	/** Where the chosen player was at the last poll, for a player that can only seek relative to now. */
	private volatile long lastPositionMs;
	private volatile long lastPositionAtNs;
	private volatile boolean lastPlaying;
	private volatile double lastRate = 1.0;
	private String currentDisplayName = "";

	/** Where the players' state comes from; replaced in tests. */
	interface Reader {
		/** The bus names currently on the bus. */
		List<String> names() throws Exception;

		/** One player's state, or null when it has nothing to show. Throws when it can't be read. */
		@Nullable Reading read(String busName) throws Exception;
	}

	private final Reader reader;
	private final BooleanSupplier flatpak;
	/** How many polls in a row saw no MPRIS player at all. */
	private int noPlayerPolls;
	/** How many polls in a row with no player at all mean the Flatpak sandbox hides them. */
	static final int HIDDEN_POLLS = 3;

	public MprisSource(Supplier<String> preferredPlayer) {
		this.preferredPlayer = preferredPlayer;
		this.flatpak = () -> hidesPlayers(System.getenv(), flatpakInfo());
		this.reader = new Reader() {
			@Override
			public List<String> names() throws Exception {
				return List.of(connection().getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus.class).ListNames());
			}

			@Override
			public @Nullable Reading read(String busName) throws Exception {
				return MprisSource.read(connection(), busName);
			}
		};
	}

	MprisSource(Supplier<String> preferredPlayer, Reader reader) {
		this(preferredPlayer, reader, () -> false);
	}

	MprisSource(Supplier<String> preferredPlayer, Reader reader, BooleanSupplier flatpak) {
		this.preferredPlayer = preferredPlayer;
		this.reader = reader;
		this.flatpak = flatpak;
	}

	/** Opens the session bus connection; replaced in tests. */
	interface Connector {
		DBusConnection connect() throws Exception;
	}

	private static final long RETRY_FIRST_NS = 5_000_000_000L;
	private static final long RETRY_MAX_NS = 30_000_000_000L;

	private Connector connector = MprisSource::connectSession;
	private LongSupplier nanos = System::nanoTime;
	private Predicate<DBusConnection> connected = DBusConnection::isConnected;
	// volatile: close() resets these from the shutdown thread
	private volatile int connectFailures;
	private volatile long retryAt;
	private volatile @Nullable IOException lastConnectFailure;

	/** For tests: the way the bus is opened, whether a connection still lives, and the clock. */
	MprisSource(Supplier<String> preferredPlayer, Connector connector, Predicate<DBusConnection> connected, LongSupplier nanos) {
		this(preferredPlayer);
		this.connector = connector;
		this.connected = connected;
		this.nanos = nanos;
	}

	/** How long to wait before trying the bus again after this many failed tries in a row: 5 s, 10 s, 20 s, then 30 s. */
	static long retryDelayNs(int failuresInARow) {
		int doublings = Math.max(0, Math.min(failuresInARow, 31) - 1);
		return Math.min(RETRY_MAX_NS, RETRY_FIRST_NS << Math.min(doublings, 10));
	}

	synchronized DBusConnection connection() throws Exception {
		if (connection == null || !connected.test(connection)) {
			// No bus (a container, a launcher that strips the environment): don't build a connection every poll
			IOException wait = lastConnectFailure;
			if (wait != null && nanos.getAsLong() - retryAt < 0) {
				throw wait;
			}
			try {
				connection = connector.connect();
				connectFailures = 0;
				lastConnectFailure = null;
			} catch (Exception e) {
				connectFailures++;
				retryAt = nanos.getAsLong() + retryDelayNs(connectFailures);
				IOException failure = new IOException(busFailureMessage(System.getenv(), Files.exists(Path.of("/.flatpak-info"))), e);
				lastConnectFailure = failure;
				throw failure; // the local: close() may clear the field in between
			}
		}
		return connection;
	}

	/**
	 * The session bus address to connect to, or null to leave it to the library (the environment's address as it is,
	 * or nothing usable). The transport only opens {@code unix:path=} addresses and doesn't split a ';' list, so the
	 * first such entry of the environment's list is used; when there is none (abstract, tmpdir, empty, unset) it is
	 * the user's runtime-dir socket, if it exists.
	 *
	 * @param uid the user id, "" when unknown
	 */
	static @Nullable String sessionBusAddress(Map<String, String> env, Predicate<String> exists, String uid) {
		String given = env.get("DBUS_SESSION_BUS_ADDRESS");
		if (given != null && !given.isBlank()) {
			for (String entry : given.split(";")) {
				String e = entry.strip();
				if (e.startsWith("unix:path=")) {
					return e.equals(given.strip()) ? null : e;
				}
			}
		}
		return runtimeBusAddress(env, exists, uid);
	}

	/** The user's runtime-dir session bus socket as an address, or null when there is none. */
	static @Nullable String runtimeBusAddress(Map<String, String> env, Predicate<String> exists, String uid) {
		String runtime = env.get("XDG_RUNTIME_DIR");
		if (runtime != null && !runtime.isBlank()) {
			String socket = runtime.strip().replaceAll("/+$", "") + "/bus";
			if (exists.test(socket)) {
				return "unix:path=" + socket;
			}
		}
		if (!uid.isBlank()) {
			String socket = "/run/user/" + uid + "/bus";
			if (exists.test(socket)) {
				return "unix:path=" + socket;
			}
		}
		return null;
	}

	private static String currentUid() {
		try {
			return String.valueOf(Files.getAttribute(Path.of("/proc/self"), "unix:uid"));
		} catch (Exception | LinkageError e) {
			return "";
		}
	}

	private static DBusConnectionBuilder sessionBus() {
		String address = sessionBusAddress(System.getenv(), p -> Files.exists(Path.of(p)), currentUid());
		return (address == null ? DBusConnectionBuilder.forSessionBus() : DBusConnectionBuilder.forAddress(address)).withShared(false);
	}

	/** The socket path of a {@code unix:path=} address (without {@code ,guid=} and the like), "" for anything else. */
	static String socketPath(@Nullable String address) {
		if (address == null || !address.strip().startsWith("unix:path=")) {
			return "";
		}
		String rest = address.strip().substring("unix:path=".length());
		int comma = rest.indexOf(',');
		return comma < 0 ? rest : rest.substring(0, comma);
	}

	/** Connects to the session bus; when that fails (a stale address after a re-login) the runtime-dir socket is tried once. */
	private static DBusConnection connectSession() throws Exception {
		try {
			return sessionBus().build();
		} catch (Exception first) {
			Map<String, String> env = System.getenv();
			Predicate<String> exists = p -> Files.exists(Path.of(p));
			String tried = sessionBusAddress(env, exists, currentUid());
			String effective = tried != null ? tried : env.get("DBUS_SESSION_BUS_ADDRESS");
			String runtime = runtimeBusAddress(env, exists, currentUid());
			if (runtime == null || socketPath(runtime).equals(socketPath(effective))) {
				throw first;
			}
			try {
				return DBusConnectionBuilder.forAddress(runtime).withShared(false).build();
			} catch (Exception second) {
				first.addSuppressed(second);
				throw first;
			}
		}
	}

	private static @Nullable String flatpakInfoText;
	private static boolean flatpakInfoRead;

	/** The text of /.flatpak-info (read once), or null outside a Flatpak or when it can't be read. */
	private static synchronized @Nullable String flatpakInfo() {
		if (!flatpakInfoRead) {
			flatpakInfoRead = true;
			try {
				flatpakInfoText = Files.readString(Path.of("/.flatpak-info"));
			} catch (Exception e) {
				flatpakInfoText = null;
			}
		}
		return flatpakInfoText;
	}

	/**
	 * Whether an empty player list should be blamed on the Flatpak sandbox: it is a Flatpak, and its permissions give
	 * neither the whole session bus nor talking to the MPRIS names. {@code info} is the text of /.flatpak-info, null
	 * when it is missing (then nothing is said).
	 */
	static boolean hidesPlayers(Map<String, String> env, @Nullable String info) {
		if (info == null) {
			return false;
		}
		String section = "";
		for (String raw : info.split("\\R")) {
			String line = raw.strip();
			if (line.startsWith("[") && line.endsWith("]")) {
				section = line.substring(1, line.length() - 1);
				continue;
			}
			int eq = line.indexOf('=');
			if (eq < 0) {
				continue;
			}
			String key = line.substring(0, eq).strip();
			String value = line.substring(eq + 1).strip();
			if (section.equals("Context") && key.equals("sockets")) {
				for (String socket : value.split(";")) {
					if (socket.strip().equals("session-bus")) {
						return false;
					}
				}
			} else if (section.equals("Session Bus Policy") && key.startsWith("org.mpris.MediaPlayer2")
					&& (value.equals("talk") || value.equals("own"))) {
				return false;
			}
		}
		return true;
	}

	/** Whether the game runs inside a Flatpak sandbox, which hides the music players from it. */
	static boolean inFlatpak(Map<String, String> env, boolean flatpakInfoExists) {
		String id = env.get("FLATPAK_ID");
		return (id != null && !id.isBlank()) || flatpakInfoExists;
	}

	/** The Flatpak app id, or a placeholder the player fills in when it isn't known. */
	static String flatpakAppId(Map<String, String> env) {
		String id = env.get("FLATPAK_ID");
		return id == null || id.isBlank() ? "<app id>" : id.strip();
	}

	static String busFailureMessage(Map<String, String> env, boolean flatpakInfoExists) {
		if (inFlatpak(env, flatpakInfoExists)) {
			return Tr.t("Minecraft runs as a Flatpak and can't see your music players. Allow it with: flatpak override --user --talk-name=org.mpris.MediaPlayer2.* %s", flatpakAppId(env));
		}
		return Tr.t("Can't reach your music players (no D-Bus session). Start the game from your desktop session.");
	}

	record Reading(String busName, Track track, long positionMs, boolean playing, String trackId, double rate) {
		/** The speed as the song clock takes it, so a reading moved on by hand and a relative seek agree with the lyrics. */
		Reading {
			rate = Snapshot.usableRate(rate);
		}

		Reading(String busName, Track track, long positionMs, boolean playing, String trackId) {
			this(busName, track, positionMs, playing, trackId, 1.0);
		}

		/** The same reading {@code ms} later (a playing song has moved on). */
		Reading advancedBy(long ms) {
			long pos = playing ? positionMs + (long) (ms * rate) : positionMs;
			if (track.lengthMs() > 0) {
				pos = Math.min(pos, track.lengthMs());
			}
			return new Reading(busName, track, pos, playing, trackId, rate);
		}
	}

	/** How long poll() waits for the players to answer before going on with the ones that did. */
	static final long ANSWER_WAIT_MS = 750;
	/** How long the last good reading of a player that doesn't answer is still believed. */
	static final long KEEP_READING_MS = 5000;

	private record Remembered(Reading reading, long atNanos) {
	}

	/** What a read answered and when it asked, so a slow answer is not mistaken for a fresh one. */
	private record Answered(@Nullable Reading reading, long askedAtNanos) {
	}

	private final ExecutorService readers = Executors.newCachedThreadPool(r -> {
		Thread t = new Thread(r, "SkyLyrics MPRIS read");
		t.setDaemon(true);
		return t;
	});
	/** Reads still running from an earlier poll (a stuck player); only touched on the media thread. */
	private final Map<String, Future<Answered>> asked = new HashMap<>();
	private final Map<String, Remembered> lastGood = new HashMap<>();
	/** How many polls in a row the followed player has answered with nothing (a blip between songs). */
	private int emptyPolls;
	/** Polls (half a second apart, so about a second in all) the followed player may answer with nothing before another player takes over. */
	static final int EMPTY_GRACE_POLLS = 2;

	/** Held for the whole of a poll, which is the only code that touches {@link #asked} and {@link #lastGood}. */
	private final java.util.concurrent.locks.ReentrantLock pollLock = new java.util.concurrent.locks.ReentrantLock();
	/** Set by close(); a poll that was still running then clears the maps itself when it ends. */
	private volatile boolean closed;
	/** How long close() waits for a poll in progress before leaving the clearing to it. */
	static final long CLOSE_WAIT_MS = 250;

	@Override
	public Snapshot poll() throws Exception {
		pollLock.lock();
		try {
			return pollLocked();
		} finally {
			if (closed) {
				clearReads();
			}
			pollLock.unlock();
		}
	}

	private void clearReads() {
		for (Future<Answered> f : asked.values()) {
			f.cancel(true);
		}
		asked.clear();
		lastGood.clear();
	}

	private Snapshot pollLocked() throws Exception {
		List<String> names = new ArrayList<>();
		for (String name : reader.names()) {
			if (name.startsWith(PREFIX) && !name.equals(PREFIX + "playerctld")) {
				names.add(name);
			}
		}
		// In a Flatpak the bus connects but the sandbox hides every player: say so instead of "nothing playing"
		if (names.isEmpty() && flatpak.getAsBoolean()) {
			if (++noPlayerPolls >= HIDDEN_POLLS) {
				throw new IOException(Tr.t("If music is playing and nothing shows: Minecraft runs as a Flatpak and may not see your music players. Allow it with: flatpak override --user --talk-name=org.mpris.MediaPlayer2.* %s", flatpakAppId(System.getenv())));
			}
		} else {
			noPlayerPolls = 0;
		}
		asked.keySet().removeIf(n -> {
			if (names.contains(n)) {
				return false;
			}
			asked.get(n).cancel(true);
			return true;
		});
		lastGood.keySet().retainAll(names);

		// Ask every player at once; one that's stuck only costs ANSWER_WAIT_MS, and is not asked again until it answers
		Set<String> fresh = new HashSet<>();
		for (String name : names) {
			if (!asked.containsKey(name)) {
				asked.put(name, readers.submit(() -> {
					long askedAt = System.nanoTime();
					return new Answered(reader.read(name), askedAt);
				}));
				fresh.add(name);
			}
		}
		long deadline = System.nanoTime() + ANSWER_WAIT_MS * 1_000_000;

		List<Reading> readings = new ArrayList<>();
		// When each reading's position was true: the moment its read asked (a read that was pending from an
		// earlier poll is old by now), or now for one moved on by hand
		Map<Reading, Long> readAt = new IdentityHashMap<>();
		for (String name : names) {
			Future<Answered> future = asked.get(name);
			Reading reading = null;
			long askedAt = 0;
			boolean answered = false;
			try {
				long wait = fresh.contains(name) ? Math.max(0, deadline - System.nanoTime()) : 0;
				Answered a = future.get(wait, TimeUnit.NANOSECONDS);
				reading = a.reading();
				askedAt = a.askedAtNanos();
				answered = true;
			} catch (TimeoutException e) {
				// Still thinking: keep waiting for it in the background
			} catch (ExecutionException e) {
				// The read failed (timed out on the bus, odd reply...)
			}
			if (answered || future.isDone()) {
				asked.remove(name);
			}
			if (answered) {
				if (reading == null || reading.track.isNone()) {
					// The followed player, still on the bus, may blink between songs: for a couple of polls keep
					// what it said last, so a muted browser tab doesn't take over and then keep the lyrics
					if (!(name.equals(current) && lastGood.containsKey(name) && ++emptyPolls <= EMPTY_GRACE_POLLS)) {
						lastGood.remove(name);
						continue;
					}
				} else {
					if (name.equals(current)) {
						emptyPolls = 0;
					}
					lastGood.put(name, new Remembered(reading, askedAt));
					readings.add(reading);
					readAt.put(reading, askedAt);
					continue;
				}
			}
			// No answer this time: go on with what it said last, moved along by the time since
			Remembered kept = lastGood.get(name);
			if (kept != null) {
				long ageMs = (System.nanoTime() - kept.atNanos) / 1_000_000;
				if (ageMs <= KEEP_READING_MS) {
					Reading moved = kept.reading.advancedBy(ageMs);
					readings.add(moved);
					readAt.put(moved, System.nanoTime());
				} else {
					lastGood.remove(name);
				}
			}
		}

		Reading chosen = PlayerPicker.choose(readings, Reading::busName, r -> r.track.player(), Reading::playing,
				preferredPlayer.get(), current);
		if (chosen == null) {
			current = null;
			emptyPolls = 0;
			currentTrackId = null;
			return Snapshot.NOTHING;
		}
		if (!chosen.busName.equals(current)) {
			emptyPolls = 0;
		}
		current = chosen.busName;
		currentTrackId = chosen.trackId;
		lastPositionMs = chosen.positionMs;
		Long at = readAt.get(chosen);
		lastPositionAtNs = at != null ? at : System.nanoTime();
		lastPlaying = chosen.playing;
		lastRate = chosen.rate;
		currentDisplayName = prettyName(chosen.track.player());
		return new Snapshot(chosen.track, chosen.positionMs, chosen.playing, readAt.get(chosen), chosen.rate);
	}

	/** The Position from GetAll, or, when the player left it out of that, asked for once on its own (0 if it doesn't say). */
	static long positionMicros(Map<String, Variant<?>> all, Callable<Object> getPosition) {
		if (all.containsKey("Position")) {
			return number(value(all.get("Position")));
		}
		try {
			return number(value(getPosition.call()));
		} catch (Exception e) {
			return 0;
		}
	}

	/** Whether the player says it is Stopped (not just Paused). */
	static boolean stopped(Map<String, Variant<?>> all) {
		return "Stopped".equals(value(all.get("PlaybackStatus")));
	}

	private static @Nullable Reading read(DBusConnection conn, String busName) throws Exception {
		Properties props = conn.getRemoteObject(busName, PATH, Properties.class);
		Map<String, Variant<?>> all = props.GetAll(PLAYER_IFACE);

		if (stopped(all)) {
			return null; // a stopped player may keep its old song's data: that isn't playing anything
		}
		boolean playing = "Playing".equals(value(all.get("PlaybackStatus")));
		long positionUs = positionMicros(all, () -> props.Get(PLAYER_IFACE, "Position"));
		Object metaObj = value(all.get("Metadata"));
		if (!(metaObj instanceof Map<?, ?> meta)) {
			return null;
		}

		String title = string(meta.get("xesam:title"));
		String artist = joined(meta.get("xesam:artist"));
		String album = string(meta.get("xesam:album"));
		long lengthUs = number(value(meta.get("mpris:length")));
		String art = string(meta.get("mpris:artUrl"));
		String trackId = string(meta.get("mpris:trackid"));
		String url = string(meta.get("xesam:url"));

		String player = playerId(busName);

		Track track = TitleCleaner.clean(new Track(title, artist, album, lengthUs / 1000, art, player, url.length() > 1000 ? "" : songUri(player, url)));
		double rate = value(all.get("Rate")) instanceof Number n ? n.doubleValue() : 1.0;
		return new Reading(busName, track, Math.max(0, positionUs / 1000), playing, trackId, rate);
	}

	private static final Pattern SPOTIFY_SONG_URL = Pattern.compile("https://open\\.spotify\\.com/(track|episode)/([A-Za-z0-9]+)(?:[?#].*)?");

	/**
	 * The song address to keep. Spotify's desktop app sends "https://open.spotify.com/track/ID" as xesam:url; it
	 * becomes "spotify:track:ID", the form the other readers use, so two songs with the same title, artist and album
	 * are told apart. Only for the Spotify app itself: a browser's page address changes when you click around.
	 */
	static String songUri(String player, String url) {
		if ("spotify".equals(player)) {
			Matcher m = SPOTIFY_SONG_URL.matcher(url);
			if (m.matches()) {
				return "spotify:" + m.group(1) + ":" + m.group(2);
			}
		}
		return url;
	}

	/**
	 * The player's id from its bus name: a trailing ".instance..." goes ("firefox.instance_1_42" -> "firefox"); then
	 * a name with one dot after the prefix is cut there as always ("kdeconnect.mpris_000001" -> "kdeconnect"), and a
	 * real reverse-DNS name with two or more dots stays whole ("io.bassi.Amberol").
	 */
	static String playerId(String busName) {
		String id = busName.startsWith(PREFIX) ? busName.substring(PREFIX.length()) : busName;
		id = id.replaceFirst("\\.instance[_0-9]*$", "");
		long dots = id.chars().filter(c -> c == '.').count();
		int first = id.indexOf('.');
		return dots == 1 && first > 0 ? id.substring(0, first) : id;
	}

	static String prettyName(String player) {
		int dot = player.lastIndexOf('.');
		if (dot > 0 && dot < player.length() - 1) {
			player = player.substring(dot + 1); // "io.bassi.Amberol" -> "Amberol"
		}
		return switch (player.toLowerCase(Locale.ROOT)) {
			case "spotify" -> "Spotify";
			case "firefox" -> "Firefox";
			case "chromium", "chrome", "google-chrome" -> "Chrome";
			case "brave" -> "Brave";
			case "vlc" -> "VLC";
			case "mpv" -> "mpv";
			case "" -> "";
			default -> Character.toUpperCase(player.charAt(0)) + player.substring(1);
		};
	}

	private @Nullable Player player() {
		try {
			return current == null ? null : connection().getRemoteObject(current, PATH, Player.class);
		} catch (Exception e) {
			return null;
		}
	}

	/** The bus names of the running players called {@code player} ("firefox" also finds "firefox.instance_1_42"). */
	private static List<String> busNames(DBus bus, String player) {
		List<String> found = new ArrayList<>();
		for (String name : bus.ListNames()) {
			if (name.equals(PREFIX + player) || name.startsWith(PREFIX + player + ".")) {
				found.add(name);
			}
		}
		return found;
	}

	/**
	 * Which kinds of address (file, https, spotify…) the running player {@code player} says it can open, or null
	 * when no such player is running. Browsers usually list none.
	 */
	public static @Nullable List<String> supportedSchemes(String player) {
		try (DBusConnection conn = connectSession()) {
			DBus bus = conn.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus.class);
			List<String> names = busNames(bus, player);
			if (names.isEmpty()) {
				return null;
			}
			List<String> schemes = new ArrayList<>();
			try {
				Object list = value(conn.getRemoteObject(names.getFirst(), PATH, Properties.class)
						.Get("org.mpris.MediaPlayer2", "SupportedUriSchemes"));
				if (list instanceof Object[] array) {
					list = List.of(array);
				}
				if (list instanceof Collection<?> items) {
					for (Object item : items) {
						schemes.add(string(item).toLowerCase(Locale.ROOT));
					}
				}
			} catch (Exception e) {
				// The player doesn't say: it can't open anything we know of
			}
			return schemes;
		} catch (Exception e) {
			return null;
		}
	}

	/** Asks the running player {@code player} to play {@code uri}. False if it isn't running or refuses. */
	public static boolean openUri(String player, String uri) {
		try (DBusConnection conn = connectSession()) {
			DBus bus = conn.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus.class);
			for (String name : busNames(bus, player)) {
				conn.getRemoteObject(name, PATH, Player.class).OpenUri(uri);
				return true;
			}
		} catch (Exception e) {
			// No session bus, or the player went away
		}
		return false;
	}

	/**
	 * Asks the Spotify app on this computer to play {@code uri}. Unlike starting a song through Spotify's servers this
	 * works without Premium, but it plays just that one song. False if the Spotify app isn't running.
	 */
	public static boolean openInSpotify(String uri) {
		return openUri("spotify", uri);
	}

	@Override
	public void playPause() {
		Player p = player();
		if (p != null) {
			p.PlayPause();
		}
	}

	@Override
	public void next() {
		Player p = player();
		if (p != null) {
			p.Next();
		}
	}

	@Override
	public void previous() {
		Player p = player();
		if (p != null) {
			p.Previous();
		}
	}

	@Override
	public void seek(long positionMs) {
		Player p = player();
		if (p != null) {
			long known = lastPositionMs;
			if (lastPlaying) {
				known += (long) ((System.nanoTime() - lastPositionAtNs) / 1_000_000 * lastRate);
			}
			seekIfTrack(p, currentTrackId, positionMs, known);
		}
	}

	/** A player that sends no track id at all (null or empty) gets the relative Seek too. */
	static void seekIfTrack(Player p, @Nullable String trackId, long targetMs, long knownMs) {
		seek(p, trackId == null ? "" : trackId, targetMs, knownMs);
	}

	/** SetPosition when the track id is an object path, otherwise the relative Seek from the position we know. */
	static void seek(Player p, String trackId, long targetMs, long knownMs) {
		if (usableTrackId(trackId)) {
			p.SetPosition(new DBusPath(trackId), targetMs * 1000);
		} else {
			p.Seek((targetMs - knownMs) * 1000);
		}
	}

	/** True for a track id SetPosition can carry: a D-Bus object path, and not the spec's "no track" id (which players ignore). */
	static boolean usableTrackId(String id) {
		return validObjectPath(id) && !id.equals("/org/mpris/MediaPlayer2/TrackList/NoTrack");
	}

	/** The D-Bus object path rules: "/" alone, or "/" and segments of A-Z a-z 0-9 _ split by single "/", no trailing "/". */
	static boolean validObjectPath(String path) {
		if (path.equals("/")) {
			return true;
		}
		if (path.length() < 2 || path.charAt(0) != '/' || path.charAt(path.length() - 1) == '/') {
			return false;
		}
		boolean segmentStart = true;
		for (int i = 1; i < path.length(); i++) {
			char c = path.charAt(i);
			if (c == '/') {
				if (segmentStart) {
					return false;
				}
				segmentStart = true;
			} else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') {
				segmentStart = false;
			} else {
				return false;
			}
		}
		return true;
	}

	@Override
	public String displayName() {
		return currentDisplayName;
	}

	@Override
	public void close() {
		closed = true;
		// A deliberate reconnect must not wait out the back-off of an earlier failure
		connectFailures = 0;
		retryAt = 0;
		lastConnectFailure = null;
		// The maps belong to the poll: clear them here only if no poll is running (or it ends within a moment),
		// otherwise the poll clears them itself as it finishes
		boolean got = false;
		try {
			got = pollLock.tryLock(CLOSE_WAIT_MS, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		if (got) {
			try {
				clearReads();
			} finally {
				pollLock.unlock();
			}
		}
		if (connection != null) {
			connection.disconnect();
			connection = null;
		}
	}

	private static @Nullable Object value(@Nullable Object o) {
		while (o instanceof Variant<?> v) {
			o = v.getValue();
		}
		return o;
	}

	private static String string(@Nullable Object o) {
		o = value(o);
		if (o instanceof DBusPath path) {
			return path.getPath();
		}
		return o == null ? "" : o.toString().trim();
	}

	private static String joined(@Nullable Object o) {
		o = value(o);
		if (o instanceof Object[] array) {
			o = List.of(array);
		}
		if (o instanceof Collection<?> list) {
			List<String> parts = new ArrayList<>();
			for (Object part : list) {
				String s = string(part);
				if (!s.isEmpty()) {
					parts.add(s);
				}
			}
			return String.join(", ", parts);
		}
		return string(o);
	}

	private static long number(@Nullable Object o) {
		o = value(o);
		return o instanceof Number n ? n.longValue() : 0;
	}
}
