package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.UInt64;
import org.freedesktop.dbus.types.Variant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.Fuzz;

/**
 * What the music players hand over (D-Bus properties on Linux, the Windows helper's JSON lines, the Mac script's tab
 * lines) is whatever the playing app put in its song info: control characters, lone surrogates, 100k characters,
 * positions and lengths of any size or sign. None of it may throw out of the readers or give a song the rest of the
 * mod can't show.
 */
class PlayerFuzzTest {
	private static final Duration BOUND = Duration.ofSeconds(20);

	/** What a {@link Track} promises once it is made, whatever it was made from. */
	private static void checkTrack(Track t) {
		for (String s : List.of(t.title(), t.artist(), t.album())) {
			assertTrue(s.length() <= Track.MAX_TEXT, "field of " + s.length());
			assertTrue(s.chars().noneMatch(c -> Character.getType(c) == Character.CONTROL), "control char in " + Fuzz.show(s));
			assertFalse(s.indexOf('\u00a7') >= 0, "formatting code in " + Fuzz.show(s));
			assertTrue(s.chars().noneMatch(c -> c >= 0x202A && c <= 0x202E || c >= 0x2066 && c <= 0x2069), "direction override in " + Fuzz.show(s));
			assertTrue(s.equals(s.strip()), "unstripped " + Fuzz.show(s));
		}
		t.key();
		t.isNone();
		t.sameSong(t);
	}

	private static String big(Fuzz f) {
		return switch (f.random.nextInt(8)) {
			case 0 -> f.text(4).repeat(1 + f.random.nextInt(20_000)); // up to 100k and more
			case 1 -> "(" + "a".repeat(f.random.nextInt(100_000)); // an unclosed bracket, long
			case 2 -> "\u00a7".repeat(f.random.nextInt(1000)) + "x";
			default -> f.nasty(60);
		};
	}

	@Test
	void anySongInfoMakesAShowableTrack() {
		assertTimeoutPreemptively(BOUND, () -> Fuzz.cases(10, 600, (f, i) -> {
			Track t = new Track(big(f), big(f), big(f), f.random.nextLong(), f.nasty(20), f.nasty(5), f.nasty(10));
			checkTrack(t);
			checkTrack(TitleCleaner.clean(t));
			checkTrack(TitleCleaner.clean(new Track(big(f), big(f), "", 0, "", "firefox")));
		}));
	}

	@Test
	void titleCleanerTakesAnyText() {
		assertTimeoutPreemptively(BOUND, () -> Fuzz.cases(11, 500, (f, i) -> {
			String a = big(f), b = f.random.nextBoolean() ? big(f) : f.mutate(a);
			TitleCleaner.forSearch(a);
			TitleCleaner.withoutVersionBrackets(a);
			TitleCleaner.key(a);
			TitleCleaner.songKey(a);
			TitleCleaner.sameSong(a, b);
			TitleCleaner.withoutBrackets(a);
			TitleCleaner.shownArtist(a);
			TitleCleaner.mainArtist(a);
			assertTrue(TitleCleaner.capped(a).length() <= TitleCleaner.MAX_TEXT);
		}));
	}

	// Linux: D-Bus properties

	/** A D-Bus value of a random type, now and then the right one for the key. */
	private static Variant<?> variant(Fuzz f, String text) {
		return switch (f.random.nextInt(12)) {
			case 0 -> new Variant<>(f.random.nextLong());
			case 1 -> new Variant<>(f.random.nextInt());
			case 2 -> new Variant<>(f.random.nextBoolean());
			case 3 -> new Variant<>(switch (f.random.nextInt(6)) {
				case 0 -> Double.NaN;
				case 1 -> Double.POSITIVE_INFINITY;
				case 2 -> -1e300;
				case 3 -> 1e300;
				case 4 -> -0.0;
				default -> f.random.nextDouble() * 1e6;
			});
			case 4 -> new Variant<>(new UInt64(Long.MAX_VALUE)); // unsigned: 2^63 - 1
			case 5 -> new Variant<>(new UInt32(f.random.nextInt(Integer.MAX_VALUE)));
			case 6 -> new Variant<>(new DBusPath("/" + f.text(3).replaceAll("[^A-Za-z0-9_]", "")));
			case 7 -> new Variant<>(new String[] {text, f.nasty(10), ""}, "as");
			case 8 -> new Variant<>(new Variant<>(text), "v");
			case 9 -> new Variant<>((byte) f.random.nextInt());
			default -> new Variant<>(text);
		};
	}

