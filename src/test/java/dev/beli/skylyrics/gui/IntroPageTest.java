package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.config.SkyLyricsConfig.Layout;
import dev.beli.skylyrics.config.Themes;
import org.junit.jupiter.api.Test;

class IntroPageTest {
	@Test
	void everyPageHasWordsForTheNarrator() {
		for (boolean mac : new boolean[] {false, true}) {
			for (int page = 0; page < IntroScreen.PAGES; page++) {
				IntroPage p = IntroScreen.content(page, mac, Themes.ALL.get(0), Layout.ARC, "J", "K");
				assertFalse(p.heading().isBlank(), "heading of page " + page);
				assertFalse(p.spoken().isBlank(), "page " + page);
				assertTrue(p.spoken().startsWith(p.heading()), "page " + page);
			}
		}
	}

	@Test
	void everyPageButTheLayoutOneSaysMoreThanItsHeading() {
		for (int page : new int[] {0, 1, 3, 4}) {
			IntroPage p = IntroScreen.content(page, false, Themes.ALL.get(0), Layout.ARC, "J", "K");
			assertFalse(p.paragraphs().isEmpty(), "page " + page);
		}
	}

	@Test
	void theChosenLookAndLayoutAreSaidApartFromThePage() {
		IntroPage look = IntroScreen.content(1, false, Themes.ALL.get(1), Layout.ARC, null, null);
		assertEquals(Themes.ALL.get(1).name().get(), look.chosen());
		assertFalse(look.spoken().contains(look.chosen()));
		IntroPage arc = IntroScreen.content(2, false, Themes.ALL.get(0), Layout.ARC, null, null);
		IntroPage lines = IntroScreen.content(2, false, Themes.ALL.get(0), Layout.LINES, null, null);
		assertEquals(arc.spoken(), lines.spoken());
		assertTrue(arc.chosen().startsWith(IntroScreen.layoutName(Layout.ARC)));
		assertTrue(lines.chosen().startsWith(IntroScreen.layoutName(Layout.LINES)));
	}

	@Test
	void theKeysPageNamesTheKeys() {
		IntroPage p = IntroScreen.content(4, false, Themes.ALL.get(0), Layout.ARC, "J", "K");
		assertTrue(p.spoken().contains(": J"));
		assertTrue(p.spoken().contains(": K"));
		assertEquals(2, p.keys().size());
	}
}
