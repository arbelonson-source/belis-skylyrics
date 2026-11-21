package dev.beli.skylyrics.lyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.media.Track;

class LrclibClientTest {
	private static final Track TRACK = new Track("A Made-Up Song", "Made Up Band", "Made Up Album", 200_000, "", "spotify");
	private static final String HIT = "[{\"trackName\":\"A Made-Up Song\",\"duration\":200,\"syncedLyrics\":\"[00:01.00]A made-up line\",\"instrumental\":false}]";

	private HttpServer server;
	private LrclibClient client;
	private final List<String> asked = new CopyOnWriteArrayList<>();
	private volatile int getCode = 404;
	private volatile int searchCode = 200;
	private volatile String searchBody = "[]";
	private volatile String getBody = "{}";
	/** When set, the searches that name an artist get this instead of {@link #searchBody} (the free-text one still gets that). */
	private volatile String artistSearchBody;
	private volatile String retryAfter;
	/** When set, answers every /search from its decoded query instead of {@link #searchBody}. */
	private volatile java.util.function.Function<String, String> searchByQuery;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/api", this::answer);
		server.start();
		client = new LrclibClient("test", "http://127.0.0.1:" + server.getAddress().getPort() + "/api");
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private void answer(HttpExchange ex) throws IOException {
		String path = ex.getRequestURI().getPath();
		asked.add(path);
		int code = path.endsWith("/get") ? getCode : searchCode;
		String body = path.endsWith("/get") ? getBody : searchBody;
		String rawQuery = ex.getRequestURI().getRawQuery();
		if (artistSearchBody != null && path.endsWith("/search") && rawQuery != null && !rawQuery.startsWith("q=")) {
			body = artistSearchBody;
		}
		if (searchByQuery != null && path.endsWith("/search")) {
			body = searchByQuery.apply(java.net.URLDecoder.decode(rawQuery == null ? "" : rawQuery, StandardCharsets.UTF_8));
		}
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		if (retryAfter != null) {
			ex.getResponseHeaders().add("Retry-After", retryAfter);
		}
		ex.sendResponseHeaders(code, bytes.length);
		try (OutputStream out = ex.getResponseBody()) {
			out.write(bytes);
		}
	}

	@Test
	void getNotFoundFallsThroughToSearch() throws Exception {
		getCode = 404;
		searchBody = HIT;
		assertTrue(client.find(TRACK).found());
		assertEquals("/api/get", asked.get(0));
		assertEquals("/api/search", asked.get(1));
	}

	@Test
	void getServerErrorStillTriesSearch() throws Exception {
		getCode = 500;
		searchBody = HIT;
		LrclibClient.Result r = client.find(TRACK);
		assertEquals("[00:01.00]A made-up line", r.synced());
	}

	@Test
	void getServerErrorWithSearchErrorIsAnError() {
		getCode = 500;
		searchCode = 503;
		LrclibClient.StatusException e = assertThrows(LrclibClient.StatusException.class, () -> client.find(TRACK));
		assertEquals(503, e.status());
	}

	@Test
	void issue1GetAnswers503ButSearchWorksFindsLyrics() throws Exception {
		getCode = 503;
		searchBody = HIT;
		LrclibClient.Result r = client.find(TRACK);
		assertEquals("[00:01.00]A made-up line", r.synced());
		assertEquals(List.of("/api/get", "/api/search"), asked);
	}

	@Test
	void getBusyThenSearchBusyReportsTheSearchAnswerWithoutABurst() {
		getCode = 503;
		searchCode = 503;
		retryAfter = "9";
		LrclibClient.StatusException e = assertThrows(LrclibClient.StatusException.class, () -> client.find(TRACK));
		assertEquals(503, e.status());
		assertEquals(9000, e.retryAfterMs());
		assertEquals(2, asked.size());
	}

	@Test
	void retryAfterAsADateBecomesADelay() {
		java.time.Instant now = java.time.Instant.parse("2026-10-21T07:27:00Z");
		assertEquals(60_000, LrclibClient.retryAfterMs("Wed, 21 Oct 2026 07:28:00 GMT", now));
		assertEquals(-1, LrclibClient.retryAfterMs("Wed, 21 Oct 2026 07:26:00 GMT", now)); // already past
		assertEquals(-1, LrclibClient.retryAfterMs("Wed, 21 Oct 2027 07:28:00 GMT", now)); // over a day away
		assertEquals(-1, LrclibClient.retryAfterMs("soon", now));
		assertEquals(7000, LrclibClient.retryAfterMs("7", now));
	}

