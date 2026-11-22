package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TypewriterTest {
	private static final long MS = 1_000_000L;

	@Test
	void aLineTypesOneLetterAtATime() {
		assertEquals(0, LyricsRenderer.typedChars(0, 0, 30));
		assertEquals(10, LyricsRenderer.typedChars(10 * LyricsRenderer.TYPE_NS, 0, 30));
		// Sung slower than it types: the typing sets the pace, ahead of the singing
		assertEquals(10, LyricsRenderer.typedChars(10 * LyricsRenderer.TYPE_NS, 0.1f, 30));
	}

	@Test
	void aLineSungFasterThanItTypesIsNeverBehindTheSinging() {
		// 80 letters sung in 1.5 s: typing one every 28 ms would take 2.24 s, so the karaoke fill ran into letters not
		// typed yet and the line's end never came before the next line took its place
		int total = 80;
		long lineMs = 1_500;
		for (long ms = 0; ms <= lineMs; ms += 50) {
			float singing = LyricsRenderer.progress(ms, 0, lineMs);
			int typed = LyricsRenderer.typedChars(ms * MS, singing, total);
			assertTrue(typed >= Math.min(total, singing * total), ms + " ms in: " + typed + " typed, " + singing * total + " sung");
		}
		assertEquals(total, LyricsRenderer.typedChars(lineMs * MS, 1, total), "all typed once it is sung");
	}

	@Test
	void seekingIntoTheMiddleOfALineShowsWhatHasBeenSung() {
		// A seek (or lyrics coming in) halfway through a line starts its typing where the singing is, not at the first letter
		assertTrue(LyricsRenderer.typedChars(0, 0.5f, 40) >= 20);
		// Past the end of the singing (progress runs on to 2) it is simply all there
		assertEquals(40, LyricsRenderer.typedChars(0, 2, 40));
	}
}
