package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
		// a title that fills the room but for a few units leaves nothing to show of a long artist
		assertEquals(false, NowPlayingHud.subFits(190, 200, 60));
		assertEquals(false, NowPlayingHud.subFits(200 - NowPlayingHud.SUB_MIN, 200, 60));
		assertEquals(true, NowPlayingHud.subFits(100, 200, 60));
	}

	/**
	 * The pill is sized for the title and "  ·  Artist" together. An artist of one or two narrow letters makes that part
	 * narrower than {@link NowPlayingHud#SUB_MIN} (about 23 font units for two letters in the bar's font), and it was left
	 * out: the bar showed the title alone with an empty stretch after it, where the artist had room.
	 */
	@Test
	void aShortArtistShowsInThePillSizedForIt() {
		float scale = 0.75f; // the smooth font's size in the bar
		for (float title : new float[] {1, 9, 40, 80, 150}) {
			for (float sub = 13; sub <= 40; sub += 0.5f) {
				float pill = TopBarLayout.pillFor((title + sub) * scale, TopBarLayout.PILL_W);
				int room = NowPlayingHud.titleRoom(TopBarLayout.textRoom(pill) / scale);
				assertTrue(title + sub <= room, "the pill has room for the title and the artist: " + title + " + " + sub + " in " + room);
				assertTrue(NowPlayingHud.subFits(title, room, sub), "title " + title + ", artist part " + sub + " wide, room " + room);
			}
		}
	}

	/** The pill at its widest with a title that leaves less than the whole artist still cuts it with "…" as before. */
	@Test
	void aLongArtistIsStillCutOrLeftOutAsBefore() {
		assertTrue(NowPlayingHud.subFits(100, 200, 150), "cut to the 100 left");
		assertFalse(NowPlayingHud.subFits(180, 200, 25), "20 left: not enough for a cut one");
		assertTrue(NowPlayingHud.subFits(180, 200, 20), "but a 20 wide one fits whole");
	}
}
