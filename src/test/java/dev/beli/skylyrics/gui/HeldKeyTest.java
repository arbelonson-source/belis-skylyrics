package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;

/**
 * A key held down on the settings' own buttons. The game hands the screen the key's repeats as more presses, from the
 * computer's repeat delay (0.25 to 0.66 s) on, some 30 a second.
 */
class HeldKeyTest {
	private static final int ENTER = InputConstants.KEY_RETURN;
	private static final int SPACE = InputConstants.KEY_SPACE;
	private static final int ESC = InputConstants.KEY_ESCAPE;
	private static final int TAB = InputConstants.KEY_TAB;

	@Test
	void enterHeldOnResetAllPressesItOnce() {
		// Reset all asks to be pressed again and takes a press from 0.4 s on as the answer: Enter held half a second reset
		// every setting, "Click again to reset" not yet read, let alone pressed
		HeldKey keys = new HeldKey();
		assertFalse(keys.isRepeat(ENTER), "the press itself");
		keys.hold(ENTER);
		for (int repeat = 0; repeat < 40; repeat++) {
			assertTrue(keys.isRepeat(ENTER), "repeat " + repeat + " presses nothing");
		}
		keys.released(ENTER);
		assertFalse(keys.isRepeat(ENTER), "let go and pressed again, it presses Reset all again (and resets)");
		keys.hold(ENTER);
		assertTrue(keys.isRepeat(ENTER), "and that one's repeats press nothing either");
	}

	@Test
	void theKeyThatLeftThePreviewDoesNothingElseWhileHeld() {
		// "Click or press any key to go back": Esc held a little long left the preview and closed the settings, Enter or
		// Space held on Preview showed and hid it over and over
		HeldKey keys = new HeldKey();
		assertFalse(keys.isRepeat(ESC));
		keys.hold(ESC);
		assertTrue(keys.isRepeat(ESC));
		keys.released(ESC);
		assertFalse(keys.isRepeat(ESC), "Esc pressed again closes as always");
	}

	@Test
	void otherKeysAndLettingGoOfOthersChangeNothing() {
		HeldKey keys = new HeldKey();
		assertFalse(keys.isRepeat(ENTER), "nothing pressed a button yet");
		assertFalse(keys.isRepeat(ENTER));
		keys.hold(SPACE);
		keys.released(ENTER);
		assertTrue(keys.isRepeat(SPACE), "letting go of another key leaves Space held");
		// The computer repeats only the last key pressed: after Tab, a press of Space is a new one
		assertFalse(keys.isRepeat(TAB));
		assertFalse(keys.isRepeat(SPACE));
	}
}
