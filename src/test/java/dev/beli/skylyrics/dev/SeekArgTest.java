package dev.beli.skylyrics.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SeekArgTest {
	@Test
	void plainSecondsAreAbsolute() {
		assertEquals(12_500, SeekArg.targetMs("12.5", 40_000, 78_000));
		assertEquals(9_000, SeekArg.targetMs("9", 40_000, 78_000));
	}

	@Test
	void aLeadingSignIsRelative() {
		assertEquals(37_000, SeekArg.targetMs("-3", 40_000, 78_000));
		assertEquals(43_250, SeekArg.targetMs("+3.25", 40_000, 78_000));
	}

	@Test
	void theResultStaysInsideTheSong() {
		assertEquals(0, SeekArg.targetMs("-3", 1_000, 78_000));
		assertEquals(77_999, SeekArg.targetMs("500", 1_000, 78_000));
	}

	@Test
	void junkIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> SeekArg.targetMs("abc", 0, 78_000));
		assertThrows(IllegalArgumentException.class, () -> SeekArg.targetMs("", 0, 78_000));
	}

	@Test
	void nonFiniteValuesAreRefused() {
		assertThrows(IllegalArgumentException.class, () -> SeekArg.targetMs("+Infinity", 1_000, 78_000));
		assertThrows(IllegalArgumentException.class, () -> SeekArg.targetMs("NaN", 1_000, 78_000));
		assertThrows(IllegalArgumentException.class, () -> SeekArg.targetMs("-Infinity", 1_000, 78_000));
	}
}
