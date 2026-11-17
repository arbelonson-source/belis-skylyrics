package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class TrackLongTitleTest {
	@Test
	void hugeTitleIsCappedWithoutSplittingAPair() {
		String title = "x".repeat(Track.MAX_TEXT - 1) + "😀".repeat(500_000);
		Track t = new Track(title, "a".repeat(1_000_000), "b".repeat(1_000_000), 0, "", "vlc");
		assertEquals(Track.MAX_TEXT - 1, t.title().length());
		assertFalse(Character.isHighSurrogate(t.title().charAt(t.title().length() - 1)));
		assertEquals(Track.MAX_TEXT, t.artist().length());
		assertEquals(Track.MAX_TEXT, t.album().length());
	}

	@Test
	void normalTitlesAreUntouched() {
		assertEquals("Hello", new Track("Hello", "A", "B", 0, "", "x").title());
	}
}
