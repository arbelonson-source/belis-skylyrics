package dev.beli.skylyrics.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SetValueTest {
	@Test
	void twoQuotesMeanTheEmptyStringAndEverythingElseStays() {
		assertEquals("", SetValue.string("\"\""));
		assertEquals("abc", SetValue.string("abc"));
		assertEquals("\"a\"", SetValue.string("\"a\""));
		assertEquals("\"", SetValue.string("\""));
	}
}
