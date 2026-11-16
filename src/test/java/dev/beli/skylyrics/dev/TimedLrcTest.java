package dev.beli.skylyrics.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TimedLrcTest {
	@Test
	void linesComeEveryStepInTurn() {
		String lrc = TimedLrc.of("a | b", 250, 1000);
		assertEquals("[00:00.00]a%n[00:00.25]b%n[00:00.50]a%n[00:00.75]b%n".formatted(), lrc);
	}

	@Test
	void theUsualFourSecondsKeepWholeSecondStamps() {
		String lrc = TimedLrc.of("x", 4000, 70000);
		assertEquals("[01:04.00]x", lrc.lines().toList().get(16));
	}

	@Test
	void aZeroStepIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> TimedLrc.of("x", 0, 1000));
	}
}
