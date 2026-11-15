package dev.beli.skylyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.media.TitleCleaner;
import dev.beli.skylyrics.media.Track;

class TitleCleanerTest {
	private static Track track(String title, String artist, String player) {
		return new Track(title, artist, "", 200_000, "", player);
	}

	@Test
	void spotifyTracksAreLeftAlone() {
		Track t = track("Song - Live", "Band", "spotify");
		assertSame(t, TitleCleaner.clean(t));
	}

	@Test
	void browserVideoTitlesAreSplit() {
		Track t = TitleCleaner.clean(track("Some Band - Cool Song (Official Video)", "SomeBandVEVO", "firefox"));
		assertEquals("Some Band", t.artist());
		assertEquals("Cool Song", t.title());
	}

	@Test
	void topicChannelSuffixIsDropped() {
		Track t = TitleCleaner.clean(track("Cool Song", "Some Band - Topic", "chromium"));
		assertEquals("Some Band", t.artist());
	}

	@Test
	void versionTagIsNotTakenForTheSongName() {
		Track t = TitleCleaner.clean(track("Made Up Song - Remastered 2011", "Made Up Band", "firefox"));
		assertEquals("Made Up Band", t.artist());
		assertEquals("Made Up Song - Remastered 2011", t.title());
		assertEquals("Made Up Band", TitleCleaner.clean(track("Made Up Song - Live at Some Hall", "Made Up Band", "chromium")).artist());
		assertEquals("Made Up Band", TitleCleaner.clean(track("Made Up Song - 2009 Remaster", "Made Up Band", "firefox")).artist());
	}

	@Test
	void songsThatStartLikeATagAreStillSplit() {
		Track t = TitleCleaner.clean(track("Made Up Band - Live Forever And Ever", "SomeChannel", "firefox"));
		assertEquals("Made Up Band", t.artist());
		assertEquals("Live Forever And Ever", t.title());
	}

	@Test
	void topicChannelTitlesAreNeverSplit() {
		Track t = TitleCleaner.clean(track("Made Up Song - From the Made Up Movie", "Made Up Band - Topic", "firefox"));
		assertEquals("Made Up Band", t.artist());
		assertEquals("Made Up Song - From the Made Up Movie", t.title());
	}

	@Test
	void searchTitleDropsVersionNoise() {
		assertEquals("Song", TitleCleaner.forSearch("Song - Remastered 2011"));
		assertEquals("Song", TitleCleaner.forSearch("Song - 2011 Remaster"));
		assertEquals("Song", TitleCleaner.forSearch("Song (feat. Someone)"));
	}

	@Test
	void searchTitleOnlyCutsAtASpacedDash() {
		assertEquals("Re-Live", TitleCleaner.forSearch("Re-Live"));
		assertEquals("Made Up Re-Edit", TitleCleaner.forSearch("Made Up Re-Edit"));
		assertEquals("Made Up Song", TitleCleaner.forSearch("Made Up Song \u2014 Remastered 2011"));
		assertEquals("Made Up Song", TitleCleaner.forSearch("Made Up Song \u2013 Live"));
	}

	@Test
	void searchTitleDropsGuestArtists() {
		assertEquals("Made Up Song", TitleCleaner.forSearch("Made Up Song (with SOMEONE)"));
		assertEquals("Made Up Song", TitleCleaner.forSearch("Made Up Song [with Someone & Another]"));
		assertEquals("Made Up Song", TitleCleaner.forSearch("Made Up Song (feat. Someone)"));
		assertEquals("Made Up Song", TitleCleaner.forSearch("Made Up Song [Ft. Someone]"));
		assertEquals("Made Up Song", TitleCleaner.forSearch("Made Up Song (prod. Someone)"));
		assertEquals("Without You", TitleCleaner.forSearch("Without You"));
	}

	@Test
	void searchTitleDropsGuestArtistsInOtherSpellings() {
		assertEquals("Made Up Song", TitleCleaner.forSearch("Made Up Song\uFF08feat. Someone\uFF09"));
		assertEquals("Made Up Song", TitleCleaner.forSearch("Made Up Song \uFF08with Someone\uFF09"));
		assertEquals("Made Up Song", TitleCleaner.forSearch("Made Up Song \u3010feat. Someone\u3011"));
		assertEquals("Made Up Song", TitleCleaner.forSearch("Made Up Song feat. Someone"));
		assertEquals("Made Up Song", TitleCleaner.forSearch("Made Up Song FT. Someone Else"));
		assertEquals("Made Up Song", TitleCleaner.forSearch("Made Up Song ft Someone"));
		assertEquals("Made Up Song", TitleCleaner.forSearch("Made Up Song featuring Someone & Another"));
		assertEquals("Made Up Song", TitleCleaner.forSearch("Made Up Song (feat. Someone)"));
	}