	@Test
	void getServerErrorWithEmptySearchIsNoLyrics() throws Exception {
		getCode = 500;
		searchBody = "[]";
		assertFalse(client.find(TRACK).found());
	}

	@Test
	void getClientErrorIsStillAnError() {
		getCode = 429;
		searchBody = HIT;
		LrclibClient.StatusException e = assertThrows(LrclibClient.StatusException.class, () -> client.find(TRACK));
		assertEquals(429, e.status());
		assertEquals(1, asked.size());
	}

	@Test
	void emptyOkReplyIsATemporaryErrorNotNoLyrics() {
		// The client's own complaint, so a connection failure (also an IOException) can't pass for it
		for (String body : new String[] {"", "  \n"}) {
			getCode = 200;
			getBody = body;
			asked.clear();
			IOException e = assertThrows(IOException.class, () -> client.find(TRACK), "get body '" + body + "'");
			assertEquals("LRCLIB sent an empty reply", e.getMessage());
		}
		for (String body : new String[] {"nope", "null", "42"}) {
			getCode = 200;
			getBody = body;
			asked.clear();
			IOException e = assertThrows(IOException.class, () -> client.find(TRACK), "get body '" + body + "'");
			assertTrue(e.getMessage().startsWith("LRCLIB sent a reply that isn't"), e.getMessage());
		}
		getCode = 404;
		for (String body : new String[] {"", "<html>log in</html>", "true"}) {
			searchBody = body;
			IOException e = assertThrows(IOException.class, () -> client.find(TRACK), "search body '" + body + "'");
			assertTrue(e.getMessage().startsWith("LRCLIB sent a"), "search body '" + body + "': " + e.getMessage());
		}
	}

	@Test
	void forbiddenIsAStatusError() {
		getCode = 403;
		assertEquals(403, assertThrows(LrclibClient.StatusException.class, () -> client.find(TRACK)).status());
	}

	@Test
	void retryAfterIsKept() {
		getCode = 429;
		retryAfter = "7";
		assertEquals(7000, assertThrows(LrclibClient.StatusException.class, () -> client.find(TRACK)).retryAfterMs());
		assertEquals(-1, LrclibClient.retryAfterMs("Wed, 21 Oct 2026 07:28:00 GMT"));
		assertEquals(-1, LrclibClient.retryAfterMs(null));
	}

	private static final Track BARE = new Track("Made Up Song", "", "", 0, "", "browser");

	private static String hit(String name, String lyric) {
		return "{\"trackName\":\"" + name + "\",\"duration\":200,\"syncedLyrics\":\"" + lyric + "\",\"instrumental\":false}";
	}

	@Test
	void noArtistAndNoLengthNeedsTheSameTitle() throws Exception {
		searchBody = "[" + hit("Made Up Song (Remix Of Another Thing)", "[00:01.00]wrong") + "," + hit("MADE UP SONG", "[00:01.00]right") + "]";
		assertEquals("[00:01.00]right", client.find(BARE).synced());
	}

	@Test
	void noArtistAndNoLengthWithOnlyOtherTitlesFindsNothing() throws Exception {
		searchBody = "[" + hit("Something Else Entirely", "[00:01.00]wrong") + "]";
		assertFalse(client.find(BARE).found());
	}

	private static String hit(String name, String artist, String lyric) {
		return "{\"trackName\":\"" + name + "\",\"artistName\":\"" + artist + "\",\"duration\":200,\"syncedLyrics\":\"" + lyric
				+ "\",\"instrumental\":false}";
	}

	@Test
	void noArtistAndNoLengthWithSameTitlesByDifferentArtistsPicksNone() throws Exception {
		searchBody = "[" + hit("Made Up Song", "Some Band", "[00:01.00]one") + "," + hit("Made Up Song", "Other Singer", "[00:01.00]two") + "]";
		assertFalse(client.find(BARE).found());
	}

	@Test
	void noArtistAndNoLengthWithSeveralUploadsOfOneArtistsSongStillFinds() throws Exception {
		searchBody = "[" + hit("Made Up Song", "Some Band", "[00:01.00]one") + "," + hit("Made Up Song", "SOME BAND", "[00:01.00]two") + "]";
		assertTrue(client.find(BARE).found());
	}

	@Test
	void anArtistStillAllowsALooserTitleMatch() throws Exception {
		searchBody = "[" + hit("A Made-Up Song - Remastered", "[00:01.00]fine") + "]";
		assertTrue(client.find(TRACK).found());
	}

