package dev.beli.skylyrics.media;

import dev.beli.skylyrics.Tr;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Made-up songs, so the song library can be tried out (and photographed) without a Spotify account.
 * Every title and band here is invented; nothing can be played.
 */
public final class DemoLibrary implements Library {
	static final int PAGE = 12;

	private static final String[][] SONGS = {
			{"Neon Skyline", "SkyLyrics Demo", "Built-in demo"},
			{"Glass Over Water", "The Quiet Biomes", "Shallow Light"},
			{"Lantern Hours", "Moss & Copper", "Under the Oaks"},
			{"Beacon Hum", "Lumen Drift", "Signals"},
			{"Deepslate Lullaby", "Night Shift Choir", "Low Tones"},
			{"Cherry Grove Waltz", "The Quiet Biomes", "Shallow Light"},
			{"Amethyst Rain", "Pixel Harbor", "Geode"},
			{"Elytra Glide", "Lumen Drift", "Signals"},
			{"End City Lights", "Void Orchard", "Far Islands"},
			{"Moss Carpet", "Moss & Copper", "Under the Oaks"},
			{"Sculk Echo", "Night Shift Choir", "Low Tones"},
			{"Tide Pool Tapes", "Pixel Harbor", "Geode"},
			{"Soul Fire Slowdance", "Void Orchard", "Far Islands"},
			{"Snowy Taiga Morning", "The Quiet Biomes", "Shallow Light"},
			{"Copper Rust Blues", "Moss & Copper", "Verdigris"},
			{"Redstone Pulse", "Lumen Drift", "Circuits"},
			{"Bamboo Raft", "Pixel Harbor", "Drift Season"},
			{"Warped Forest Radio", "Void Orchard", "Far Islands"},
			{"Campfire Static", "Night Shift Choir", "Low Tones"},
			{"Starlit Shulker", "Lumen Drift", "Circuits"},
			{"Mangrove Fog", "The Quiet Biomes", "Wetlands"},
			{"Pale Garden", "Moss & Copper", "Verdigris"},
			{"Ocean Monument", "Pixel Harbor", "Drift Season"},
			{"Last Light at Spawn", "Night Shift Choir", "Long Days"},
			{"Chorus Fruit", "Void Orchard", "Far Islands"},
			{"Lodestone Home", "Lumen Drift", "Circuits"},
			{"Desert Well", "The Quiet Biomes", "Wetlands"},
			{"Nether Portal Hum", "Night Shift Choir", "Long Days"},
	};
	private static final String[] PLAYLISTS = {"Late-night building", "Elytra runs", "Rainy cabin", "Nether trip", "Morning farm", "Someone else's mix"};

	@Override
	public CompletableFuture<SpotifyLibrary.Page<LibraryItem>> search(String query, int offset) {
		String q = query.strip().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) {
			return CompletableFuture.completedFuture(SpotifyLibrary.Page.empty());
		}
		List<LibraryItem> found = new ArrayList<>();
		for (int i = 0; i < SONGS.length; i++) {
			if ((SONGS[i][0] + " " + SONGS[i][1]).toLowerCase(Locale.ROOT).contains(q)) {
				found.add(song(i, ""));
			}
		}
		return done(page(found, offset));
	}

	@Override
	public CompletableFuture<SpotifyLibrary.Page<LibraryItem>> liked(int offset) {
		List<LibraryItem> all = new ArrayList<>();
		for (int i = 0; i < SONGS.length; i++) {
			all.add(song(i, ""));
		}
		return done(page(all, offset));
	}

	@Override
	public CompletableFuture<SpotifyLibrary.Page<LibraryPlaylist>> playlists(int offset) {
		List<LibraryPlaylist> all = new ArrayList<>();
		for (int i = 0; i < PLAYLISTS.length; i++) {
			all.add(new LibraryPlaylist("demo" + i, "demo:playlist:" + i, PLAYLISTS[i], "", songsIn(i).size(), i < PLAYLISTS.length - 1));
		}
		return done(page(all, offset));
	}

	@Override
	public CompletableFuture<SpotifyLibrary.Page<LibraryItem>> playlist(LibraryPlaylist playlist, int offset) {
		int index = Integer.parseInt(playlist.id().substring(4));
		List<LibraryItem> songs = new ArrayList<>();
		for (int song : songsIn(index)) {
			songs.add(song(song, playlist.uri()));
		}
		return done(page(songs, offset));
	}

	@Override
	public CompletableFuture<String> play(LibraryItem item, List<LibraryItem> after) {
		return done(Tr.t("These songs are made up, so nothing plays"));
	}

	/** Playlist {@code index} takes every few songs from its own starting point, so each holds a different mix. */
	private static List<Integer> songsIn(int index) {
		List<Integer> songs = new ArrayList<>();
		for (int i = index % 4; i < SONGS.length; i += 2 + index % 3) {
			songs.add(i);
		}
		return songs;
	}

	private static LibraryItem song(int i, String context) {
		return new LibraryItem("demo" + i, "demo:track:" + i, SONGS[i][0], SONGS[i][1], SONGS[i][2], "", 150_000 + i * 7_000L, context);
	}

	static <T> SpotifyLibrary.Page<T> page(List<T> all, int offset) {
		int from = Math.min(offset, all.size());
		return new SpotifyLibrary.Page<>(List.copyOf(all.subList(from, Math.min(all.size(), from + PAGE))), all.size(), from);
	}

	private static <T> CompletableFuture<T> done(T value) {
		return CompletableFuture.completedFuture(value);
	}
}
