package dev.beli.skylyrics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.render.CoverCache;
import org.junit.jupiter.api.Test;

class CoverRetryTest {
	private static final long SEC = 1_000_000_000L;

	@Test
	void neverFailedIsNotRetried() {
		assertFalse(CoverCache.retryDue(0, 500 * SEC));
	}

	@Test
	void failedCoverWaitsThenRetries() {
		assertFalse(CoverCache.retryDue(10 * SEC, 11 * SEC));
		assertFalse(CoverCache.retryDue(10 * SEC, 129 * SEC));
		assertTrue(CoverCache.retryDue(10 * SEC, 130 * SEC));
		assertTrue(CoverCache.retryDue(10 * SEC, 1000 * SEC));
	}
}
