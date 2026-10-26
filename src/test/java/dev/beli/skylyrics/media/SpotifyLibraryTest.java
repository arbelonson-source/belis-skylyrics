package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class SpotifyLibraryTest {
	private static String song(String id, String name) {
		return """
				{"id":"%s","uri":"spotify:track:%s","name":"%s","duration_ms":201000,
				 "artists":[{"name":"Paper Comets"},{"name":"Lumen Fox"}],
				 "album":{"name":"Glass Harbour","images":[{"url":"big","width":640},{"url":"mid","width":300},{"url":"small","width":64}]}}
				""".formatted(id, id, name);
	}

	@Test
	void searchRepliesBecomeSongCards() {
		String body = "{\"tracks\":{\"total\":37,\"offset\":10,\"items\":[" + song("a1", "Lantern Weather") + "," + song("a2", "Slow Satellites") + "]}}";
		SpotifyLibrary.Page<LibraryItem> page = SpotifyLibrary.parseSearch(body);
		assertEquals(2, page.items().size());
		LibraryItem first = page.items().getFirst();
		assertEquals("Lantern Weather", first.title());
		assertEquals("Paper Comets, Lumen Fox", first.artists());
		assertEquals("Glass Harbour", first.album());
		assertEquals("mid", first.coverUrl());
		assertEquals(201000, first.lengthMs());
		assertEquals("spotify:track:a1", first.uri());
		assertEquals("", first.context());
		assertEquals(12, page.next());
	}

	@Test
	void theLastPageHasNoNext() {
		String body = "{\"tracks\":{\"total\":12,\"offset\":10,\"items\":[" + song("a1", "x") + "," + song("a2", "y") + "]}}";
		assertEquals(-1, SpotifyLibrary.parseSearch(body).next());
		assertEquals(-1, SpotifyLibrary.Page.empty().next());
	}

	@Test
	void likedSongsUnwrapTheirTrack() {
		String body = "{\"total\":1,\"offset\":0,\"items\":[{\"added_at\":\"x\",\"track\":" + song("b1", "Velvet Tram") + "}]}";
		assertEquals("Velvet Tram", SpotifyLibrary.parseSaved(body).items().getFirst().title());
	}

	@Test
	void playlistSongsKeepTheirPlaylistAndSkipPodcastsAndLocalFiles() {
		String podcast = "{\"id\":\"e1\",\"uri\":\"spotify:episode:e1\",\"name\":\"Talk\"}";
		String local = "{\"id\":null,\"uri\":\"spotify:local:x\",\"name\":\"Home Demo\"}";
		String body = "{\"total\":4,\"offset\":0,\"items\":[{\"item\":" + song("c1", "Moth Radio") + "},{\"item\":" + podcast
				+ "},{\"track\":" + local + "},{\"track\":" + song("c2", "Neon Orchard") + "},{\"item\":null}]}";
		SpotifyLibrary.Page<LibraryItem> page = SpotifyLibrary.parsePlaylistItems(body, "spotify:playlist:p1");
		assertEquals(List.of("Moth Radio", "Neon Orchard"), page.items().stream().map(LibraryItem::title).toList());
		assertEquals("spotify:playlist:p1", page.items().getFirst().context());
		// Skipped entries still count, so the next page starts after all four
		assertEquals(4, page.total());
	}

	@Test
	void onlyYourOwnOrSharedPlaylistsOpen() {
		String body = """
				{"total":3,"offset":0,"items":[
				 {"id":"p1","uri":"spotify:playlist:p1","name":"Night Drive","owner":{"id":"me"},"items":{"total":12},"images":[]},
				 {"id":"p2","uri":"spotify:playlist:p2","name":"Borrowed","owner":{"id":"them"},"tracks":{"total":30}},
				 {"id":"p3","uri":"spotify:playlist:p3","name":"Together","owner":{"id":"them"},"collaborative":true}
				]}""";
		List<LibraryPlaylist> lists = SpotifyLibrary.parsePlaylists(body, "me").items();
		assertEquals(3, lists.size());
		assertTrue(lists.get(0).openable());
		assertEquals(12, lists.get(0).songs());
		assertFalse(lists.get(1).openable());
		assertEquals(30, lists.get(1).songs());
		assertTrue(lists.get(2).openable());
		assertEquals("", lists.get(0).coverUrl());
	}

	@Test
	void brokenRepliesGiveEmptyPages() {
		assertTrue(SpotifyLibrary.parseSearch("not json").items().isEmpty());
		assertTrue(SpotifyLibrary.parseSaved("{}").items().isEmpty());
		assertTrue(SpotifyLibrary.parsePlaylists("[]", "me").items().isEmpty());
	}

	@Test
	void playlistSongsPlayOnThroughThePlaylist() {
		LibraryItem item = new LibraryItem("c1", "spotify:track:c1", "Moth Radio", "", "", "", 0, "spotify:playlist:p1");
		assertEquals("{\"context_uri\":\"spotify:playlist:p1\",\"offset\":{\"uri\":\"spotify:track:c1\"}}",
				SpotifyLibrary.playBody(item, List.of()));
	}

	@Test
	void otherSongsQueueWhatComesAfterThem() {
		LibraryItem a = new LibraryItem("a", "spotify:track:a", "", "", "", "", 0, "");
		LibraryItem b = new LibraryItem("b", "spotify:track:b", "", "", "", "", 0, "");
		assertEquals("{\"uris\":[\"spotify:track:a\",\"spotify:track:b\"]}", SpotifyLibrary.playBody(a, List.of(a, b)));
	}

	@Test
	void theFirstUsableDeviceIsPicked() {
		String body = "{\"devices\":[{\"id\":\"tv\",\"is_restricted\":true},{\"id\":\"laptop\",\"is_restricted\":false}]}";
		assertEquals("laptop", SpotifyLibrary.firstDevice(body));
		assertNull(SpotifyLibrary.firstDevice("{\"devices\":[]}"));
	}

	@Test
	void searchWordsAreEncodedWithPercentSpaces() {
		assertEquals("glass%20harbour%20%26%20co", SpotifyLibrary.encode("glass harbour & co"));
	}

	@Test
	void oldSignInsMissTheLibraryScopes() {
		String old = "user-read-currently-playing user-read-playback-state user-modify-playback-state";
		assertFalse(SpotifyWebSource.covers(old, SpotifyWebSource.LIBRARY_SCOPES));
		assertTrue(SpotifyWebSource.covers(old + " playlist-read-private user-library-read", SpotifyWebSource.LIBRARY_SCOPES));
		assertFalse(SpotifyWebSource.covers("", SpotifyWebSource.LIBRARY_SCOPES));
	}

	@Test
	void withoutPremiumTheSongIsPlayedInTheSpotifyAppOnThisComputer() {
		List<String> opened = new java.util.ArrayList<>();
		assertEquals("", SpotifyLibrary.outcome(403, "spotify:track:a", uri -> opened.add(uri)));
		assertEquals("", SpotifyLibrary.outcome(404, "spotify:track:b", uri -> opened.add(uri)));
		assertEquals(List.of("spotify:track:a", "spotify:track:b"), opened);
	}

	@Test
	void whenSpotifyStartsTheSongTheAppIsLeftAlone() {
		assertEquals("", SpotifyLibrary.outcome(204, "spotify:track:a", uri -> {
			throw new AssertionError("asked the app as well");
		}));
		// No app either: the user is told why
		assertFalse(SpotifyLibrary.outcome(403, "spotify:track:a", uri -> false).isEmpty());
		assertFalse(SpotifyLibrary.outcome(500, "spotify:track:a", uri -> true).isEmpty());
	}
}
