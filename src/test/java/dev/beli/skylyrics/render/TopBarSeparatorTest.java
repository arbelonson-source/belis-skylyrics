package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.client.resources.language.FormattedBidiReorder;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

/** The "  ·  " between the song's name and its artist in the top bar, as the game lays the text out on screen. */
class TopBarSeparatorTest {
	private static final String DOT = "  ·  ";

	/** What the game draws from left to right, in an English game or a Hebrew or Arabic one, without the invisible marks. */
	private static String seen(String text, boolean rtlGame) {
		StringBuilder out = new StringBuilder();
		LyricsFont.dropMarks(FormattedBidiReorder.reorder(FormattedText.of(text, Style.EMPTY), rtlGame)).accept((i, style, cp) -> {
			out.appendCodePoint(cp);
			return true;
		});
		return out.toString();
	}

	private static String game(boolean rtlGame) {
		return rtlGame ? "a Hebrew or Arabic game" : "an English game";
	}

	@Test
	void theDotStaysBetweenTheTitleAndAHebrewArtist() {
		for (boolean rtlGame : new boolean[] {false, true}) {
			// Drawn right after the title: the dot first, then the artist read right to left
			assertEquals(DOT + "ןמאה םש", seen(NowPlayingHud.dotted("שם האמן"), rtlGame),
					"Hebrew artist in " + game(rtlGame) + ": the dot must sit next to the title, not after the artist");
		}
	}

	@Test
	void theDotStaysInFrontOfAnArabicArtistAndANumber() {
		for (boolean rtlGame : new boolean[] {false, true}) {
			String arabic = seen(NowPlayingHud.dotted("فنان عربي"), rtlGame);
			assertEquals(DOT, arabic.substring(0, DOT.length()), "Arabic artist in " + game(rtlGame) + ", drawn as " + arabic);
			// A band named with digits only, like "112"
			assertEquals(DOT + "112", seen(NowPlayingHud.dotted("112"), rtlGame), "number artist in " + game(rtlGame));
		}
	}

	@Test
	void aCutHebrewArtistEndsInAnEllipsisAtItsReadingEnd() {
		String dotted = NowPlayingHud.dotted("שם האמן");
		// What clip() makes of it with little room: the start of the text, then "…"
		String cut = dotted.substring(0, dotted.indexOf("הא") + 2) + "…";
		for (boolean rtlGame : new boolean[] {false, true}) {
			assertEquals(DOT + "…אה םש", seen(cut, rtlGame), "cut Hebrew artist in " + game(rtlGame));
		}
	}

	@Test
	void anEnglishArtistReadsAsBefore() {
		for (boolean rtlGame : new boolean[] {false, true}) {
			assertEquals(DOT + "Some Artist", seen(NowPlayingHud.dotted("Some Artist"), rtlGame), game(rtlGame));
			assertEquals(DOT + "Some Artist" + DOT + "no lyrics", seen(NowPlayingHud.dotted(NowPlayingHud.subText("Some Artist", "no lyrics", "")), rtlGame),
					game(rtlGame));
		}
	}

	@Test
	void theMarksTakeNoRoom() {
		// The pill is sized from these widths, so the marks must not make it any wider
		net.minecraft.client.StringSplitter oneEach = new net.minecraft.client.StringSplitter((cp, style) -> 1);
		assertEquals(DOT.length() + "שם האמן".length(), LyricsFont.width(oneEach, Style.EMPTY, NowPlayingHud.dotted("שם האמן")));
	}
}
