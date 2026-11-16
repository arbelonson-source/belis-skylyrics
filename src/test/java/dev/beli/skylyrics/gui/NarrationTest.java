package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
		assertEquals("Timing, slider, +0.50 seconds, Late? Move right. Early? Left", timing.narrateHint().narration());
	}
}
