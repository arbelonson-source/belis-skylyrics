package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

class PlayerPickerTest {
	private record P(String id, String player, boolean playing) {
	}

	private static P choose(List<P> players, String preferred, String current) {
		return PlayerPicker.choose(players, P::id, P::player, P::playing, preferred, current);
	}

	@Test
	void preferredPlayerPrefersThePlayingInstance() {
		P paused = new P("org.mpris.MediaPlayer2.mpv.instance1", "mpv", false);
		P playing = new P("org.mpris.MediaPlayer2.mpv.instance2", "mpv", true);
		P other = new P("org.mpris.MediaPlayer2.spotify", "spotify", true);
		assertEquals(playing, choose(List.of(paused, other, playing), "mpv", null));
		assertEquals(playing, choose(List.of(paused, other, playing), "mpv", paused.id()));
	}

	@Test
	void preferredPlayerKeepsTheCurrentInstanceWhileItPlays() {
		P a = new P("a", "firefox", true);
		P b = new P("b", "firefox", true);
		assertEquals(b, choose(List.of(a, b), "firefox", "b"));
	}

	@Test
	void noPlayersMeansNone() {
		assertNull(choose(List.of(), "mpv", null));
		assertNull(choose(List.of(), "", "x"));
	}

	@Test
	void whenNothingPlaysTheCurrentOneIsKept() {
		P a = new P("a", "firefox", false);
		P b = new P("b", "firefox", false);
		assertEquals(b, choose(List.of(a, b), "", "b"));
		assertEquals(a, choose(List.of(a, b), "", null), "no current: the first");
		assertEquals(a, choose(List.of(a, b), "", "gone"), "the current one closed: the first");
	}

	@Test
	void thePreferredNameIgnoresCaseAndSurroundingSpaces() {
		P other = new P("o", "spotify", true);
		P mpv = new P("m", "mpv", false);
		assertEquals(mpv, choose(List.of(other, mpv), "  MPV ", null));
		P loud = new P("l", "MPV Media Player", false);
		assertEquals(loud, choose(List.of(other, loud), "mpv", null), "a match inside a longer name, in any case");
	}

	@Test
	void preferredPlayerNotRunningFallsBackToWhateverPlays() {
		P spotify = new P("s", "spotify", true);
		assertEquals(spotify, choose(List.of(spotify), "mpv", null));
	}
}
