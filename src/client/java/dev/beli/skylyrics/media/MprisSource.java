package dev.beli.skylyrics.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

	public MprisSource(Supplier<String> preferredPlayer) {
		this.preferredPlayer = preferredPlayer;
	}

	private DBusConnection connection() throws Exception {
		if (connection == null || !connection.isConnected()) {
			try {
				connection = DBusConnectionBuilder.forSessionBus().withShared(false).build();
			} catch (Exception e) {
				throw new IOException(busFailureMessage(System.getenv(), Files.exists(Path.of("/.flatpak-info"))), e);
			}
		}
		return connection;
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

	private record Reading(String busName, Track track, long positionMs, boolean playing, String trackId) {
	}

	@Override
	public Snapshot poll() throws Exception {
		DBusConnection conn = connection();
		DBus bus = conn.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus.class);

		List<Reading> readings = new ArrayList<>();
		for (String name : bus.ListNames()) {
			if (!name.startsWith(PREFIX) || name.equals(PREFIX + "playerctld")) {
				continue;
			}
			Reading reading = read(conn, name);
			if (reading != null && !reading.track.isNone()) {
				readings.add(reading);
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
		return new Snapshot(chosen.track, chosen.positionMs, chosen.playing, System.nanoTime());
	}

	private static @Nullable Reading read(DBusConnection conn, String busName) {
		try {
			Properties props = conn.getRemoteObject(busName, PATH, Properties.class);
			Map<String, Variant<?>> all = props.GetAll(PLAYER_IFACE);

			boolean playing = "Playing".equals(value(all.get("PlaybackStatus")));
			long positionUs = number(value(all.get("Position")));
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

			String player = busName.substring(PREFIX.length());
			int dot = player.indexOf('.');
			if (dot > 0) {
				player = player.substring(0, dot); // "firefox.instance_1_42" -> "firefox"
			}

			Track track = TitleCleaner.clean(new Track(title, artist, album, lengthUs / 1000, art, player, url.length() > 1000 ? "" : url));
			return new Reading(busName, track, Math.max(0, positionUs / 1000), playing, trackId);
		} catch (Exception e) {
			return null; // Player went away mid-read or doesn't implement the interface properly
		}
	}

	private static String prettyName(String player) {
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
		try (DBusConnection conn = DBusConnectionBuilder.forSessionBus().withShared(false).build()) {
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
		try (DBusConnection conn = DBusConnectionBuilder.forSessionBus().withShared(false).build()) {
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
