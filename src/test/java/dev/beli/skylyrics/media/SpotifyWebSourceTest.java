package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpotifyWebSourceTest {
	private static final String ID = "0123456789abcdef0123456789abcdef";

	@Test
	void challengeMatchesThePkceExample() {
		// The worked example in RFC 7636, appendix B
		assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
				SpotifyWebSource.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
	}

	@Test
	void onlyTheCallbackPageCounts() {
		Map<String, String> q = SpotifyWebSource.callbackQuery("GET /callback?code=abc%2B1&state=x+y HTTP/1.1");
		assertEquals("abc+1", q.get("code"));
		assertEquals("x y", q.get("state"));
		assertEquals(Map.of("error", "access_denied"), SpotifyWebSource.callbackQuery("GET /callback?error=access_denied HTTP/1.1"));
		assertNull(SpotifyWebSource.callbackQuery("GET /favicon.ico HTTP/1.1"));
		assertNull(SpotifyWebSource.callbackQuery("POST /callback?code=abc HTTP/1.1"));
		assertNull(SpotifyWebSource.callbackQuery(""));
	}

	@Test
	void signInPageAsksForTheRightThings() {
		String url = SpotifyWebSource.authorizeUrl(ID, "CH", "ST");
		assertTrue(url.startsWith("https://accounts.spotify.com/authorize?client_id=" + ID + "&"));
		assertTrue(url.contains("redirect_uri=http%3A%2F%2F127.0.0.1%3A43827%2Fcallback"));
		assertTrue(url.contains("code_challenge_method=S256&code_challenge=CH"));
		assertTrue(url.contains("scope=user-read-currently-playing+user-read-playback-state"));
		assertTrue(url.endsWith("&state=ST"));
		assertEquals("a=1+2&b=%26", SpotifyWebSource.form("a", "1 2", "b", "&"));
	}

	@Test
	void clientIdsAre32HexCharacters() {
		assertTrue(SpotifyWebSource.validClientId(ID));
		assertTrue(SpotifyWebSource.validClientId(ID.toUpperCase()));
		assertFalse(SpotifyWebSource.validClientId(""));
		assertFalse(SpotifyWebSource.validClientId(null));
		assertFalse(SpotifyWebSource.validClientId(ID + "0"));
		assertFalse(SpotifyWebSource.validClientId("https://open.spotify.com/xyz"));
	}

	@Test
	void readsASong() {
		String body = """
				{"is_playing": true, "progress_ms": 61000, "currently_playing_type": "track",
				 "item": {"name": "Neon Skyline", "duration_ms": 200000,
				  "artists": [{"name": "Beli"}, {"name": "The Demo Band"}],
				  "album": {"name": "Night Drive", "images": [
				   {"url": "https://i/640", "width": 640}, {"url": "https://i/300", "width": 300}, {"url": "https://i/64", "width": 64}]}}}
				""";
		Snapshot s = SpotifyWebSource.parse(body, 42);
		assertEquals(new Track("Neon Skyline", "Beli, The Demo Band", "Night Drive", 200000, "https://i/300", "Spotify"), s.track());
		assertEquals(61000, s.positionMs());
		assertTrue(s.playing());
		assertEquals(42, s.readAtNanos());
	}

	@Test
	void readsAPodcastEpisode() {
		String body = """
				{"is_playing": false, "progress_ms": 5000,
				 "item": {"name": "Episode 3", "duration_ms": 1800000, "images": [{"url": "https://i/ep", "width": 300}],
				  "show": {"name": "Sky Talk", "publisher": "Beli Radio", "images": [{"url": "https://i/show", "width": 300}]}}}
				""";
		Snapshot s = SpotifyWebSource.parse(body, 0);
		assertEquals(new Track("Episode 3", "Beli Radio", "Sky Talk", 1800000, "https://i/ep", "Spotify"), s.track());
		assertFalse(s.playing());
	}

	@Test
	void adsAndOddAnswersAreNothing() {
		assertSame(Snapshot.NOTHING, SpotifyWebSource.parse("{\"is_playing\": true, \"item\": null}", 0));
		assertSame(Snapshot.NOTHING, SpotifyWebSource.parse("", 0));
		assertSame(Snapshot.NOTHING, SpotifyWebSource.parse("not json", 0));
		// A position past the end is kept at the end
		Snapshot s = SpotifyWebSource.parse("{\"progress_ms\": 9999, \"item\": {\"name\": \"A\", \"duration_ms\": 1000}}", 0);
		assertEquals(1000, s.positionMs());
	}

	@Test
	void accountFollowsTheClientIdAndTheSavedSignIn(@TempDir Path dir) throws IOException {
		Path login = dir.resolve("spotify-login.json");
		String[] id = {""};
		assertEquals(SpotifyWebSource.Account.NO_CLIENT_ID, new SpotifyWebSource(() -> id[0], login).account());
		id[0] = ID;
		assertEquals(SpotifyWebSource.Account.SIGNED_OUT, new SpotifyWebSource(() -> id[0], login).account());

		Files.writeString(login, "{\"clientId\": \"" + ID + "\", \"refreshToken\": \"r\"}");
		SpotifyWebSource source = new SpotifyWebSource(() -> id[0], login);
		assertEquals(SpotifyWebSource.Account.SIGNED_IN, source.account());
		// Another app's Client ID can't use this sign-in
		id[0] = "ffffffffffffffffffffffffffffffff";
		assertEquals(SpotifyWebSource.Account.SIGNED_OUT, source.account());

		id[0] = ID;
		source.signOut();
		assertEquals(SpotifyWebSource.Account.SIGNED_OUT, source.account());
		assertFalse(Files.exists(login));
	}
}