	private static String hit(String name, String artist, int seconds, String lyric) {
		return "{\"trackName\":\"" + name + "\",\"artistName\":\"" + artist + "\",\"duration\":" + seconds
				+ ",\"syncedLyrics\":\"" + lyric + "\",\"instrumental\":false}";
	}

	private LrclibClient.Result findWith(Track track, String... hits) throws Exception {
		searchBody = "[" + String.join(",", hits) + "]";
		return client.find(track);
	}

	private static Track song(String title, String artist, int seconds) {
		return new Track(title, artist, "", seconds * 1000L, "", "spotify");
	}

	private static final String BAND = "Made Up Band";

	private static final Track UNTAGGED = new Track("Intro", "", "", 200_000, "", "browser");

	@Test
	void noArtistWithALengthPicksNothingWhenDifferentArtistsShareTheTitle() throws Exception {
		assertFalse(findWith(UNTAGGED, hit("Intro", "Some Band", 200, "[00:01.00]one"), hit("Intro", "Other Singer", 201, "[00:01.00]two")).found());
	}

	@Test
	void noArtistWithALengthNeedsACloseLength() throws Exception {
		assertTrue(findWith(UNTAGGED, hit("Intro", "Some Band", 203, "[00:01.00]one")).found()); // NO_ARTIST_SLACK_S = 3
		assertTrue(findWith(UNTAGGED, hit("Intro", "Some Band", 197, "[00:01.00]one")).found());
		assertFalse(findWith(UNTAGGED, hit("Intro", "Some Band", 204, "[00:01.00]far")).found());
		assertFalse(findWith(UNTAGGED, hit("Intro", "Some Band", 196, "[00:01.00]far")).found());
	}

	@Test
	void aHitWithAnUnreadableLengthIsSkippedNotFatal() throws Exception {
		String bad = "{\"trackName\":\"A Made-Up Song\",\"artistName\":\"" + BAND + "\",\"duration\":\"abc\",\"syncedLyrics\":\"[00:01.00]bad\",\"instrumental\":false}";
		assertEquals("[00:01.00]good", findWith(TRACK, bad, hit("A Made-Up Song", BAND, 200, "[00:01.00]good")).synced());
		assertFalse(findWith(TRACK, bad).found());
	}

	@Test
	void aHitWhoseLengthIsNotANumberSlipsPastNoLengthCheck() throws Exception {
		// Only NaN discriminates: "Infinity" is skipped by the length limit anyway, but NaN fails every comparison.
		String bad = "{\"trackName\":\"A Made-Up Song\",\"artistName\":\"" + BAND + "\",\"duration\":\"NaN\",\"syncedLyrics\":\"[00:01.00]bad\",\"instrumental\":false}";
		assertFalse(findWith(TRACK, bad).found());
		String ok = "{\"trackName\":\"A Made-Up Song\",\"artistName\":\"" + BAND + "\",\"duration\":\"200.5\",\"syncedLyrics\":\"[00:01.00]ok\",\"instrumental\":false}";
		assertTrue(findWith(TRACK, ok).found());
	}

	@Test
	void anotherSongBySameArtistIsNotPicked() throws Exception {
		assertFalse(findWith(TRACK, hit("Another Song", BAND, 200, "[00:01.00]wrong")).found());
	}

	@Test
	void versionTagsAndCreditsStillMatch() throws Exception {
		assertEquals("[00:01.00]x", findWith(TRACK, hit("A Made-Up Song (feat. Guest)", BAND, 200, "[00:01.00]x")).synced());
		assertEquals("[00:01.00]x", findWith(TRACK, hit("A Made-Up Song (Radio Edit)", BAND, 200, "[00:01.00]x")).synced());
	}

	@Test
	void caseAccentsPunctuationAndAmpersandStillMatch() throws Exception {
		assertEquals("[00:01.00]x", findWith(song("Caf\u00e9 Don\u2019t Stop", BAND, 200), hit("CAFE DON'T STOP", BAND, 200, "[00:01.00]x")).synced());
		assertEquals("[00:01.00]x", findWith(song("Rock & Roll", BAND, 200), hit("Rock and Roll", BAND, 200, "[00:01.00]x")).synced());
	}

	@Test
	void nonLatinTitlesMatchOrNot() throws Exception {
		assertEquals("[00:01.00]x", findWith(song("\u05e9\u05dc\u05d5\u05dd", "\u05dc\u05d4\u05e7\u05d4", 200), hit("\u05e9\u05dc\u05d5\u05dd!", "\u05dc\u05d4\u05e7\u05d4", 200, "[00:01.00]x")).synced());
		Track yoasobi = song("\u591c\u306b\u99c6\u3051\u308b", "YOASOBI", 260);
		assertEquals("[00:01.00]x", findWith(yoasobi, hit("\u591c\u306b\u99c6\u3051\u308b", "YOASOBI", 260, "[00:01.00]x")).synced());
		assertFalse(findWith(yoasobi, hit("Yoru ni Kakeru", "YOASOBI", 260, "[00:01.00]x")).found());
	}

