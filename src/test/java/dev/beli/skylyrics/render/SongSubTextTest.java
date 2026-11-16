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
}
