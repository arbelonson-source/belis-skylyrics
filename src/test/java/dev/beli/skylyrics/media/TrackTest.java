package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrackTest {
	@Test
	void aTitleOfOnlyInvisibleCharactersCountsAsNothing() {
		assertTrue(new Track("​‌‍⁠﻿", "A", "", 0, "", "mpv").isNone());
		assertTrue(new Track("ㅤ ⠀", "A", "", 0, "", "mpv").isNone());
	}

	@Test
	void joinersInRealTitlesStay() {
		String family = "👨‍👩‍👧";
		assertEquals(family, new Track(family, "A", "", 0, "", "mpv").title());
		String persian = "می‌خواهم";
		assertEquals(persian, new Track(persian, "A", "", 0, "", "mpv").title());
		assertFalse(new Track("x", "A", "", 0, "", "mpv").isNone());
	}

	@Test
	void capNeverEmptiesANonEmptyTitle() {
		String stack = "e" + "́".repeat(600);
		String cut = Track.cap(stack, 500);
		assertFalse(cut.isEmpty());
		assertEquals(500, cut.length());
		assertFalse(new Track(stack, "A", "", 0, "", "mpv").isNone());
	}
}
