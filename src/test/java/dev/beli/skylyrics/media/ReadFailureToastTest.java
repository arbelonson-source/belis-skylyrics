package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReadFailureToastTest {
	private static final long SECOND = 1_000_000_000L;

	@Test
	void notBeforeTenSecondsOfFailing() {
		assertFalse(MediaTracker.toastDue(0, false));
		assertFalse(MediaTracker.toastDue(9 * SECOND, false));
	}

	@Test
	void afterTenSecondsOfFailing() {
		assertTrue(MediaTracker.toastDue(10 * SECOND, false));
		assertTrue(MediaTracker.toastDue(60 * SECOND, false));
	}

	@Test
	void onlyOncePerSession() {
		assertFalse(MediaTracker.toastDue(60 * SECOND, true));
	}
}
