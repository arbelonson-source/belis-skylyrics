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
	void positionCountsFromOne() {
		assertEquals("1 of 3", Narration.position("%s of %s", 0, 3));
		assertEquals("3 of 3", Narration.position("%s of %s", 2, 3));
	}

	@Test
	void aChoiceSaysItsNameKindValueAndPlace() {
		assertEquals("Layout, choice, Arc, 1 of 3", Narration.choice("Layout", "choice", "Arc", "%s of %s", 0, 3));
		assertEquals("Look, tab, 2 of 5", Narration.choice("Look", "tab", "", "%s of %s", 1, 5));
	}

	@Test
	void unitsAreSpokenAsWords() {
		assertEquals("40 percent", Narration.speakable("40%", "percent", "degrees", "times"));
		assertEquals("-10 degrees", Narration.speakable("-10°", "percent", "degrees", "times"));
		assertEquals("1.50 times", Narration.speakable("1.50×", "percent", "degrees", "times"));
		assertEquals("12 blocks", Narration.speakable("12 blocks", "percent", "degrees", "times"));
		assertEquals("+0.50 s", Narration.speakable("+0.50 s", "percent", "degrees", "times"));
	}
}
