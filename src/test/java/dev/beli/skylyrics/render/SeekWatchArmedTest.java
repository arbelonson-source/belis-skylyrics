package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** An asked seek that brought no jump back leaves nothing behind: a later drop is judged as if nobody asked. */
class SeekWatchArmedTest {
	private static final long MS = 1_000_000L, SEC = 1_000_000_000L;
	private static final long CLICK = 100 * SEC;
	private static final long FRAME = 16 * MS;

	/** Plays on from song time {@code startMs} at wall time {@code startNanos} for {@code nanos}, one frame at a time; the position keeps pace with the clock. Returns whether any frame rewound. */
	private static boolean play(SeekWatch w, long startMs, long startNanos, long nanos, long click) {
		boolean rewound = false;
		for (long t = FRAME; t <= nanos; t += FRAME) {
			rewound |= w.step(startMs + t / MS, click, startNanos + t);
		}
		return rewound;
	}

	private static SeekWatch playing() {
		SeekWatch w = new SeekWatch();
		assertFalse(w.step(60_000, 0, CLICK - FRAME));
		return w;
	}

	@Test
	void aDropAfterTheWindowOfAnAskedSeekThatNeverJumpedIsNotARewind() {
		for (long drop : new long[] {600, 1000, 1400}) {
			SeekWatch w = playing();
			long now = CLICK;
			assertFalse(w.step(60_016, CLICK, now), "the click frame, normal play");
			// normal play to well past the window
			long afterWindow = LyricsRenderer.SEEK_WINDOW_NANOS + 520 * MS;
			assertFalse(play(w, 60_016, now, afterWindow, CLICK), "normal play must not rewind");
			long posAfter = 60_016 + afterWindow / MS;
			assertFalse(w.step(posAfter - drop, CLICK, now + afterWindow + FRAME),
					"drop " + drop + " ms after the window, no jump back inside it: an unasked drop needs more than "
							+ LyricsRenderer.SEEK_BACK_UNASKED_MS + " ms, an old click must not lower that to " + LyricsRenderer.SEEK_BACK_MS);
		}
	}

	@Test
	void aDropJustInsideTheWindowStillCountsAndOneJustOutsideDoesNot() {
		long window = LyricsRenderer.SEEK_WINDOW_NANOS;
		SeekWatch inside = playing();
		assertFalse(inside.step(60_016, CLICK, CLICK));
		assertTrue(inside.step(59_416, CLICK, CLICK + window - FRAME), "600 ms drop, one frame inside the window (position " + 59_416 + ")");
		SeekWatch outside = playing();
		assertFalse(outside.step(60_016, CLICK, CLICK));
		assertFalse(outside.step(59_416, CLICK, CLICK + window + FRAME), "600 ms drop, one frame after the window");
	}

	@Test
	void anAskedSeekThatHadNormalPlayForTheWholeWindowThenAnotherAskedSeekRewindsExactlyOnce() {
		SeekWatch w = playing();
		long pos = 60_000;
		long now = CLICK - FRAME;
		int rewinds = 0;
		// first click: normal play for the whole window and a bit
		long end = CLICK + LyricsRenderer.SEEK_WINDOW_NANOS + SEC;
		for (; now < end; now += FRAME) {
			pos += 16;
			if (w.step(pos, CLICK, now + FRAME)) {
				rewinds++;
			}
		}
		assertTrue(rewinds == 0, "no rewind in normal play, got " + rewinds);
		// second click, 600 ms back, then normal play inside its window
		long second = now + SEC;
		pos += 1_000;
		w.step(pos, CLICK, second - FRAME);
		pos -= 600;
		for (int k = 0; k < 40; k++) {
			if (w.step(pos, second, second + k * FRAME)) {
				rewinds++;
			}
			pos += 16;
		}
		assertTrue(rewinds == 1, "the second asked seek rewinds once, got " + rewinds);
		// a further 600 ms drop under the same click is not another rewind
		pos -= 600;
		assertFalse(w.step(pos, second, second + 41 * FRAME), "the click is used up: 600 ms drop again");
	}
}
