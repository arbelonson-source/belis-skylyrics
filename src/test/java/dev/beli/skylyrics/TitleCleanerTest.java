package dev.beli.skylyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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
	void searchTitleDropsGuestArtists() {
		assertEquals("Made Up Song", TitleCleaner.forSearch("Made Up Song (with SOMEONE)"));
		assertEquals("Made Up Song", TitleCleaner.forSearch("Made Up Song [with Someone & Another]"));
		assertEquals("Made Up Song", TitleCleaner.forSearch("Made Up Song (feat. Someone)"));
		assertEquals("Made Up Song", TitleCleaner.forSearch("Made Up Song [Ft. Someone]"));
		assertEquals("Made Up Song", TitleCleaner.forSearch("Made Up Song (prod. Someone)"));
		assertEquals("Without You", TitleCleaner.forSearch("Without You"));
	}

	@Test
	void mainArtistIsTheFirstOne() {
		assertEquals("A", TitleCleaner.mainArtist("A, B & C"));
		assertEquals("A", TitleCleaner.mainArtist("A feat. B"));
	}
}
