package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

/** An Arc word's glow submits no more copies than a faint glow needs. */
class ArcGlowTest {
	private static final class Counting implements LyricsRenderer.Canvas {
		int copies;

		@Override
		public void push(float x, float y, float scale, float angle) {
		}

		@Override
		public void pop() {
		}

		@Override
		public void letters(float x, float y, FormattedCharSequence seq, boolean shadow, int argb, int behind) {
			copies++;
		}
	}

	private static int copies(float glow, float alpha, boolean light) {
		Counting c = new Counting();
		LyricsRenderer.glowRing(c, 0, 0, FormattedCharSequence.forward("word", Style.EMPTY), glow, alpha, 0xFFFFFF, light);
		return c.copies;
	}

	@Test
	void faintGlowDropsToTheCopiesLinesUse() {
		assertEquals(6, copies(1, 1, false), "strong glow keeps six");
		assertEquals(6, copies(0.8f, 0.5f, false), "exactly the threshold side: 0.4 and up keeps six");
		assertEquals(4, copies(1, 0.3f, false), "a dim next word");
		assertEquals(4, copies(0.5f, 0.6f, false));
		assertEquals(3, copies(1, 1, true), "light effects keep their small ring");
		assertEquals(3, copies(1, 0.3f, true));
		assertEquals(0, copies(1, 0.01f, false), "too faint to see");
	}
}