	private static Map<String, Variant<?>> properties(Fuzz f) {
		Map<String, Variant<?>> meta = new LinkedHashMap<>();
		for (String key : List.of("xesam:title", "xesam:artist", "xesam:album", "mpris:length", "mpris:artUrl", "mpris:trackid", "xesam:url")) {
			if (f.random.nextInt(6) > 0) {
				meta.put(key, variant(f, big(f)));
			}
		}
		Map<String, Variant<?>> all = new HashMap<>();
		all.put("PlaybackStatus", f.random.nextInt(4) > 0 ? new Variant<>(new String[] {"Playing", "Paused", "Stopped"}[f.random.nextInt(3)]) : variant(f, f.nasty(8)));
		if (f.random.nextBoolean()) {
			all.put("Position", variant(f, "0"));
		}
		if (f.random.nextBoolean()) {
			all.put("Rate", variant(f, "1"));
		}
		if (f.random.nextInt(8) > 0) {
			all.put("Metadata", new Variant<>(meta, "a{sv}"));
		} else {
			all.put("Metadata", variant(f, f.nasty(10)));
		}
		return all;
	}

	@Test
	void anyPlayerPropertiesGiveASongOrNone() {
		assertTimeoutPreemptively(BOUND, () -> Fuzz.cases(12, 1500, (f, i) -> {
			Map<String, Variant<?>> all = properties(f);
			String bus = "org.mpris.MediaPlayer2." + (f.random.nextBoolean() ? "spotify" : f.nasty(10));
			Variant<?> asked = variant(f, "5");
			MprisSource.Reading r = MprisSource.reading(bus, all, () -> {
				if (f.random.nextInt(4) == 0) {
					throw new IllegalStateException("no answer");
				}
				return asked;
			});
			if (r == null) {
				return;
			}
			checkTrack(r.track());
			assertTrue(r.positionMs() >= 0, "position " + r.positionMs());
			// A speed of over 1000x (no player has one; the Windows reader takes over 4x as 1x) can run the moved-on position
			// past what a long holds: whether to cap it is a question for the owner, not checked here
			for (long ms : r.rate() > 1000 ? new long[0] : new long[] {0, 1, 1000, 3_600_000}) {
				MprisSource.Reading later = r.advancedBy(ms);
				assertTrue(later.positionMs() >= 0, "rate " + r.rate() + " moved " + r.positionMs() + " by " + ms + " to " + later.positionMs());
			}
			MprisSource.prettyName(r.track().player());
			MprisSource.validObjectPath(r.trackId());
			MprisSource.usableTrackId(r.trackId());
		}));
	}

	@Test
	void busNamesAddressesAndFlatpakInfoOfAnyText() {
		assertTimeoutPreemptively(BOUND, () -> Fuzz.cases(13, 2000, (f, i) -> {
			String s = f.nasty(30);
			MprisSource.prettyName(MprisSource.playerId(s));
			MprisSource.prettyName(s);
			MprisSource.songUri("spotify", f.random.nextBoolean() ? s : f.mutate("https://open.spotify.com/track/abc123?si=x"));
			MprisSource.validObjectPath(s);
			MprisSource.socketPath(f.random.nextBoolean() ? s : f.mutate("unix:path=/run/user/1000/bus,guid=abc"));
			Map<String, String> env = Map.of("DBUS_SESSION_BUS_ADDRESS", f.mutate("unix:abstract=/tmp/x;unix:path=/run/user/1000/bus"),
					"XDG_RUNTIME_DIR", f.nasty(10), "FLATPAK_ID", f.nasty(5));
			MprisSource.sessionBusAddress(env, p -> f.random.nextBoolean(), f.nasty(4));
			MprisSource.hidesPlayers(env, f.random.nextBoolean() ? f.nasty(80)
					: f.mutate("[Context]\nsockets=x11;wayland;\n[Session Bus Policy]\norg.mpris.MediaPlayer2.*=talk\n"));
			MprisSource.busFailureMessage(env, f.random.nextBoolean());
		}));
	}

