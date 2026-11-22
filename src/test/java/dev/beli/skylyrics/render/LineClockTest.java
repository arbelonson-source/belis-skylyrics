package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LineClockTest {
	private static final long SETTLE = 450_000_000L;
	private static final long MS = 1_000_000L;

	@Test
	void aNewLineRestartsTheClockEvenWhenOnlyTheFrameCallRuns() {
		// A World-layout frame: nothing but the per-frame call feeds the clock
		LineClock clock = new LineClock();
		clock.frame(true, false, 0, 1_000 * MS, SETTLE);
		clock.frame(true, false, 1, 5_000 * MS, SETTLE);
		assertEquals(0f, clock.secondsSince(5_000 * MS), 1e-6f);
		assertEquals(0.25f, clock.secondsSince(5_250 * MS), 1e-4f);
	}

	@Test
	void theSameLineDoesNotRestartTheClock() {
		LineClock clock = new LineClock();
		clock.frame(true, false, 0, 1_000 * MS, SETTLE);
		clock.frame(true, false, 1, 5_000 * MS, SETTLE);
		clock.frame(true, false, 1, 5_100 * MS, SETTLE);
		clock.frame(true, false, 1, 5_200 * MS, SETTLE);
		assertEquals(0.2f, clock.secondsSince(5_200 * MS), 1e-4f);
	}

	@Test
	void callingNoteAgainWithTheSameLineInAFrameIsANoOp() {
		LineClock clock = new LineClock();
		clock.frame(true, false, 3, 1_000 * MS, SETTLE);
		clock.frame(true, false, 4, 2_000 * MS, SETTLE);
		clock.note(4, 2_016 * MS, SETTLE); // what drawLines does after the frame call
		assertEquals(3, clock.previous());
		assertEquals(4, clock.last());
		assertEquals(2_000 * MS, clock.changedAt());
	}

	@Test
	void theFirstLineAfterNewLyricsDoesNotFlash() {
		LineClock clock = new LineClock();
		clock.frame(true, false, 0, 10_000 * MS, SETTLE);
		assertTrue(clock.secondsSince(10_000 * MS) >= 0.45f - 1e-4f);
		clock.reset();
		clock.frame(true, false, 7, 20_000 * MS, SETTLE);
		assertTrue(clock.secondsSince(20_000 * MS) >= 0.45f - 1e-4f);
	}

	@Test
	void aSettledFirstLineLeavesNoTailOfAFlash() {
		// The crosshair, sky and hats read the flash clock straight away: a first line must give them nothing, not the
		// last part of a flash that never started
		LineClock clock = new LineClock();
		clock.frame(true, false, 0, 10_000 * MS, SETTLE);
		for (long ms = 0; ms <= 400; ms += 20) {
			assertEquals(0f, LyricsRenderer.pulse(clock.flashSince((10_000 + ms) * MS)), "flash " + ms + " ms after the first line");
		}
		assertEquals(0x3366CC, CrosshairShape.flash(0x3366CC, clock.flashSince(10_000 * MS)));
		// New lyrics, first line again
		clock.reset();
		clock.frame(true, false, 4, 20_000 * MS, SETTLE);
		assertEquals(0f, LyricsRenderer.pulse(clock.flashSince(20_000 * MS)));
	}

	@Test
	void aLineChangeWhileTheLyricsAreHiddenLeavesNoTailOfAFlash() {
		LineClock clock = new LineClock();
		clock.frame(true, false, 2, 1_000 * MS, SETTLE);
		clock.follow(3, 10_000 * MS, SETTLE);
		for (long ms = 0; ms <= 400; ms += 20) {
			assertEquals(0f, LyricsRenderer.pulse(clock.flashSince((10_000 + ms) * MS)), "flash " + ms + " ms after a hidden line change");
		}
		// Shown again: the next real change flashes in full
		clock.frame(true, false, 3, 11_000 * MS, SETTLE);
		clock.frame(true, false, 4, 12_000 * MS, SETTLE);
		assertEquals(1f, LyricsRenderer.pulse(clock.flashSince(12_000 * MS) + LyricsRenderer.PULSE_RISE), 1e-4f);
	}

	@Test
	void hiddenOrInstrumentalFramesLeaveTheClockAlone() {
		LineClock clock = new LineClock();
		clock.frame(true, false, 0, 1_000 * MS, SETTLE);
		clock.frame(false, false, 1, 2_000 * MS, SETTLE);
		clock.frame(true, true, 2, 3_000 * MS, SETTLE);
		assertEquals(0, clock.last());
	}

	@Test
	void showingTheLyricsAgainMidSongIsNotALineChange() {
		LineClock clock = new LineClock();
		clock.frame(true, false, 2, 1_000 * MS, SETTLE);
		// Hidden for a while: the song moves on to line 5
		clock.follow(3, 10_000 * MS, SETTLE);
		clock.follow(5, 20_000 * MS, SETTLE);
		clock.frame(true, false, 5, 21_000 * MS, SETTLE);
		assertEquals(5, clock.last());
		assertEquals(5, clock.previous());
		assertTrue(clock.secondsSince(21_000 * MS) > 0.4f);
		// A real change afterwards still restarts it
		clock.frame(true, false, 6, 22_000 * MS, SETTLE);
		assertEquals(5, clock.previous());
		assertEquals(0f, clock.secondsSince(22_000 * MS), 1e-6f);
	}
}
