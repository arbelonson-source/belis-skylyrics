package dev.beli.skylyrics.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DevKeysTest {
	@Test
	void namedKeysUseGlfwCodes() {
		assertEquals(258, DevKeys.parse("TAB").code());
		assertEquals(257, DevKeys.parse("ENTER").code());
		assertEquals(32, DevKeys.parse("SPACE").code());
		assertEquals(256, DevKeys.parse("ESCAPE").code());
		assertEquals(265, DevKeys.parse("UP").code());
		assertEquals(264, DevKeys.parse("DOWN").code());
		assertEquals(263, DevKeys.parse("LEFT").code());
		assertEquals(262, DevKeys.parse("RIGHT").code());
		assertEquals(266, DevKeys.parse("PAGE_UP").code());
		assertEquals(267, DevKeys.parse("PAGE_DOWN").code());
		assertEquals(268, DevKeys.parse("HOME").code());
		assertEquals(269, DevKeys.parse("END").code());
		assertEquals(259, DevKeys.parse("BACKSPACE").code());
	}

	@Test
	void lettersDigitsAndFunctionKeys() {
		assertEquals(65, DevKeys.parse("A").code());
		assertEquals(70, DevKeys.parse("f").code());
		assertEquals(90, DevKeys.parse("Z").code());
		assertEquals(48, DevKeys.parse("0").code());
		assertEquals(57, DevKeys.parse("9").code());
		assertEquals(290, DevKeys.parse("F1").code());
		assertEquals(301, DevKeys.parse("F12").code());
	}

	@Test
	void modifiersAreBitsInAnyOrderAndCase() {
		assertEquals(0, DevKeys.parse("TAB").modifiers());
		assertEquals(2, DevKeys.parse("TAB ctrl").modifiers());
		assertEquals(1, DevKeys.parse("TAB shift").modifiers());
		assertEquals(4, DevKeys.parse("TAB ALT").modifiers());
		assertEquals(3, DevKeys.parse("tab shift CTRL").modifiers());
		assertEquals(7, DevKeys.parse("F ctrl shift alt").modifiers());
	}

	@Test
	void unknownNamesAndModifiersAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> DevKeys.parse("BANANA"));
		assertThrows(IllegalArgumentException.class, () -> DevKeys.parse("TAB hyper"));
		assertThrows(IllegalArgumentException.class, () -> DevKeys.parse(""));
		assertThrows(IllegalArgumentException.class, () -> DevKeys.parse("F13x"));
	}

	@Test
	void printableKeysTypeACharacter() {
		assertEquals('f', DevKeys.charFor(DevKeys.parse("F")));
		assertEquals('F', DevKeys.charFor(DevKeys.parse("F shift")));
		assertEquals('7', DevKeys.charFor(DevKeys.parse("7")));
		assertEquals(' ', DevKeys.charFor(DevKeys.parse("SPACE")));
	}

	@Test
	void controlKeysAndShortcutsTypeNothing() {
		assertEquals(0, DevKeys.charFor(DevKeys.parse("TAB")));
		assertEquals(0, DevKeys.charFor(DevKeys.parse("ENTER")));
		assertEquals(0, DevKeys.charFor(DevKeys.parse("F ctrl")));
		assertEquals(0, DevKeys.charFor(DevKeys.parse("F alt")));
		assertEquals(0, DevKeys.charFor(DevKeys.parse("F5")));
	}
}
