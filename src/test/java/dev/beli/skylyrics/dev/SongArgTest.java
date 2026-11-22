package dev.beli.skylyrics.dev;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.media.DemoSource;

/** The test hook's {@code song}: what a check script writes, and the demo song it gives. */
class SongArgTest {
	@Test
	void titleAndArtistAreSplitAtTheLastBar() {
		assertArrayEquals(new String[] {"Paper Lanterns", "Mira Vale"}, SongArg.parse("Paper Lanterns | Mira Vale"));
		assertArrayEquals(new String[] {"Side A | Part 2", "Mira Vale"}, SongArg.parse("Side A | Part 2 | Mira Vale"));
		assertArrayEquals(new String[] {"Only A Title", ""}, SongArg.parse(" Only A Title "));
		assertArrayEquals(new String[] {"", ""}, SongArg.parse(""));
	}

	@Test
	void escapesStandForTheirCharacters() {
		assertEquals("Hearts ❤️", SongArg.unescape("Hearts \\u2764\\uFE0F"));
		assertEquals("سلام", SongArg.unescape("\\u0633\\u0644\\u0627\\u0645"));
		assertEquals("no escape \\u12", SongArg.unescape("no escape \\u12"));
	}

	@Test
	void theDemoSongTakesTheNameAndKeepsItsLengthAndPlayer() {
		try {
			DemoSource.renameForTest("Paper Lanterns", "Mira Vale");
			var track = new DemoSource().poll().track();
			assertEquals("Paper Lanterns", track.title());
			assertEquals("Mira Vale", track.artist());
			assertEquals(DemoSource.TRACK.lengthMs(), track.lengthMs());
			assertEquals(DemoSource.PLAYER, track.player());
			DemoSource.renameForTest(" ", "");
			assertEquals(DemoSource.TRACK, new DemoSource().poll().track());
		} finally {
			DemoSource.renameForTest("", "");
		}
	}
}