	@Test
	void similarTitlesAreNotTheSameSong() throws Exception {
		Track hello = song("Hello", BAND, 200);
		assertFalse(findWith(hello, hit("Hello Again", BAND, 200, "[00:01.00]x")).found());
		assertFalse(findWith(hello, hit("Hello (Remix)", BAND, 200, "[00:01.00]x")).found());
	}

	@Test
	void aRecordFarOffTheTrackLengthIsNotUsed() throws Exception {
		assertFalse(findWith(TRACK, hit("A Made-Up Song", BAND, 240, "[00:01.00]x")).found());
		assertEquals("[00:01.00]x", findWith(TRACK, hit("A Made-Up Song", BAND, 215, "[00:01.00]x")).synced());
		assertFalse(findWith(TRACK, hit("A Made-Up Song", BAND, 225, "[00:01.00]x")).found());
	}

	@Test
	void plainOnlyExactHitLosesToASyncedSearchResult() throws Exception {
		getCode = 200;
		getBody = "{\"trackName\":\"A Made-Up Song\",\"duration\":200,\"plainLyrics\":\"plain words\",\"instrumental\":false}";
		searchBody = HIT;
		LrclibClient.Result r = client.find(TRACK);
		assertEquals("[00:01.00]A made-up line", r.synced());
		assertEquals(2, asked.size());
	}

	@Test
	void plainOnlyExactHitStaysWhenSearchHasNothingSynced() throws Exception {
		getCode = 200;
		getBody = "{\"trackName\":\"A Made-Up Song\",\"duration\":200,\"plainLyrics\":\"plain words\",\"instrumental\":false}";
		searchBody = "[]";
		LrclibClient.Result r = client.find(TRACK);
		assertEquals("plain words", r.plain());
		assertEquals(null, r.synced());
	}

	@Test
	void syncedExactHitAddsNoSearch() throws Exception {
		getCode = 200;
		getBody = "{\"trackName\":\"A Made-Up Song\",\"duration\":200,\"syncedLyrics\":\"[00:01.00]hi\",\"instrumental\":false}";
		assertEquals("[00:01.00]hi", client.find(TRACK).synced());
		assertEquals(List.of("/api/get"), asked);
	}

	@Test
	void syncedTextOfOnlyTagsIsNotAnAnswer() throws Exception {
		getCode = 200;
		getBody = "{\"trackName\":\"A Made-Up Song\",\"duration\":200,\"syncedLyrics\":\"[ar:Someone]\\n[ti:Song]\",\"instrumental\":false}";
		searchBody = HIT;
		assertEquals("[00:01.00]A made-up line", client.find(TRACK).synced());
		assertFalse(new LrclibClient.Result("[ar:x]", null, false).found());
	}

	@Test
	void aSlightlyDifferentLengthStillMatches() throws Exception {
		assertEquals("[00:01.00]x", findWith(TRACK, hit("A Made-Up Song", BAND, 203, "[00:01.00]x")).synced());
	}

	@Test
	void unknownLengthWithAnArtistNeedsTheTitle() throws Exception {
		Track noLength = song("A Made-Up Song", BAND, 0);
		assertFalse(findWith(noLength, hit("Another Song", BAND, 200, "[00:01.00]wrong")).found());
		assertEquals("[00:01.00]x", findWith(noLength, hit("A Made-Up Song", BAND, 200, "[00:01.00]x")).synced());
	}

	@Test
	void freeTextResultsMustBeByTheArtist() throws Exception {
		artistSearchBody = "[]";
		Track noLength = song("A Made-Up Song", BAND, 0);
		assertFalse(findWith(noLength, hit("A Made-Up Song", "Other Singer", 200, "[00:01.00]wrong")).found());
		assertEquals("[00:01.00]x", findWith(noLength, hit("A Made-Up Song", "Made Up Band & Friends", 200, "[00:01.00]x")).synced());
	}

	@Test
	void aResultWithoutATitleIsRejected() throws Exception {
		assertFalse(findWith(TRACK, "{\"artistName\":\"Made Up Band\",\"duration\":200,\"syncedLyrics\":\"[00:01.00]x\",\"instrumental\":false}").found());
	}

