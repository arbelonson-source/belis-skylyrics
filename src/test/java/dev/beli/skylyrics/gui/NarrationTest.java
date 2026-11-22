package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NarrationTest {
	@Test
	void partsAreJoinedWithCommasAndBlanksLeftOut() {
		assertEquals("Glow, slider, 40 percent", Narration.join("Glow", "slider", "40 percent"));
		assertEquals("Glow, slider", Narration.join("Glow", "slider", "", null));
		assertEquals("", Narration.join("", " "));
	}

	@Test
	void aRepeatedWordIsSaidOnce() {
		assertEquals("Open, button", Narration.join("Open", "Open", "button"));
		assertEquals("Hat, None, button", Narration.join("Hat", "None", "none", "button"));
	}

	@Test
	void aChoiceSaysItsNameKindValueAndPlace() {
		assertEquals("Layout, choice, Arc, 1 of 3", Narration.choice("Layout", "choice", "Arc", "1 of 3"));
		assertEquals("Look, tab, 2 of 5", Narration.choice("Look", "tab", "", "2 of 5"));
	}

	@Test
	void aLibraryCardSaysItsSectionOnlyWhenGivenOne() {
		assertEquals("Liked songs, Blue, Ana, 3 of 50", Narration.card("Liked songs", "", "Blue", "Ana", "3 of 50"));
		assertEquals("Blue, Ana, 4 of 50", Narration.card("", "", "Blue", "Ana", "4 of 50"));
		assertEquals("Mix, 12 songs, 1 of 4", Narration.card("", "", "Mix", "12 songs", "1 of 4"));
		assertEquals("Playlists, Couldn't load", Narration.card("Playlists", "Couldn't load", "", "", ""));
	}

	@Test
	void theLibraryButtonsAndSearchBoxAreNamed() {
		assertEquals("Try again, button", Narration.button("Try again", "button"));
		assertEquals("Search, text box", Narration.searchBox("Search", "text box", ""));
		assertEquals("Search, text box, blue", Narration.searchBox("Search", "text box", "blue"));
	}

	@Test
	void unitsAreSpokenAsWords() {
		assertEquals("40 percent", Narration.speakable("40%", "percent", "degrees", "times", "seconds"));
		assertEquals("-10 degrees", Narration.speakable("-10°", "percent", "degrees", "times", "seconds"));
		assertEquals("1.50 times", Narration.speakable("1.50×", "percent", "degrees", "times", "seconds"));
		assertEquals("12 blocks", Narration.speakable("12 blocks", "percent", "degrees", "times", "seconds"));
	}

	@Test
	void secondsAreSpokenAsWords() {
		assertEquals("+0.50 seconds", Narration.speakable("+0.50 s", "percent", "degrees", "times", "seconds"));
		assertEquals("0 seconds", Narration.speakable("0 s", "percent", "degrees", "times", "seconds"));
		assertEquals("1.5 seconds", Narration.speakable("1.5 s", "percent", "degrees", "times", "seconds"));
		assertEquals("12 blocks", Narration.speakable("12 blocks", "percent", "degrees", "times", "seconds"));
	}

	@Test
	void aTabSaysItsNameAndTheRowItLandsOn() {
		assertEquals("Look, tab, 2 of 5", Narration.tab("Look", "tab", "2 of 5", ""));
		assertEquals("Look, tab, 2 of 5, Glow, slider, 40 percent", Narration.tab("Look", "tab", "2 of 5", "Glow, slider, 40 percent"));
	}

	@Test
	void aSearchResultThatItsTabIsHidingSaysSo() {
		assertEquals("Glow colour, swatches", Narration.row("Glow colour, swatches", false, "not in use now"));
		assertEquals("Glow colour, swatches, not in use now", Narration.row("Glow colour, swatches", true, "not in use now"));
	}

	@Test
	void anOpenPlaylistNamesItselfWithNothingChosen() {
		assertEquals("Road trip", Narration.section(true, true, "Road trip", "Playlists"));
		assertEquals("Playlists", Narration.section(true, false, "Road trip", "Playlists"));
		assertEquals("", Narration.section(false, true, "Road trip", "Playlists"));
		// the words said with nothing chosen: the name, then what the list is doing
		assertEquals("Road trip, This playlist is empty", Narration.join(Narration.card("Road trip", "", "", "", ""),
				Narration.shelfState(0, true, false, "", "Loading", "This playlist is empty")));
	}

	@Test
	void anEmptyListSaysWhetherItIsLoadingOrEmpty() {
		assertEquals("Loading", Narration.shelfState(0, false, false, "", "Loading", "No liked songs yet"));
		assertEquals("Loading", Narration.shelfState(0, true, true, "", "Loading", "No liked songs yet"));
		assertEquals("No liked songs yet", Narration.shelfState(0, true, false, "", "Loading", "No liked songs yet"));
		assertEquals("", Narration.shelfState(3, true, false, "", "Loading", "No liked songs yet"));
		assertEquals("", Narration.shelfState(0, true, false, "Couldn't load", "Loading", "No liked songs yet"));
	}

	@Test
	void theSearchBoxSaysHowManyResults() {
		assertEquals("", Narration.resultCount(false, false, 0, "Searching", "Nothing found", "1 result", "5 results"));
		assertEquals("Searching", Narration.resultCount(true, true, 0, "Searching", "Nothing found", "1 result", "5 results"));
		assertEquals("Nothing found", Narration.resultCount(true, false, 0, "Searching", "Nothing found", "1 result", "5 results"));
		assertEquals("1 result", Narration.resultCount(true, false, 1, "Searching", "Nothing found", "1 result", "5 results"));
		assertEquals("5 results", Narration.resultCount(true, false, 5, "Searching", "Nothing found", "1 result", "5 results"));
		assertEquals("Search, text box, blue, 5 results",
				Narration.join(Narration.searchBox("Search", "text box", "blue"), "5 results"));
	}

	@Test
	void aTileSaysWhatEnterDoesWithIt() {
		assertEquals("Blue, Ana, 3 of 50, button", Narration.tile(Narration.card("", "", "Blue", "Ana", "3 of 50"), "button"));
		assertEquals("Mix, 12 songs, 1 of 4, Press Enter to open",
				Narration.tile(Narration.card("", "", "Mix", "12 songs", "1 of 4"), "Press Enter to open"));
	}

	@Test
	void theTimingRowSaysWhichWayIsWhich() {
		Controls.Row timing = new Controls.Slider("Timing", "Late? Move right. Early? Left", -5000, 5000, 50,
				() -> 500, v -> {
				}, SkyLyricsScreen::seconds);
		assertEquals("Timing, slider, +0.50 seconds", timing.narration());
		assertEquals("Timing, slider, +0.50 seconds", timing.narrateHint().narration());
		assertEquals("Late? Move right. Early? Left", timing.hint());
	}

	@Test
	void theSignInCardSaysItsTextAndTheButtonEnterPresses() {
		String text = "Sign in on the Music tab";
		assertEquals("Sign in on the Music tab, Open Music tab, button", Narration.signIn(text, "Open Music tab", "Try made-up songs", -1, "button"));
		assertEquals("Sign in on the Music tab, Open Music tab, button", Narration.signIn(text, "Open Music tab", "Try made-up songs", 0, "button"));
		assertEquals("Sign in on the Music tab, Try made-up songs, button", Narration.signIn(text, "Open Music tab", "Try made-up songs", 1, "button"));
	}

	@Test
	void theSongPlayingNowIsSaidSo() {
		assertEquals("Ana, Now playing", Narration.underPlaying("Ana", true, "Now playing"));
		assertEquals("Ana", Narration.underPlaying("Ana", false, "Now playing"));
		assertEquals("Blue, Ana, Now playing, 3 of 50",
				Narration.card("", "", "Blue", Narration.underPlaying("Ana", true, "Now playing"), "3 of 50"));
	}

	@Test
	void aSectionSaysWhereItSits() {
		assertEquals("Playlists, 3 of 3", Narration.section(true, false, "Mix", Narration.join("Playlists", "3 of 3")));
		assertEquals("Mix", Narration.section(true, true, "Mix", Narration.join("Playlists", "3 of 3")));
	}

	@Test
	void resultsAreCountedAfterTypingOrAPickedRecentSearch() {
		assertTrue(Narration.countsResults(true, false));
		assertTrue(Narration.countsResults(false, true));
		assertFalse(Narration.countsResults(false, false));
	}

	@Test
	void aHintIsSaidWithTheRowOnlyWhenAskedFor() {
		assertEquals("Timing, slider, +0.50 seconds, Late? Move right", Narration.hinted("Timing, slider, +0.50 seconds", "Late? Move right"));
		assertEquals("Timing, slider, +0.50 seconds", Narration.hinted("Timing, slider, +0.50 seconds", ""));
	}

	@Test
	void aRowSaysItsHintApartFromItsNarration() {
		Controls.Row row = new Controls.Row("Timing", "Late? Move right") {
			@Override
			int controlWidth(net.minecraft.client.gui.Font font) {
				return 0;
			}

			@Override
			void renderControl(Controls.Frame f) {
			}
		}.narrateHint();
		assertEquals("Late? Move right", row.hint());
		assertEquals("Timing", row.narration());
	}

	@Test
	void thePreviewSaysItsOwnWordsWhileItShows() {
		String hint = "Previewing. Click or press any key to go back.";
		// the keyboard is still on Preview, but the panel is gone: the Narrator reads the hint, not the button again
		assertEquals(hint, Narration.previewOr(true, hint, "Preview, button"));
		// back from the preview, the button is said again
		assertEquals("Preview, button", Narration.previewOr(false, hint, "Preview, button"));
	}

	@Test
	void aRowTheKeyboardLandsOnSaysItsPlaceBeforeItsHint() {
		// vanilla says a list entry's title, then its position, then its hint
		assertEquals("Timing, slider, +0.50 seconds, Selected list row 4 out of 7, Late? Move right",
				Narration.landed("Timing, slider, +0.50 seconds", "Selected list row 4 out of 7", "Late? Move right"));
		assertEquals("Glow, switch, on, Selected list row 2 out of 3", Narration.landed("Glow, switch, on", "Selected list row 2 out of 3", ""));
		// a lone row (or the Lyrics switch in the sidebar) has no place
		assertEquals("Lyrics, switch, on", Narration.landed("Lyrics, switch, on", "", ""));
	}

	@Test
	void escInTheSearchSaysItClearedAndWhereTheKeyboardIs() {
		// on a row: the row
		assertEquals("Search cleared, Glow, switch, on, Selected list row 2 out of 3",
				Narration.searchCleared(true, "Search cleared", "Glow, switch, on, Selected list row 2 out of 3", "Look, tab, 2 of 5"));
		// on nothing (the row it was on is on another tab): the tab, so it is clear the settings are still open
		assertEquals("Search cleared, Look, tab, 2 of 5", Narration.searchCleared(true, "Search cleared", "", "Look, tab, 2 of 5"));
		// Esc in an empty box cleared nothing: only where the keyboard is
		assertEquals("Lyrics, tab, 1 of 5", Narration.searchCleared(false, "Search cleared", "", "Lyrics, tab, 1 of 5"));
		assertEquals("Done, button", Narration.searchCleared(false, "Search cleared", "Done, button", "Lyrics, tab, 1 of 5"));
	}

	@Test
	void aListThatHasFinishedLoadingSaysHowManyCardsItHas() {
		// Nothing chosen yet: once the songs are there the Narrator says how many, where it said nothing after "Loading"
		assertEquals("300 songs", Narration.listState("", 300, "1 song", "300 songs"));
		assertEquals("1 song", Narration.listState("", 1, "1 song", "1 song"));
		assertEquals("6 playlists", Narration.listState("", 6, "1 playlist", "6 playlists"));
		assertEquals("Liked songs, 2 of 3, 300 songs",
				Narration.join(Narration.card("Liked songs, 2 of 3", "", "", "", ""), Narration.listState("", 300, "1 song", "300 songs")));
		// Still loading, empty, or a search's own count: those words, not a count
		assertEquals("Loading", Narration.listState("Loading", 0, "1 song", "0 songs"));
		assertEquals("No liked songs yet", Narration.listState("No liked songs yet", 0, "1 song", "0 songs"));
		assertEquals("12 results", Narration.listState("12 results", 12, "1 result", "12 results"));
		assertEquals("", Narration.listState("", 0, "1 song", "0 songs"));
	}

	@Test
	void theLineUnderTheSongsSaysWhyTheNextPageFailedAndThatEnterTriesAgain() {
		assertEquals("Can't reach the internet or Spotify, Try again, button",
				Narration.footer(false, "Loading", "Can't reach the internet or Spotify", "Try again", "button"));
		// Enter pressed: the page is being tried again
		assertEquals("Loading", Narration.footer(true, "Loading", "", "Try again", "button"));
	}
}
