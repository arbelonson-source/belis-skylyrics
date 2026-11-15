package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/** What the settings' preferred-player text does when it is empty, blank or padded with spaces. */
class PlayerPickerPreferredTextTest {
	private record P(String id, String player, boolean playing) {
	}

	private static P choose(List<P> players, String preferred, String current) {
		return PlayerPicker.choose(players, P::id, P::player, P::playing, preferred, current);
	}

	private static final P MPV_PAUSED = new P("mpv", "mpv", false);
	private static final P SPOTIFY_PLAYING = new P("spotify", "spotify", true);

	@Test
	void anEmptyOrBlankPreferenceFollowsWhateverIsPlaying() {
		for (String preferred : new String[] {"", " ", "   ", "\t"}) {
			assertEquals(SPOTIFY_PLAYING, choose(List.of(MPV_PAUSED, SPOTIFY_PLAYING), preferred, null), "[" + preferred + "]");
		}
	}

	@Test
	void anEmptyPreferenceStillKeepsTheCurrentPlayerWhileItPlays() {
		P a = new P("a", "firefox", true);
		P b = new P("b", "chrome", true);
		assertEquals(b, choose(List.of(a, b), "", "b"));
		assertEquals(b, choose(List.of(a, b), "  ", "b"));
	}

	@Test
	void aPaddedPreferenceStillMatchesThePlayerEvenWhenAnotherOnePlays() {
		// The preferred player wins over a playing one that isn't preferred, however the text is padded or cased
		for (String preferred : new String[] {"mpv", " mpv", "mpv ", "  MPV  "}) {
			assertEquals(MPV_PAUSED, choose(List.of(SPOTIFY_PLAYING, MPV_PAUSED), preferred, null), "[" + preferred + "]");
		}
	}

	@Test
	void aBlankPreferenceWithNothingPlayingKeepsTheCurrentThenTheFirst() {
		P a = new P("a", "x", false);
		P b = new P("b", "y", false);
		assertEquals(b, choose(List.of(a, b), " ", "b"));
		assertEquals(a, choose(List.of(a, b), " ", null));
	}
}
