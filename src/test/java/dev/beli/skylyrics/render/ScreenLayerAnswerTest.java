package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScreenLayerAnswerTest {
	@Test
	void anAnswerStandsWithinTheSameFrameOnly() {
		assertTrue(ScreenLayer.answerKept(7, 7, 1_000, 2_000));
		assertFalse(ScreenLayer.answerKept(-1, 0, 1_000, 2_000), "nothing asked yet");
		assertFalse(ScreenLayer.answerKept(7, 8, 1_000, 2_000), "a new frame asks again");
	}

	@Test
	void anAnswerDoesNotOutliveItsTimeWhenNoFrameComes() {
		assertTrue(ScreenLayer.answerKept(7, 7, 0, 49_000_000L));
		assertFalse(ScreenLayer.answerKept(7, 7, 0, 50_000_000L), "a menu with no world frames still re-asks");
	}
}