	// Windows: the helper's JSON lines

	private static final String SESSIONS = "{\"seq\":1,\"ok\":true,\"sessions\":["
			+ "{\"id\":\"Spotify.exe\",\"index\":0,\"title\":\"A Made-Up Song\",\"artist\":\"Made Up Band\",\"album\":\"Made Up Album\","
			+ "\"status\":\"Playing\",\"startMs\":0,\"endMs\":200000,\"positionMs\":1000,\"updatedMs\":1763000000000,\"rate\":1.0,"
			+ "\"cover\":\"C:\\\\Users\\\\x\\\\cover.jpg\"},"
			+ "{\"id\":\"MSEdge\",\"index\":1,\"title\":\"Another\",\"artist\":\"\",\"album\":\"\",\"status\":\"Paused\",\"startMs\":0,"
			+ "\"endMs\":0,\"positionMs\":0,\"updatedMs\":0,\"cover\":\"\"}]}";

	@Test
	void anyWindowsAnswerGivesSongsWithSanePositions() {
		assertTimeoutPreemptively(BOUND, () -> Fuzz.cases(14, 1500, (f, i) -> {
			Map<String, WindowsMediaSource.Seen> before = new HashMap<>();
			long wall = 1_763_000_000_000L, mono = 5_000;
			for (int poll = 0; poll < 4; poll++) {
				String text = f.damagedText(SESSIONS, 0.2);
				JsonElement json;
				try {
					json = JsonParser.parseString(text);
				} catch (RuntimeException notJson) {
					continue; // the reader drops a line that isn't JSON
				}
				if (!json.isJsonObject()) {
					continue;
				}
				Map<String, WindowsMediaSource.Seen> after = new HashMap<>();
				wall += f.random.nextInt(3) == 0 ? f.random.nextLong() : 1000;
				mono += f.random.nextInt(2000);
				for (WindowsMediaSource.Session s : WindowsMediaSource.sessions(json.getAsJsonObject(), wall, mono, before, after)) {
					checkTrack(s.track());
					assertTrue(s.positionMs() >= 0, "position " + s.positionMs() + " from " + Fuzz.show(text));
					assertTrue(s.track().lengthMs() <= 0 || s.positionMs() <= s.track().lengthMs(), "past the end from " + Fuzz.show(text));
					WindowsMediaSource.prettyName(s.track().player());
				}
				WindowsMediaSource.helperError(json.getAsJsonObject());
				before = after;
			}
			String id = f.nasty(30);
			WindowsMediaSource.prettyName(WindowsMediaSource.playerKey(id));
		}));
	}

	@Test
	void anyHelperLogEndsInALineOrNothing(@TempDir Path dir) throws Exception {
		Path log = dir.resolve("windows-media.log");
		Fuzz.cases(15, 200, (f, i) -> {
			Files.write(log, f.random.nextBoolean() ? f.bytes(5000) : f.nasty(300).getBytes(java.nio.charset.StandardCharsets.UTF_16LE));
			String line = WindowsMediaSource.lastLogLine(log);
			assertTrue(line.length() <= 205, "log line of " + line.length());
		});
	}

	// Mac: the script's tab lines

	private static String number(Fuzz f) {
		String[] odd = {"200", "200.5", "12,5", "1.0E+9", "-1", "NaN", "Infinity", "-Infinity", "1e308", "1e309", "0x1p3", "", " ", "missing value",
				"9".repeat(400), "4.9E-324", "-0"};
		return f.random.nextInt(3) == 0 ? f.nasty(5) : odd[f.random.nextInt(odd.length)];
	}

