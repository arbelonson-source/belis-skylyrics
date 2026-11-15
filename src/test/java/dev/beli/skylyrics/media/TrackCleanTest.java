package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrackCleanTest {
	private static Track of(String title, String artist, String album) {
		return new Track(title, artist, album, 1000, "https://x/a.png\n", "spotify", "spotify:track:1\t");
	}

	@Test
	void lineBreaksAndTabsBecomeOneSpace() {
		Track t = of("Hello\n\tWorld\r\n", "A\u0000B", "C\u0085D");
		assertEquals("Hello World", t.title());
		assertEquals("A B", t.artist());
		assertEquals("C D", t.album());
	}

	@Test
	void textDirectionOverridesAreRemoved() {
		assertEquals("abc", of("‮abc‬", "⁦abc⁩", "a‪b‭c").title());
		assertEquals("abc", of("‮abc‬", "⁦abc⁩", "x").artist());
		assertEquals("abc", of("x", "x", "a‪b‭c").album());
	}

	@Test
	void spaceRunsCollapseAndEndsAreTrimmed() {
		assertEquals("a b c", of("  a   b  c  ", "", "").title());
	}

	@Test
	void nullBecomesEmpty() {
		Track t = new Track(null, null, null, 0, null, null);
		assertEquals("", t.title());
		assertEquals("", t.artist());
		assertEquals("", t.album());
	}

	@Test
	void artAddressPlayerAndUriAreLeftAlone() {
		Track t = of("a", "b", "c");
		assertEquals("https://x/a.png\n", t.artUrl());
		assertEquals("spotify:track:1\t", t.uri());
		assertEquals("spotify", t.player());
	}

	@Test
	void noneAndSameSongStillWork() {
		assertTrue(Track.NONE.isNone());
		assertTrue(of("Song\n", "Band", "").sameSong(of("Song", "Band", "")));
		assertFalse(of("Song", "Band", "").sameSong(of("Other", "Band", "")));
		assertEquals("Band – Song", of("Song\n", " Band", "").key());
		assertTrue(of("\n ‮", "x", "").isNone());
	}

	@Test
	void sectionSignCodesAreRemoved() {
		Track t = of("§kHello", "A§cB", "Tail§");
		assertEquals("Hello", t.title());
		assertEquals("AB", t.artist());
		assertEquals("Tail", t.album());
		assertEquals("¶ Ș", of("¶ Ș", "x", "x").title());
	}
}
