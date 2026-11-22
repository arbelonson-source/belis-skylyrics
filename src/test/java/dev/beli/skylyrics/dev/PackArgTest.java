package dev.beli.skylyrics.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PackArgTest {
	@Test
	void aPackIsAnIdAndOnOrOff() {
		assertEquals(new PackArg.Pack("high_contrast", true), PackArg.parse("high_contrast on"));
		assertEquals(new PackArg.Pack("programmer_art", false), PackArg.parse("  programmer_art   OFF "));
		assertEquals(new PackArg.Pack("file/tall.zip", true), PackArg.parse("file/tall.zip true"));
	}

	@Test
	void onAndOffInAnyCase() {
		assertTrue(PackArg.onOff("On"));
		assertTrue(PackArg.onOff("true"));
		assertFalse(PackArg.onOff("OFF"));
		assertFalse(PackArg.onOff(" false "));
	}

	@Test
	void junkIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> PackArg.parse(""));
		assertThrows(IllegalArgumentException.class, () -> PackArg.parse("high_contrast"));
		assertThrows(IllegalArgumentException.class, () -> PackArg.parse("high_contrast maybe"));
		assertThrows(IllegalArgumentException.class, () -> PackArg.parse("high contrast on"));
		assertThrows(IllegalArgumentException.class, () -> PackArg.onOff("yes"));
	}
}