	@Test
	void anyMacScriptOutputGivesSongsWithSanePositions() {
		assertTimeoutPreemptively(BOUND, () -> Fuzz.cases(16, 2000, (f, i) -> {
			List<String> lines = new ArrayList<>();
			int n = f.random.nextInt(5);
			for (int k = 0; k < n; k++) {
				String app = new String[] {"Spotify", "Music", "Spotify", f.nasty(4)}[f.random.nextInt(4)];
				String state = new String[] {"playing", "paused", "stopped", "error", f.nasty(4)}[f.random.nextInt(5)];
				List<String> fields = new ArrayList<>(List.of(app, state, number(f), number(f), big(f), f.nasty(20), f.nasty(20),
						f.nasty(10), f.random.nextBoolean() ? "spotify:track:abc" : f.random.nextBoolean() ? "spotify:ad:x" : f.nasty(10),
						f.random.nextBoolean() ? "" : f.nasty(10) + " - " + f.nasty(10)));
				fields.subList(f.random.nextInt(fields.size() + 1), fields.size()).clear();
				lines.add(String.join("\t", fields));
			}
			String out = f.random.nextInt(5) == 0 ? f.nasty(200) : f.random.nextBoolean() ? String.join("\n", lines) : f.mutate(String.join("\n", lines) + "\n");
			MacMediaSource.Answer a = MacMediaSource.parse(out);
			for (MacMediaSource.Reading r : a.players()) {
				checkTrack(r.track());
				assertTrue(r.positionMs() >= 0, "position " + r.positionMs());
				assertTrue(r.track().lengthMs() >= 0, "length " + r.track().lengthMs());
				assertTrue(r.track().lengthMs() == 0 || r.positionMs() <= r.track().lengthMs(), "past the end");
			}
		}));
	}

	// The recently heard songs, saved on disk and read at start

	private static final String HEARD = "{\"songs\":[{\"title\":\"A Made-Up Song\",\"artist\":\"Made Up Band\",\"album\":\"Made Up Album\","
			+ "\"lengthMs\":200000,\"cover\":\"https://x/a\",\"uri\":\"spotify:track:abc\",\"player\":\"spotify\"},"
			+ "{\"title\":\"Another\",\"artist\":\"\",\"album\":\"\",\"lengthMs\":0,\"cover\":\"\",\"uri\":\"file:///a.mp3\",\"player\":\"vlc\"}]}";

	@Test
	void aDamagedHeardFileLoadsAsSongsOrNone(@TempDir Path dir) {
		assertTimeoutPreemptively(BOUND, () -> Fuzz.cases(17, 300, (f, i) -> {
			Path file = dir.resolve("heard" + i + ".json");
			byte[] bytes = switch (f.random.nextInt(5)) {
				case 0 -> f.bytes(3000);
				case 1 -> f.mutate(HEARD.getBytes(java.nio.charset.StandardCharsets.UTF_8));
				case 2 -> ("\uFEFF" + HEARD).getBytes(java.nio.charset.StandardCharsets.UTF_8);
				default -> f.damagedText(HEARD, 0.3).getBytes(java.nio.charset.StandardCharsets.UTF_8);
			};
			Files.write(file, bytes);
			List<HeardLibrary.Entry> songs = HeardLibrary.load(file, dev.beli.skylyrics.util.SafeFile::readText, 1, 0).songs();
			assertTrue(songs.size() <= HeardLibrary.CAP);
			for (HeardLibrary.Entry e : songs) {
				assertTrue(e.lengthMs() >= 0);
				checkTrack(new Track(e.title(), e.artist(), e.album(), e.lengthMs(), e.coverUrl(), e.player(), e.uri()));
			}
			String query = f.random.nextBoolean() ? f.nasty(20) : f.mutate("made up");
			HeardLibrary.filter(songs, query);
			HeardLibrary.page(songs, f.random.nextInt());
			for (HeardLibrary.Entry e : songs) {
				HeardLibrary.openableUri(e.uri(), List.of("spotify", "file", "http"));
			}
			HeardLibrary.openableUri(f.nasty(30), List.of("http", "https"));
			HeardLibrary.schemeOf(f.nasty(10));
		}));
	}
}