	@Test
	void theSameTitleBeatsAnotherSongEvenWhenItComesLater() throws Exception {
		assertEquals("[00:01.00]right", findWith(TRACK, hit("Another Song", BAND, 200, "[00:01.00]wrong"),
				hit("A Made-Up Song", BAND, 203, "[00:01.00]right")).synced());
	}

	@Test
	void aReplyOverTheCapIsAFailedRequest() {
		searchBody = "[{\"trackName\":\"x\",\"pad\":\"" + "a".repeat(3 * 1024 * 1024) + "\"}]";
		getCode = 404;
		IOException e = assertThrows(IOException.class, () -> client.find(TRACK));
		assertFalse(e instanceof LrclibClient.StatusException);
	}


	@Test
	void withoutALengthTheExactTitleBeatsAVersionBracketedOne() throws Exception {
		Track noLength = song("Made Song", BAND, 0);
		assertEquals("[00:01.00]studio", findWith(noLength,
				hit("Made Song (Live)", BAND, 200, "[00:01.00]live"), hit("Made Song", BAND, 200, "[00:01.00]studio")).synced());
	}

	@Test
	void theTitleTieBreakNeverBeatsSyncedOverPlain() throws Exception {
		Track noLength = song("Made Song", BAND, 0);
		String plain = "{\"trackName\":\"Made Song\",\"artistName\":\"" + BAND + "\",\"duration\":200,\"plainLyrics\":\"plain words\",\"instrumental\":false}";
		assertEquals("[00:01.00]live", findWith(noLength, plain, hit("Made Song (Live)", BAND, 200, "[00:01.00]live")).synced());
	}

	/** A LRCLIB that only knows "Song" by "Artist" and finds nothing for a query with a version tag. */
	private int searchesOnlyKnowingSong() {
		searchByQuery = q -> q.contains("Remaster") ? "[]"
				: "[" + hit("Song", "Artist", 200, "[00:01.00]right") + "]";
		return 0;
	}

	@Test
	void aVersionTagInBracketsIsSearchedAgainWithoutIt() throws Exception {
		searchesOnlyKnowingSong();
		LrclibClient.Result r = client.find(song("Song (2011 Remaster)", "Artist", 200));
		assertEquals("[00:01.00]right", r.synced());
		assertEquals(List.of("/api/get", "/api/search", "/api/search", "/api/search"), asked);
	}

	@Test
	void aBracketFreeSearchStillRefusesAnotherLength() throws Exception {
		searchesOnlyKnowingSong();
		assertFalse(client.find(song("Song (Live)", "Artist", 260)).found());
	}

	@Test
	void aRemixBracketIsNotSearchedWithoutIt() throws Exception {
		searchByQuery = q -> q.contains("Remix") ? "[]" : "[" + hit("Song", "Artist", 200, "[00:01.00]right") + "]";
		assertFalse(client.find(song("Song (Remix)", "Artist", 200)).found());
		assertEquals(List.of("/api/get", "/api/search", "/api/search"), asked);
	}

	@Test
	void aTitleWithoutBracketsMakesTheSameRequestsAsBefore() throws Exception {
		searchByQuery = q -> "[]";
		assertFalse(client.find(song("Song", "Artist", 200)).found());
		assertEquals(List.of("/api/get", "/api/search", "/api/search"), asked);
	}

	@Test
	void aFirstHitMakesNoExtraRequest() throws Exception {
		searchByQuery = q -> "[" + hit("Song", "Artist", 200, "[00:01.00]right") + "]";
		assertTrue(client.find(song("Song (2011 Remaster)", "Artist", 200)).found());
		assertEquals(List.of("/api/get", "/api/search"), asked);
	}

	@Test
	void aSyncedCopyWithNoWordsNeverBeatsOneWithWords() throws Exception {
		String real = hit("A Made-Up Song", BAND, 202, "[00:01.00]A made-up line");
		// Only time stamps (shown as "instrumental"), only tags, or words without times: closer in length, nothing to sing along to
		for (String junk : new String[] {"[00:01.00]\\n[00:30.00]", "[ar:Someone]\\n[ti:Song]", "Made-up words without times"}) {
			assertEquals("[00:01.00]A made-up line", findWith(TRACK, hit("A Made-Up Song", BAND, 200, junk), real).synced(), junk);
		}
		// Alone, a copy of only time stamps still counts as instrumental, as before
		assertTrue(LyricsService.parse(findWith(TRACK, hit("A Made-Up Song", BAND, 200, "[00:01.00]\\n[00:30.00]")), 200_000).instrumental());
	}
}
