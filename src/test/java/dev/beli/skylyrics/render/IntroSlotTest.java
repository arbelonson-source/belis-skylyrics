package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.lyrics.LyricLine;
import dev.beli.skylyrics.lyrics.Lyrics;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Lines layout before the first line. When the singing starts within 2.5 s there are no little notes to wait with,
 * but the first line still waits in the next line's place, below the middle, and the Rise animation takes it up from
 * there: it must not sit in the middle first and drop a whole slot as the second line comes in.
 */
class IntroSlotTest {
	/** Made-up lines; the first one comes 2 s in, like the demo song's. */
	private static final Lyrics SOON = song(2_000);
	/** The same with a 5 s wait, which shows the little notes. */
	private static final Lyrics LATER = song(5_000);

	private static Lyrics song(long first) {
		return new Lyrics(List.of(
				new LyricLine(first, "first made up line"),
				new LyricLine(first + 4_000, "second made up line"),
				new LyricLine(first + 8_000, "third made up line")), true, false);
	}

	/**
	 * Where the middle of line {@code line} sits relative to the anchor (positive is lower) in the stack the Lines layout
	 * builds around {@code center}, slid down by {@code shift} slots, with every line one row of 10 tall.
	 */
	private static float y(Lyrics lyrics, int center, float shift, int line) {
		SkyLyricsConfig cfg = new SkyLyricsConfig(); // previous and next lines shown
		float[] r = new float[5];
		int n = 0, at = -1;
		for (int i = center - 2; i <= center + 2; i++) {
			float slot = (i - center) + shift;
			if (slot <= -2 || slot >= 2 || LyricsRenderer.slotAlpha(cfg, slot) <= 0.01f || !LyricsRenderer.hasSlot(lyrics, i)) {
				continue;
			}
			if (i == line) {
				at = n;
			}
			r[n++] = slot;
		}
		assertTrue(at >= 0, "line " + line + " is in the stack");
		float[] heights = new float[n];
		Arrays.fill(heights, 10);
		return LyricsRenderer.stack(Arrays.copyOf(r, n), heights)[at];
	}

	@Test
	void theFirstLineWaitsBelowTheMiddleWithOrWithoutNotes() {
		float withNotes = y(LATER, -1, 0, 0);
		assertTrue(withNotes > 0, "below the notes: " + withNotes);
		assertEquals(withNotes, y(SOON, -1, 0, 0), 1e-4f, "in the same place when there are no notes to show");
	}

	@Test
	void theRiseTakesTheFirstLineUpWithoutAHop() {
		float waiting = y(SOON, -1, 0, 0);
		// The Rise starts with the stack slid down a whole slot and brings it up to 0; in steps of a hundredth of the
		// way, the line may move a little each step (two slots are 15 apart here), never most of a slot at once
		float last = waiting;
		for (float shift = 1; shift >= 0; shift -= 0.01f) {
			float now = y(SOON, 0, Math.max(0, shift), 0);
			assertTrue(Math.abs(now - last) < 0.75f,
					"at shift " + shift + " the first line jumped from " + last + " to " + now);
			last = now;
		}
		assertEquals(0, y(SOON, 0, 0, 0), 1e-4f, "it ends in the middle");
	}

	@Test
	void theLittleNotesShowOnlyForAWaitOfTwoAndAHalfSeconds() {
		assertFalse(LyricsRenderer.introNotes(2_000));
		assertFalse(LyricsRenderer.introNotes(2_499));
		assertTrue(LyricsRenderer.introNotes(2_500));
		assertTrue(LyricsRenderer.introNotes(5_000));
		assertFalse(LyricsRenderer.hasSlot(SOON, -2));
		assertFalse(LyricsRenderer.hasSlot(SOON, 3));
		assertFalse(LyricsRenderer.hasSlot(Lyrics.NONE, -1), "no lines, no wait before them");
	}

	/** Once the singing has started, the stack is as it always was: the line being sung in the middle, the others a slot away. */
	@Test
	void midSongTheStackIsUnchanged() {
		assertEquals(0, y(SOON, 1, 0, 1), 1e-4f);
		assertEquals(-15, y(SOON, 1, 0, 0), 1e-4f);
		assertEquals(15, y(SOON, 1, 0, 2), 1e-4f);
		assertEquals(0, y(LATER, 0, 0, 0), 1e-4f);
	}
}
