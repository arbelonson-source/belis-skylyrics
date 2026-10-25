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
	void searchTitleDropsVersionNoise() {
		assertEquals("Song", TitleCleaner.forSearch("Song - Remastered 2011"));
		assertEquals("Song", TitleCleaner.forSearch("Song - 2011 Remaster"));
		assertEquals("Song", TitleCleaner.forSearch("Song (feat. Someone)"));
	}

	@Test
	void mainArtistIsTheFirstOne() {
		assertEquals("A", TitleCleaner.mainArtist("A, B & C"));
		assertEquals("A", TitleCleaner.mainArtist("A feat. B"));
	}
}