	@Test
	void searchTitleKeepsRealTitlesThatLookLikeCredits() {
		assertEquals("Feel It", TitleCleaner.forSearch("Feel It"));
		assertEquals("Lift Me Up", TitleCleaner.forSearch("Lift Me Up"));
		assertEquals("Feathers", TitleCleaner.forSearch("Feathers"));
		assertEquals("Defeat Of Me", TitleCleaner.forSearch("Defeat Of Me"));
		assertEquals("Soft Light", TitleCleaner.forSearch("Soft Light"));
		assertEquals("feat. Someone", TitleCleaner.forSearch("feat. Someone"));
		assertEquals("Ft. Someone", TitleCleaner.forSearch("Ft. Someone"));
		assertEquals("Featuring You", TitleCleaner.forSearch("Featuring You"));
		assertEquals("Song Ft", TitleCleaner.forSearch("Song Ft"));
		assertEquals("A Feat Of Strength", TitleCleaner.forSearch("A Feat Of Strength"));
	}

	@Test
	void aTitleCardKeepsBandNamesWhole() {
		assertEquals("Simon & Garfunkel", TitleCleaner.shownArtist("Simon & Garfunkel"));
		assertEquals("Tyler, The Creator", TitleCleaner.shownArtist("Tyler, The Creator"));
		assertEquals("Made Up, Band", TitleCleaner.shownArtist(" Made Up, Band "));
		assertEquals("Someone Long Named",
				TitleCleaner.shownArtist("Someone Long Named, Another Long Name, A Third One"));
		assertEquals("Tyler, The Creator",
				TitleCleaner.shownArtist("Tyler, The Creator, Someone Else, And Another Name"));
		assertEquals("Someone With A Long Name",
				TitleCleaner.shownArtist("Someone With A Long Name feat. Another"));
	}

	@Test
	void mainArtistIsTheFirstOne() {
		assertEquals("A", TitleCleaner.mainArtist("A, B & C"));
		assertEquals("A", TitleCleaner.mainArtist("A feat. B"));
	}

	private static void same(String a, String b) {
		assertTrue(TitleCleaner.sameSong(a, b), a + " / " + b);
		assertTrue(TitleCleaner.sameSong(b, a), b + " / " + a);
	}

	private static void different(String a, String b) {
		assertFalse(TitleCleaner.sameSong(a, b), a + " / " + b);
		assertFalse(TitleCleaner.sameSong(b, a), b + " / " + a);
	}

	@Test
	void sameSongIgnoresVersionTagsCreditsCaseAccentsAndPunctuation() {
		same("Song", "Song");
		same("Song", "Song (feat. X)");
		same("Song", "Song - Remastered 2011");
		same("Song", "Song (Radio Edit)");
		same("Song", "Song [Live]");
		same("Song", "Song (Live at Wembley)");
		same("Song (2011 Remaster)", "Song");
		same("Beyonc\u00e9", "Beyonce");
		same("Don\u2019t", "Don't");
		same("Rock & Roll", "Rock and Roll");
		same("\u05e9\u05dc\u05d5\u05dd", "\u05e9\u05dc\u05d5\u05dd!");
		same("\u591c\u306b\u99c6\u3051\u308b", "\u591c\u306b\u99c6\u3051\u308b");
		same("\u4f60\u597d", "\u4f60\u597d (Live)");
		same("\uc548\ub155", "\uc548\ub155");
		same("\u0645\u0631\u062d\u0628\u0627", "\u0645\u0631\u062d\u0628\u0627\u064b");
		// Uploads that add the title's romanisation or translation in brackets
		same("\u591c\u306b\u99c6\u3051\u308b", "\u591c\u306b\u99c6\u3051\u308b (Yoru ni Kakeru)");
		same("\u7a3b\u9999", "\u7a3b\u9999 [Dao Xiang]");
	}

	@Test
	void sameSongTellsDifferentSongsApart() {
		different("Yellow", "Fix You");
		different("Hello", "Hello Again");
		different("Hello", "Hello (Remix)");
		different("Song (Part 1)", "Song (Part 2)");
		different("Song", "Song (Part 2)");
		different("Song", "Song (Interlude)");
		different("Song", "Song [Reprise]");
		different("\u591c\u306b\u99c6\u3051\u308b", "Yoru ni Kakeru");
		different("!!!", "???");
		assertFalse(TitleCleaner.sameSong("Song", ""));
		assertFalse(TitleCleaner.sameSong("Song", "  "));
	}

	@Test
	void aHugeTitleWithAnUnclosedBracketIsStillQuick() {
		// Titles come from players and lyrics sites; these shapes made the bracket patterns backtrack for minutes
		String[] hostile = {
				"(" + "from x ".repeat(2000),
				"(" + "official live ".repeat(2000),
				"[" + "a".repeat(10000),
				"Song" + " - live".repeat(2000),
				"\uFF08" + "with ".repeat(3000)};
		org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> {
			for (String h : hostile) {
				TitleCleaner.sameSong(h, h + "x");
				TitleCleaner.forSearch(h);
				TitleCleaner.clean(new Track(h, h + " - Topic", "", 200_000, "", "firefox", ""));
			}
		});
	}

	@Test
	void cappingNeverSplitsAnEmoji() {
		String s = "a".repeat(TitleCleaner.MAX_TEXT - 1) + "\uD83C\uDFB5" + "b";
		String c = TitleCleaner.capped(s);
		assertFalse(Character.isHighSurrogate(c.charAt(c.length() - 1)));
		assertEquals("short", TitleCleaner.capped("short"));
	}
}
