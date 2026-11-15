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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
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

		void OpenUri(String uri);
	}

	private final Supplier<String> preferredPlayer;
	private @Nullable DBusConnection connection;
	private @Nullable String current;
	private @Nullable String currentTrackId;
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
		this.flatpak = () -> inFlatpak(System.getenv(), Files.exists(Path.of("/.flatpak-info")));
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

	private synchronized DBusConnection connection() throws Exception {
		if (connection == null || !connection.isConnected()) {
			try {
				connection = sessionBus().build();
			} catch (Exception e) {
				throw new IOException(busFailureMessage(System.getenv(), Files.exists(Path.of("/.flatpak-info"))), e);
			}
		}
		return connection;
	}

	/**
	 * The session bus address to connect to when the environment doesn't give one (launchers that clear it, pure
	 * Wayland): the user's runtime-dir socket, if it exists. Null means leave it to the library.
	 *
	 * @param uid the user id, "" when unknown
	 */
	static @Nullable String sessionBusAddress(Map<String, String> env, Predicate<String> exists, String uid) {
		String given = env.get("DBUS_SESSION_BUS_ADDRESS");
		if (given != null && !given.isBlank()) {
			return null;
		}
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

	@Override
	public Snapshot poll() throws Exception {
		List<String> names = new ArrayList<>();
		for (String name : reader.names()) {
			if (name.startsWith(PREFIX) && !name.equals(PREFIX + "playerctld")) {
				names.add(name);
			}
		}
		// In a Flatpak the bus connects but the sandbox hides every player: say so instead of "nothing playing"
		if (names.isEmpty() && flatpak.getAsBoolean()) {
			if (++noPlayerPolls >= HIDDEN_POLLS) {
				throw new IOException(busFailureMessage(System.getenv(), true));
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
					lastGood.remove(name);
				} else {
					lastGood.put(name, new Remembered(reading, askedAt));
					readings.add(reading);
					readAt.put(reading, askedAt);
				}
				continue;
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
			currentTrackId = null;
			return Snapshot.NOTHING;
		}
		current = chosen.busName;
		currentTrackId = chosen.trackId;
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

	private static @Nullable Reading read(DBusConnection conn, String busName) throws Exception {
		Properties props = conn.getRemoteObject(busName, PATH, Properties.class);
		Map<String, Variant<?>> all = props.GetAll(PLAYER_IFACE);

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

		Track track = TitleCleaner.clean(new Track(title, artist, album, lengthUs / 1000, art, player, url.length() > 1000 ? "" : url));
		double rate = value(all.get("Rate")) instanceof Number n ? n.doubleValue() : 1.0;
		return new Reading(busName, track, Math.max(0, positionUs / 1000), playing, trackId, rate);
	}

	/** The player's id from its bus name: "firefox.instance_1_42" -> "firefox", "io.bassi.Amberol" stays whole. */
	static String playerId(String busName) {
		String id = busName.startsWith(PREFIX) ? busName.substring(PREFIX.length()) : busName;
		return id.replaceFirst("\\.instance[_0-9]*$", "");
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
		try (DBusConnection conn = sessionBus().build()) {
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
		try (DBusConnection conn = sessionBus().build()) {
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
		if (p != null && currentTrackId != null && !currentTrackId.isEmpty()) {
			p.SetPosition(new DBusPath(currentTrackId), positionMs * 1000);
		}
	}

	@Override
	public String displayName() {
		return currentDisplayName;
	}

	@Override
	public void close() {
		for (Future<Answered> f : asked.values()) {
			f.cancel(true);
		}
		asked.clear();
		lastGood.clear();
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
