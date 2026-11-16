package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SongSubTextTest {
	@Test
	void artistStatusAndDemoMarkAreJoinedWithDots() {
		assertEquals("Artist  ·  no lyrics  ·  Demo", NowPlayingHud.subText("Artist", "no lyrics", "Demo"));
		assertEquals("Artist  ·  Demo", NowPlayingHud.subText("Artist", "", "Demo"));
		assertEquals("Artist  ·  no lyrics", NowPlayingHud.subText("Artist", "no lyrics", ""));
		assertEquals("Artist", NowPlayingHud.subText("Artist", "", ""));
	}

	@Test
	void emptyArtistGivesJustTheMarks() {
		assertEquals("Demo", NowPlayingHud.subText("", "", "Demo"));
		assertEquals("finding lyrics…  ·  Demo", NowPlayingHud.subText("", "finding lyrics…", "Demo"));
	}

	@Test
	void subTextNeedsRoomForMoreThanAnEllipsis() {
		// a title that fills the room but for a few units leaves nothing to show of the artist
		assertEquals(false, NowPlayingHud.subFits(190, 200));
		assertEquals(false, NowPlayingHud.subFits(200 - NowPlayingHud.SUB_MIN, 200));
		assertEquals(true, NowPlayingHud.subFits(100, 200));
	}
}
