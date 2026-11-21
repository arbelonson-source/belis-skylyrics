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
	void sectionSignCodesInNamesNeverReachTheList() {
		String body = "{\"tracks\":{\"total\":1,\"offset\":0,\"items\":[{\"id\":\"z\",\"uri\":\"spotify:track:z\",\"name\":\"\u00a7cRed \u00a7lSong\","
				+ "\"artists\":[{\"name\":\"\u00a74Band\"}],\"album\":{\"name\":\"\u00a7kAlbum\",\"images\":[]}}]}}";
		LibraryItem item = SpotifyLibrary.parseSearch(body).items().getFirst();
		assertEquals("Red Song", item.title());
		assertEquals("Band", item.artists());
		assertEquals("Album", item.album());
		String lists = "{\"total\":1,\"offset\":0,\"items\":[{\"id\":\"p\",\"uri\":\"spotify:playlist:p\",\"name\":\"\u00a7aMix\u00a7r\",\"owner\":{\"id\":\"me\"}}]}";
		assertEquals("Mix", SpotifyLibrary.parsePlaylists(lists, "me").items().getFirst().name());
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
	void pagingCountsDroppedEntriesToo() {
		String local = "{\"track\":{\"id\":null,\"uri\":\"spotify:local:x\"}}";
		String episode = "{\"item\":{\"id\":\"e1\",\"uri\":\"spotify:episode:e1\"}}";
		// 50 entries sent, 2 kept: the next page starts at 100, not 52
		StringBuilder items = new StringBuilder("{\"item\":" + song("c1", "Moth Radio") + "},{\"item\":" + song("c2", "Neon Orchard") + "}");
		for (int i = 2; i < 50; i++) {
			items.append(',').append(i % 2 == 0 ? local : episode);
		}
		SpotifyLibrary.Page<LibraryItem> page = SpotifyLibrary.parsePlaylistItems("{\"total\":300,\"offset\":50,\"items\":[" + items + "]}", "spotify:playlist:p1");
		assertEquals(2, page.items().size());
		assertEquals(100, page.next());
		// A page where everything was dropped still has a next one
		String dropped = "{\"total\":300,\"offset\":0,\"items\":[" + local + "," + episode + "]}";
		assertEquals(2, SpotifyLibrary.parsePlaylistItems(dropped, "spotify:playlist:p1").next());
	}

	@Test
	void playlistPagingCountsTheGapsSpotifySentToo() {
		// Spotify has sent null in place of a playlist it won't show this app: the next page still starts after the gap,
		// or the playlist at the page's edge shows twice
		String body = """
				{"total":5,"offset":0,"items":[
				 {"id":"p1","uri":"spotify:playlist:p1","name":"Night Drive","owner":{"id":"me"}},
				 null,
				 {"id":"p3","uri":"spotify:playlist:p3","name":"Glass Harbour","owner":{"id":"me"}}
				]}""";
		SpotifyLibrary.Page<LibraryPlaylist> page = SpotifyLibrary.parsePlaylists(body, "me");
		assertEquals(2, page.items().size());
		assertEquals(3, page.next());
		// A page of nothing but gaps is not the end of the list
		assertEquals(2, SpotifyLibrary.parsePlaylists("{\"total\":5,\"offset\":0,\"items\":[null,null]}", "me").next());
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

	@Test
	void ownIdIsAskedAgainAfterAnotherSignIn() throws Exception {
		SpotifyLibrary.OwnId own = new SpotifyLibrary.OwnId();
		assertEquals("first", own.get(1, () -> "first"));
		assertEquals("first", own.get(1, () -> "never asked"));
		assertEquals("second", own.get(2, () -> "second"));
	}

	@Test
	void emptyOwnIdIsNotKept() throws Exception {
		SpotifyLibrary.OwnId own = new SpotifyLibrary.OwnId();
		assertEquals("", own.get(1, () -> ""));
		assertEquals("real", own.get(1, () -> "real"));
	}

	@Test
	void aFailedLoadSaysWhatToDoNotWhatBrokeInside() {
		assertEquals("Can't reach the internet or Spotify", SpotifyLibrary.errorText(new java.net.http.HttpTimeoutException("request timed out")));
		assertEquals("Can't reach the internet or Spotify", SpotifyLibrary.errorText(new java.net.UnknownHostException("api.spotify.com")));
		assertEquals("Sign in to Spotify on the Music tab to see your songs here",
				SpotifyLibrary.errorText(SpotifyWebSource.notSetUp("0123456789abcdef0123456789abcdef")));
		assertEquals("Spotify didn't answer, try again", SpotifyLibrary.errorText(new java.io.IOException("Spotify didn't answer, try again")));
		assertEquals("Something went wrong, try again", SpotifyLibrary.errorText(new IllegalStateException("x")));
		assertEquals("Something went wrong, try again", SpotifyLibrary.errorText(null));
	}

	@Test
	void libraryNamesGetTheSameCleaningAsNowPlayingTitles() {
		assertEquals("Mix txt", SpotifyLibrary.plain("\u202EMix\ttxt"));
		assertEquals("a b", SpotifyLibrary.plain("a \u00a0 \n b"));
		assertEquals(500, SpotifyLibrary.plain("x".repeat(5000)).length());
		assertEquals("", SpotifyLibrary.plain(null));
	}

	private record Reply(int status, java.net.http.HttpHeaders headers, String body) implements java.net.http.HttpResponse<String> {
		public int statusCode() { return status; }
		public java.net.http.HttpRequest request() { return null; }
		public java.util.Optional<java.net.http.HttpResponse<String>> previousResponse() { return java.util.Optional.empty(); }
		public java.net.http.HttpHeaders headers() { return headers; }
		public String body() { return body; }
		public java.util.Optional<javax.net.ssl.SSLSession> sslSession() { return java.util.Optional.empty(); }
		public java.net.URI uri() { return java.net.URI.create("https://api.spotify.com/"); }
		public java.net.http.HttpClient.Version version() { return java.net.http.HttpClient.Version.HTTP_1_1; }
	}

	@Test
	void aClosedListStopsWaitingOutTooManyRequests() throws Exception {
		java.util.concurrent.CountDownLatch asked = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		SpotifyLibrary library = new SpotifyLibrary(null, uri -> false, (method, path, json) -> {
			if (calls.getAndIncrement() == 0) {
				asked.countDown();
				return new Reply(429, java.net.http.HttpHeaders.of(java.util.Map.of("Retry-After", List.of("25")), (a, b) -> true), "");
			}
			return new Reply(200, java.net.http.HttpHeaders.of(java.util.Map.of(), (a, b) -> true), "{\"items\":[],\"total\":0}");
		});
		var old = library.liked(0);
		assertTrue(asked.await(30, java.util.concurrent.TimeUnit.SECONDS));
		old.cancel(true);
		long start = System.nanoTime();
		var next = library.liked(0);
		assertEquals(0, next.get(15, java.util.concurrent.TimeUnit.SECONDS).items().size());
		assertTrue(System.nanoTime() - start < 15_000_000_000L);
	}
}
