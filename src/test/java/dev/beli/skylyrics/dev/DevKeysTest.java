package dev.beli.skylyrics.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DevKeysTest {
	@Test
	void namedKeysAreNormalised() {
		assertEquals("tab", DevKeys.parse("TAB").name());
		assertEquals("enter", DevKeys.parse("ENTER").name());
		assertEquals("space", DevKeys.parse("SPACE").name());
		assertEquals("escape", DevKeys.parse("ESCAPE").name());
		assertEquals("up", DevKeys.parse("UP").name());
		assertEquals("down", DevKeys.parse("DOWN").name());
		assertEquals("left", DevKeys.parse("LEFT").name());
		assertEquals("right", DevKeys.parse("RIGHT").name());
		assertEquals("page_up", DevKeys.parse("PAGE_UP").name());
		assertEquals("page_down", DevKeys.parse("PAGE_DOWN").name());
		assertEquals("home", DevKeys.parse("HOME").name());
		assertEquals("end", DevKeys.parse("END").name());
		assertEquals("backspace", DevKeys.parse("BACKSPACE").name());
	}

	@Test
	void lettersDigitsAndFunctionKeys() {
		assertEquals("a", DevKeys.parse("A").name());
		assertEquals("f", DevKeys.parse("f").name());
		assertEquals("z", DevKeys.parse("Z").name());
		assertEquals("0", DevKeys.parse("0").name());
		assertEquals("9", DevKeys.parse("9").name());
		assertEquals("f1", DevKeys.parse("F1").name());
		assertEquals("f12", DevKeys.parse("F12").name());
	}

	@Test
	void aliasesAndCaseMapToOneName() {
		assertEquals("escape", DevKeys.parse("ESC").name());
		assertEquals("enter", DevKeys.parse("Return").name());
		assertEquals("f24", DevKeys.parse("F24").name());
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
		assertThrows(IllegalArgumentException.class, () -> DevKeys.parse("F25"));
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

	@Test
	void sdlKeycodesAndModifiers() {
		assertEquals('j', DevKeys.sdlKeycode(DevKeys.parse("J"), 13));
		assertEquals(9, DevKeys.sdlKeycode(DevKeys.parse("TAB"), 43));
		assertEquals(27, DevKeys.sdlKeycode(DevKeys.parse("ESC"), 41));
		assertEquals(0x4000004F, DevKeys.sdlKeycode(DevKeys.parse("RIGHT"), 79));
		assertEquals(0x40000050, DevKeys.sdlKeycode(DevKeys.parse("LEFT"), 80));
		assertEquals(0x40000051, DevKeys.sdlKeycode(DevKeys.parse("DOWN"), 81));
		assertEquals(0x40000052, DevKeys.sdlKeycode(DevKeys.parse("UP"), 82));
		assertEquals(1, DevKeys.sdlModifiers(DevKeys.parse("A shift")));
		assertEquals(0x1 | 0x40 | 0x100, DevKeys.sdlModifiers(DevKeys.parse("A shift ctrl alt")));
		assertEquals(7, DevKeys.glfwModifiers(DevKeys.parse("A shift ctrl alt")));
	}
}
