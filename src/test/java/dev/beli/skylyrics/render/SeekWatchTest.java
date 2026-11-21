package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SeekWatchTest {
	private static final long MS = 1_000_000L, SEC = 1_000_000_000L;
	private static final long CLICK = 100 * SEC;

	/** A watch that has seen a frame at song time 60 s, 16 ms of wall time before the click's stamp. */
	private static SeekWatch playing() {
		SeekWatch w = new SeekWatch();
		assertFalse(w.step(60_000, 0, CLICK - 16 * MS));
		return w;
	}

	@Test
	void anAskedSeekRewindsOnceFromAThousandMsDrop() {
		SeekWatch w = playing();
		assertTrue(w.step(59_000, CLICK, CLICK), "asked, 1000 ms drop");
		assertFalse(w.step(58_000, CLICK, CLICK + 16 * MS), "the same click is used up");
	}

	@Test
	void withoutAnAskedSeekItTakesMoreThan1500() {
		SeekWatch w = playing();
		assertFalse(w.step(59_000, 0, CLICK), "1000 ms, no click");
		assertTrue(w.step(57_000, 0, CLICK + 16 * MS), "2000 ms, no click");
	}

	@Test
	void aStalledFrameAfterTheClickDoesNotUseTheClickUp() {
		SeekWatch w = playing();
		assertFalse(w.step(60_600, CLICK, CLICK + 600 * MS), "600 ms stall: the position just moved on");
		assertTrue(w.step(59_600, CLICK, CLICK + 616 * MS), "the real 1000 ms drop");
	}

	@Test
	void anAskedSeekArrivingAsAJumpForwardIsUsedUp() {
		SeekWatch w = playing();
		assertFalse(w.step(63_000, CLICK, CLICK), "+3000 ms in one frame");
		assertFalse(w.step(62_000, CLICK, CLICK + 16 * MS), "a later drop: the click is gone, 1000 < 1500");
	}

	@Test
	void theClickExpiresAndAResetForgetsThePosition() {
		SeekWatch w = playing();
		assertFalse(w.step(59_000, CLICK, CLICK + LyricsRenderer.SEEK_WINDOW_NANOS), "window over: like no click");
		SeekWatch r = playing();
		r.reset();
		assertFalse(r.step(0, CLICK, CLICK), "first step after reset");
	}
}
