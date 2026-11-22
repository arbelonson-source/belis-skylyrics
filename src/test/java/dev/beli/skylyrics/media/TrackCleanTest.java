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

	@Test
	void invisibleDirectionMarksAreRemovedButLettersAndSpacesStay() {
		Track t = of("\u200Fשלום\u200E עולם\u061C", "\u200F\u05D0\u05D1\u200F", "ال\u061Cعربية \u200E");
		assertEquals("שלום עולם", t.title());
		assertEquals("אב", t.artist());
		assertEquals("العربية", t.album());
		assertEquals("a b", of("a \u200F b", "", "").title());
		assertTrue(of("\u200E\u200F\u061C", "x", "").isNone());
	}

	/**
	 * An emoji's style selector (VS16 after "❤" makes a phone draw it in colour), the other variation selectors, a word
	 * joiner, a zero-width space and a stray byte order mark are invisible, but the game draws them: none of the bundled
	 * smooth fonts has a variation selector or a word joiner, so they come from Minecraft's Unifont, which draws each as a
	 * dashed box with its name in it ("VS16", "WJ"), as it does the zero-width space and the byte order mark with the
	 * pixel font. A song named "Paper Hearts ❤️" showed "Paper Hearts ❤" and a box in the top bar and on the Record card.
	 */
	@Test
	void selectorsAndZeroWidthCharactersThatDrawAsBoxesAreRemoved() {
		Track t = of("Paper Hearts \u2764\uFE0F", "Mira Vale \u2728\uFE0F", "Sun\u2600\uFE0E Days");
		assertEquals("Paper Hearts \u2764", t.title());
		assertEquals("Mira Vale \u2728", t.artist());
		assertEquals("Sun\u2600 Days", t.album());
		assertEquals("Glass Tide", of("Glass\u2060 \uFEFFTide\u200B", "x", "x").title());
		assertEquals("a\u6F22b", of("a\u6F22\uDB40\uDD00b", "x", "x").title()); // VS17, a Han variant selector
		assertTrue(of("\uFE0F\u200B", "x", "").isNone());
		// The joiners are kept: Persian writing and emoji sequences need them, and the smooth fonts draw them as nothing
		assertEquals("\u0645\u06CC\u200C\u062E\u0648", of("\u0645\u06CC\u200C\u062E\u0648", "x", "x").title());
		assertEquals("\uD83D\uDC69\u200D\uD83C\uDFA4", of("\uD83D\uDC69\u200D\uD83C\uDFA4", "x", "x").title());
	}
}
